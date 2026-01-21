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

        // [NEW] Đọc biến Public (Mặc định false)
        boolean isPublic = config.getBoolean("public", false);

        // [CHANGE] Đọc Conditions (Hỗ trợ cả String và Map)
        List<DungeonTemplate.Condition> conditions = new ArrayList<>();
        List<?> rawConds = config.getList("conditions");
        if (rawConds != null) {
            for (Object obj : rawConds) {
                if (obj instanceof String) {
                    // Format cũ: - "%papi% >= 100"
                    conditions.add(new DungeonTemplate.Condition((String) obj, null));
                } else if (obj instanceof Map) {
                    // Format mới: - check: "..." msg: "..."
                    Map<?, ?> map = (Map<?, ?>) obj;
                    String check = (String) map.get("check");
                    String msg = (String) map.get("msg");
                    if (check != null) {
                        conditions.add(new DungeonTemplate.Condition(check, msg));
                    }
                }
            }
        }

        // Đọc Rewards
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
        List<Map<?, ?>> pool = config.getMapList("rewards.pool");
        for (Map<?, ?> map : pool) {
            Map<String, Object> data = (Map<String, Object>) map;
            String type = (String) data.get("type");
            String value = (String) data.get("value");
            String name = (String) data.get("name");
            List<String> lore = (List<String>) data.get("lore");
            double chance = data.containsKey("chance") ? ((Number) data.get("chance")).doubleValue() : 100.0;

            if (type != null && value != null) {
                rewards.add(new DungeonReward(type, value, chance, name, lore));
            }
        }

        // Đọc Stages
        List<List<Map<String, Object>>> stages = new ArrayList<>();
        ConfigurationSection stageSec = config.getConfigurationSection("stages");
        if (stageSec != null) {
            // Sort stages by ID just in case
            List<String> keys = new ArrayList<>(stageSec.getKeys(false));
            keys.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (Exception e) {
                    return a.compareTo(b);
                }
            });

            for (String key : keys) {
                List<Map<?, ?>> actions = stageSec.getMapList(key + ".actions");
                List<Map<String, Object>> stageActions = new ArrayList<>();
                for (Map<?, ?> action : actions) {
                    stageActions.add((Map<String, Object>) action);
                }
                stages.add(stageActions);
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