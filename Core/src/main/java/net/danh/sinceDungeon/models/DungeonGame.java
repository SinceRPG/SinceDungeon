package net.danh.sinceDungeon.models;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sinceDungeon.api.events.DungeonStageCompleteEvent;
import net.danh.sinceDungeon.guis.reward.RewardGUI;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.hooks.PAPIHook;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.managers.PartyManager;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.managers.WorldManager;
import net.danh.sinceDungeon.utils.BungeeUtils;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Represents an active instance of a Dungeon.
 * Manages player lifecycles, active stages, actions, timer ticking,
 * command triggers, and memory cleanup upon termination.
 */
public class DungeonGame {
    private final SinceDungeon plugin;
    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private final String worldName;
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private final String cachedObjectivePrefix;
    private final String cachedTimeLeftFormat;

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

    /**
     * Constructs a new DungeonGame instance.
     * Prepares player states, generates a unique world name, and parses dungeon stages.
     *
     * @param plugin          The plugin instance.
     * @param initiator       The player initiating the session.
     * @param rawParticipants The unmodifiable set of players joining.
     * @param template        The structured layout of the dungeon map.
     */
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

    /**
     * Parses and initializes the stages and actions from the template data.
     * Handles optional rogue-like randomization if configured in the template settings.
     */
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

    /**
     * Executes defined console commands globally for all active participants.
     * Resolves multiple variations of placeholders (e.g., %player%, <player>)
     * to ensure absolute foolproof configuration, then integrates with PlaceholderAPI.
     *
     * @param commands   List of raw command strings to execute.
     * @param stageIndex Current stage integer index.
     */
    private void executeActionCommands(List<String> commands, int stageIndex) {
        if (commands == null || commands.isEmpty()) return;

        for (Player p : participants) {
            executeActionCommandsForPlayer(commands, p, stageIndex);
        }
    }

    /**
     * Executes defined console commands explicitly targeted at a single player.
     * Includes intelligent condition parsing using brackets (e.g., [condition] command).
     *
     * @param commands   List of raw command strings to execute.
     * @param p          The target player to evaluate placeholders against.
     * @param stageIndex Current stage integer index.
     */
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

            String parsed = cmd.replace("<player>", p.getName())
                    .replace("%player%", p.getName())
                    .replace("{player}", p.getName())
                    .replace("<dungeon>", template.id())
                    .replace("%dungeon%", template.id())
                    .replace("<stage>", String.valueOf(stageIndex + 1))
                    .replace("%stage%", String.valueOf(stageIndex + 1));

            parsed = PAPIHook.setPlaceholders(p, parsed);

            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    /**
     * Initiates the asynchronous creation of the dungeon world instance.
     * Transitions into the lobby countdown phase once the world successfully loads.
     */
    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;

        int fadeIn = plugin.getConfigFile().getInt("titles.fade-in", 200);
        int stay = plugin.getConfigFile().getInt("titles.stay", 3000);
        int fadeOut = plugin.getConfigFile().getInt("titles.fade-out", 500);

        broadcastTitle("game.title.loading_main", "game.title.loading_sub", fadeIn, stay, fadeOut);
        broadcastMessage("lobby.preparing");

        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isStopping) {
                        WorldManager.unloadAndDeleteWorld(plugin, world);
                        return;
                    }
                    this.dungeonWorld = world;
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

    /**
     * Executes the lobby countdown timer before teleporting players into the active dungeon.
     */
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

                String titleMain = plugin.getLanguageManager().getString("game.title.countdown_main", "<red><bold><time>").replace("<time>", String.valueOf(count));
                String titleSub = plugin.getLanguageManager().getString("game.title.countdown_sub", "<gold>Prepare for battle!");

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

    /**
     * Teleports all active participants into the dungeon instance.
     * Manages inventory states, applies startup commands, and starts the main ticking loop for actions.
     */
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

                        p.getInventory().clear();
                        p.getInventory().setArmorContents(null);
                        p.getInventory().setExtraContents(null);
                        p.setLevel(0);
                        p.setExp(0f);
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

        executeActionCommands(template.settings().onStartCmds(), 0);

        int fadeIn = plugin.getConfigFile().getInt("titles.fade-in", 200);
        int stay = plugin.getConfigFile().getInt("titles.stay", 3000);
        int fadeOut = plugin.getConfigFile().getInt("titles.fade-out", 500);

        broadcastTitle("game.title.start_main", "game.title.start_sub", fadeIn, stay, fadeOut);
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

    /**
     * Executes the main dungeon loop.
     * Evaluates active conditions, manages timeouts, and processes HUD updates.
     */
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
                    plugin.getLogger().warning("Tick error in action: " + e.getMessage());
                }
            }

            if (!action.isCompleted()) {
                String objText = action.getObjectiveText();

                if (action.getTimeLimitSeconds() > 0) {
                    long timeLeft = action.getTimeLimitSeconds() - ((System.currentTimeMillis() - action.getStartTimeMillis()) / 1000);
                    objText += cachedTimeLeftFormat.replace("<time>", String.valueOf(timeLeft));
                }

                String finalBar = cachedObjectivePrefix + objText;
                for (Player p : participants) {
                    if (p.isOnline() && p.getWorld().equals(dungeonWorld)) {
                        p.sendActionBar(ColorUtils.parse(finalBar));
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

    /**
     * Evaluates the timeout sequence for a specific action.
     * Deducts lives, manages spectator transitions, and attempts to restart the stage if possible.
     *
     * @param action The active action that breached its time limit.
     */
    private void handleTimeLimitPenalty(DungeonAction action) {
        broadcastMessage("game.time_out");
        int penalty = action.getTimeLimitPenalty();
        boolean outOfLives = false;

        for (Player p : participants) {
            if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;

            plugin.getLivesManager().removeLives(p.getUniqueId(), penalty);
            LivesManager.PlayerLives livesData = plugin.getLivesManager().getLives(p.getUniqueId());
            int current = livesData != null ? livesData.getCurrentLives() : 0;

            String lossMsg = plugin.getLanguageManager().getString("lives.time_out_penalty", "&cYou lost <amount> lives due to time limit! Current: <current>")
                    .replace("<amount>", String.valueOf(penalty))
                    .replace("<current>", String.valueOf(current));
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
                    handlePlayerDisconnect(p);
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
                    plugin.getLogger().warning("Cleanup error on timeout: " + ex.getMessage());
                }
            }
            startStage(currentStageIndex);
        }
    }

    /**
     * Instantiates the progression of a specific stage by index.
     * Identifies the end of the dungeon if the stages are depleted.
     *
     * @param index The ordinal tier of the stage.
     */
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

    /**
     * Commences the logic for the currently queued action inside the active stage.
     */
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
            plugin.getLogger().severe("Error starting action: " + e.getMessage());
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

    /**
     * Funnels server events to the currently active action object for assessment.
     *
     * @param event The Bukkit Event triggering the check.
     */
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

    /**
     * Orchestrates the transition period between two consecutive stages.
     * Fires configured stage-complete commands securely.
     */
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(currentStageIndex + 1), 60L);
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    /**
     * Safely bypasses the remaining actions, terminates the current stage cleanly,
     * and forcibly jumps to the target stage without causing recursive reflection crashes.
     * Supplying a target stage greater than the total stages will instantly finish the dungeon.
     *
     * @param targetStage The 1-based index of the stage to jump to (e.g. 5 jumps to Stage 5).
     */
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(targetStage - 1), 60L);
    }


    /**
     * Checks if all participants are disconnected, dead, or spectating.
     * Forcibly stops the game instance as a failure if conditions are met.
     */
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

    /**
     * Initiates the countdown sequence after a dungeon is successfully completed.
     * Handles the visual notifications based on configuration and executes the
     * provided completion logic when the timer reaches zero.
     *
     * @param plugin       The SinceDungeon plugin instance.
     * @param players      The collection of players currently inside the dungeon instance.
     * @param delaySeconds The total time in seconds before the execution happens.
     * @param onComplete   The runnable to execute once the countdown finishes.
     */
    public void startKickCountdown(SinceDungeon plugin, Collection<Player> players, int delaySeconds, Runnable onComplete) {
        String displayType = plugin.getConfigFile().getString("dungeon.gameplay.kick-countdown.display-type", "ACTIONBAR").toUpperCase();

        new BukkitRunnable() {
            int timeLeft = delaySeconds;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    this.cancel();
                    return;
                }

                for (Player p : players) {
                    if (p == null || !p.isOnline()) continue;

                    switch (displayType) {
                        case "ACTIONBAR":
                            String actionMsg = plugin.getLanguageManager().getString("game.kick_countdown.actionbar", "&eTeleporting to Lobby in &c<time>s&e...");
                            p.sendActionBar(ColorUtils.parse(actionMsg.replace("<time>", String.valueOf(timeLeft))));
                            break;

                        case "TITLE":
                            String titleMain = plugin.getLanguageManager().getString("game.kick_countdown.title.main", "&c<time>");
                            String titleSub = plugin.getLanguageManager().getString("game.kick_countdown.title.sub", "&eSeconds until teleport");

                            Title.Times times = Title.Times.times(
                                    Duration.ZERO,
                                    Duration.ofSeconds(2),
                                    Duration.ZERO
                            );
                            Title title = Title.title(
                                    ColorUtils.parse(titleMain.replace("<time>", String.valueOf(timeLeft))),
                                    ColorUtils.parse(titleSub.replace("<time>", String.valueOf(timeLeft))),
                                    times
                            );
                            p.showTitle(title);
                            break;

                        case "CHAT":
                            String chatMsg = plugin.getLanguageManager().getString("game.kick_countdown.chat", "&eThe dungeon will close in &c<time> &eseconds.");
                            p.sendMessage(ColorUtils.parseWithPrefix(chatMsg.replace("<time>", String.valueOf(timeLeft))));
                            break;

                        case "NONE":
                        default:
                            break;
                    }
                }
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Executes the conclusion routines of the dungeon.
     * Submits scores to the database asynchronously, validates First-Time completion commands,
     * fires standard conclusion commands, registers player cooldowns, initiates victory titles,
     * and launches the kick countdown.
     */
    private void finishDungeon() {
        this.isCleared = true;
        this.isRunning = false;

        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();

        broadcastMessage("game.finish");
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        int finalElapsed = (int) elapsedSeconds;
        String formattedTime = formatTime(finalElapsed);

        Map<Integer, Integer> activeTiers = (participants.size() > 1) ? template.partyRewardTiers() : template.soloRewardTiers();
        int chestCount = 1;
        for (Map.Entry<Integer, Integer> entry : activeTiers.entrySet()) {
            if (elapsedSeconds <= entry.getKey()) chestCount = Math.max(chestCount, entry.getValue());
        }
        final int finalChestCount = chestCount;

        if (plugin.getTopManager() != null && template != null) {
            String dungeonId = template.id();
            String awardedTo = plugin.getConfigFile().getString("dungeon.top-awarded-to", "ALL_MEMBERS");
            TopManager topManager = plugin.getTopManager();

            PartyManager.Party topParty = plugin.getPartyManager().getParty(initiatorId);
            UUID leaderId = topParty != null ? topParty.getLeader() : initiatorId;

            if (participants.size() > 1) {
                String membersNames = participants.stream()
                        .filter(Player::isOnline)
                        .map(Player::getName)
                        .collect(Collectors.joining(", "));
                topManager.savePartyClearTime(dungeonId, membersNames, finalElapsed);
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                for (Player p : participants) {
                    if (!p.isOnline()) continue;

                    int previousClears = topManager.getPlayerClears(dungeonId, p.getUniqueId());
                    boolean isFirstTime = (previousClears == 0);

                    if (isFirstTime) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            executeActionCommandsForPlayer(template.settings().onFirstFinishCmds(), p, currentStageIndex);
                        });
                    }

                    boolean isLeader = p.getUniqueId().equals(leaderId);
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

                    int cooldownSeconds = template.settings().cooldownSeconds();
                    if (cooldownSeconds > 0) {
                        long expireEpoch = System.currentTimeMillis() + (cooldownSeconds * 1000L);
                        plugin.getCooldownManager().setCooldown(p.getUniqueId(), dungeonId, expireEpoch);
                    }
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

        int kickDelay = template.settings().kickDelayAfterFinish();

        startKickCountdown(plugin, participants, kickDelay, () -> {
            String shareMode = plugin.getConfigFile().getString("party.reward-share-mode", "EQUAL");
            RewardGUI rewardHelper = new RewardGUI(plugin);

            for (Player p : participants) {
                if (!p.isOnline() || p.isDead()) continue;

                PartyManager.Party party = plugin.getPartyManager().getParty(p.getUniqueId());
                UUID currentLeader = party != null ? party.getLeader() : initiatorId;

                if (shareMode.equalsIgnoreCase("LEADER_ONLY") && !p.getUniqueId().equals(currentLeader)) {
                    // Logic allows the leader only to be rewarded.
                } else if (eventChestCount > 0 && hasRewards) {
                    RewardSession oldSession = RewardSessionManager.getSession(p);
                    if (oldSession != null && oldSession.getChestCount() > 0) {
                        rewardHelper.forceClaimAll(p, oldSession);
                    }
                    RewardSessionManager.addSession(p, new RewardSession(eventChestCount, template));
                }

                if (p.isInsideVehicle()) p.leaveVehicle();
                p.setVelocity(new Vector(0, 0, 0));

                PlayerState state = savedStates.get(p.getUniqueId());
                Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

                plugin.getDungeonManager().addTransitioning(p.getUniqueId());

                p.teleportAsync(targetLoc).thenAccept(success -> {
                    if (success && p.isOnline()) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline() && !p.isDead()) {
                                if (eventChestCount > 0 && hasRewards && (shareMode.equalsIgnoreCase("EQUAL") || p.getUniqueId().equals(currentLeader))) {
                                    rewardHelper.openRewardGUI(p, eventChestCount, template);
                                } else {
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("game.no_reward")));
                                }
                            }
                        }, 15L);
                    } else if (p.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.teleport(targetLoc);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerState(p), 5L);
                        });
                    }
                });
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false, DungeonEndEvent.EndReason.CLEARED), 40L);
        });
    }

    /**
     * Handles logic when a player leaves the server or is forcefully kicked.
     * Safely processes inventory restoration regardless of their online state.
     *
     * @param p The player entity.
     */
    public void handlePlayerDisconnect(Player p) {
        boolean wasInDungeon = (dungeonWorld != null && p.getWorld().equals(dungeonWorld));

        if (p.isDead()) {
            p.spigot().respawn();
        }
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setVelocity(new Vector(0, 0, 0));

        PlayerState state = savedStates.get(p.getUniqueId());
        Location targetLoc = (state != null && state.location.getWorld() != null) ? state.location : Bukkit.getWorlds().get(0).getSpawnLocation();

        if (wasInDungeon) {
            p.teleport(targetLoc);
        }

        restorePlayerState(p);

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

    /**
     * Interrupts and halts the dungeon sequence cleanly.
     * Identical to explicitly defining the EndReason as a FORCE_STOPPED state.
     *
     * @param teleport Should players be returned to their saved locations.
     */
    public void stop(boolean teleport) {
        stop(teleport, DungeonEndEvent.EndReason.FORCE_STOPPED);
    }

    /**
     * Executes the overarching unloader mechanism, detaching all players
     * and signaling the WorldManager to safely purge the dungeon environment.
     *
     * @param teleport Should players be returned.
     * @param reason   The technical reason for closure.
     */
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
                            BungeeUtils.sendPlayerToServer(p, returnServer);
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
                } else {
                    restorePlayerState(p);
                }
            }
        }

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

    /**
     * Executes an emergency termination sequence.
     * Deconstructs states faster to resolve potential server deadlocks or ghost worlds.
     */
    public void forceShutdown() {
        if (isStopping) return;
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
                } else if (p.isOnline()) {
                    restorePlayerState(p);
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

    /**
     * Empties all memory arrays to enforce rigid Java garbage collection.
     */
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

    /**
     * Reverts a player back to their original state captured before entering the dungeon.
     * Safely executes even if the player has disconnected from the server.
     *
     * @param p The player to restore.
     */
    public void restorePlayerState(Player p) {
        PlayerState state = savedStates.get(p.getUniqueId());
        if (state != null) {
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
            } else {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
            p.setFallDistance(0);
            p.setVelocity(new Vector(0, 0, 0));

            savedStates.remove(p.getUniqueId());
        }
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

    public Player getPlayer() {
        return Bukkit.getPlayer(initiatorId);
    }

    public String getCachedObjectivePrefix() {
        return cachedObjectivePrefix;
    }

    public String getCachedTimeLeftFormat() {
        return cachedTimeLeftFormat;
    }

    /**
     * Encapsulates the cached metadata of a player prior to entering the dungeon.
     */
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
                copy[i] = (original[i] != null && original[i].getType() != Material.AIR) ? original[i].clone() : null;
            }
            return copy;
        }
    }
}