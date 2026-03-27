package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.system.WorldGuardHook;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DungeonGame {
    private final SinceDungeon plugin;
    private final Player initiator;
    private final Set<Player> participants;
    private final DungeonTemplate template;

    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private final List<CopyOnWriteArrayList<DungeonAction>> stages = new ArrayList<>();

    private final String worldName;
    private World dungeonWorld;
    private int currentStageIndex = 0;
    private boolean isRunning = false;
    private boolean isPreparing = false;
    private boolean stageCompleting = false;
    private boolean isStopping = false;

    private BukkitTask tickTask;
    private long startTime;

    public DungeonGame(SinceDungeon plugin, Player initiator, Set<Player> rawParticipants, DungeonTemplate template) {
        this.plugin = plugin;
        this.initiator = initiator;
        this.participants = ConcurrentHashMap.newKeySet();
        this.participants.addAll(rawParticipants);
        this.template = template;

        for (Player p : participants) {
            savedStates.put(p.getUniqueId(), new PlayerState(p));
        }

        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        this.worldName = prefix + initiator.getName() + "_" + UUID.randomUUID().toString().substring(0, 8);
        parseStages();
    }

    public DungeonTemplate getTemplate() {
        return template;
    }

    public Location getSavedLocation(UUID uuid) {
        PlayerState state = savedStates.get(uuid);
        return state != null ? state.location : null;
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

    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;

        broadcastTitle("game.title.loading_main", "game.title.loading_sub", 200, 3000, 500);
        broadcastMessage("lobby.preparing");

        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isStopping) {
                        WorldManager.unloadAndDeleteWorld(plugin, world);
                        return;
                    }

                    this.dungeonWorld = world;
                    dungeonWorld.setGameRule(GameRules.SPAWN_MOBS, false);
                    dungeonWorld.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                    dungeonWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
                    dungeonWorld.setGameRule(GameRules.ADVANCE_TIME, false);
                    dungeonWorld.setAutoSave(false);

                    // ĐỌC TỪ TEMPLATE THAY VÌ CONFIG
                    if (template.settings().forceDaylightAndClearWeather()) {
                        dungeonWorld.setTime(6000);
                        dungeonWorld.setStorm(false);
                        dungeonWorld.setThundering(false);
                    }

                    startCountdown();
                }))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        broadcastMessage("error.create_failed");
                        plugin.getLogger().severe("Failed to create dungeon world: " + ex.getMessage());
                        stop(true, DungeonEndEvent.EndReason.FORCE_STOPPED);
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

                for (Player p : participants) {
                    if (p.isOnline()) {
                        p.showTitle(Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                        playSound(p, "lobby_countdown", 1f, 2f);
                    }
                }

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
        boolean saveStats = template.settings().saveAndRestoreStats();

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) continue;

            if (p.isInsideVehicle()) p.leaveVehicle();

            p.teleportAsync(spawnLoc).thenAccept(success -> {
                if (success && p.isOnline()) {
                    if (saveStats) {
                        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
                        p.setHealth(attr != null ? attr.getValue() : 20.0);
                        p.setFoodLevel(20);
                        p.setGameMode(GameMode.SURVIVAL);

                        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
                        p.setFireTicks(0);
                    }
                    p.setFallDistance(0);
                }
            });
        }

        broadcastTitle("game.title.start_main", "game.title.start_sub", 200, 2000, 500);
        broadcastMessage("game.start");
        participants.forEach(p -> playSound(p, "game_start", 0.5f, 1f));

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
                if (action instanceof Tickable tickable) {
                    try {
                        tickable.onTick(this);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Tick error in action: " + e.getMessage());
                    }
                }

                if (!action.isCompleted()) {
                    allCompleted = false;
                    if (objectiveText.length() > 0) objectiveText.append(objSeparator);
                    objectiveText.append(action.getObjectiveText());
                }
            }
        }

        if (!allCompleted && objectiveText.length() > 0) {
            String objPrefix = plugin.getMessagesFile().getString("game.hud.objective_prefix", "<gold><bold>OBJECTIVES: <reset>");
            for (Player p : participants) {
                if (p.isOnline() && p.getWorld().equals(dungeonWorld)) {
                    p.sendActionBar(ColorUtils.parse(objPrefix + objectiveText));
                }
            }
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
        participants.forEach(p -> playSound(p, "stage_start", 1f, 1f));

        for (DungeonAction action : stages.get(index)) {
            action.announceStart(this);
            action.start(this);
        }
    }

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

        participants.forEach(p -> {
            if (p.isOnline()) p.sendActionBar(ColorUtils.parse(" "));
        });

        broadcastMessage("game.stage_complete", "<stage>", String.valueOf(currentStageIndex + 1));
        participants.forEach(p -> playSound(p, "stage_complete", 1f, 1f));

        DungeonStageCompleteEvent stageEvent = new DungeonStageCompleteEvent(this, currentStageIndex);
        Bukkit.getPluginManager().callEvent(stageEvent);

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(currentStageIndex + 1), 60L);
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void finishDungeon() {
        broadcastMessage("game.finish");
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int chestCount = 1;

        for (Map.Entry<Integer, Integer> entry : template.rewardTiers().entrySet()) {
            if (elapsedSeconds <= entry.getKey()) chestCount = Math.max(chestCount, entry.getValue());
        }

        int finalElapsed = (int) elapsedSeconds;
        String formattedTime = formatTime(finalElapsed);

        isRunning = false;

        if (tickTask != null) tickTask.cancel();

        DungeonFinishEvent finishEvent = new DungeonFinishEvent(this, finalElapsed, chestCount);
        Bukkit.getPluginManager().callEvent(finishEvent);
        int finalChestCount = finishEvent.getChestCount();

        String shareMode = plugin.getConfigFile().getString("party.reward-share-mode", "EQUAL");

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) continue;

            if (shareMode.equalsIgnoreCase("LEADER_ONLY") && !p.equals(initiator)) {
                continue;
            }

            if (finalChestCount > 0) {
                net.danh.sinceDungeon.reward.RewardSessionManager.addSession(p,
                        new net.danh.sinceDungeon.reward.RewardSession(finalChestCount, template));
            }

            if (p.isInsideVehicle()) p.leaveVehicle();

            PlayerState state = savedStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            String titleMain = plugin.getMessagesFile().getString("game.title.finish_main", "<green><bold>CLEARED!");
            String titleSub = plugin.getMessagesFile().getString("game.title.finish_sub", "<yellow>Time: <time>").replace("<time>", formattedTime);
            p.showTitle(Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.sendActionBar(ColorUtils.parse(" "));

            p.teleportAsync(targetLoc).thenAccept(success -> {
                if (success && p.isOnline()) {
                    restorePlayerState(p);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && !p.isDead()) {
                            if (finalChestCount > 0) {
                                new net.danh.sinceDungeon.reward.RewardGUI(plugin).openRewardGUI(p, finalChestCount, template);
                            } else {
                                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("game.no_reward")));
                            }
                        }
                    }, 10L);
                } else if (p.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.teleport(targetLoc);
                        restorePlayerState(p);
                    });
                }
            });
        }

        // Lấy thời gian đá khỏi Template
        int kickDelay = template.settings().kickDelayAfterFinish();
        Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false, DungeonEndEvent.EndReason.CLEARED), kickDelay * 20L);
    }

    public void handlePlayerDisconnect(Player p) {
        boolean wasInDungeon = (dungeonWorld != null && p.getWorld().equals(dungeonWorld));

        if (wasInDungeon) {
            PlayerState state = savedStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            p.teleport(targetLoc);
            restorePlayerState(p);
        }

        participants.remove(p);
        plugin.getDungeonManager().removeGame(p.getUniqueId());
        savedStates.remove(p.getUniqueId());

        if (participants.isEmpty()) {
            stop(false, DungeonEndEvent.EndReason.FAILED);
        } else {
            broadcastMessage("game.player_disconnect", "<player>", p.getName());
        }
    }

    public void stop(boolean teleport) {
        stop(teleport, DungeonEndEvent.EndReason.FORCE_STOPPED);
    }

    public void stop(boolean teleport, DungeonEndEvent.EndReason reason) {
        if (isStopping) return;
        isStopping = true;
        isRunning = false;

        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this, reason));

        if (tickTask != null) tickTask.cancel();

        for (Player p : participants) {
            plugin.getDungeonManager().removeGame(p.getUniqueId());
            if (!p.isOnline()) continue;

            p.sendActionBar(ColorUtils.parse(" "));

            if (dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                if (teleport) {
                    if (p.isInsideVehicle()) p.leaveVehicle();
                    PlayerState state = savedStates.get(p.getUniqueId());
                    Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                    p.teleportAsync(targetLoc).thenAccept(success -> {
                        if (success) restorePlayerState(p);
                        else {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                p.teleport(targetLoc);
                                restorePlayerState(p);
                            });
                        }
                    });
                } else {
                    restorePlayerState(p);
                }
            }
        }

        savedStates.clear();

        if (dungeonWorld != null) {
            World w = dungeonWorld;
            dungeonWorld = null;

            for (Entity entity : w.getEntities()) {
                if (!(entity instanceof Player)) entity.remove();
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> WorldManager.unloadAndDeleteWorld(plugin, w), 40L);
        }
    }

    public void forceShutdown() {
        isStopping = true;
        isRunning = false;

        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this, DungeonEndEvent.EndReason.FORCE_STOPPED));

        if (tickTask != null) tickTask.cancel();

        for (Player p : participants) {
            plugin.getDungeonManager().removeGame(p.getUniqueId());
            if (p.isOnline() && dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                if (p.isInsideVehicle()) p.leaveVehicle();
                PlayerState state = savedStates.get(p.getUniqueId());
                Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();
                p.teleport(targetLoc);
                restorePlayerState(p);
                p.sendActionBar(ColorUtils.parse(" "));
            }
        }

        if (dungeonWorld != null) {
            WorldManager.forceUnloadAndDelete(plugin, dungeonWorld);
            dungeonWorld = null;
        }
    }

    public void restorePlayerState(Player p) {
        PlayerState state = savedStates.get(p.getUniqueId());
        if (state != null) {
            if (template.settings().saveAndRestoreStats()) {
                p.setGameMode(state.gameMode);
                AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = attr != null ? attr.getValue() : 20.0;

                p.setHealth(Math.max(1.0, Math.min(state.health, maxHealth)));
                p.setFoodLevel(state.foodLevel);

                for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
                if (state.potionEffects != null) {
                    for (PotionEffect effect : state.potionEffects) p.addPotionEffect(effect);
                }
                p.setFireTicks(state.fireTicks);
            }
            p.setFallDistance(0);
        }
    }

    public void sendMessage(String key, String... placeholders) {
        broadcastMessage(key, placeholders);
    }

    public Set<Player> getParticipants() {
        return participants;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void broadcastMessage(String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString(key);
        if (msg == null || msg.isEmpty()) return;

        String prefix = plugin.getMessagesFile().getString("prefix", "");
        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }

        String finalMsg = prefix + msg;
        participants.forEach(p -> {
            if (p.isOnline()) p.sendMessage(ColorUtils.parse(finalMsg));
        });
    }

    public void broadcastTitle(String mainKey, String subKey, int fadeIn, int stay, int fadeOut) {
        String main = plugin.getMessagesFile().getString(mainKey, "");
        String sub = plugin.getMessagesFile().getString(subKey, "");
        Title title = Title.title(ColorUtils.parse(main), ColorUtils.parse(sub), Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut)));
        participants.forEach(p -> {
            if (p.isOnline()) p.showTitle(title);
        });
    }

    private void playSound(Player p, String key, float volume, float pitch) {
        String soundName = plugin.getConfigFile().getString("sounds." + key);
        if (soundName == null || soundName.trim().isEmpty()) return;
        try {
            p.playSound(p.getLocation(), soundName.replace("minecraft:", ""), volume, pitch);
        } catch (Exception ignored) {
        }
    }

    public World getWorld() {
        return dungeonWorld;
    }

    public Player getPlayer() {
        return initiator;
    }

    private static class PlayerState {
        final Location location;
        final GameMode gameMode;
        final double health;
        final int foodLevel;
        final Collection<PotionEffect> potionEffects;
        final int fireTicks;

        PlayerState(Player p) {
            this.location = p.getLocation();
            this.gameMode = p.getGameMode();
            this.health = p.getHealth();
            this.foodLevel = p.getFoodLevel();
            this.potionEffects = p.getActivePotionEffects();
            this.fireTicks = p.getFireTicks();
        }
    }
}