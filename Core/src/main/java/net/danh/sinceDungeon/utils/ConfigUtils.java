package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility wrapper for simplified YAML Configuration operations.
 * Integrated with Auto-Updater to sync missing keys from plugin jar.
 * Now supports per-file auto-update toggling.
 */
public class ConfigUtils {
    private final SinceDungeon plugin;
    private final String name;
    private File file;
    private FileConfiguration config;

    public ConfigUtils(SinceDungeon plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.load();
    }

    public void load() {
        file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            } else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create config file " + name + ": " + e.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        autoUpdateConfig(false);
    }

    /**
     * Compares the current file with the original file inside the .jar to automatically update it.
     * Aborts immediately if the specific file contains 'auto-update: false'.
     */
    private void autoUpdateConfig(boolean removeObsolete) {
        // Feature: Per-file Auto Update Toggle
        if (!config.getBoolean("auto-update", true)) return;

        InputStream defaultStream = plugin.getResource(name);
        if (defaultStream == null) return;
        YamlConfiguration defaultConfig;
        try (InputStream stream = defaultStream;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            defaultConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read default config " + name + ": " + e.getMessage());
            return;
        }
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }
        if (removeObsolete) {
            for (String key : config.getKeys(true)) {
                if (!defaultConfig.contains(key)) {
                    config.set(key, null);
                    changed = true;
                }
            }
        }

        if (changed) {
            save();
            plugin.getLogger().info("[Auto-Updater] Automatically updated " + name + " with missing configuration keys.");
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config file " + name + ": " + e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    public String getString(String path) {
        return config.getString(path, "");
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean defValue) {
        return config.getBoolean(path, defValue);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public Component getComponent(String path) {
        String raw = config.getString(path);
        if (raw == null) return Component.empty();
        return ColorUtils.parse(raw);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public void setAndSave(String path, Object value) {
        config.set(path, value);
        save();
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
