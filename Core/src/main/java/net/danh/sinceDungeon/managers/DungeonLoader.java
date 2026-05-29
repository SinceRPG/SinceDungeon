package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.models.DungeonTemplate;
import net.danh.sinceDungeon.utils.FoliaDungeonValidator;
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
 * Translates structural YML files into active Java Records to ensure Thread Safety.
 */
public class DungeonLoader {

    /**
     * Loads a DungeonTemplate from a YAML file asynchronously or synchronously.
     * Safely implements fallbacks to default global settings if missing from the specific file.
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
        FoliaDungeonValidator.warnUnsupportedTemplateLoad(plugin, id, world);

        boolean isPublic = config.getBoolean("public", false);

        boolean keepInv = config.contains("settings.keep-inventory-on-death") ? config.getBoolean("settings.keep-inventory-on-death") : plugin.getConfigFile().getBoolean("dungeon.gameplay.keep-inventory-on-death", true);
        boolean preventDrop = config.contains("settings.prevent-item-dropping") ? config.getBoolean("settings.prevent-item-dropping") : plugin.getConfigFile().getBoolean("dungeon.gameplay.prevent-item-dropping", true);
        boolean blockPearls = config.contains("settings.block-ender-pearls") ? config.getBoolean("settings.block-ender-pearls") : plugin.getConfigFile().getBoolean("dungeon.gameplay.block-ender-pearls", true);
        int kickDelay = config.contains("settings.kick-delay-after-finish") ? config.getInt("settings.kick-delay-after-finish") : plugin.getConfigFile().getInt("dungeon.gameplay.kick-delay-after-finish", 10);
        boolean forceWeather = config.contains("settings.force-daylight-and-clear-weather") ? config.getBoolean("settings.force-daylight-and-clear-weather") : plugin.getConfigFile().getBoolean("dungeon.gameplay.force-daylight-and-clear-weather", true);
        boolean saveStats = config.contains("settings.save-and-restore-stats") ? config.getBoolean("settings.save-and-restore-stats") : plugin.getConfigFile().getBoolean("dungeon.save-and-restore-stats", false);
        String deathAction = config.contains("settings.death-action") ? config.getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
        boolean clearMobDrops = config.contains("settings.clear-mob-drops") ? config.getBoolean("settings.clear-mob-drops") : plugin.getConfigFile().getBoolean("dungeon.clear-mob-drops", true);
        int reqLives = config.getInt("settings.required-lives-to-join", 1);
        int deductLives = config.getInt("settings.lives-deducted-per-death", 1);
        int deductLivesOnLeave = Math.max(0, config.getInt("settings.lives-deducted-on-leave", 0));
        int deductLivesOnFail = Math.max(0, config.getInt("settings.lives-deducted-on-fail", 0));
        int deductLivesOnClear = Math.max(0, config.getInt("settings.lives-deducted-on-clear", 0));
        boolean randomizeStages = config.contains("settings.randomize-stages") ? config.getBoolean("settings.randomize-stages") : plugin.getConfigFile().getBoolean("dungeon.gameplay.randomize-stages", false);
        int maxPlayers = config.getInt("settings.max-players", -1);
        int cooldownSeconds = config.getInt("settings.cooldown-seconds", 0);
        boolean cooldownOnLeave = config.contains("settings.cooldown-on-leave") ? config.getBoolean("settings.cooldown-on-leave") : plugin.getConfigFile().getBoolean("dungeon.gameplay.cooldown-on-leave", false);
        String requiredItem = config.getString("settings.required-item", "NONE");
        boolean consumeRequiredItem = config.getBoolean("settings.consume-required-item", true);
        String startLocation = config.contains("settings.start-location") ? config.getString("settings.start-location", "NONE") : plugin.getConfigFile().getString("dungeon.start-location", "NONE");

        List<String> onStartCmds = config.getStringList("settings.commands.on-start");
        List<String> onFinishCmds = config.getStringList("settings.commands.on-finish");
        List<String> onFirstFinishCmds = config.getStringList("settings.commands.on-first-finish");

        DungeonTemplate.Settings settings = new DungeonTemplate.Settings(
                keepInv, preventDrop, blockPearls, kickDelay, forceWeather,
                saveStats, deathAction, clearMobDrops, reqLives, deductLives,
                deductLivesOnLeave, deductLivesOnFail, deductLivesOnClear,
                randomizeStages, maxPlayers, cooldownSeconds, cooldownOnLeave,
                onStartCmds, onFinishCmds, onFirstFinishCmds, requiredItem,
                consumeRequiredItem, startLocation
        );

        List<DungeonTemplate.Condition> conditions = new ArrayList<>();
        ConfigurationSection condSec = config.getConfigurationSection("conditions");
        if (condSec != null) {
            for (String key : condSec.getKeys(false)) {
                ConfigurationSection c = condSec.getConfigurationSection(key);
                if (c != null) {
                    String check = c.getString("check");
                    if (check != null) {
                        conditions.add(new DungeonTemplate.Condition(key, c.getString("name", key), check, c.getString("msg")));
                    }
                }
            }
        }

        Map<Integer, Integer> soloTiers = loadTiers(config, "rewards.solo-tiers");
        Map<Integer, Integer> partyTiers = loadTiers(config, "rewards.party-tiers");

        // Backward compatibility fallback
        if (soloTiers.isEmpty()) soloTiers = loadTiers(config, "rewards.tiers");
        if (partyTiers.isEmpty()) partyTiers = new HashMap<>(soloTiers);

        List<DungeonReward> rewards = new ArrayList<>();
        ConfigurationSection poolSec = config.getConfigurationSection("rewards.pool");
        if (poolSec != null) {
            for (String key : poolSec.getKeys(false)) {
                ConfigurationSection itemSec = poolSec.getConfigurationSection(key);
                if (itemSec == null) continue;
                String type = itemSec.getString("type");
                String value = itemSec.getString("value");
                if (type == null || value == null) continue;
                rewards.add(new DungeonReward(type, value, itemSec.getDouble("chance", 100.0), itemSec.getString("name"), itemSec.getStringList("lore")));
            }
        }

        Map<Integer, DungeonTemplate.StageData> stages = new HashMap<>();
        ConfigurationSection stageSec = config.getConfigurationSection("stages");

        if (stageSec != null) {
            for (String stageKey : stageSec.getKeys(false)) {
                try {
                    int stageNum = Integer.parseInt(stageKey);
                    double chance = stageSec.getDouble(stageKey + ".chance", 100.0);
                    List<String> stageCommands = stageSec.getStringList(stageKey + ".commands");

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
                    stages.put(stageNum, new DungeonTemplate.StageData(chance, stageCommands, actionList));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid stage key in " + id + ": " + stageKey);
                }
            }
        }

        return new DungeonTemplate(id, world, isPublic, conditions, soloTiers, partyTiers, rewards, stages, settings);
    }

    private static Map<Integer, Integer> loadTiers(YamlConfiguration config, String path) {
        Map<Integer, Integer> tiers = new HashMap<>();
        ConfigurationSection tierSec = config.getConfigurationSection(path);
        if (tierSec != null) {
            for (String key : tierSec.getKeys(false)) {
                try {
                    tiers.put(Integer.parseInt(key), tierSec.getInt(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return tiers;
    }

    /**
     * Parses a string representation of a vector consistently.
     * Prevents system crashes from bad inputs by catching arbitrary formatting.
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
            String msg = SinceDungeon.getPlugin().getLanguageManager().getString("admin.warning.vector_parse_fail", "Vector parsing failed: '<data>'");
            SinceDungeon.getPlugin().getLogger().warning(msg.replace("<data>", s));
            return new Vector(0, 0, 0);
        }
    }
}
