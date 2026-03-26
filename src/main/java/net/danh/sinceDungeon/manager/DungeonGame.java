package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.system.WorldGuardHook;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/**
 * Represents an active dungeon game session running for a player.
 */
public class DungeonGame {
    private final SinceDungeon plugin;
    private final Player player;
    private final DungeonTemplate template;

    private final Location oldLocation;
    private final GameMode oldGameMode;
    private final double oldHealth;
    private final int oldFoodLevel;
    private final float oldExp;
    private final int oldLevel;

    private final List<List<DungeonAction>> stages = new ArrayList<>();
    private final String worldName;

    private World dungeonWorld;
    private int currentStageIndex = 0;
    private boolean isRunning = false;
    private boolean isPreparing = false;
    private boolean stageCompleting = false;

    private BukkitTask tickTask;
    private long startTime;

    private boolean isStopping = false;

    /**
     * Constructs a new DungeonGame.
     *
     * @param plugin   The plugin instance.
     * @param player   The player taking part in the dungeon.
     * @param template The template used to structure the dungeon.
     */
    public DungeonGame(SinceDungeon plugin, Player player, DungeonTemplate template) {
        this.plugin = plugin;
        this.player = player;
        this.template = template;

        this.oldLocation = player.getLocation();
        this.oldGameMode = player.getGameMode();
        this.oldHealth = player.getHealth();
        this.oldFoodLevel = player.getFoodLevel();
        this.oldExp = player.getExp();
        this.oldLevel = player.getLevel();

        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        this.worldName = prefix + player.getName() + "_" + UUID.randomUUID().toString().substring(0, 8);
        parseStages();
    }

    private void parseStages() {
        List<Integer> keys = new ArrayList<>(template.stages().keySet());
        Collections.sort(keys);

        for (Integer key : keys) {
            List<Map<String, Object>> rawActions = template.stages().get(key);
            List<DungeonAction> actions = new ArrayList<>();
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
     * Gets the template of this dungeon.
     *
     * @return The DungeonTemplate.
     */
    public DungeonTemplate getTemplate() {
        return template;
    }

    /**
     * Gets the parsed stages.
     *
     * @return List of action lists representing stages.
     */
    public List<List<DungeonAction>> getStages() {
        return stages;
    }

    /**
     * Gets the current stage index.
     *
     * @return The current stage index.
     */
    public int getCurrentStageIndex() {
        return currentStageIndex;
    }

    /**
     * Gets the start time of the dungeon in milliseconds.
     *
     * @return The start time.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Checks if the dungeon game is currently running.
     *
     * @return True if running, false otherwise.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Forces the current stage to complete immediately, skipping all objectives.
     */
    public void forceCompleteCurrentStage() {
        if (!isRunning || stageCompleting || currentStageIndex >= stages.size()) return;
        for (DungeonAction action : stages.get(currentStageIndex)) action.forceComplete();
        checkCompletion();
    }

    /**
     * Injects a new action into a specific stage during runtime.
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
     * Sends a formatted message to the player.
     *
     * @param key          The message key from the language file.
     * @param placeholders An array of placeholders and values.
     */
    public void sendMessage(String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString(key);
        String prefix = plugin.getMessagesFile().getString("prefix", "");
        if (msg == null || msg.isEmpty()) return;

        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        player.sendMessage(ColorUtils.parse(prefix + msg));
    }

    private void playConfigSound(String key, float volume, float pitch) {
        String soundName = plugin.getConfigFile().getString("sounds." + key);
        if (soundName == null || soundName.trim().isEmpty()) return;
        soundName = soundName.trim();
        if (soundName.startsWith("minecraft:")) soundName = soundName.substring(10);

        try {
            NamespacedKey nkey = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
            if (nkey == null) nkey = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
            Sound sound = org.bukkit.Registry.SOUND_EVENT.get(nkey);
            if (sound == null) sound = (Sound) Sound.class.getField(soundName.toUpperCase(Locale.ROOT)).get(null);

            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Starts the lobby phase for the dungeon game.
     */
    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;

        String titleMain = plugin.getMessagesFile().getString("game.title.loading_main", "<yellow><bold>LOADING...");
        String titleSub = plugin.getMessagesFile().getString("game.title.loading_sub", "<gray>Please wait a moment");
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500));
        Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), times);
        player.showTitle(title);

        sendMessage("lobby.preparing");

        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isStopping || !player.isOnline()) {
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
                        sendMessage("error.create_failed");
                        plugin.getLogger().severe("Failed to create dungeon world: " + ex.getMessage());
                        plugin.getDungeonManager().removeGame(player.getUniqueId());
                    });
                    return null;
                });
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int count = plugin.getConfigFile().getInt("dungeon.lobby-countdown", 5);

            @Override
            public void run() {
                if (isStopping || !player.isOnline()) {
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

                Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
                Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), times);
                player.showTitle(title);

                sendMessage("lobby.countdown", "<time>", String.valueOf(count));
                playConfigSound("lobby_countdown", 1f, 2f);
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

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);

        player.teleportAsync(spawnLoc).thenAccept(success -> {
            if (success && player.isOnline()) {

                String titleMain = plugin.getMessagesFile().getString("game.title.start_main", "<red><bold>START!");
                String titleSub = plugin.getMessagesFile().getString("game.title.start_sub", "<white>Good luck");
                Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500));
                Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), times);
                player.showTitle(title);

                sendMessage("game.start");
                playConfigSound("game_start", 0.5f, 1f);
                this.startTime = System.currentTimeMillis();

                tickTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!isRunning || !player.isOnline()) {
                            cancel();
                            stop(true);
                            return;
                        }
                        runTick();
                    }
                }.runTaskTimer(plugin, 4L, 4L);

                startStage(0);
            }
        });
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
            player.sendActionBar(ColorUtils.parse(objPrefix + objectiveText));
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

        sendMessage("game.stage_start", "<stage>", String.valueOf(index + 1));
        playConfigSound("stage_start", 1f, 1f);

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

        player.sendActionBar(ColorUtils.parse(" "));

        sendMessage("game.stage_complete", "<stage>", String.valueOf(currentStageIndex + 1));
        playConfigSound("stage_complete", 1f, 1f);

        DungeonStageCompleteEvent stageEvent = new DungeonStageCompleteEvent(this, currentStageIndex);
        Bukkit.getPluginManager().callEvent(stageEvent);

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(currentStageIndex + 1), 60L);
    }

    private void restorePlayerState() {
        if (!player.isOnline()) return;
        player.setGameMode(oldGameMode);
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        player.setHealth(Math.min(oldHealth, maxHealth));
        player.setFoodLevel(oldFoodLevel);
    }

    private void finishDungeon() {
        sendMessage("game.finish");
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

        if (finalChestCount > 0) {
            net.danh.sinceDungeon.reward.RewardSessionManager.addSession(player,
                    new net.danh.sinceDungeon.reward.RewardSession(finalChestCount, template));
        }

        Location targetLoc = oldLocation;
        if (targetLoc.getWorld() == null) {
            targetLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        if (player.isInsideVehicle()) player.leaveVehicle();

        String titleMain = plugin.getMessagesFile().getString("game.title.finish_main", "<green><bold>CLEARED!");
        String titleSub = plugin.getMessagesFile().getString("game.title.finish_sub", "<yellow>Time: <time> seconds").replace("<time>", String.valueOf(finalElapsed));
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500));
        Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), times);

        player.showTitle(title);
        player.sendActionBar(ColorUtils.parse(" "));

        player.teleportAsync(targetLoc).thenAccept(success -> {
            if (success && player.isOnline()) {
                restorePlayerState();
                sendMessage("game.completion_time", "<time>", String.valueOf(finalElapsed));

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        if (finalChestCount > 0) {
                            new RewardGUI(plugin).openRewardGUI(player, finalChestCount, template);
                        } else {
                            sendMessage("game.no_reward");
                        }
                    }
                }, 10L);

                Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false), 100L);
            }
        });
    }

    /**
     * Gracefully stops the dungeon session and cleans up.
     *
     * @param teleport Whether to teleport the player back to their original location.
     */
    public void stop(boolean teleport) {
        if (isStopping) return;
        isStopping = true;
        isRunning = false;

        if (tickTask != null) tickTask.cancel();
        if (player.isOnline()) player.sendActionBar(ColorUtils.parse(" "));

        if (teleport && player.isOnline() && dungeonWorld != null && player.getWorld().equals(dungeonWorld)) {
            Location targetLoc = oldLocation;
            if (targetLoc.getWorld() == null) {
                targetLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            if (player.isInsideVehicle()) player.leaveVehicle();

            player.teleportAsync(targetLoc).thenAccept(success -> {
                if (success) restorePlayerState();
            });
        } else if (player.isOnline() && dungeonWorld != null && player.getWorld().equals(dungeonWorld)) {
            restorePlayerState();
        }

        if (dungeonWorld != null) {
            World w = dungeonWorld;
            dungeonWorld = null;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldManager.unloadAndDeleteWorld(plugin, w);
            }, 20L);
        }

        plugin.getDungeonManager().removeGame(player.getUniqueId());
    }

    /**
     * Forcefully shuts down the dungeon, ignoring normal checks.
     */
    public void forceShutdown() {
        isRunning = false;
        if (tickTask != null) tickTask.cancel();

        if (player.isOnline() && dungeonWorld != null && player.getWorld().equals(dungeonWorld)) {
            Location targetLoc = oldLocation;
            if (targetLoc.getWorld() == null) {
                targetLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            if (player.isInsideVehicle()) player.leaveVehicle();

            player.teleport(targetLoc);
            restorePlayerState();
            player.sendActionBar(ColorUtils.parse(" "));
        }

        if (dungeonWorld != null) {
            WorldManager.forceUnloadAndDelete(plugin, dungeonWorld);
            dungeonWorld = null;
        }

        plugin.getDungeonManager().removeGame(player.getUniqueId());
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
        return player;
    }

    /**
     * Gets the original location of the player before joining.
     *
     * @return The original location.
     */
    public Location getOldLocation() {
        return oldLocation;
    }
}