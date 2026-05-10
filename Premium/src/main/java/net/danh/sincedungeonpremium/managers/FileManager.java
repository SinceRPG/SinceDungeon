package net.danh.sincedungeonpremium.managers;

import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Configuration and Language Manager for SinceDungeon Premium.
 * Responsibilities:
 * - Initializes, loads, and saves config.yml and messages.yml.
 * - Auto-Updates YAML files with new keys from the plugin jar to prevent errors.
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
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        autoUpdate(config, configFile, "config.yml");

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        autoUpdate(messages, messagesFile, "messages.yml");
    }

    /**
     * Compares the current file with the default resource inside the jar and injects missing keys.
     * Aborts immediately if the specific file contains 'auto-update: false'.
     */
    private void autoUpdate(FileConfiguration currentConfig, File file, String resourceName) {
        // Feature: Per-file Auto Update Toggle
        if (!currentConfig.getBoolean("auto-update", true)) return;

        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream == null) return;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.contains(key)) {
                currentConfig.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                currentConfig.save(file);
                plugin.getLogger().info("[Auto-Updater] Automatically updated " + resourceName + " with missing keys.");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to auto-update " + resourceName + ": " + e.getMessage());
            }
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
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

        if (player != null && player.isOnline()) {
            player.sendMessage(ColorUtils.parse(finalMsg));
        }
    }
}