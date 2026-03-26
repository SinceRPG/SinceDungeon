package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.party.PartyManager.Party;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.system.WorldGuardHook;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an active dungeon game session running for a Party.
 * Completely re-engineered for concurrency, stage-injection, and multi-player state tracking.
 */
public class DungeonGame {
    private final SinceDungeon plugin;
    private final Party party;
    private final DungeonTemplate template;

    private final Map<UUID, PlayerState> originalStates = new ConcurrentHashMap<>();
    private final List<CopyOnWriteArrayList<DungeonAction>> stages = new CopyOnWriteArrayList<>();
    private final String worldName;

    private World dungeonWorld;
    private volatile int currentStageIndex = 0;
    private volatile boolean isRunning = false;
    private volatile boolean isPreparing = false;
    private volatile boolean stageCompleting = false;
    private volatile boolean isStopping = false;

    private BukkitTask tickTask;
    private long startTime;

    /**
     * Constructs a highly-concurrent DungeonGame instance for a Party.
     *
     * @param plugin   The plugin instance.
     * @param party    The party engaging in the instance.
     * @param template The structural template of the dungeon.
     */
    public DungeonGame(SinceDungeon plugin, Party party, DungeonTemplate template) {
        this.plugin = plugin;
        this.party = party;
        this.template = template;

        // Snapshot states for all online members
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                originalStates.put(uuid, new PlayerState(p));
            }
        }

        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        this.worldName = prefix + party.getLeader().toString().substring(0, 8) + "_" + System.currentTimeMillis() % 10000;
        parseStages();
    }

    private void parseStages() {
        List<Integer> keys = new ArrayList<>(template.stages().keySet());
        Collections.sort(keys);

        for (Integer key : keys) {
            List<Map<String, Object>> rawActions = template.stages().get(key);
            CopyOnWriteArrayList<DungeonAction> actions = new CopyOnWriteArrayList<>();
            for (Map<String, Object> map : rawActions) {
                String type = (String) map.get("type");
                if (type != null) {
                    DungeonAction action = plugin.getDungeonManager().createAction(type, map);
                    if (action != null) actions.add(action);
                }
            }
            stages.add(actions);
        }
    }

    /**
     * Injects a new action into a specific stage safely during runtime via CopyOnWriteArrayList.
     *
     * @param stageIndex The index of the stage.
     * @param action     The action to inject.
     */
    public void injectAction(int stageIndex, DungeonAction action) {
        if (stageIndex >= 0 && stageIndex < stages.size()) {
            stages.get(stageIndex).add(action);

            if (currentStageIndex == stageIndex && isRunning && !stageCompleting) {
                action.announceStart(this);
                action.start(this);
            }
        }
    }

    /**
     * Sends a formatted message to all active party members inside the instance.
     *
     * @param key          The message key from the language file.
     * @param placeholders An array of placeholders and values.
     */
    public void broadcastMessage(String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString(key);
        if (msg == null || msg.isEmpty()) return;

        String prefix = plugin.getMessagesFile().getString("prefix", "");
        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }

        final String finalMsg = prefix + msg;
        forEachOnlinePlayer(p -> p.sendMessage(ColorUtils.parse(finalMsg)));
    }

    /**
     * Executes a consumer function on all online players currently associated with this session.
     * Prevents allocation of new collections during tick loops.
     *
     * @param action The consumer to execute.
     */
    public void forEachOnlinePlayer(java.util.function.Consumer<Player> action) {
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && (dungeonWorld == null || p.getWorld().equals(dungeonWorld))) {
                action.accept(p);
            }
        }
    }

    /**
     * Initiates asynchronous generation of the dungeon world and sequence logic.
     */
    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;

        Title title = Title.title(
                ColorUtils.parse(plugin.getMessagesFile().getString("game.title.loading_main", "<yellow><bold>LOADING...")),
                ColorUtils.parse(plugin.getMessagesFile().getString("game.title.loading_sub", "<gray>Please wait a moment")),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        forEachOnlinePlayer(p -> p.showTitle(title));
        broadcastMessage("lobby.preparing");

        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isStopping || party.getMembers().isEmpty()) {
                        WorldManager.unloadAndDeleteWorld(plugin, world);
                        return;
                    }

                    this.dungeonWorld = world;
                    dungeonWorld.setGameRule(GameRules.SPAWN_MOBS, false);
                    dungeonWorld.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                    dungeonWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
                    dungeonWorld.setGameRule(GameRules.ADVANCE_TIME, false);
                    dungeonWorld.setAutoSave(false);

                    startCountdown();
                }))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        broadcastMessage("error.create_failed");
                        plugin.getLogger().severe("Dungeon world async creation failed: " + ex.getMessage());
                        stop(true);
                    });
                    return null;
                });
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int count = plugin.getConfigFile().getInt("dungeon.lobby-countdown", 5);

            @Override
            public void run() {
                if (isStopping) {
                    cancel();
                    return;
                }
                if (count <= 0) {
                    enterDungeon();
                    cancel();
                    return;
                }

                String titleMain = plugin.getMessagesFile().getString("game.title.countdown_main", "<red><bold><time>").replace("<time>", String.valueOf(count));
                String titleSub = plugin.getMessagesFile().getString("game.title.countdown_sub", "<gold>Prepare for battle!");
                Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));

                forEachOnlinePlayer(p -> p.showTitle(title));
                broadcastMessage("lobby.countdown", "<time>", String.valueOf(count));
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void enterDungeon() {
        isPreparing = false;
        isRunning = true;

        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            WorldGuardHook.applyDungeonFlags(dungeonWorld);
        }

        Location spawnLoc = dungeonWorld.getSpawnLocation().add(0.5, 1, 0.5);

        forEachOnlinePlayer(p -> {
            if (p.isInsideVehicle()) p.leaveVehicle();
            if (p.isDead()) p.spigot().respawn();

            double maxHealth = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            p.setHealth(maxHealth);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.SURVIVAL);

            p.teleportAsync(spawnLoc).thenAccept(success -> {
                if (success) {
                    Title title = Title.title(
                            ColorUtils.parse(plugin.getMessagesFile().getString("game.title.start_main", "<red><bold>START!")),
                            ColorUtils.parse(plugin.getMessagesFile().getString("game.title.start_sub", "<white>Good luck")),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                    );
                    p.showTitle(title);
                }
            });
        });

        broadcastMessage("game.start");
        this.startTime = System.currentTimeMillis();

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    cancel();
                    return;
                }
                runTick();
            }
        }.runTaskTimer(plugin, 4L, 4L);

        startStage(0);
    }

    private void runTick() {
        if (stageCompleting || currentStageIndex >= stages.size()) return;

        boolean allCompleted = true;
        StringBuilder objectiveText = new StringBuilder();
        String objSeparator = plugin.getMessagesFile().getString("game.hud.objective_separator", " <dark_gray>| ");

        for (DungeonAction action : stages.get(currentStageIndex)) {
            if (!action.isCompleted()) {
                if (action instanceof Tickable) {
                    try {
                        ((Tickable) action).onTick(this);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Tick error in action: " + e.getMessage());
                    }
                }
                if (!action.isCompleted()) {
                    allCompleted = false;
                    if (!objectiveText.isEmpty()) objectiveText.append(objSeparator);
                    objectiveText.append(action.getObjectiveText());
                }
            }
        }

        if (!allCompleted && !objectiveText.isEmpty()) {
            String objPrefix = plugin.getMessagesFile().getString("game.hud.objective_prefix", "<gold><bold>OBJECTIVES: <reset>");
            final String barData = objPrefix + objectiveText;
            forEachOnlinePlayer(p -> p.sendActionBar(ColorUtils.parse(barData)));
        }

        if (allCompleted) checkCompletion();
    }

    private void startStage(int index) {
        if (!isRunning) return;

        if (index >= stages.size()) {
            finishDungeon();
            return;
        }
        currentStageIndex = index;
        this.stageCompleting = false;

        broadcastMessage("game.stage_start", "<stage>", String.valueOf(index + 1));

        for (DungeonAction action : stages.get(index)) {
            try {
                action.announceStart(this);
                action.start(this);
            } catch (Exception e) {
                plugin.getLogger().severe("Error starting action in stage " + index + ": " + e.getMessage());
            }
        }
    }

    /**
     * Propagates Bukkit events to active actions.
     *
     * @param event The Bukkit event.
     */
    public void onEvent(Event event) {
        if (!isRunning || stageCompleting || currentStageIndex >= stages.size()) return;

        boolean allCompleted = true;
        for (DungeonAction action : stages.get(currentStageIndex)) {
            if (!action.isCompleted()) {
                action.onEvent(this, event);
                if (!action.isCompleted()) allCompleted = false;
            }
        }
        if (allCompleted) checkCompletion();
    }

    private void checkCompletion() {
        if (stageCompleting) return;
        stageCompleting = true;

        forEachOnlinePlayer(p -> p.sendActionBar(ColorUtils.parse(" ")));
        broadcastMessage("game.stage_complete", "<stage>", String.valueOf(currentStageIndex + 1));

        DungeonStageCompleteEvent stageEvent = new DungeonStageCompleteEvent(this, currentStageIndex);
        Bukkit.getPluginManager().callEvent(stageEvent);

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(currentStageIndex + 1), 60L);
    }

    private void finishDungeon() {
        broadcastMessage("game.finish");
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int chestCount = 1;

        for (Map.Entry<Integer, Integer> entry : template.rewardTiers().entrySet()) {
            if (elapsedSeconds <= entry.getKey()) {
                chestCount = Math.max(chestCount, entry.getValue());
            }
        }

        int finalElapsed = (int) elapsedSeconds;
        isRunning = false;
        if (tickTask != null) tickTask.cancel();

        DungeonFinishEvent finishEvent = new DungeonFinishEvent(this, finalElapsed, chestCount);
        Bukkit.getPluginManager().callEvent(finishEvent);
        int finalChestCount = finishEvent.getChestCount();

        Title title = Title.title(
                ColorUtils.parse(plugin.getMessagesFile().getString("game.title.finish_main", "<green><bold>CLEARED!")),
                ColorUtils.parse(plugin.getMessagesFile().getString("game.title.finish_sub", "<yellow>Time: <time> seconds").replace("<time>", String.valueOf(finalElapsed))),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
        );

        forEachOnlinePlayer(p -> {
            p.showTitle(title);
            p.sendActionBar(ColorUtils.parse(" "));

            if (finalChestCount > 0) {
                net.danh.sinceDungeon.reward.RewardSessionManager.addSession(p, new net.danh.sinceDungeon.reward.RewardSession(finalChestCount, template));
            }

            PlayerState state = originalStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            p.teleportAsync(targetLoc).thenAccept(success -> {
                if (success) {
                    state.restore(p);
                    if (finalChestCount > 0) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> new RewardGUI(plugin).openRewardGUI(p, finalChestCount, template), 10L);
                    }
                }
            });
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false), 100L);
    }

    /**
     * Executes the secure shutdown and entity-sweep sequence.
     * Prevents lock-contention by delaying world deletion.
     *
     * @param teleport True if players need to be actively extracted.
     */
    public void stop(boolean teleport) {
        if (isStopping) return;
        isStopping = true;
        isRunning = false;

        if (tickTask != null) tickTask.cancel();

        if (teleport && dungeonWorld != null) {
            forEachOnlinePlayer(p -> {
                if (p.isInsideVehicle()) p.leaveVehicle();
                PlayerState state = originalStates.get(p.getUniqueId());
                Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                p.teleportAsync(targetLoc).thenAccept(success -> {
                    if (success) state.restore(p);
                });
                p.sendActionBar(ColorUtils.parse(" "));
            });
        }

        if (dungeonWorld != null) {
            World w = dungeonWorld;
            dungeonWorld = null;

            // Explicitly sweep entities to release handles before unload
            for (Entity e : w.getEntities()) {
                if (!(e instanceof Player)) e.remove();
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldManager.unloadAndDeleteWorld(plugin, w);
            }, 40L); // 40-tick delay to allow OS file handles to clear
        }

        for (UUID uuid : party.getMembers()) {
            plugin.getDungeonManager().removeGame(uuid);
        }
    }

    /**
     * Forcefully shuts down the dungeon, ignoring normal checks.
     */
    public void forceShutdown() {
        stop(true);
    }

    /**
     * Gets the active world of this dungeon instance.
     *
     * @return The active World.
     */
    public World getWorld() {
        return dungeonWorld;
    }

    /**
     * Gets the player running the dungeon.
     *
     * @return The player.
     */
    public Player getPlayer() {
        Player p = Bukkit.getPlayer(party.getLeader());
        return p != null ? p : Bukkit.getPlayer(party.getMembers().iterator().next());
    }

    /**
     * Immutable snapshot of a player's entry state.
     */
    private static class PlayerState {
        public final Location location;
        public final GameMode gameMode;
        public final double health;
        public final int foodLevel;

        public PlayerState(Player p) {
            this.location = p.getLocation();
            this.gameMode = p.getGameMode();
            this.health = p.getHealth();
            this.foodLevel = p.getFoodLevel();
        }

        public void restore(Player p) {
            if (!p.isOnline()) return;
            p.setGameMode(gameMode);
            double maxHealth = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            p.setHealth(Math.min(health, maxHealth));
            p.setFoodLevel(foodLevel);
        }
    }
}