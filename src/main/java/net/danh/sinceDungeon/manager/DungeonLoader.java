package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.reward.DungeonReward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DungeonLoader {

    public static DungeonTemplate loadTemplate(SinceDungeon plugin, String id) {
        File file = new File(plugin.getDataFolder(), "dungeons/" + id + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String world = config.getString("template-world");
        if (world == null) return null;

        boolean isPublic = config.getBoolean("public", false);

        // Load Conditions
        List<DungeonTemplate.Condition> conditions = new ArrayList<>();
        ConfigurationSection condSec = config.getConfigurationSection("conditions");
        if (condSec != null) {
            for (String key : condSec.getKeys(false)) {
                // Tối ưu: Dùng ConfigurationSection trực tiếp
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

        // Load Tiers
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

        // Load Rewards
        List<DungeonReward> rewards = new ArrayList<>();
        ConfigurationSection poolSec = config.getConfigurationSection("rewards.pool");
        if (poolSec != null) {
            for (String key : poolSec.getKeys(false)) {
                ConfigurationSection itemSec = poolSec.getConfigurationSection(key);
                if (itemSec == null) continue;

                String type = itemSec.getString("type");
                String value = itemSec.getString("value");
                // Skip if invalid
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

        // [CORE] Load Stages & Actions
        // Sử dụng getValues(false) để giữ nguyên cấu trúc ConfigurationSection cho các node con
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

        return new DungeonTemplate(id, world, isPublic, conditions, tiers, rewards, stages);
    }

    public static Vector parseVector(String s) {
        try {
            String[] split = s.split(",");
            return new Vector(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
        } catch (Exception e) {
            return new Vector(0, 0, 0);
        }
    }
}