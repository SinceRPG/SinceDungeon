package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.models.DungeonTemplate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to handle loading of dungeon configurations from files.
 */
public class DungeonLoader {

    /**
     * Loads a DungeonTemplate from a YAML file.
     *
     * @param plugin The main plugin instance.
     * @param id     The name of the dungeon file (without .yml).
     * @return The loaded DungeonTemplate or null if invalid.
     */
    public static DungeonTemplate loadTemplate(SinceDungeon plugin, String id) {
        File file = new File(plugin.getDataFolder(), "dungeons/" + id + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String world = config.getString("template-world");
        if (world == null) return null;

        boolean isPublic = config.getBoolean("public", false);

        boolean keepInv = config.contains("settings.keep-inventory-on-death") ? config.getBoolean("settings.keep-inventory-on-death") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.keep-inventory-on-death", true);
        boolean preventDrop = config.contains("settings.prevent-item-dropping") ? config.getBoolean("settings.prevent-item-dropping") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.prevent-item-dropping", true);
        boolean blockPearls = config.contains("settings.block-ender-pearls") ? config.getBoolean("settings.block-ender-pearls") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.block-ender-pearls", true);
        int kickDelay = config.contains("settings.kick-delay-after-finish") ? config.getInt("settings.kick-delay-after-finish") : plugin.getConfigFile().getInt("dungeon.gameplay.kick-delay-after-finish", 10);
        boolean forceWeather = config.contains("settings.force-daylight-and-clear-weather") ? config.getBoolean("settings.force-daylight-and-clear-weather") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.force-daylight-and-clear-weather", true);
        boolean saveStats = config.contains("settings.save-and-restore-stats") ? config.getBoolean("settings.save-and-restore-stats") : plugin.getConfigFile().getConfig().getBoolean("dungeon.save-and-restore-stats", false);
        String deathAction = config.contains("settings.death-action") ? config.getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
        boolean clearMobDrops = config.contains("settings.clear-mob-drops") ? config.getBoolean("settings.clear-mob-drops") : plugin.getConfigFile().getConfig().getBoolean("dungeon.clear-mob-drops", true);

        int reqLives = config.getInt("settings.required-lives-to-join", 1);
        int deductLives = config.getInt("settings.lives-deducted-per-death", 1);

        DungeonTemplate.Settings settings = new DungeonTemplate.Settings(keepInv, preventDrop, blockPearls, kickDelay, forceWeather, saveStats, deathAction, clearMobDrops, reqLives, deductLives);

        List<DungeonTemplate.Condition> conditions = new ArrayList<>();
        ConfigurationSection condSec = config.getConfigurationSection("conditions");
        if (condSec != null) {
            for (String key : condSec.getKeys(false)) {
                ConfigurationSection c = condSec.getConfigurationSection(key);
                if (c != null) {
                    String check = c.getString("check");
                    if (check != null) {
                        conditions.add(new DungeonTemplate.Condition(
                                key,
                                c.getString("name", key),
                                check,
                                c.getString("msg")
                        ));
                    }
                }
            }
        }

        Map<Integer, Integer> tiers = new HashMap<>();
        ConfigurationSection tierSec = config.getConfigurationSection("rewards.tiers");
        if (tierSec != null) {
            for (String key : tierSec.getKeys(false)) {
                try {
                    tiers.put(Integer.parseInt(key), tierSec.getInt(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        List<DungeonReward> rewards = new ArrayList<>();
        ConfigurationSection poolSec = config.getConfigurationSection("rewards.pool");
        if (poolSec != null) {
            for (String key : poolSec.getKeys(false)) {
                ConfigurationSection itemSec = poolSec.getConfigurationSection(key);
                if (itemSec == null) continue;

                String type = itemSec.getString("type");
                String value = itemSec.getString("value");
                if (type == null || value == null) continue;

                rewards.add(new DungeonReward(
                        type,
                        value,
                        itemSec.getDouble("chance", 100.0),
                        itemSec.getString("name"),
                        itemSec.getStringList("lore")
                ));
            }
        }

        Map<Integer, List<Map<String, Object>>> stages = new HashMap<>();
        ConfigurationSection stageSec = config.getConfigurationSection("stages");

        if (stageSec != null) {
            for (String stageKey : stageSec.getKeys(false)) {
                try {
                    int stageNum = Integer.parseInt(stageKey);
                    ConfigurationSection actionSec = stageSec.getConfigurationSection(stageKey + ".actions");
                    List<Map<String, Object>> actionList = new ArrayList<>();

                    if (actionSec != null) {
                        for (String actionKey : actionSec.getKeys(false)) {
                            if (actionSec.isConfigurationSection(actionKey)) {
                                ConfigurationSection specificAction = actionSec.getConfigurationSection(actionKey);
                                if (specificAction != null)
                                    actionList.add(specificAction.getValues(false));
                            }
                        }
                    }
                    stages.put(stageNum, actionList);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid stage key in " + id + ": " + stageKey);
                }
            }
        }

        return new DungeonTemplate(id, world, isPublic, conditions, tiers, rewards, stages, settings);
    }

    /**
     * Parses a string representation of a vector.
     *
     * @param s The string to parse.
     * @return The parsed Vector.
     */
    public static Vector parseVector(String s) {
        try {
            String cleanString = s.replace(" ", "");
            String[] split = cleanString.split(",");
            if (split.length < 3) throw new IllegalArgumentException("Missing XYZ coordinates");

            return new Vector(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
        } catch (Exception e) {
            String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.vector_parse_fail", "Vector parsing failed: '<data>'");
            SinceDungeon.getPlugin().getLogger().warning(msg.replace("<data>", s));
            return new Vector(0, 0, 0);
        }
    }
}