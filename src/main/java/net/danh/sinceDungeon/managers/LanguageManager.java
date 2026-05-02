package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ConfigUtils;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Manages dynamically modularized language files.
 * Routes message queries to the correct category file based on the root path node.
 */
public class LanguageManager {
    private final SinceDungeon plugin;
    private final String locale;
    private final String basePath;
    private final Map<String, ConfigUtils> configs = new HashMap<>();
    private final Map<String, String> categoryToFile = new HashMap<>();

    public LanguageManager(SinceDungeon plugin, String locale) {
        this.plugin = plugin;
        this.locale = locale;
        // English defaults strictly to the root folder, others into languages/<locale>/
        this.basePath = locale.equalsIgnoreCase("en") ? "" : "languages/" + locale + "/";
        initMapping();
        loadConfigs();
    }

    private void initMapping() {
        categoryToFile.put("prefix", "general.yml");
        categoryToFile.put("papi", "general.yml");
        categoryToFile.put("cross_server", "cross_server.yml");
        categoryToFile.put("cooldown", "cooldown.yml");
        categoryToFile.put("lives", "lives.yml");
        categoryToFile.put("top", "top.yml");
        categoryToFile.put("admin", "admin.yml");
        categoryToFile.put("party", "party.yml");
        categoryToFile.put("game", "game.yml");
        categoryToFile.put("lobby", "game.yml");
        categoryToFile.put("objective", "game.yml");
        categoryToFile.put("action", "game.yml");
        categoryToFile.put("error", "error.yml");
        categoryToFile.put("reward", "reward.yml");
        categoryToFile.put("editor", "editor.yml");
    }

    public void loadConfigs() {
        configs.clear();
        Set<String> files = new HashSet<>(categoryToFile.values());
        for (String file : files) {
            configs.put(file, new ConfigUtils(plugin, basePath + file));
        }
    }

    public void reload() {
        for (ConfigUtils config : configs.values()) {
            config.reload();
        }
    }

    public ConfigUtils getConfigUtilsForPath(String path) {
        String root = path.split("\\.")[0];
        String fileName = categoryToFile.getOrDefault(root, "general.yml");
        return configs.get(fileName);
    }

    public String getString(String path) {
        return getConfigUtilsForPath(path).getString(path, "");
    }

    public String getString(String path, String def) {
        return getConfigUtilsForPath(path).getString(path, def);
    }

    public List<String> getStringList(String path) {
        return getConfigUtilsForPath(path).getStringList(path);
    }

    public FileConfiguration getConfigByRoot(String rootNode) {
        String fileName = categoryToFile.getOrDefault(rootNode, "general.yml");
        return configs.get(fileName).getConfig();
    }

    public String getLocale() {
        return locale;
    }
}