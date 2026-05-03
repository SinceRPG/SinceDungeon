package net.danh.sincedungeonpremium.managers;

import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Configuration and Language Manager for SinceDungeon Premium.
 * Responsibilities:
 * - Initializes, loads, and saves config.yml and messages.yml.
 * - Retrieves values safely and applies modern MiniMessage coloring via Core's ColorUtils.
 */
public class FileManager {

    private final SinceDungeonPremium plugin;
    private File configFile;
    private FileConfiguration config;
    private File messagesFile;
    private FileConfiguration messages;

    public FileManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getMessageRaw(String path) {
        return messages.getString(path, "<red>Message not found: " + path);
    }

    public void sendMessage(Player player, String path, String... placeholders) {
        String prefix = messages.getString("prefix", "&6&lPremium &8» &7");
        String msg = messages.getString(path, "<red>Message not found: " + path);

        String finalMsg = prefix + msg;
        for (int i = 0; i < placeholders.length; i += 2) {
            finalMsg = finalMsg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }

        player.sendMessage(ColorUtils.parse(finalMsg));
    }
}