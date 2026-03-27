package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class EditorSession {
    private final SinceDungeon plugin;
    private final Player player;
    private final File file;
    private final YamlConfiguration config;
    private final Map<String, Integer> pageCache = new HashMap<>();
    private String currentStage = null;
    private String currentActionKey = null;
    private String currentRewardKey = null;
    private String currentConditionKey = null;
    private InputType currentInput = InputType.NONE;
    private String promptKey = null;
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

        String yamlData = config.saveToString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), yamlData);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = plugin.getMessagesFile().getString("editor.chat.saved");
                    if (msg != null && player.isOnline()) player.sendMessage(ColorUtils.parseWithPrefix(msg));

                    String soundName = plugin.getConfigFile().getString("sounds.editor_save");
                    Sound sound = getSound(soundName);
                    if (sound != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound, 1f, 1f);
                    }
                });
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = plugin.getMessagesFile().getString("editor.chat.save_error");
                    if (errorMsg != null && player.isOnline())
                        player.sendMessage(ColorUtils.parse(errorMsg.replace("<error>", e.getMessage())));
                });
                plugin.getLogger().severe("Error saving Dungeon: " + e.getMessage());
            }
        });
    }

    private org.bukkit.Sound getSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return null;
        soundName = soundName.trim();
        if (soundName.startsWith("minecraft:")) {
            soundName = soundName.substring(10);
        }

        if (ServerVersion.isAtLeast(1, 21, 3)) {
            try {
                NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
                if (key == null) key = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
                Sound sound = org.bukkit.Registry.SOUND_EVENT.get(key);
                if (sound != null) return sound;
                return (Sound) Sound.class.getField(soundName.toUpperCase(Locale.ROOT)).get(null);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return org.bukkit.Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e1) {
                try {
                    String legacyName = soundName.replace(".", "_").toUpperCase(java.util.Locale.ROOT);
                    return org.bukkit.Sound.valueOf(legacyName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public void awaitInput(InputType type, String promptKey, EditorCallback callback) {
        this.currentInput = type;
        this.promptKey = promptKey;
        this.inputCallback = callback;
    }

    public void completeInput(String value) {
        if (inputCallback != null) inputCallback.onInput(value);
        this.currentInput = InputType.NONE;
        this.promptKey = null;
    }

    public void reopenLastMenu() {
        if (lastMenuOpener != null && player.isOnline()) lastMenuOpener.accept(player);
    }

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

    public int getPage(String menuType) {
        return pageCache.getOrDefault(menuType, 0);
    }

    public void setPage(String menuType, int page) {
        pageCache.put(menuType, page);
    }

    public InputType getInputType() {
        return currentInput;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public void setLastMenuOpener(Consumer<Player> opener) {
        this.lastMenuOpener = opener;
    }

    public void cancelInput() {
        this.currentInput = InputType.NONE;
        this.promptKey = null;
        this.inputCallback = null;
    }

    public enum InputType {
        NONE, CREATE_FILENAME, EDIT_STRING, EDIT_NUMBER, EDIT_BOOLEAN, EDIT_LOCATION, EDIT_LOCATION_LIST, EDIT_LIST, EDIT_TIER, EDIT_CONDITION_CHECK, EDIT_KICK_DELAY
    }

    public interface EditorCallback {
        void onInput(String value);
    }
}