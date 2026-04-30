package net.danh.sinceDungeon.models;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.managers.PartyManager;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.managers.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DungeonGame {
    private final SinceDungeon plugin;
    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private final String worldName;
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private UUID initiatorId;
    private Set<Player> participants;
    private DungeonTemplate template;
    private List<CopyOnWriteArrayList<DungeonAction>> stages = new ArrayList<>();
    private World dungeonWorld;
    private int currentStageIndex = 0;
    private int currentActionIndex = 0;
    private boolean isRunning = false;
    private boolean isPreparing = false;
    private boolean stageCompleting = false;
    private boolean isStopping = false;
    private boolean isCleared = false;
    private BukkitTask lobbyTask;
    private BukkitTask tickTask;
    private long startTime;
    private int serverTicksActive = 0;

    public DungeonGame(SinceDungeon plugin, Player initiator, Set<Player> rawParticipants, DungeonTemplate template) {
        this.plugin = plugin;
        this.initiatorId = initiator.getUniqueId();
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
//                    if (ServerVersion.isAtMost(1, 21, 10)) {
//                        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
//                        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
//                        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
//                        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
//                    } else {
//                        world.setGameRule(GameRules.SPAWN_MOBS, false);
//                        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
//                        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
//                        world.setGameRule(GameRules.ADVANCE_TIME, false);
//                    }
                    dungeonWorld.setAutoSave(false);

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
        lobbyTask = new BukkitRunnable() {
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

        Location spawnLoc = dungeonWorld.getSpawnLocation().add(0.5, 1, 0.5);
        boolean saveStats = template.settings().saveAndRestoreStats();

        Set<Player> failedToEnter = new HashSet<>();

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) {
                failedToEnter.add(p);
                continue;
            }

            if (p.isInsideVehicle()) p.leaveVehicle();

            p.setVelocity(new Vector(0, 0, 0));

            p.teleportAsync(spawnLoc).thenAccept(success -> {
                if (success && p.isOnline()) {
                    p.setNoDamageTicks(60);

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

        for (Player failed : failedToEnter) {
            participants.remove(failed);
            plugin.getDungeonManager().removeGame(failed.getUniqueId());
            savedStates.remove(failed.getUniqueId());
        }

        if (participants.isEmpty()) {
            stop(false, DungeonEndEvent.EndReason.FAILED);
            return;
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
                serverTicksActive += 4;
                runTick();
            }
        }.runTaskTimer(plugin, 4L, 4L);

        startStage(0);
    }

    private void runTick() {
        if (stageCompleting || currentStageIndex >= stages.size()) return;

        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
        if (currentActionIndex >= currentStageActions.size()) return;

        DungeonAction action = currentStageActions.get(currentActionIndex);

        if (!action.isCompleted()) {
            if (action instanceof Tickable tickable) {
                try {
                    tickable.onTick(this);
                } catch (Exception e) {
                    plugin.getLogger().warning("Tick error in action: " + e.getMessage());
                }
            }

            if (!action.isCompleted()) {
                String objPrefix = plugin.getMessagesFile().getString("game.hud.objective_prefix", "<gold><bold>MỤC TIÊU: <reset>");
                String objText = action.getObjectiveText();
                for (Player p : participants) {
                    if (p.isOnline() && p.getWorld().equals(dungeonWorld)) {
                        p.sendActionBar(ColorUtils.parse(objPrefix + objText));
                    }
                }
            } else {
                advanceNextAction();
            }
        } else {
            advanceNextAction();
        }
    }

    private void startStage(int index) {
        if (!isRunning) return;

        if (index >= stages.size()) {
            finishDungeon();
            return;
        }

        currentStageIndex = index;
        currentActionIndex = 0;
        this.stageCompleting = false;

        broadcastMessage("game.stage_start", "<stage>", String.valueOf(index + 1));
        participants.forEach(p -> playSound(p, "stage_start", 1f, 1f));

        startCurrentAction();
    }

    private void startCurrentAction() {
        if (!isRunning || stageCompleting) return;
        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);

        if (currentActionIndex >= currentStageActions.size()) {
            checkStageCompletion();
            return;
        }

        DungeonAction action = currentStageActions.get(currentActionIndex);
        try {
            action.announceStart(this);
            action.start(this);
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting action: " + e.getMessage());
            action.forceComplete();
        }

        if (action.isCompleted()) {
            advanceNextAction();
        }
    }

    private void advanceNextAction() {
        currentActionIndex++;
        startCurrentAction();
    }

    public void onEvent(Event event) {
        if (!isRunning || stageCompleting || currentStageIndex >= stages.size()) return;

        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
        if (currentActionIndex >= currentStageActions.size()) return;

        DungeonAction action = currentStageActions.get(currentActionIndex);

        if (!action.isCompleted()) {
            try {
                action.onEvent(this, event);
            } catch (Exception e) {
                plugin.getLogger().warning("Event handling error in action: " + e.getMessage());
            }

            if (action.isCompleted()) {
                advanceNextAction();
            }
        }

        // Track kills for leaderboard
        if (event instanceof EntityDeathEvent ede) {
            Player killer = ede.getEntity().getKiller();
            if (killer != null && participants != null && participants.contains(killer)) {
                playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
            }
        }
    }

    private void checkStageCompletion() {
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

    /**
     * Checks if all active participants are dead or in Spectator mode.
     * If no players are left actively fighting (survival/adventure),
     * the dungeon is considered wiped out and fails immediately.
     */
    public void checkWipeout() {
        boolean allDeadOrSpectating = true;

        for (Player p : participants) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                allDeadOrSpectating = false; // Found at least one player still fighting
                break;
            }
        }

        // If everyone is spectating/dead and the game hasn't ended yet -> Fail the dungeon
        if (allDeadOrSpectating && !isCleared && !isStopping) {
            broadcastMessage("game.wipeout");
            stop(true, DungeonEndEvent.EndReason.FAILED);
        }
    }

    private void finishDungeon() {
        this.isCleared = true;

        broadcastMessage("game.finish");
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int chestCount = 1;

        for (Map.Entry<Integer, Integer> entry : template.rewardTiers().entrySet()) {
            if (elapsedSeconds <= entry.getKey()) chestCount = Math.max(chestCount, entry.getValue());
        }

        int finalElapsed = (int) elapsedSeconds;
        String formattedTime = formatTime(finalElapsed);

        isRunning = false;

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();

        // --- Save leaderboard stats ---
        if (plugin.getTopManager() != null && template != null) {
            String dungeonId = template.id();
            String awardedTo = plugin.getConfigFile().getString("dungeon.top-awarded-to", "ALL_MEMBERS");
            TopManager topManager = plugin.getTopManager();

            // Determine which players are eligible for clear-time and clear-count top
            PartyManager.Party topParty = plugin.getPartyManager().getParty(initiatorId);
            UUID leaderId = topParty != null ? topParty.getLeader() : initiatorId;

            for (Player p : participants) {
                if (!p.isOnline()) continue;
                boolean isLeader = p.getUniqueId().equals(leaderId);

                // Save individual kill count (always per-player, regardless of award setting)
                int kills = playerKills.getOrDefault(p.getUniqueId(), 0);
                topManager.saveKills(dungeonId, p.getUniqueId(), p.getName(), kills);

                // Save clear time and increment clear count based on setting
                if (awardedTo.equalsIgnoreCase("ALL_MEMBERS") || isLeader) {
                    topManager.saveClearTime(dungeonId, p.getUniqueId(), p.getName(), finalElapsed);
                    topManager.incrementClears(dungeonId, p.getUniqueId(), p.getName());
                }
            }
        }

        DungeonFinishEvent finishEvent = new DungeonFinishEvent(this, finalElapsed, chestCount);
        Bukkit.getPluginManager().callEvent(finishEvent);
        int finalChestCount = finishEvent.getChestCount();
        boolean hasRewards = template.rewardPool() != null && !template.rewardPool().isEmpty();

        String shareMode = plugin.getConfigFile().getString("party.reward-share-mode", "EQUAL");
        net.danh.sinceDungeon.guis.reward.RewardGUI rewardHelper = new net.danh.sinceDungeon.guis.reward.RewardGUI(plugin);

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) continue;

            PartyManager.Party party = plugin.getPartyManager().getParty(p.getUniqueId());
            UUID currentLeader = party != null ? party.getLeader() : initiatorId;

            if (shareMode.equalsIgnoreCase("LEADER_ONLY") && !p.getUniqueId().equals(currentLeader)) {
                continue;
            }

            if (finalChestCount > 0 && hasRewards) {
                net.danh.sinceDungeon.guis.reward.RewardSession oldSession = net.danh.sinceDungeon.guis.reward.RewardSessionManager.getSession(p);
                if (oldSession != null && oldSession.getChestCount() > 0) {
                    rewardHelper.forceClaimAll(p, oldSession);
                }
                net.danh.sinceDungeon.guis.reward.RewardSessionManager.addSession(p, new net.danh.sinceDungeon.guis.reward.RewardSession(finalChestCount, template));
            }

            if (p.isInsideVehicle()) p.leaveVehicle();
            p.setVelocity(new Vector(0, 0, 0));

            PlayerState state = savedStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            String titleMain = plugin.getMessagesFile().getString("game.title.finish_main", "<green><bold>CLEARED!");
            String titleSub = plugin.getMessagesFile().getString("game.title.finish_sub", "<yellow>Time: <time>").replace("<time>", formattedTime);
            p.showTitle(Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.sendActionBar(ColorUtils.parse(" "));

            plugin.getDungeonManager().addTransitioning(p.getUniqueId());

            p.teleportAsync(targetLoc).thenAccept(success -> {
                if (success && p.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && !p.isDead()) {
                            if (finalChestCount > 0 && hasRewards) {
                                rewardHelper.openRewardGUI(p, finalChestCount, template);
                            } else {
                                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("game.no_reward")));
                            }
                        }
                    }, 10L);
                } else if (p.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.teleport(targetLoc);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                    });
                }
            });
        }

        int kickDelay = template.settings().kickDelayAfterFinish();
        Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false, DungeonEndEvent.EndReason.CLEARED), kickDelay * 20L);
    }

    /**
     * Handles logic when a player leaves the server, uses a leave command,
     * or is forcefully kicked from the dungeon.
     */
    public void handlePlayerDisconnect(Player p) {
        boolean wasInDungeon = (dungeonWorld != null && p.getWorld().equals(dungeonWorld));

        if (wasInDungeon) {
            if (p.isDead()) {
                p.spigot().respawn();
            }
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.setVelocity(new Vector(0, 0, 0));

            PlayerState state = savedStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            p.teleport(targetLoc);
            restorePlayerState(p);
        }

        if (participants != null) participants.remove(p);
        plugin.getDungeonManager().removeGame(p.getUniqueId());
        savedStates.remove(p.getUniqueId());

        if (participants == null || participants.isEmpty()) {
            if (!isCleared) {
                stop(false, DungeonEndEvent.EndReason.FAILED);
            }
        } else {
            broadcastMessage("game.player_disconnect", "<player>", p.getName());

            checkWipeout();
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

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (lobbyTask != null && !lobbyTask.isCancelled()) lobbyTask.cancel();

        if (participants != null) {
            for (Player p : participants) {
                plugin.getDungeonManager().removeGame(p.getUniqueId());
                if (!p.isOnline()) continue;

                p.sendActionBar(ColorUtils.parse(" "));

                if (dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                    if (teleport) {
                        if (p.isDead()) p.spigot().respawn();

                        if (p.isInsideVehicle()) p.leaveVehicle();
                        p.setVelocity(new Vector(0, 0, 0));
                        PlayerState state = savedStates.get(p.getUniqueId());
                        Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                        plugin.getDungeonManager().addTransitioning(p.getUniqueId());

                        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false)) {
                            String returnServer = plugin.getConfigFile().getString("cross-server.return-server", "lobby");
                            restorePlayerState(p);
                            net.danh.sinceDungeon.utils.BungeeUtils.sendPlayerToServer(p, returnServer);
                        } else {
                            p.teleportAsync(targetLoc).thenAccept(success -> {
                                if (success) {
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                                } else {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        p.teleport(targetLoc);
                                        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                                    });
                                }
                            });
                        }
                    } else {
                        restorePlayerState(p);
                    }
                }
            }
        }

        savedStates.clear();

        if (dungeonWorld != null) {
            World w = dungeonWorld;
            for (Entity entity : w.getEntities()) {
                if (!(entity instanceof Player)) entity.remove();
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldManager.unloadAndDeleteWorld(plugin, w);
                aggressivelyCleanupMemory();
            }, 40L);
        } else {
            aggressivelyCleanupMemory();
        }
    }

    public void forceShutdown() {
        isStopping = true;
        isRunning = false;

        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this, DungeonEndEvent.EndReason.FORCE_STOPPED));

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (lobbyTask != null && !lobbyTask.isCancelled()) lobbyTask.cancel();

        if (participants != null) {
            for (Player p : participants) {
                plugin.getDungeonManager().removeGame(p.getUniqueId());
                if (p.isOnline() && dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                    if (p.isDead()) p.spigot().respawn();

                    if (p.isInsideVehicle()) p.leaveVehicle();
                    p.setVelocity(new Vector(0, 0, 0));

                    PlayerState state = savedStates.get(p.getUniqueId());
                    Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                    plugin.getDungeonManager().addTransitioning(p.getUniqueId());
                    p.teleport(targetLoc);
                    restorePlayerState(p);
                    p.sendActionBar(ColorUtils.parse(" "));
                }
            }
        }

        if (dungeonWorld != null) {
            World w = dungeonWorld;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldManager.forceUnloadAndDelete(plugin, w);
            }, 5L);
        }
        aggressivelyCleanupMemory();
    }

    private void aggressivelyCleanupMemory() {
        if (savedStates != null) savedStates.clear();
        if (stages != null) {
            for (List<DungeonAction> list : stages) {
                for (DungeonAction action : list) {
                    try {
                        action.cleanup(this);
                    } catch (Exception ignored) {
                    }
                }
                list.clear();
            }
            stages.clear();
            stages = null;
        }
        if (participants != null) {
            participants.clear();
            participants = null;
        }
        this.dungeonWorld = null;
        this.initiatorId = null;
        this.template = null;
    }

    public void restorePlayerState(Player p) {
        if (!p.isOnline()) {
            plugin.getDungeonManager().removeTransitioning(p.getUniqueId());
            return;
        }

        PlayerState state = savedStates.get(p.getUniqueId());
        if (state != null) {
            if (template != null && template.settings().saveAndRestoreStats()) {
                p.setGameMode(state.gameMode);
                AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = attr != null ? attr.getValue() : 20.0;

                p.setHealth(Math.max(1.0, Math.min(state.health, maxHealth)));
                p.setFoodLevel(state.foodLevel);

                for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());

                if (state.potionEffects != null) {
                    for (PotionEffect effect : state.potionEffects) {
                        int newDuration = effect.getDuration() - serverTicksActive;
                        if (newDuration > 0) {
                            p.addPotionEffect(new PotionEffect(effect.getType(), newDuration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
                        }
                    }
                }
                p.setFireTicks(state.fireTicks);
            }
            p.setFallDistance(0);
            p.setVelocity(new Vector(0, 0, 0));
        }
        plugin.getDungeonManager().removeTransitioning(p.getUniqueId());
    }

    /**
     * Sends a specialized message respecting action toggles from config.yml and specific overrides.
     *
     * @param action       The action instance calling this
     * @param category     The category of message (e.g., init, progress, complete)
     * @param key          The locale message key
     * @param placeholders Key-value pair replacements
     */
    public void sendActionMessage(DungeonAction action, String category, String key, String... placeholders) {
        String actionName = action != null ? action.getActionType() : "unknown";
        if (actionName == null) actionName = "unknown";

        boolean canShow = plugin.getConfigFile().getBoolean("action-notifications." + actionName.toLowerCase() + "." + category, true);
        if (action != null && action.getNotifications().containsKey(category)) {
            canShow = action.getNotifications().get(category);
        }

        if (canShow) {
            broadcastMessage(key, placeholders);
        }
    }

    public void sendMessage(String key, String... placeholders) {
        broadcastMessage(key, placeholders);
    }

    public Set<Player> getParticipants() {
        return participants != null ? participants : Collections.emptySet();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void broadcastMessage(String key, String... placeholders) {
        if (participants == null || participants.isEmpty()) return;

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
        if (participants == null || participants.isEmpty()) return;

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
        return Bukkit.getPlayer(initiatorId);
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