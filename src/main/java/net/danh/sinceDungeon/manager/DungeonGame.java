package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.system.WorldGuardHook;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DungeonGame {
    private final SinceDungeon plugin;
    private final Player player;
    private final DungeonTemplate template;
    private final Location oldLocation;
    private final List<List<DungeonAction>> stages = new ArrayList<>();
    private final String worldName;

    private World dungeonWorld;
    private int currentStageIndex = 0;
    private boolean isRunning = false;
    private boolean isPreparing = false;
    private boolean stageCompleting = false;

    private BukkitTask tickTask;
    private long startTime;

    public DungeonGame(SinceDungeon plugin, Player player, DungeonTemplate template) {
        this.plugin = plugin;
        this.player = player;
        this.template = template;
        this.oldLocation = player.getLocation();

        // [TEST CASE: Race Condition]
        // Sử dụng UUID ngẫu nhiên trong tên world để tránh trùng lặp nếu nhiều người chơi cùng lúc
        this.worldName = player.getName() + "_" + template.id() + "_" + UUID.randomUUID().toString().substring(0, 8);
        parseStages();
    }

    private void parseStages() {
        // template.stages() is now Map<Integer, List<Map<String, Object>>>
        // We need to sort them to ensure order
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

    public void sendMessage(String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString(key);
        String prefix = plugin.getMessagesFile().getString("prefix");
        if (msg == null) return;
        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        player.sendMessage(ColorUtils.parse(prefix + msg));
    }

    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;
        sendMessage("lobby.preparing");

        // [OPTIMIZATION] Async World Creation
        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> {
                    this.dungeonWorld = world;

                    // [OPTIMIZATION] Gamerules for Performance
                    dungeonWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    dungeonWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                    dungeonWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    dungeonWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    dungeonWorld.setAutoSave(false); // Quan trọng: Không autosave map rác

                    startCountdown();
                })
                .exceptionally(ex -> {
                    sendMessage("error.create_failed");
                    plugin.getLogger().severe("Failed to create dungeon world: " + ex.getMessage());
                    plugin.getDungeonManager().removeGame(player.getUniqueId());
                    return null;
                });
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int count = plugin.getConfigFile().getInt("dungeon.lobby-countdown", 5);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop(false);
                    cancel();
                    return;
                }
                if (count <= 0) {
                    enterDungeon();
                    cancel();
                    return;
                }
                sendMessage("lobby.countdown", "<time>", String.valueOf(count));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void enterDungeon() {
        isPreparing = false;
        isRunning = true;

        WorldGuardHook.applyDungeonFlags(dungeonWorld);

        Location spawnLoc = dungeonWorld.getSpawnLocation().add(0.5, 1, 0.5);
        player.teleport(spawnLoc);
        player.setGameMode(GameMode.SURVIVAL);

        sendMessage("game.start");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f);
        this.startTime = System.currentTimeMillis();

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning || !player.isOnline()) {
                    cancel();
                    stop(true); // Ensure clean exit
                    return;
                }
                runTick();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        startStage(0);
    }

    private void runTick() {
        if (stageCompleting || currentStageIndex >= stages.size()) return;

        boolean allCompleted = true;
        for (DungeonAction action : stages.get(currentStageIndex)) {
            if (!action.isCompleted()) {
                if (action instanceof Tickable) {
                    try {
                        ((Tickable) action).onTick(this);
                    } catch (Exception e) {
                        // [TEST CASE: Action Error]
                        // Nếu logic tick bị lỗi, log ra console và force complete để không kẹt dungeon
                        plugin.getLogger().warning("Tick error in action: " + e.getMessage());
                        // action.setCompleted(true); // Cần thêm setter nếu muốn force skip
                    }
                }
                if (!action.isCompleted()) allCompleted = false;
            }
        }

        if (allCompleted) checkCompletion();
    }

    private void startStage(int index) {
        if (index >= stages.size()) {
            finishDungeon();
            return;
        }
        currentStageIndex = index;
        this.stageCompleting = false;

        sendMessage("game.stage_start", "<stage>", String.valueOf(index + 1));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);

        for (DungeonAction action : stages.get(index)) {
            try {
                action.announceStart(this);
                action.start(this);
            } catch (Exception e) {
                // [TEST CASE: Config Error]
                // Nếu start action lỗi (vd Mob không tồn tại), log lỗi và tiếp tục
                plugin.getLogger().severe("Error starting action in stage " + index + ": " + e.getMessage());
            }
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
        sendMessage("game.stage_complete", "<stage>", String.valueOf(currentStageIndex + 1));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> startStage(currentStageIndex + 1), 60L);
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

        int finalChestCount = chestCount;
        int finalElapsed = (int) elapsedSeconds;

        isRunning = false;
        if (tickTask != null) tickTask.cancel();

        // Teleport về trước khi mở GUI
        player.teleport(oldLocation);
        sendMessage("game.completion_time", "<time>", String.valueOf(finalElapsed));

        // Mở GUI phần thưởng
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (finalChestCount > 0) {
                    new RewardGUI(plugin).openRewardGUI(player, finalChestCount, template);
                } else {
                    sendMessage("game.no_reward");
                }
            }
        }, 10L);

        // Xóa world sau khi chắc chắn player đã rời đi
        Bukkit.getScheduler().runTaskLater(plugin, () -> stop(false), 100L);
    }

    public void stop(boolean teleport) {
        isRunning = false;
        if (tickTask != null) tickTask.cancel();

        // Chỉ teleport nếu người chơi còn trong world dungeon
        if (teleport && player.isOnline() && dungeonWorld != null && player.getWorld().equals(dungeonWorld)) {
            player.teleport(oldLocation);
        }

        if (dungeonWorld != null) {
            WorldManager.unloadAndDeleteWorld(plugin, dungeonWorld);
            dungeonWorld = null;
        }

        plugin.getDungeonManager().removeGame(player.getUniqueId());
    }

    public World getWorld() {
        return dungeonWorld;
    }

    public Player getPlayer() {
        return player;
    }
}