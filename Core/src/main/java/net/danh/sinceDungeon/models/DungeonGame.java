package net.danh.sinceDungeon.models;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.actions.impl.MythicMobWaveAction;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
import net.danh.sinceDungeon.hooks.PAPIHook;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.utils.BungeeUtils;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class DungeonGame {
    private final SinceDungeon plugin;
    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permAttachments = new ConcurrentHashMap<>();
    private final String worldName;
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private final String cachedObjectivePrefix;
    private final String cachedTimeLeftFormat;

    private UUID initiatorId;
    private Set<Player> participants;
    private DungeonTemplate template;
    private List<CopyOnWriteArrayList<DungeonAction>> stages = new ArrayList<>();
    private World dungeonWorld;
    private InstanceProvider instanceProvider;
    private Location instanceOrigin;
    private Location respawnLocation;
    private int instanceRadius = -1;

    private int currentStageIndex = 0;
    private int currentActionIndex = 0;
    private boolean isRunning = false;
    private boolean isPreparing = false;
    private boolean stageCompleting = false;
    private boolean isStopping = false;
    private boolean isCleared = false;

    private SchedulerCompat.TaskHandle lobbyTask;
    private SchedulerCompat.TaskHandle tickTask;
    private SchedulerCompat.TaskHandle kickTask; // Note: Task explicitly tracked to avoid ghost countdowns
    private long startTime;
    private int serverTicksActive = 0;

    private String lastActionBarText = "";
    private Component lastParsedBar = null;

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

        this.cachedObjectivePrefix = plugin.getLanguageManager().getString("game.hud.objective_prefix", "<gold><bold>OBJECTIVES: <reset>");
        this.cachedTimeLeftFormat = plugin.getLanguageManager().getString("game.hud.time_left", " <red>(<time>s)");

        parseStages();
    }

    public DungeonTemplate getTemplate() {
        return template;
    }

    public Location getSavedLocation(UUID uuid) {
        PlayerState state = savedStates.get(uuid);
        return state != null ? state.location : null;
    }

    private void applyMviBypass(Player p) {
        if (p == null || !p.isOnline()) return;

        boolean mviEnabled = Bukkit.getPluginManager().isPluginEnabled("Multiverse-Inventories") || Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core");
        if (!mviEnabled) return;

        if (!permAttachments.containsKey(p.getUniqueId())) {
            PermissionAttachment attachment = p.addAttachment(plugin);

            List<String> bypassPerms = plugin.getConfigFile().getStringList("settings.mvi-bypass-permissions");
            if (bypassPerms == null || bypassPerms.isEmpty()) {
                bypassPerms = Arrays.asList("mvinv.bypass.*", "Multiverse-Inventories.bypass.*");
            }

            for (String perm : bypassPerms) {
                attachment.setPermission(perm, true);
            }

            p.recalculatePermissions();
            permAttachments.put(p.getUniqueId(), attachment);
        }
    }

    private void removeMviBypass(Player p) {
        if (p == null || !p.isOnline()) return;
        PermissionAttachment attachment = permAttachments.remove(p.getUniqueId());
        if (attachment != null) {
            try {
                p.removeAttachment(attachment);
                p.recalculatePermissions();
            } catch (Exception ignored) {
            }
        }
    }

    private void parseStages() {
        List<Integer> keys = new ArrayList<>(template.stages().keySet());
        Collections.sort(keys);

        List<CopyOnWriteArrayList<DungeonAction>> parsedStages = new ArrayList<>();

        for (Integer key : keys) {
            DungeonTemplate.StageData stageData = template.stages().get(key);

            if (stageData.chance() < 100.0) {
                double roll = Math.random() * 100.0;
                if (roll > stageData.chance()) {
                    continue;
                }
            }

            CopyOnWriteArrayList<DungeonAction> actions = new CopyOnWriteArrayList<>();
            for (Map<String, Object> map : stageData.actions()) {
                String type = (String) map.get("type");
                if (type != null) {
                    DungeonAction action = plugin.getDungeonManager().createAction(type, map);
                    if (action != null) actions.add(action);
                }
            }
            if (!actions.isEmpty()) {
                parsedStages.add(actions);
            }
        }

        if (template.settings().randomizeStages() && parsedStages.size() > 2) {
            CopyOnWriteArrayList<DungeonAction> firstStage = parsedStages.get(0);
            CopyOnWriteArrayList<DungeonAction> lastStage = parsedStages.get(parsedStages.size() - 1);

            List<CopyOnWriteArrayList<DungeonAction>> middleStages = new ArrayList<>(parsedStages.subList(1, parsedStages.size() - 1));
            Collections.shuffle(middleStages);

            this.stages.add(firstStage);
            this.stages.addAll(middleStages);
            this.stages.add(lastStage);
        } else {
            this.stages.addAll(parsedStages);
        }
    }

    private void executeActionCommands(List<String> commands, int stageIndex) {
        if (commands == null || commands.isEmpty()) return;
        for (Player p : participants) {
            executeActionCommandsForPlayer(commands, p, stageIndex);
        }
    }

    private void executeActionCommandsForPlayer(List<String> commands, Player p, int stageIndex) {
        if (commands == null || commands.isEmpty() || p == null || !p.isOnline()) return;

        for (String cmd : commands) {
            if (cmd.startsWith("[") && cmd.contains("]")) {
                int endIdx = cmd.indexOf("]");
                String condition = cmd.substring(1, endIdx).trim();
                cmd = cmd.substring(endIdx + 1).trim();

                if (!condition.isEmpty() && !PAPIHook.checkCondition(p, condition)) {
                    continue;
                }
            }

            String parsed = cmd.replace("<player>", p.getName()).replace("%player%", p.getName()).replace("{player}", p.getName()).replace("<dungeon>", template.id()).replace("%dungeon%", template.id()).replace("<stage>", String.valueOf(stageIndex + 1)).replace("%stage%", String.valueOf(stageIndex + 1));

            parsed = PAPIHook.setPlaceholders(p, parsed);
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;

        int fadeIn = plugin.getConfigFile().getInt("titles.fade-in", 200);
        int stay = plugin.getConfigFile().getInt("titles.stay", 3000);
        int fadeOut = plugin.getConfigFile().getInt("titles.fade-out", 500);

        broadcastTitle("game.title.loading_main", "game.title.loading_sub", fadeIn, stay, fadeOut);
        broadcastMessage("lobby.preparing");

        InstanceProvider provider = plugin.getInstanceManager().getProvider();
        this.instanceProvider = provider;
        provider.createInstance(template.templateWorld(), worldName).thenAccept(world -> SchedulerCompat.runGlobal(plugin, () -> {
            if (isStopping) {
                provider.releaseInstance(worldName, world);
                return;
            }
            this.dungeonWorld = world;
            this.instanceOrigin = provider.getInstanceOrigin(worldName, world);
            if (this.instanceOrigin == null) {
                this.instanceOrigin = new Location(world, 0, 0, 0);
            } else if (this.instanceOrigin.getWorld() == null) {
                this.instanceOrigin.setWorld(world);
            }
            this.instanceRadius = provider.getInstanceRadius(worldName);
            this.respawnLocation = provider.getInstanceSpawnLocation(worldName, world);
            if (this.respawnLocation == null) {
                this.respawnLocation = world.getSpawnLocation().clone().add(0.5, 1, 0.5);
            }
            plugin.getDungeonManager().registerWorldGame(world.getName(), this);

            dungeonWorld.setAutoSave(false);

            if (template.settings().forceDaylightAndClearWeather()) {
                dungeonWorld.setTime(6000);
                dungeonWorld.setStorm(false);
                dungeonWorld.setThundering(false);
            }
            startCountdown();
        })).exceptionally(ex -> {
            SchedulerCompat.runGlobal(plugin, () -> {
                broadcastMessage("error.create_failed");
                String logFail = plugin.getLanguageManager().getString("admin.log.instance_create_fail", "Failed to create dungeon world: <error>");
                plugin.getLogger().severe(logFail.replace("<error>", ex.getMessage()));
                stop(true, DungeonEndEvent.EndReason.FORCE_STOPPED);
            });
            return null;
        });
    }

    private void startCountdown() {
        final int[] count = {plugin.getConfigFile().getInt("dungeon.lobby-countdown", 5)};
        lobbyTask = SchedulerCompat.runGlobalTimer(plugin, () -> {
            if (isStopping) {
                if (lobbyTask != null) lobbyTask.cancel();
                return;
            }
            if (count[0] <= 0) {
                enterDungeon();
                if (lobbyTask != null) lobbyTask.cancel();
                return;
            }

            String titleMain = plugin.getLanguageManager().getString("game.title.countdown_main", "<red><bold><time>").replace("<time>", String.valueOf(count[0]));
            String titleSub = plugin.getLanguageManager().getString("game.title.countdown_sub", "<gold>Prepare for battle!");

            for (Player p : participants) {
                if (p.isOnline()) {
                    p.showTitle(Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    playSound(p, "lobby_countdown", 1f, 2f);
                }
            }
            broadcastMessage("lobby.countdown", "<time>", String.valueOf(count[0]));
            count[0]--;
        }, 0L, 20L);
    }

    private void enterDungeon() {
        isPreparing = false;
        isRunning = true;
        Location spawnLoc = getRespawnLocation();
        boolean saveStats = template.settings().saveAndRestoreStats();
        Set<Player> failedToEnter = new HashSet<>();

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) {
                failedToEnter.add(p);
                continue;
            }
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.closeInventory();
            p.setVelocity(new Vector(0, 0, 0));

            applyMviBypass(p);

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

                        p.getInventory().clear();
                        p.getInventory().setArmorContents(null);
                        p.getInventory().setExtraContents(null);
                        p.setLevel(0);
                        p.setExp(0f);
                        p.updateInventory();
                    }
                    p.setFallDistance(0);
                }
            });
        }

        for (Player failed : failedToEnter) {
            participants.remove(failed);
            plugin.getDungeonManager().removeGame(failed.getUniqueId());
            savedStates.remove(failed.getUniqueId());
            removeMviBypass(failed);
        }

        if (participants.isEmpty()) {
            stop(false, DungeonEndEvent.EndReason.FAILED);
            return;
        }

        executeActionCommands(template.settings().onStartCmds(), 0);

        int fadeIn = plugin.getConfigFile().getInt("titles.fade-in", 200);
        int stay = plugin.getConfigFile().getInt("titles.stay", 3000);
        int fadeOut = plugin.getConfigFile().getInt("titles.fade-out", 500);

        broadcastTitle("game.title.start_main", "game.title.start_sub", fadeIn, stay, fadeOut);
        broadcastMessage("game.start");
        participants.forEach(p -> playSound(p, "game_start", 0.5f, 1f));

        this.startTime = System.currentTimeMillis();

        tickTask = SchedulerCompat.runAtLocationTimer(plugin, getRespawnLocation(), () -> {
            if (!isRunning) {
                if (tickTask != null) tickTask.cancel();
                return;
            }
            serverTicksActive += 4;
            runTick();
        }, 4L, 4L);

        startStage(0);
    }

    private void runTick() {
        if (stageCompleting || currentStageIndex >= stages.size()) return;

        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
        if (currentActionIndex >= currentStageActions.size()) return;

        DungeonAction action = currentStageActions.get(currentActionIndex);

        if (!action.isCompleted()) {
            if (action.getTimeLimitSeconds() > 0 && action.getStartTimeMillis() > 0) {
                long elapsed = (System.currentTimeMillis() - action.getStartTimeMillis()) / 1000;
                if (elapsed >= action.getTimeLimitSeconds()) {
                    handleTimeLimitPenalty(action);
                    return;
                }
            }

            if (action instanceof Tickable tickable) {
                try {
                    tickable.onTick(this);
                } catch (Exception e) {
                    if (serverTicksActive % 100 == 0) {
                        String logWarn = plugin.getLanguageManager().getString("admin.log.action_tick_error", "Tick error in action: <error>");
                        plugin.getLogger().warning(logWarn.replace("<error>", e.getMessage() != null ? e.getMessage() : "Unknown Exception"));
                    }
                }
            }

            if (!action.isCompleted()) {
                String objText = action.getObjectiveText();

                if (action.getTimeLimitSeconds() > 0) {
                    long timeLeft = action.getTimeLimitSeconds() - ((System.currentTimeMillis() - action.getStartTimeMillis()) / 1000);
                    objText += cachedTimeLeftFormat.replace("<time>", String.valueOf(timeLeft));
                }

                String finalBarText = cachedObjectivePrefix + objText;
                if (!finalBarText.equals(lastActionBarText) || lastParsedBar == null) {
                    lastActionBarText = finalBarText;
                    lastParsedBar = ColorUtils.parse(finalBarText);
                }

                for (Player p : participants) {
                    if (p.isOnline() && p.getWorld().equals(dungeonWorld)) {
                        p.sendActionBar(lastParsedBar);
                    }
                }
            } else {
                action.cleanup(this);
                advanceNextAction();
            }
        } else {
            action.cleanup(this);
            advanceNextAction();
        }
    }

    private void handleTimeLimitPenalty(DungeonAction action) {
        broadcastMessage("game.time_out");
        int penalty = action.getTimeLimitPenalty();
        boolean outOfLives = false;

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;

            plugin.getLivesManager().removeLives(p.getUniqueId(), penalty);
            LivesManager.PlayerLives livesData = plugin.getLivesManager().getLives(p.getUniqueId());
            int current = livesData != null ? livesData.getCurrentLives() : 0;

            String lossMsg = plugin.getLanguageManager().getString("lives.time_out_penalty", "&cYou lost <amount> lives due to time limit! Current: <current>").replace("<amount>", String.valueOf(penalty)).replace("<current>", String.valueOf(current));
            p.sendMessage(ColorUtils.parseWithPrefix(lossMsg));

            if (current <= 0) {
                outOfLives = true;
                String outOfLivesAction = plugin.getConfigFile().getString("dungeon.out-of-lives-action", "SPECTATE");
                if (outOfLivesAction.equalsIgnoreCase("SPECTATE")) {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_spectate")));
                    p.setGameMode(GameMode.SPECTATOR);
                } else if (outOfLivesAction.equalsIgnoreCase("FAIL")) {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_kick")));
                    stop(true, DungeonEndEvent.EndReason.FAILED);
                    return;
                } else {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_kick")));
                    handlePlayerDisconnect(p, false);
                }
            }
        }

        checkWipeout();

        if (isRunning && !isStopping) {
            List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
            for (int i = 0; i <= currentActionIndex; i++) {
                try {
                    currentStageActions.get(i).cleanup(this);
                } catch (Exception ex) {
                    String logWarn = plugin.getLanguageManager().getString("admin.log.action_cleanup_error", "Cleanup error on timeout: <error>");
                    plugin.getLogger().warning(logWarn.replace("<error>", ex.getMessage()));
                }
            }
            startStage(currentStageIndex);
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
        action.setStartTimeMillis(System.currentTimeMillis());
        try {
            action.announceStart(this);
            action.start(this);
        } catch (Exception e) {
            String logWarn = plugin.getLanguageManager().getString("admin.log.action_start_error", "Error starting action: <error>");
            plugin.getLogger().severe(logWarn.replace("<error>", e.getMessage()));
            action.forceComplete();
        }

        if (action.isCompleted()) {
            action.cleanup(this);
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
                if (System.currentTimeMillis() % 5000 < 50) {
                    String logWarn = plugin.getLanguageManager().getString("admin.log.action_event_error", "Event handling error in action: <error>");
                    plugin.getLogger().warning(logWarn.replace("<error>", e.getMessage() != null ? e.getMessage() : "Unknown exception"));
                }
            }

            if (action.isCompleted()) {
                action.cleanup(this);
                advanceNextAction();
            }
        }

        if (event instanceof EntityDeathEvent ede) {
            Player killer = ede.getEntity().getKiller();
            if (killer != null && participants != null && participants.contains(killer)) {
                playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
            }
        }
    }

    public void checkAndTrackMythicMob(UUID uuid, Location loc, String internalName) {
        if (!isRunning || stageCompleting || currentStageIndex >= stages.size()) return;
        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
        if (currentActionIndex >= currentStageActions.size()) return;

        DungeonAction action = currentStageActions.get(currentActionIndex);
        if (action instanceof MythicMobWaveAction mmAction) {
            mmAction.checkAndTrackTarget(uuid, loc, internalName);
        }
    }

    public void trackChildEntity(UUID parentId, UUID childId, Location loc, String internalName) {
        if (!isRunning || stageCompleting || currentStageIndex >= stages.size()) return;
        List<DungeonAction> currentStageActions = stages.get(currentStageIndex);
        if (currentActionIndex >= currentStageActions.size()) return;
        DungeonAction action = currentStageActions.get(currentActionIndex);

        if (action.getSpawnedEntities().contains(parentId)) {
            action.trackChildEntity(childId, loc, internalName);
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

        DungeonTemplate.StageData stageData = template.stages().get(currentStageIndex);
        if (stageData != null) {
            executeActionCommands(stageData.commands(), currentStageIndex);
        }

        DungeonStageCompleteEvent stageEvent = new DungeonStageCompleteEvent(this, currentStageIndex);
        Bukkit.getPluginManager().callEvent(stageEvent);

        SchedulerCompat.runAtLocationLater(plugin, getRespawnLocation(), () -> startStage(currentStageIndex + 1), 60L);
    }

    public void jumpToStage(int targetStage) {
        if (!isRunning || stageCompleting) return;

        stageCompleting = true;

        participants.forEach(p -> {
            if (p.isOnline()) p.sendActionBar(ColorUtils.parse(" "));
        });

        broadcastMessage("game.stage_complete", "<stage>", String.valueOf(currentStageIndex + 1));
        participants.forEach(p -> playSound(p, "stage_complete", 1f, 1f));

        DungeonTemplate.StageData stageData = template.stages().get(currentStageIndex);
        if (stageData != null) {
            executeActionCommands(stageData.commands(), currentStageIndex);
        }

        DungeonStageCompleteEvent stageEvent = new DungeonStageCompleteEvent(this, currentStageIndex);
        Bukkit.getPluginManager().callEvent(stageEvent);

        SchedulerCompat.runAtLocationLater(plugin, getRespawnLocation(), () -> startStage(targetStage - 1), 60L);
    }

    public void checkWipeout() {
        boolean allDeadOrSpectating = true;

        for (Player p : participants) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                allDeadOrSpectating = false;
                break;
            }
        }

        if (allDeadOrSpectating && !isCleared && !isStopping) {
            broadcastMessage("game.wipeout");
            stop(true, DungeonEndEvent.EndReason.FAILED);
        }
    }

    public void startKickCountdown(SinceDungeon plugin, Collection<Player> players, int delaySeconds, Runnable onComplete) {
        String displayType = plugin.getConfigFile().getString("dungeon.gameplay.kick-countdown.display-type", "ACTIONBAR").toUpperCase();

        if (this.kickTask != null && !this.kickTask.isCancelled()) {
            this.kickTask.cancel();
        }

        final int[] timeLeft = {delaySeconds};
        this.kickTask = SchedulerCompat.runGlobalTimer(plugin, () -> {
            if (isStopping) {
                if (this.kickTask != null) this.kickTask.cancel();
                return;
            }

            if (timeLeft[0] <= 0) {
                if (onComplete != null) {
                    onComplete.run();
                }
                if (this.kickTask != null) this.kickTask.cancel();
                return;
            }

            for (Player p : players) {
                if (p == null || !p.isOnline()) continue;

                switch (displayType) {
                    case "ACTIONBAR":
                        String actionMsg = plugin.getLanguageManager().getString("game.kick_countdown.actionbar", "&eTeleporting to Lobby in &c<time>s&e...");
                        p.sendActionBar(ColorUtils.parse(actionMsg.replace("<time>", String.valueOf(timeLeft[0]))));
                        break;

                    case "TITLE":
                        String titleMain = plugin.getLanguageManager().getString("game.kick_countdown.title.main", "&c<time>");
                        String titleSub = plugin.getLanguageManager().getString("game.kick_countdown.title.sub", "&eSeconds until teleport");

                        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);
                        Title title = Title.title(ColorUtils.parse(titleMain.replace("<time>", String.valueOf(timeLeft[0]))), ColorUtils.parse(titleSub.replace("<time>", String.valueOf(timeLeft[0]))), times);
                        p.showTitle(title);
                        break;

                    case "CHAT":
                        String chatMsg = plugin.getLanguageManager().getString("game.kick_countdown.chat", "&eThe dungeon will close in &c<time> &eseconds.");
                        p.sendMessage(ColorUtils.parseWithPrefix(chatMsg.replace("<time>", String.valueOf(timeLeft[0]))));
                        break;

                    case "NONE":
                    default:
                        break;
                }
            }
            timeLeft[0]--;
        }, 0L, 20L);
    }

    private void applyCooldown(Player p) {
        if (template == null) return;
        int cooldownSeconds = template.settings().cooldownSeconds();
        if (cooldownSeconds > 0) {
            long expireEpoch = System.currentTimeMillis() + (cooldownSeconds * 1000L);
            plugin.getCooldownManager().setCooldown(p.getUniqueId(), template.id(), expireEpoch);
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void finishDungeon() {
        this.isCleared = true;
        this.isRunning = false;

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();

        broadcastMessage("game.finish");
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int finalElapsed = (int) elapsedSeconds;
        String formattedTime = formatTime(finalElapsed);

        Map<Integer, Integer> activeTiers = (participants.size() > 1) ? template.partyRewardTiers() : template.soloRewardTiers();

        int chestCount = 0;
        for (Map.Entry<Integer, Integer> entry : activeTiers.entrySet()) {
            if (elapsedSeconds <= entry.getKey()) chestCount = Math.max(chestCount, entry.getValue());
        }
        final int finalChestCount = chestCount;

        if (plugin.getTopManager() != null && template != null) {
            String dungeonId = template.id();
            String awardedTo = plugin.getConfigFile().getString("dungeon.top-awarded-to", "ALL_MEMBERS");
            TopManager topManager = plugin.getTopManager();

            UUID leaderId = plugin.getPartyManager().getProvider().getLeader(initiatorId);
            if (leaderId == null) leaderId = initiatorId;

            if (participants.size() > 1) {
                String membersNames = participants.stream().filter(Player::isOnline).map(Player::getName).collect(Collectors.joining(", "));
                topManager.savePartyClearTime(dungeonId, membersNames, finalElapsed);
            }

            final UUID resolvedLeaderId = leaderId;
            SchedulerCompat.runAsync(plugin, () -> {
                for (Player p : participants) {
                    if (!p.isOnline()) continue;

                    int previousClears = topManager.getPlayerClears(dungeonId, p.getUniqueId());
                    boolean isFirstTime = (previousClears == 0);

                    if (isFirstTime) {
                        SchedulerCompat.runGlobal(plugin, () -> {
                            executeActionCommandsForPlayer(template.settings().onFirstFinishCmds(), p, currentStageIndex);
                        });
                    }

                    boolean isLeader = p.getUniqueId().equals(resolvedLeaderId);
                    int kills = playerKills.getOrDefault(p.getUniqueId(), 0);

                    topManager.saveKills(dungeonId, p.getUniqueId(), p.getName(), kills);

                    if (participants.size() == 1) {
                        if (awardedTo.equalsIgnoreCase("ALL_MEMBERS") || isLeader) {
                            topManager.saveClearTime(dungeonId, p.getUniqueId(), p.getName(), finalElapsed);
                        }
                    }

                    if (awardedTo.equalsIgnoreCase("ALL_MEMBERS") || isLeader) {
                        topManager.incrementClears(dungeonId, p.getUniqueId(), p.getName());
                    }

                    applyCooldown(p);
                }
            });
        }

        executeActionCommands(template.settings().onFinishCmds(), currentStageIndex);

        DungeonFinishEvent finishEvent = new DungeonFinishEvent(this, finalElapsed, finalChestCount);
        Bukkit.getPluginManager().callEvent(finishEvent);

        final int eventChestCount = finishEvent.getChestCount();
        final boolean hasRewards = template.rewardPool() != null && !template.rewardPool().isEmpty();

        int fadeIn = plugin.getConfigFile().getInt("titles.fade-in", 200);
        int stay = plugin.getConfigFile().getInt("titles.stay", 3000);
        int fadeOut = plugin.getConfigFile().getInt("titles.fade-out", 500);

        String titleMain = plugin.getLanguageManager().getString("game.title.finish_main", "<green><bold>CLEARED!");
        String titleSub = plugin.getLanguageManager().getString("game.title.finish_sub", "<yellow>Time: <time>").replace("<time>", formattedTime);
        Title victoryTitle = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut)));

        for (Player p : participants) {
            if (p.isOnline()) {
                p.showTitle(victoryTitle);
                p.sendActionBar(ColorUtils.parse(" "));
            }
        }

        String shareMode = plugin.getConfigFile().getString("party.reward-share-mode", "EQUAL");

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead()) continue;

            UUID currentLeader = plugin.getPartyManager().getProvider().getLeader(p.getUniqueId());
            if (currentLeader == null) currentLeader = initiatorId;

            if (shareMode.equalsIgnoreCase("LEADER_ONLY") && !p.getUniqueId().equals(currentLeader)) {
                // Ignore others
            } else if (eventChestCount > 0 && hasRewards) {
                plugin.getRewardManager().getRewardSystem().forceClaimPending(p);
                plugin.getRewardManager().getRewardSystem().distributeRewards(p, template, eventChestCount);
            } else {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("game.no_reward", "&cUnfortunately, you didn't qualify for any rewards.")));
            }
        }

        int kickDelay = template.settings().kickDelayAfterFinish();

        startKickCountdown(plugin, participants, kickDelay, () -> {
            for (Player p : participants) {
                if (!p.isOnline() || p.isDead()) continue;

                plugin.getRewardManager().getRewardSystem().forceClaimPending(p);

                if (p.isInsideVehicle()) p.leaveVehicle();
                p.closeInventory();
                p.setVelocity(new Vector(0, 0, 0));

                plugin.getDungeonManager().addTransitioning(p.getUniqueId());

                if (plugin.getConfigFile().getBoolean("cross-server.enabled", false)) {
                    String returnServer = plugin.getConfigFile().getString("cross-server.return-server", "lobby");
                    restorePlayerState(p);
                    BungeeUtils.sendPlayerToServer(p, returnServer);
                } else {
                    PlayerState state = savedStates.get(p.getUniqueId());
                    Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                    p.teleportAsync(targetLoc).thenAccept(success -> {
                        if (success && p.isOnline()) {
                            SchedulerCompat.runAtEntity(plugin, p, () -> SchedulerCompat.runGlobalLater(plugin, () -> restorePlayerState(p), 20L));
                        } else if (p.isOnline()) {
                            SchedulerCompat.runAtEntity(plugin, p, () -> {
                                p.teleport(targetLoc);
                                SchedulerCompat.runGlobalLater(plugin, () -> restorePlayerState(p), 20L);
                            });
                        }
                    });
                }
            }

            SchedulerCompat.runGlobalLater(plugin, () -> stop(false, DungeonEndEvent.EndReason.CLEARED), 40L);
        });
    }

    public void handlePlayerDisconnect(Player p, boolean isQuitting) {
        boolean wasInDungeon = (dungeonWorld != null && p.getWorld().equals(dungeonWorld));

        plugin.getRewardManager().getRewardSystem().forceClaimPending(p);

        if (!isQuitting) {
            if (p.isDead()) {
                p.spigot().respawn();
            }
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.closeInventory();
            p.setVelocity(new Vector(0, 0, 0));

            PlayerState state = savedStates.get(p.getUniqueId());
            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

            if (wasInDungeon) {
                p.teleport(targetLoc);
            }
            SchedulerCompat.runGlobalLater(plugin, () -> restorePlayerState(p), 20L);
        } else {
            restorePlayerState(p);
        }

        if (!isCleared && template != null && template.settings().cooldownOnLeave()) {
            applyCooldown(p);
        }

        if (participants != null) participants.remove(p);
        plugin.getDungeonManager().removeGame(p.getUniqueId());

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

        try {
            Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this, reason));
        } catch (Exception e) {
            plugin.getLogger().severe("Error dispatching DungeonEndEvent: " + e.getMessage());
        }

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (lobbyTask != null && !lobbyTask.isCancelled()) lobbyTask.cancel();
        if (kickTask != null && !kickTask.isCancelled()) kickTask.cancel();

        try {
            if (reason != DungeonEndEvent.EndReason.CLEARED && template != null && template.settings().cooldownOnLeave()) {
                if (participants != null) {
                    for (Player p : participants) {
                        applyCooldown(p);
                    }
                }
            }

            if (participants != null) {
                for (Player p : participants) {
                    plugin.getDungeonManager().removeGame(p.getUniqueId());
                    if (!p.isOnline()) continue;

                    p.sendActionBar(ColorUtils.parse(" "));

                    if (dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                        if (teleport) {
                            if (p.isDead()) p.spigot().respawn();

                            if (p.isInsideVehicle()) p.leaveVehicle();
                            p.closeInventory();
                            p.setVelocity(new Vector(0, 0, 0));
                            PlayerState state = savedStates.get(p.getUniqueId());
                            Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                            plugin.getDungeonManager().addTransitioning(p.getUniqueId());

                            if (plugin.getConfigFile().getBoolean("cross-server.enabled", false)) {
                                String returnServer = plugin.getConfigFile().getString("cross-server.return-server", "lobby");
                                restorePlayerState(p);
                                BungeeUtils.sendPlayerToServer(p, returnServer);
                            } else {
                                p.teleportAsync(targetLoc).thenAccept(success -> {
                                    if (success) {
                                        SchedulerCompat.runGlobalLater(plugin, () -> restorePlayerState(p), 20L);
                                    } else {
                                        SchedulerCompat.runAtEntity(plugin, p, () -> {
                                            p.teleport(targetLoc);
                                            SchedulerCompat.runGlobalLater(plugin, () -> restorePlayerState(p), 20L);
                                        });
                                    }
                                });
                            }
                        } else {
                            restorePlayerState(p);
                        }
                    } else {
                        restorePlayerState(p);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing DungeonGame stop routing: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dungeonWorld != null) {
                World w = dungeonWorld;
                String instanceId = worldName;
                InstanceProvider provider = instanceProvider != null ? instanceProvider : plugin.getInstanceManager().getProvider();
                SchedulerCompat.runGlobalLater(plugin, () -> {
                    provider.releaseInstance(instanceId, w);
                    aggressivelyCleanupMemory();
                }, 40L);
            } else {
                aggressivelyCleanupMemory();
            }
        }
    }

    public void forceShutdown() {
        if (isStopping) return;
        isStopping = true;
        isRunning = false;

        try {
            Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this, DungeonEndEvent.EndReason.FORCE_STOPPED));
        } catch (Exception ignored) {
        }

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (lobbyTask != null && !lobbyTask.isCancelled()) lobbyTask.cancel();
        if (kickTask != null && !kickTask.isCancelled()) kickTask.cancel();

        try {
            if (participants != null) {
                for (Player p : participants) {
                    plugin.getDungeonManager().removeGame(p.getUniqueId());
                    if (p.isOnline() && dungeonWorld != null && p.getWorld().equals(dungeonWorld)) {
                        if (p.isDead()) p.spigot().respawn();

                        if (p.isInsideVehicle()) p.leaveVehicle();
                        p.closeInventory();
                        p.setVelocity(new Vector(0, 0, 0));

                        PlayerState state = savedStates.get(p.getUniqueId());
                        Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                        plugin.getDungeonManager().addTransitioning(p.getUniqueId());
                        p.teleport(targetLoc);
                        restorePlayerState(p);
                        p.sendActionBar(ColorUtils.parse(" "));
                    } else if (p.isOnline()) {
                        restorePlayerState(p);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            World w = dungeonWorld;
            String instanceId = worldName;
            InstanceProvider provider = instanceProvider != null ? instanceProvider : plugin.getInstanceManager().getProvider();

            aggressivelyCleanupMemory();

            if (w != null && provider != null) {
                Runnable releaseTask = () -> {
                    provider.forceReleaseInstance(instanceId, w);
                };
                if (Bukkit.isPrimaryThread()) {
                    releaseTask.run();
                } else {
                    SchedulerCompat.runGlobal(plugin, releaseTask);
                }
            }
        }
    }

    private void aggressivelyCleanupMemory() {
        plugin.getDungeonManager().unregisterGame(this);

        if (savedStates != null) savedStates.clear();
        if (playerKills != null) playerKills.clear();

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
        }

        if (participants != null) {
            participants.clear();
        }

        if (permAttachments != null) {
            permAttachments.values().forEach(att -> {
                try {
                    if (att.getPermissible() != null) att.getPermissible().removeAttachment(att);
                } catch (Exception ignored) {
                }
            });
            permAttachments.clear();
        }

        if (kickTask != null && !kickTask.isCancelled()) {
            kickTask.cancel();
            kickTask = null;
        }

        this.dungeonWorld = null;
        this.instanceProvider = null;
        this.instanceOrigin = null;
        this.respawnLocation = null;
        this.instanceRadius = -1;
        this.initiatorId = null;
        this.template = null;
        this.lastParsedBar = null;
    }

    public void restorePlayerState(Player p) {
        if (p == null) return; // Prevent NPEs if player vanishes completely.

        PlayerState state = savedStates.get(p.getUniqueId());
        if (state != null) {
            // Note: Critical Memory Fix: Even if the player is offline,
            // we MUST remove the mapped data to free the lingering World pointer.
            savedStates.remove(p.getUniqueId());

            if (!p.isOnline()) {
                plugin.getDungeonManager().removeTransitioning(p.getUniqueId());
                return;
            }

            NamespacedKey compassTag = new NamespacedKey(plugin, "dungeon_compass");
            NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && (ItemBuilder.hasTag(item, compassTag, PersistentDataType.BYTE) || ItemBuilder.hasTag(item, keyTag, PersistentDataType.STRING))) {
                    item.setAmount(0);
                }
            }

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

                p.getInventory().setContents(state.inventoryContents);
                p.getInventory().setArmorContents(state.armorContents);
                p.getInventory().setExtraContents(state.extraContents);
                p.setLevel(state.level);
                p.setExp(state.exp);
                p.updateInventory();
            } else {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
            p.setFallDistance(0);
            p.setVelocity(new Vector(0, 0, 0));
        }

        removeMviBypass(p);
        plugin.getDungeonManager().removeTransitioning(p.getUniqueId());
    }

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

        String msg = plugin.getLanguageManager().getString(key);
        if (msg == null || msg.isEmpty()) return;

        String prefix = plugin.getLanguageManager().getString("prefix", "");
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

        String main = plugin.getLanguageManager().getString(mainKey, "");
        String sub = plugin.getLanguageManager().getString(subKey, "");
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

    public String getInstanceId() {
        return worldName;
    }

    /**
     * Converts a dungeon YAML coordinate into the real Bukkit location for this run.
     * In normal world-copy mode the origin is 0,0,0, while shared schematic mode offsets
     * X/Y/Z into the region assigned to this party.
     */
    public Location resolveLocation(Vector vector, double addX, double addY, double addZ) {
        Location origin = instanceOrigin != null ? instanceOrigin : new Location(dungeonWorld, 0, 0, 0);
        return new Location(
                dungeonWorld,
                origin.getX() + vector.getX() + addX,
                origin.getY() + vector.getY() + addY,
                origin.getZ() + vector.getZ() + addZ
        );
    }

    public Location resolveLocation(Vector vector) {
        return resolveLocation(vector, 0, 0, 0);
    }

    /**
     * Resolves block-aligned YAML coordinates for trigger blocks, levers, chests, and walls.
     */
    public Location resolveBlockLocation(Vector vector) {
        Location origin = instanceOrigin != null ? instanceOrigin : new Location(dungeonWorld, 0, 0, 0);
        return new Location(
                dungeonWorld,
                origin.getBlockX() + vector.getBlockX(),
                origin.getBlockY() + vector.getBlockY(),
                origin.getBlockZ() + vector.getBlockZ()
        );
    }

    public Location getInstanceOrigin() {
        if (instanceOrigin != null) return instanceOrigin.clone();
        return dungeonWorld != null ? new Location(dungeonWorld, 0, 0, 0) : null;
    }

    public int getInstanceRadius() {
        return instanceRadius;
    }

    /**
     * Checks whether a world event belongs to this dungeon's allocated area.
     * This lets multiple active games safely share one schematic world without
     * routing mob deaths, targets, or teleports to the wrong party.
     */
    public boolean ownsLocation(Location location) {
        if (location == null || location.getWorld() == null || dungeonWorld == null) return false;
        if (!location.getWorld().equals(dungeonWorld)) return false;
        if (instanceRadius < 1 || instanceOrigin == null) return true;

        double dx = Math.abs(location.getX() - instanceOrigin.getX());
        double dz = Math.abs(location.getZ() - instanceOrigin.getZ());
        return dx <= instanceRadius && dz <= instanceRadius;
    }

    public Location getRespawnLocation() {
        if (respawnLocation != null) return respawnLocation.clone();
        return dungeonWorld != null ? dungeonWorld.getSpawnLocation().clone().add(0.5, 1, 0.5) : null;
    }

    public void setRespawnLocation(Location respawnLocation) {
        this.respawnLocation = respawnLocation != null ? respawnLocation.clone() : null;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(initiatorId);
    }

    public String getCachedObjectivePrefix() {
        return cachedObjectivePrefix;
    }

    public String getCachedTimeLeftFormat() {
        return cachedTimeLeftFormat;
    }

    private static class PlayerState {
        final Location location;
        final GameMode gameMode;
        final double health;
        final int foodLevel;
        final Collection<PotionEffect> potionEffects;
        final int fireTicks;

        final ItemStack[] inventoryContents;
        final ItemStack[] armorContents;
        final ItemStack[] extraContents;
        final int level;
        final float exp;

        PlayerState(Player p) {
            this.location = p.getLocation();
            this.gameMode = p.getGameMode();
            this.health = p.getHealth();
            this.foodLevel = p.getFoodLevel();
            this.potionEffects = p.getActivePotionEffects();
            this.fireTicks = p.getFireTicks();

            this.inventoryContents = cloneItemArray(p.getInventory().getContents());
            this.armorContents = cloneItemArray(p.getInventory().getArmorContents());
            this.extraContents = cloneItemArray(p.getInventory().getExtraContents());

            this.level = p.getLevel();
            this.exp = p.getExp();
        }

        private ItemStack[] cloneItemArray(ItemStack[] original) {
            if (original == null) return new ItemStack[0];
            ItemStack[] copy = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                copy[i] = (original[i] != null && !original[i].getType().isAir()) ? original[i].clone() : null;
            }
            return copy;
        }
    }
}
