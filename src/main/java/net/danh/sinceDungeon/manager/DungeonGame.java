package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.system.WorldGuardHook;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
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

    // Cờ ngăn chặn vòng lặp gọi hàm stop() vô tận
    private boolean isStopping = false;

    public DungeonGame(SinceDungeon plugin, Player player, DungeonTemplate template) {
        this.plugin = plugin;
        this.player = player;
        this.template = template;
        this.oldLocation = player.getLocation();

        // [ĐÃ VÁ LỖI]: Chuẩn hóa tên world để Regex dọn rác không bị sai nếu tên người chơi có dấu "_"
        this.worldName = "SinceDungeon_" + player.getName() + "_" + UUID.randomUUID().toString().substring(0, 8);
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

    // Lấy tin nhắn từ messages.yml và xử lý Placeholder
    public void sendMessage(String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString(key);
        String prefix = plugin.getMessagesFile().getString("prefix", "");
        if (msg == null || msg.isEmpty()) return;

        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        player.sendMessage(ColorUtils.parse(prefix + msg));
    }

    // Lấy âm thanh từ config.yml
    private void playConfigSound(String key, float volume, float pitch) {
        String soundName = plugin.getConfigFile().getString("sounds." + key);
        org.bukkit.Sound sound = getSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private org.bukkit.Sound getSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return null;
        soundName = soundName.trim();

        // Bỏ tiền tố "minecraft:" nếu người dùng lỡ copy thừa
        if (soundName.startsWith("minecraft:")) {
            soundName = soundName.substring(10);
        }

        // ==========================================
        // 1. DÀNH CHO SERVER MỚI (>= 1.21.3)
        // ==========================================
        if (ServerVersion.isAtLeast(1, 21, 3)) {
            try {
                // Cách 1: Ưu tiên dùng Registry chuẩn (Giả sử họ nhập kiểu mới: block.note_block.pling)
                NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
                if (key == null) key = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));

                Sound sound = org.bukkit.Registry.SOUND_EVENT.get(key);
                if (sound != null) return sound;

                // Cách 2: Tự Parse Lên (Họ nhập kiểu cũ: BLOCK_NOTE_BLOCK_PLING)
                // Bukkit vẫn giữ các biến tĩnh (static fields) để tương thích ngược, ta dùng Reflection để gọi:
                return (Sound) Sound.class.getField(soundName.toUpperCase(Locale.ROOT)).get(null);
            } catch (Throwable ignored) {
            }
        }

        // ==========================================
        // 2. DÀNH CHO SERVER CŨ (< 1.21.3)
        // ==========================================
        else {
            try {
                // Giả sử họ nhập chuẩn cũ: BLOCK_NOTE_BLOCK_PLING
                return org.bukkit.Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e1) {
                // Tự Parse Xuống: Họ nhập chuẩn mới (block.note_block.pling), ta tự chuyển dấu "." thành "_"
                try {
                    String legacyName = soundName.replace(".", "_").toUpperCase(java.util.Locale.ROOT);
                    return org.bukkit.Sound.valueOf(legacyName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return null;
    }

    public void startLobby() {
        if (isPreparing || isRunning) return;
        isPreparing = true;
        sendMessage("lobby.preparing");

        WorldManager.createDungeonWorldAsync(plugin, template.templateWorld(), worldName)
                .thenAccept(world -> Bukkit.getScheduler().runTask(plugin, () -> {
                    this.dungeonWorld = world;

                    // Gamerules tối ưu Performance
                    if (ServerVersion.isAtMost(1, 21, 10)) {
                        dungeonWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                        dungeonWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                        dungeonWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                        dungeonWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    } else if (ServerVersion.isAtLeast(1, 21, 11)) {
                        dungeonWorld.setGameRule(GameRules.SPAWN_MOBS, false);
                        dungeonWorld.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                        dungeonWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
                        dungeonWorld.setGameRule(GameRules.ADVANCE_TIME, false);
                    }
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
                playConfigSound("lobby_countdown", 1f, 2f);
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
                        plugin.getLogger().warning("[Chống Crash] Lỗi khi chạy tick của Action: " + action.getClass().getSimpleName() + " - " + e.getMessage());
                        // Có thể cân nhắc force complete action lỗi để game tiếp tục:
                        action.completed = true;
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
        playConfigSound("stage_complete", 1f, 1f);

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

        player.teleport(oldLocation);
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

    // [ĐÃ VÁ LỖI]: Cờ isStopping ngăn sập server khi teleport kích hoạt event
    public void stop(boolean teleport) {
        if (isStopping) return;
        isStopping = true;
        isRunning = false;

        if (tickTask != null) tickTask.cancel();

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