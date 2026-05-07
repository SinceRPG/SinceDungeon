package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ConfigUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Intelligent proxy manager for modular configuration files.
 * Routes configuration queries to the appropriate file based on the root node.
 */
public class ConfigManager {
    private final SinceDungeon plugin;
    private ConfigUtils mainConfig;
    private ConfigUtils gameplayConfig;
    private ConfigUtils itemsConfig;
    private ConfigUtils effectsConfig;
    private ConfigUtils actionsConfig;
    private ConfigUtils menusConfig;

    public ConfigManager(SinceDungeon plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        mainConfig = new ConfigUtils(plugin, "config.yml");
        gameplayConfig = new ConfigUtils(plugin, "settings/gameplay.yml");
        itemsConfig = new ConfigUtils(plugin, "settings/items.yml");
        effectsConfig = new ConfigUtils(plugin, "settings/effects.yml");
        actionsConfig = new ConfigUtils(plugin, "settings/actions.yml");
        menusConfig = new ConfigUtils(plugin, "settings/menus.yml");
    }

    public void save() {
        mainConfig.save();
        gameplayConfig.save();
        itemsConfig.save();
        effectsConfig.save();
        actionsConfig.save();
        menusConfig.save();
    }

    public void reload() {
        mainConfig.reload();
        gameplayConfig.reload();
        itemsConfig.reload();
        effectsConfig.reload();
        actionsConfig.reload();
        menusConfig.reload();
    }

    private ConfigUtils route(String path) {
        if (path == null || path.isEmpty()) return mainConfig;
        String root = path.split("\\.")[0];

        return switch (root) {
            case "sounds", "particles", "titles" -> effectsConfig;
            case "action-defaults", "action-notifications" -> actionsConfig;
            case "reward", "leaderboard", "editor" -> menusConfig;
            case "items" -> itemsConfig;
            case "party", "dungeon", "lives" -> gameplayConfig;
            default -> mainConfig;
        };
    }

    public String getString(String path) {
        return route(path).getString(path);
    }

    public String getString(String path, String def) {
        return route(path).getString(path, def);
    }

    public int getInt(String path) {
        return route(path).getInt(path);
    }

    public int getInt(String path, int def) {
        return route(path).getInt(path, def);
    }

    public double getDouble(String path) {
        return route(path).getDouble(path);
    }

    public double getDouble(String path, double def) {
        return route(path).getDouble(path, def);
    }

    public boolean getBoolean(String path) {
        return route(path).getBoolean(path);
    }

    public boolean getBoolean(String path, boolean def) {
        return route(path).getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return route(path).getStringList(path);
    }

    public Component getComponent(String path) {
        return route(path).getComponent(path);
    }

    public ConfigurationSection getSection(String path) {
        return route(path).getConfig().getConfigurationSection(path);
    }

    public FileConfiguration getConfig() {
        return mainConfig.getConfig();
    }
}