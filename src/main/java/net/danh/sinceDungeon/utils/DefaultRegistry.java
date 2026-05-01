package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.impl.*;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.hooks.PAPIHook;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.managers.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class DefaultRegistry {

    public static void registerAll(SinceDungeon plugin, DungeonManager manager) {
        registerDefaultProcessors(plugin, manager);
        registerDefaultActions(plugin, manager);
    }

    private static void registerDefaultProcessors(SinceDungeon plugin, DungeonManager manager) {
        manager.registerConditionProcessor("PAPI", PAPIHook::checkCondition);

        manager.registerRewardProcessor("COMMAND", (p, val, displayName) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String cmd = PAPIHook.setPlaceholders(p, val).replace("%player%", p.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            String msg = plugin.getMessagesFile().getString("reward.messages.received_custom");
            if (displayName != null && msg != null && !msg.isEmpty()) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            }
        }));

        manager.registerRewardProcessor("ITEM", (p, val, displayName) -> {
            try {
                String parsedVal = PAPIHook.setPlaceholders(p, val);
                String[] parts = parsedVal.split(":");
                Material mat = Material.valueOf(parts[0].toUpperCase());

                // NEW: Parse random amount if a range is provided
                int amount = parts.length > 1 ? parseRandomAmount(parts[1]) : 1;
                ItemStack item = new ItemStack(mat, amount);

                if (displayName != null && !displayName.isEmpty()) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ColorUtils.parse("<!i>" + displayName));
                        item.setItemMeta(meta);
                    }
                }

                HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                if (!left.isEmpty()) {
                    for (ItemStack drop : left.values()) {
                        p.getWorld().dropItem(p.getLocation(), drop);
                    }
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("reward.messages.inventory_full")));
                }
                String msg = plugin.getMessagesFile().getString("reward.messages.received_item");
                if (msg != null)
                    p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName == null || displayName.isEmpty() ? mat.name() + " x" + amount : displayName)));
            } catch (Exception e) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.error").replace("<item>", val)));
            }
        });

        manager.registerRewardProcessor("MMOITEM", (p, val, displayName) -> {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.not_installed")));
                return;
            }
            try {
                String[] parts = PAPIHook.setPlaceholders(p, val).split(":");
                String mType = parts[0];
                String mId = parts[1];

                // NEW: Parse random amount if a range is provided
                int amount = parts.length > 2 ? parseRandomAmount(parts[2]) : 1;

                ItemStack item = MMOItemsHook.getMMOItem(mType, mId, amount);
                if (item != null) {
                    if (displayName == null) displayName = mId;
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                    if (!left.isEmpty()) {
                        for (ItemStack drop : left.values()) p.getWorld().dropItem(p.getLocation(), drop);
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("reward.messages.inventory_full")));
                    }
                    String msg = plugin.getMessagesFile().getString("reward.messages.received_item");
                    if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
                } else {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.not_found").replace("<type>", mType).replace("<id>", mId)));
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Helper method to parse amounts that might be a range (e.g., "1-5").
     */
    private static int parseRandomAmount(String amtStr) {
        try {
            if (amtStr.contains("-")) {
                String[] range = amtStr.split("-");
                int min = Integer.parseInt(range[0]);
                int max = Integer.parseInt(range[1]);
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                return min + new java.util.Random().nextInt(max - min + 1);
            } else {
                return Integer.parseInt(amtStr);
            }
        } catch (Exception e) {
            return 1; // Fallback to 1 if the format is entirely broken
        }
    }

    private static void registerDefaultActions(SinceDungeon plugin, DungeonManager manager) {
        // --- SPAWN_WAVE ---
        Map<String, Object> spawnDefaults = new LinkedHashMap<>();
        spawnDefaults.put("mob", plugin.getConfigFile().getString("action-defaults.spawn_wave.mob", "ZOMBIE"));
        spawnDefaults.put("amount", plugin.getConfigFile().getInt("action-defaults.spawn_wave.amount", 1));
        spawnDefaults.put("scale_with_party", plugin.getConfigFile().getBoolean("action-defaults.spawn_wave.scale_with_party", false));
        spawnDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.spawn_wave.time_limit", -1));
        spawnDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.spawn_wave.time_penalty", 1));
        spawnDefaults.put("custom_name", plugin.getConfigFile().getString("action-defaults.spawn_wave.custom_name", ""));
        spawnDefaults.put("is_baby", plugin.getConfigFile().getBoolean("action-defaults.spawn_wave.is_baby", false));
        spawnDefaults.put("attributes", plugin.getConfigFile().getStringList("action-defaults.spawn_wave.attributes"));
        spawnDefaults.put("equipment", plugin.getConfigFile().getStringList("action-defaults.spawn_wave.equipment"));
        spawnDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));
        spawnDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.spawn_wave.start_message"));

        manager.registerAction("SPAWN_WAVE", map -> {
                    String mobStr = String.valueOf(map.getOrDefault("mob", spawnDefaults.get("mob")));
                    EntityType mob;
                    try {
                        mob = EntityType.valueOf(mobStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        mob = EntityType.ZOMBIE;
                    }
                    int amount = getInt(map.get("amount"), (int) spawnDefaults.get("amount"));
                    List<Vector> v = parseLocList(map.get("locations"));
                    boolean scaleWithParty = map.containsKey("scale_with_party") ? Boolean.parseBoolean(map.get("scale_with_party").toString()) : (boolean) spawnDefaults.get("scale_with_party");

                    String customName = String.valueOf(map.getOrDefault("custom_name", ""));
                    boolean isBaby = false;
                    if (map.containsKey("is_baby")) isBaby = Boolean.parseBoolean(map.get("is_baby").toString());

                    List<String> attributesList = new ArrayList<>();
                    Object attrObj = map.get("attributes");
                    if (attrObj instanceof List<?> l) l.forEach(o -> attributesList.add(o.toString()));

                    List<String> equipmentList = new ArrayList<>();
                    Object equipObj = map.get("equipment");
                    if (equipObj instanceof List<?> l) l.forEach(o -> equipmentList.add(o.toString()));

                    return new SpawnWaveAction(mob, amount, v, customName, isBaby, attributesList, equipmentList, scaleWithParty);
                }, plugin.getMessagesFile().getString("editor.actions_name.spawn_wave", "Spawn Vanilla Mob"), Material.ZOMBIE_HEAD,
                plugin.getMessagesFile().getString("editor.actions.spawn_wave", "Spawn Vanilla Mobs"),
                spawnDefaults, new HashMap<>());

        // --- REACH_LOCATION ---
        Map<String, Object> reachDefaults = new HashMap<>();
        reachDefaults.put("target", "0,0,0");
        reachDefaults.put("radius", plugin.getConfigFile().getDouble("action-defaults.reach_location.radius", 3.0));
        reachDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.reach_location.time_limit", -1));
        reachDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.reach_location.time_penalty", 1));
        reachDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.reach_location.start_message"));

        manager.registerAction("REACH_LOCATION", map -> {
                    Vector target = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("target", "0,0,0")));
                    double radius = getDouble(map.get("radius"), (double) reachDefaults.get("radius"));
                    return new ReachLocationAction(target, radius);
                }, plugin.getMessagesFile().getString("editor.actions_name.reach_location", "Reach Checkpoint"), Material.COMPASS,
                plugin.getMessagesFile().getString("editor.actions.reach_location", "Reach Location"),
                reachDefaults, new HashMap<>());

        // --- LOOT_CHEST ---
        Map<String, Object> chestDefaults = new HashMap<>();
        chestDefaults.put("location", "0,0,0");
        chestDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.loot_chest.time_limit", -1));
        chestDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.loot_chest.time_penalty", 1));
        chestDefaults.put("items", new HashMap<String, String>());
        chestDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.loot_chest.start_message"));

        manager.registerAction("LOOT_CHEST", map -> {
                    Vector loc = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("location", "0,0,0")));
                    Map<Integer, String> itemsConfig = new HashMap<>();
                    Object itemsObj = map.get("items");

                    if (itemsObj instanceof ConfigurationSection section) {
                        for (String key : section.getKeys(false)) {
                            try {
                                itemsConfig.put(Integer.parseInt(key), section.getString(key));
                            } catch (Exception ignored) {
                            }
                        }
                    } else if (itemsObj instanceof Map m) {
                        for (Object rawKey : m.keySet()) {
                            try {
                                itemsConfig.put(Integer.parseInt(rawKey.toString()), m.get(rawKey).toString());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    return new LootChestAction(loc, itemsConfig);
                }, plugin.getMessagesFile().getString("editor.actions_name.loot_chest", "Loot Treasure Chest"), Material.CHEST,
                plugin.getMessagesFile().getString("editor.actions.loot_chest", "Loot Chest"),
                chestDefaults, new HashMap<>());

        // --- BREAK_WALL ---
        Map<String, Object> wallDefaults = new HashMap<>();
        wallDefaults.put("trigger", "0,0,0");
        wallDefaults.put("corner1", "0,0,0");
        wallDefaults.put("corner2", "0,0,0");
        wallDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.break_wall.time_limit", -1));
        wallDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.break_wall.time_penalty", 1));
        wallDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.break_wall.start_message"));

        manager.registerAction("BREAK_WALL", map -> new SmartBreakWallAction(
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("trigger", "0,0,0"))),
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner1", "0,0,0"))),
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner2", "0,0,0")))
                ), plugin.getMessagesFile().getString("editor.actions_name.break_wall", "Break Wall via Block"), Material.IRON_PICKAXE,
                plugin.getMessagesFile().getString("editor.actions.break_wall", "Break Wall"),
                wallDefaults, new HashMap<>());

        // --- MYTHIC_WAVE ---
        Map<String, Object> mmDefaults = new HashMap<>();
        mmDefaults.put("mob", plugin.getConfigFile().getString("action-defaults.mythic_wave.mob", "SkeletonKing"));
        mmDefaults.put("amount", plugin.getConfigFile().getInt("action-defaults.mythic_wave.amount", 1));
        mmDefaults.put("scale_with_party", plugin.getConfigFile().getBoolean("action-defaults.mythic_wave.scale_with_party", false));
        mmDefaults.put("level", plugin.getConfigFile().getInt("action-defaults.mythic_wave.level", 1));
        mmDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.mythic_wave.time_limit", -1));
        mmDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.mythic_wave.time_penalty", 1));
        mmDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));
        mmDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.mythic_wave.start_message"));

        manager.registerAction("MYTHIC_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), (int) mmDefaults.get("amount"));
                    int level = getInt(map.get("level"), (int) mmDefaults.get("level"));
                    String mob = String.valueOf(map.getOrDefault("mob", mmDefaults.get("mob")));
                    boolean scaleWithParty = map.containsKey("scale_with_party") ? Boolean.parseBoolean(map.get("scale_with_party").toString()) : (boolean) mmDefaults.get("scale_with_party");

                    return new MythicMobWaveAction(mob, amount, level, v, scaleWithParty);
                }, plugin.getMessagesFile().getString("editor.actions_name.mythic_wave", "Spawn Mythic Boss"), Material.WITHER_SKELETON_SKULL,
                plugin.getMessagesFile().getString("editor.actions.mythic_wave", "MythicMobs Boss"),
                mmDefaults, new HashMap<>());

        // --- RANDOM_WAVE ---
        List<String> defaultRandomMobs = new ArrayList<>(Arrays.asList("VANILLA:ZOMBIE:50", "VANILLA:SKELETON:30", "MYTHIC:SkeletonKing:20:1"));
        Map<String, Object> randomDefaults = new HashMap<>();
        randomDefaults.put("amount", 5);
        randomDefaults.put("scale_with_party", plugin.getConfigFile().getBoolean("action-defaults.random_wave.scale_with_party", false));
        randomDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.random_wave.time_limit", -1));
        randomDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.random_wave.time_penalty", 1));
        randomDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));
        randomDefaults.put("random_mobs", plugin.getConfigFile().getStringList("action-defaults.random_wave.random_mobs").isEmpty() ? defaultRandomMobs : plugin.getConfigFile().getStringList("action-defaults.random_wave.random_mobs"));
        randomDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.random_wave.start_message"));

        manager.registerAction("RANDOM_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), (int) randomDefaults.get("amount"));
                    boolean scaleWithParty = map.containsKey("scale_with_party") ? Boolean.parseBoolean(map.get("scale_with_party").toString()) : (boolean) randomDefaults.get("scale_with_party");

                    List<String> rawStrings = new ArrayList<>();
                    Object rawList = map.get("random_mobs");
                    if (rawList instanceof List<?> l) l.forEach(o -> rawStrings.add(o.toString()));
                    else if (rawList instanceof String s) rawStrings.add(s);

                    return new RandomWaveAction(amount, v, RandomWaveAction.parseMobPool(rawStrings), scaleWithParty);
                }, plugin.getMessagesFile().getString("editor.actions_name.random_wave", "Random Mob Wave"), Material.TRIAL_SPAWNER,
                plugin.getMessagesFile().getString("editor.actions.random_wave", "Spawn a random mix of Vanilla and Mythic mobs from a pool"),
                randomDefaults, new HashMap<>());

        // --- CONTROL_ZONE ---
        Map<String, Object> zoneDefaults = new HashMap<>();
        zoneDefaults.put("center", "0,0,0");
        zoneDefaults.put("start_radius", 10.0);
        zoneDefaults.put("end_radius", 3.0);
        zoneDefaults.put("required_time", 20);
        zoneDefaults.put("mob", "NONE");
        zoneDefaults.put("mob_interval", 60);
        zoneDefaults.put("mob_level", 1);
        zoneDefaults.put("custom_name", "");
        zoneDefaults.put("is_baby", false);
        zoneDefaults.put("attributes", new ArrayList<String>());
        zoneDefaults.put("equipment", new ArrayList<String>());
        zoneDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.control_zone.time_limit", -1));
        zoneDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.control_zone.time_penalty", 1));
        zoneDefaults.put("start_message", Collections.singletonList("&eCapture and hold the zone!"));

        manager.registerAction("CONTROL_ZONE", map -> {
                    Vector center = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("center", "0,0,0")));
                    double startRadius = getDouble(map.get("start_radius"), (double) zoneDefaults.get("start_radius"));
                    double endRadius = getDouble(map.get("end_radius"), (double) zoneDefaults.get("end_radius"));
                    int requiredTime = getInt(map.get("required_time"), (int) zoneDefaults.get("required_time"));

                    String mob = String.valueOf(map.getOrDefault("mob", map.getOrDefault("mob_type", "NONE")));
                    int mobInterval = getInt(map.get("mob_interval"), (int) zoneDefaults.get("mob_interval"));
                    int mobLevel = getInt(map.get("mob_level"), 1);

                    String customName = String.valueOf(map.getOrDefault("custom_name", ""));
                    boolean isBaby = map.containsKey("is_baby") && Boolean.parseBoolean(map.get("is_baby").toString());

                    List<String> attributesList = new ArrayList<>();
                    Object attrObj = map.get("attributes");
                    if (attrObj instanceof List<?> l) l.forEach(o -> attributesList.add(o.toString()));

                    List<String> equipmentList = new ArrayList<>();
                    Object equipObj = map.get("equipment");
                    if (equipObj instanceof List<?> l) l.forEach(o -> equipmentList.add(o.toString()));

                    return new ControlZoneAction(center, startRadius, endRadius, requiredTime, mob, mobInterval, mobLevel, customName, isBaby, attributesList, equipmentList);
                }, plugin.getMessagesFile().getString("editor.actions_name.control_zone", "Control The Zone"), Material.BEACON,
                plugin.getMessagesFile().getString("editor.actions.control_zone", "Hold the area for X seconds. Circle can shrink over time."),
                zoneDefaults, new HashMap<>());

        // --- UNLOCK_DOOR ---
        Map<String, Object> doorDefaults = new HashMap<>();
        doorDefaults.put("trigger", "0,0,0");
        doorDefaults.put("corner1", "0,0,0");
        doorDefaults.put("corner2", "0,0,0");
        doorDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.unlock_door.time_limit", -1));
        doorDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.unlock_door.time_penalty", 1));
        doorDefaults.put("key_id", "door_1");
        doorDefaults.put("particle", "ENCHANT");
        doorDefaults.put("start_message", Collections.singletonList("&eFind the key and unlock the door!"));

        manager.registerAction("UNLOCK_DOOR", map -> {
                    Vector trigger = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("trigger", "0,0,0")));
                    Vector c1 = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner1", "0,0,0")));
                    Vector c2 = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner2", "0,0,0")));
                    String keyId = String.valueOf(map.getOrDefault("key_id", "door_1"));
                    String particle = String.valueOf(map.getOrDefault("particle", "ENCHANT"));

                    return new UnlockDoorAction(trigger, c1, c2, keyId, particle);
                }, plugin.getMessagesFile().getString("editor.actions_name.unlock_door", "Find Key & Unlock Door"), Material.IRON_DOOR,
                plugin.getMessagesFile().getString("editor.actions.unlock_door", "Requires player to find a key item to open the door."),
                doorDefaults, new HashMap<>());
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDouble(Object obj, double def) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static List<Vector> parseLocList(Object obj) {
        List<Vector> v = new ArrayList<>();
        if (obj instanceof List) {
            for (Object s : (List<?>) obj) v.add(DungeonLoader.parseVector(s.toString()));
        } else if (obj instanceof String) {
            v.add(DungeonLoader.parseVector((String) obj));
        }
        return v;
    }
}