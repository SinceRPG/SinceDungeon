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
 */
public class ConfigUtils {
    private final SinceDungeon plugin;
    private final String name;
    private File file;
    private FileConfiguration config;

    /**
     * Generates or fetches a configuration file handle.
     *
     * @param plugin The parent plugin.
     * @param name   The path and name of the internal plugin resource.
     */
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
                    e.printStackTrace();
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        autoUpdateConfig(false);
    }

    /**
     * Compares the current file with the original file inside the .jar to automatically update it.
     *
     * @param removeObsolete If set to true, the plugin will DELETE keys that exist in the external file
     *                       but not in the .jar (Use with caution as this risks losing user's custom data).
     */
    private void autoUpdateConfig(boolean removeObsolete) {
        InputStream defaultStream = plugin.getResource(name);
        if (defaultStream == null) return;
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
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
            e.printStackTrace();
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