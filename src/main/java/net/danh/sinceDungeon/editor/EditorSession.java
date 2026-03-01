package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
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
            if (msg != null) player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1f);
        } catch (IOException e) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Save Error: " + e.getMessage()));
            e.printStackTrace();
        }
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