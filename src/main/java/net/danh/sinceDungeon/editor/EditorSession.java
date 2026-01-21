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

    // --- STATES ---
    private String currentStage = null;
    private int currentActionIndex = -1;
    private int currentRewardIndex = -1;

    // --- MEMORY & NAVIGATION ---
    private InputType currentInput = InputType.NONE;
    private EditorCallback inputCallback = null;

    // Lưu hàm mở menu cuối cùng để reopen khi chat xong/chat lỗi
    private Consumer<Player> lastMenuOpener = null;

    public EditorSession(SinceDungeon plugin, Player player, File file) {
        this.plugin = plugin;
        this.player = player;
        this.file = file;
        // Load config vào bộ nhớ. Mọi thay đổi set() sẽ nằm ở đây cho đến khi save()
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
            String msg = plugin.getMessagesFile().getString("editor.chat.save_error");
            if (msg != null)
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg.replace("<error>", e.getMessage())));
            e.printStackTrace();
        }
    }

    // --- INPUT HANDLING ---
    public void awaitInput(InputType type, EditorCallback callback) {
        this.currentInput = type;
        this.inputCallback = callback;
    }

    public void completeInput(String value) {
        if (inputCallback != null) {
            inputCallback.onInput(value);
        }
        // Sau khi input xong, đưa về trạng thái NONE nhưng KHÔNG xóa callback ngay
        // để tránh lỗi logic, callback sẽ được ghi đè ở lần await tiếp theo.
        this.currentInput = InputType.NONE;
    }

    public void reopenLastMenu() {
        if (lastMenuOpener != null && player.isOnline()) {
            lastMenuOpener.accept(player);
        }
    }

    // --- GETTERS & SETTERS ---
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

    public int getCurrentActionIndex() {
        return currentActionIndex;
    }

    public void setCurrentActionIndex(int index) {
        this.currentActionIndex = index;
    }

    public int getCurrentRewardIndex() {
        return currentRewardIndex;
    }

    public void setCurrentRewardIndex(int index) {
        this.currentRewardIndex = index;
    }

    public InputType getInputType() {
        return currentInput;
    }

    public void setLastMenuOpener(Consumer<Player> opener) {
        this.lastMenuOpener = opener;
    }

    public enum InputType {NONE, CREATE_FILENAME, EDIT_VALUE}

    public interface EditorCallback {
        void onInput(String value);
    }
}