package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

public class EditorSession {
    private final SinceDungeon plugin;
    private final Player player;
    private final File file;
    private final YamlConfiguration config;

    // --- STATES (Changed to Keys) ---
    private String currentStage = null;
    private String currentActionKey = null;
    private String currentRewardKey = null;
    private String currentConditionKey = null;

    // --- MEMORY & NAVIGATION ---
    private InputType currentInput = InputType.NONE;
    private EditorCallback inputCallback = null;
    private Consumer<Player> lastMenuOpener = null;

    public EditorSession(SinceDungeon plugin, Player player, File file) {
        this.plugin = plugin;
        this.player = player;
        this.file = file;
        if (file != null && file.exists()) {
            this.config = YamlConfiguration.loadConfiguration(file);
        } else {
            this.config = new YamlConfiguration();
        }
    }

    public void save() {
        if (file == null) return;
        try {
            config.save(file);
            String msg = plugin.getMessagesFile().getString("editor.chat.saved");
            if (msg != null) player.sendMessage(ColorUtils.parseWithPrefix(msg));

            // Lấy âm thanh từ config.yml an toàn
            String soundName = plugin.getConfigFile().getString("sounds.editor_save");
            Sound sound = getSound(soundName);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        } catch (IOException e) {
            String errorMsg = plugin.getMessagesFile().getString("editor.chat.save_error");
            if (errorMsg != null)
                player.sendMessage(ColorUtils.parse(errorMsg.replace("<error>", e.getMessage())));
            e.printStackTrace();
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

    // Input Handling (Unchanged)
    public void awaitInput(InputType type, EditorCallback callback) {
        this.currentInput = type;
        this.inputCallback = callback;
    }

    public void completeInput(String value) {
        if (inputCallback != null) inputCallback.onInput(value);
        this.currentInput = InputType.NONE;
    }

    public void reopenLastMenu() {
        if (lastMenuOpener != null && player.isOnline()) lastMenuOpener.accept(player);
    }

    // Getters & Setters
    public Player getPlayer() {
        return player;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public File getFile() {
        return file;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String stage) {
        this.currentStage = stage;
    }

    public String getCurrentActionKey() {
        return currentActionKey;
    }

    public void setCurrentActionKey(String key) {
        this.currentActionKey = key;
    }

    public String getCurrentRewardKey() {
        return currentRewardKey;
    }

    public void setCurrentRewardKey(String key) {
        this.currentRewardKey = key;
    }

    public String getCurrentConditionKey() {
        return currentConditionKey;
    }

    public void setCurrentConditionKey(String key) {
        this.currentConditionKey = key;
    }

    public InputType getInputType() {
        return currentInput;
    }

    public void setLastMenuOpener(Consumer<Player> opener) {
        this.lastMenuOpener = opener;
    }

    public void cancelInput() {
        this.currentInput = InputType.NONE;
        this.inputCallback = null;
    }

    public enum InputType {NONE, CREATE_FILENAME, EDIT_VALUE}

    public interface EditorCallback {
        void onInput(String value);
    }
}