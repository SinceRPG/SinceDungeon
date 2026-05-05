package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.impl.*;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.hooks.PAPIHook;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.managers.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Registers default dungeon actions, processors, and configurations during plugin initialization.
 * Translates external configuration rules into functional internal components.
 */
public class DefaultRegistry {

    public static void registerAll(SinceDungeon plugin, DungeonManager manager) {
        registerDefaultItemProviders(plugin, manager);
        registerDefaultProcessors(plugin, manager);
        registerDefaultActions(plugin, manager);
    }

    private static void registerDefaultItemProviders(SinceDungeon plugin, DungeonManager manager) {
        manager.registerItemProvider("KEY", data -> {
            String[] parts = data.split(":");
            if (parts.length < 2) return null;
            String keyId = parts[1];
            int amount = parts.length >= 3 ? ItemBuilder.parseRandomAmount(parts[2]) : 1;

            NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");
            ConfigurationSection cfg = plugin.getConfigFile().getSection("items.key");
            return ItemBuilder.fromConfig(plugin, "items.key", "TRIPWIRE_HOOK")
                    .amount(amount)
                    .applyConfig(cfg, "&6&lDungeon Key", "<id>", keyId)
                    .setTag(keyTag, PersistentDataType.STRING, keyId)
                    .build();
        });

        manager.registerItemProvider("LIFE_ITEM", data -> {
            String[] parts = data.split(":");
            int amount = parts.length >= 2 ? ItemBuilder.parseRandomAmount(parts[1]) : 1;

            NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
            ConfigurationSection cfg = plugin.getConfigFile().getSection("items.life_crystal");
            return ItemBuilder.fromConfig(plugin, "items.life_crystal", "NETHER_STAR")
                    .amount(amount)
                    .applyConfig(cfg, "&d&l✦ Soul Crystal ✦ &8| &a+<amount> Lives", "<amount>", String.valueOf(amount))
                    .setTag(lifeKey, PersistentDataType.INTEGER, amount)
                    .build();
        });

        manager.registerItemProvider("COOLDOWN_RESET", data -> {
            String[] parts = data.split(":");
            int amount = parts.length >= 2 ? ItemBuilder.parseRandomAmount(parts[1]) : 1;

            NamespacedKey resetKey = new NamespacedKey(plugin, "cooldown_reset");
            ConfigurationSection cfg = plugin.getConfigFile().getSection("items.cooldown_reset");
            return ItemBuilder.fromConfig(plugin, "items.cooldown_reset", "PAPER")
                    .amount(amount)
                    .applyConfig(cfg, "&e&lCooldown Reset Ticket")
                    .setTag(resetKey, PersistentDataType.BYTE, (byte) 1)
                    .build();
        });

        manager.registerItemProvider("COOLDOWN_REDUCE", data -> {
            String[] parts = data.split(":");
            int seconds = parts.length >= 2 ? getInt(parts[1], 300) : 300;
            int amount = parts.length >= 3 ? ItemBuilder.parseRandomAmount(parts[2]) : 1;

            NamespacedKey reduceKey = new NamespacedKey(plugin, "cooldown_reduce");
            ConfigurationSection cfg = plugin.getConfigFile().getSection("items.cooldown_reduce");
            return ItemBuilder.fromConfig(plugin, "items.cooldown_reduce", "CLOCK")
                    .amount(amount)
                    .applyConfig(cfg, "&a&lTime Skip Ticket", "<time>", String.valueOf(seconds))
                    .setTag(reduceKey, PersistentDataType.INTEGER, seconds)
                    .build();
        });

        manager.registerItemProvider("MMOITEMS", data -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;
            String[] parts = data.split(":");
            if (parts.length < 3) return null;
            String type = parts[1];
            String id = parts[2];
            int amount = parts.length > 3 ? ItemBuilder.parseRandomAmount(parts[3]) : 1;
            return MMOItemsHook.getMMOItem(type, id, amount);
        });
    }

    private static void registerDefaultProcessors(SinceDungeon plugin, DungeonManager manager) {
        manager.registerConditionProcessor("PAPI", PAPIHook::checkCondition);

        manager.registerRewardProcessor("COMMAND", (p, val, displayName) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String cmd = PAPIHook.setPlaceholders(p, val).replace("%player%", p.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            String msg = plugin.getLanguageManager().getString("reward.messages.received_custom", "&7Reward: &a<item>");
            if (displayName != null && msg != null && !msg.isEmpty()) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            }
        }));

        manager.registerRewardProcessor("ITEM", (p, val, displayName) -> {
            ItemStack item = ItemBuilder.parseDynamicItem(PAPIHook.setPlaceholders(p, val));
            if (item != null) {
                String defaultName = plugin.getLanguageManager().getString("editor.words.reward_default_name", "&7Default");
                if (displayName != null && !displayName.isEmpty() && !displayName.equals(defaultName)) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ColorUtils.parse("<!i>" + displayName));
                        item.setItemMeta(meta);
                    }
                }
                SinceDungeonAPI.get().giveItemSafely(p, item, ColorUtils.formatEnumName(item.getType().name()) + " x" + item.getAmount());
            } else {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.mmoitems.error", "&cSystem Error: Item <item> is misconfigured.").replace("<item>", val)));
            }
        });

        manager.registerRewardProcessor("MMOITEM", (p, val, displayName) -> {
            ItemStack item = ItemBuilder.parseDynamicItem("MMOITEMS:" + PAPIHook.setPlaceholders(p, val));
            if (item != null) {
                SinceDungeonAPI.get().giveItemSafely(p, item, displayName == null ? val : displayName);
            } else {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.mmoitems.not_found", "&cSystem Error: MMOItem not found.").replace("<type>", "MMOITEMS").replace("<id>", val)));
            }
        });

        manager.registerRewardProcessor("LIFE_ITEM", (p, val, displayName) -> {
            ItemStack item = ItemBuilder.parseDynamicItem("LIFE_ITEM:" + val);
            if (item != null) SinceDungeonAPI.get().giveItemSafely(p, item, displayName);
        });

        manager.registerRewardProcessor("COOLDOWN_RESET", (p, val, displayName) -> {
            ItemStack item = ItemBuilder.parseDynamicItem("COOLDOWN_RESET:" + val);
            if (item != null) SinceDungeonAPI.get().giveItemSafely(p, item, displayName);
        });

        manager.registerRewardProcessor("COOLDOWN_REDUCE", (p, val, displayName) -> {
            ItemStack item = ItemBuilder.parseDynamicItem("COOLDOWN_REDUCE:" + val);
            if (item != null) SinceDungeonAPI.get().giveItemSafely(p, item, displayName);
        });
    }

    private static void registerDefaultActions(SinceDungeon plugin, DungeonManager manager) {
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
        spawnDefaults.put("custom_drops", new ArrayList<String>());
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

                    List<String> customDrops = new ArrayList<>();
                    if (map.get("custom_drops") instanceof List<?> l) l.forEach(o -> customDrops.add(o.toString()));

                    return new SpawnWaveAction(mob, amount, v, customName, isBaby, attributesList, equipmentList, scaleWithParty, customDrops);
                }, plugin.getLanguageManager().getString("editor.actions_name.spawn_wave", "Spawn Vanilla Mob"), Material.ZOMBIE_HEAD,
                plugin.getLanguageManager().getString("editor.actions.spawn_wave", "Spawn Vanilla Mobs"),
                spawnDefaults, new HashMap<>());

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
                }, plugin.getLanguageManager().getString("editor.actions_name.reach_location", "Reach Checkpoint"), Material.COMPASS,
                plugin.getLanguageManager().getString("editor.actions.reach_location", "Reach Location"),
                reachDefaults, new HashMap<>());

        Map<String, Object> chestDefaults = new HashMap<>();
        chestDefaults.put("location", "0,0,0");
        chestDefaults.put("per_player", false);
        chestDefaults.put("required_key", "NONE"); // Defined required key default
        chestDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.loot_chest.time_limit", -1));
        chestDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.loot_chest.time_penalty", 1));
        chestDefaults.put("items", new HashMap<String, String>());
        chestDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.loot_chest.start_message"));

        manager.registerAction("LOOT_CHEST", map -> {
                    Vector loc = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("location", "0,0,0")));
                    boolean perPlayer = map.containsKey("per_player") ? Boolean.parseBoolean(map.get("per_player").toString()) : false;
                    String requiredKey = String.valueOf(map.getOrDefault("required_key", "NONE"));

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
                    // FIXED: LootChestAction constructor now properly receives 4 arguments (including requiredKey)
                    return new LootChestAction(loc, itemsConfig, perPlayer, requiredKey);
                }, plugin.getLanguageManager().getString("editor.actions_name.loot_chest", "Loot Treasure Chest"), Material.CHEST,
                plugin.getLanguageManager().getString("editor.actions.loot_chest", "Loot Chest"),
                chestDefaults, new HashMap<>());

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
                ), plugin.getLanguageManager().getString("editor.actions_name.break_wall", "Break Wall via Block"), Material.IRON_PICKAXE,
                plugin.getLanguageManager().getString("editor.actions.break_wall", "Break Wall"),
                wallDefaults, new HashMap<>());

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
                }, plugin.getLanguageManager().getString("editor.actions_name.mythic_wave", "Spawn Mythic Boss"), Material.WITHER_SKELETON_SKULL,
                plugin.getLanguageManager().getString("editor.actions.mythic_wave", "MythicMobs Boss"),
                mmDefaults, new HashMap<>());

        List<String> defaultRandomMobs = new ArrayList<>(Arrays.asList("VANILLA:ZOMBIE:50", "VANILLA:SKELETON:30", "MYTHIC:SkeletonKing:20:1"));
        Map<String, Object> randomDefaults = new HashMap<>();
        randomDefaults.put("amount", 5);
        randomDefaults.put("scale_with_party", plugin.getConfigFile().getBoolean("action-defaults.random_wave.scale_with_party", false));
        randomDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.random_wave.time_limit", -1));
        randomDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.random_wave.time_penalty", 1));
        randomDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));
        randomDefaults.put("random_mobs", plugin.getConfigFile().getStringList("action-defaults.random_wave.random_mobs").isEmpty() ? defaultRandomMobs : plugin.getConfigFile().getStringList("action-defaults.random_wave.random_mobs"));
        randomDefaults.put("custom_drops", new ArrayList<String>());
        randomDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.random_wave.start_message"));

        manager.registerAction("RANDOM_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), (int) randomDefaults.get("amount"));
                    boolean scaleWithParty = map.containsKey("scale_with_party") ? Boolean.parseBoolean(map.get("scale_with_party").toString()) : (boolean) randomDefaults.get("scale_with_party");

                    List<String> rawStrings = new ArrayList<>();
                    Object rawList = map.get("random_mobs");
                    if (rawList instanceof List<?> l) l.forEach(o -> rawStrings.add(o.toString()));
                    else if (rawList instanceof String s) rawStrings.add(s);

                    List<String> customDrops = new ArrayList<>();
                    if (map.get("custom_drops") instanceof List<?> l) l.forEach(o -> customDrops.add(o.toString()));

                    return new RandomWaveAction(amount, v, RandomWaveAction.parseMobPool(rawStrings), scaleWithParty, customDrops);
                }, plugin.getLanguageManager().getString("editor.actions_name.random_wave", "Random Mob Wave"), Material.TRIAL_SPAWNER,
                plugin.getLanguageManager().getString("editor.actions.random_wave", "Spawn a random mix of Vanilla and Mythic mobs from a pool"),
                randomDefaults, new HashMap<>());

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
        zoneDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.control_zone.start_message"));

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
                }, plugin.getLanguageManager().getString("editor.actions_name.control_zone", "Control The Zone"), Material.BEACON,
                plugin.getLanguageManager().getString("editor.actions.control_zone", "Hold the area for X seconds. Circle can shrink over time."),
                zoneDefaults, new HashMap<>());

        Map<String, Object> doorDefaults = new HashMap<>();
        doorDefaults.put("trigger", "0,0,0");
        doorDefaults.put("corner1", "0,0,0");
        doorDefaults.put("corner2", "0,0,0");
        doorDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.unlock_door.time_limit", -1));
        doorDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.unlock_door.time_penalty", 1));
        doorDefaults.put("key_id", "door_1");
        doorDefaults.put("particle", "ENCHANT");
        doorDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.unlock_door.start_message"));

        manager.registerAction("UNLOCK_DOOR", map -> {
                    Vector trigger = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("trigger", "0,0,0")));
                    Vector c1 = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner1", "0,0,0")));
                    Vector c2 = DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner2", "0,0,0")));
                    String keyId = String.valueOf(map.getOrDefault("key_id", "door_1"));
                    String particle = String.valueOf(map.getOrDefault("particle", "ENCHANT"));

                    return new UnlockDoorAction(trigger, c1, c2, keyId, particle);
                }, plugin.getLanguageManager().getString("editor.actions_name.unlock_door", "Find Key & Unlock Door"), Material.IRON_DOOR,
                plugin.getLanguageManager().getString("editor.actions.unlock_door", "Requires player to find a key item to open the door."),
                doorDefaults, new HashMap<>());

        Map<String, Object> bossDefaults = new HashMap<>();
        bossDefaults.put("location", "0,0,0");
        bossDefaults.put("mob", "ZOMBIE");
        bossDefaults.put("custom_name", "&4&lThe Boss");
        bossDefaults.put("base_health", 500.0);
        bossDefaults.put("scale_health_per_player", 150.0);
        bossDefaults.put("bar_color", "RED");
        bossDefaults.put("bar_style", "SOLID");
        bossDefaults.put("attributes", new ArrayList<>(Collections.singletonList("movement_speed:0.3")));
        bossDefaults.put("equipment", new ArrayList<String>());
        bossDefaults.put("enrage_time", -1);
        bossDefaults.put("enrage_message", "&c&lThe Boss has become ENRAGED!");
        bossDefaults.put("enrage_attributes", new ArrayList<>(Arrays.asList("attack_damage:20.0", "movement_speed:0.5")));
        bossDefaults.put("custom_drops", new ArrayList<String>());
        bossDefaults.put("phases", new HashMap<String, Object>());
        bossDefaults.put("time_limit", plugin.getConfigFile().getInt("action-defaults.boss_battle.time_limit", -1));
        bossDefaults.put("time_penalty", plugin.getConfigFile().getInt("action-defaults.boss_battle.time_penalty", 1));
        bossDefaults.put("start_message", plugin.getConfigFile().getStringList("action-defaults.boss_battle.start_message"));

        manager.registerAction("BOSS_BATTLE", map -> {
                    Vector loc = parseLocList(map.get("location")).get(0);
                    EntityType mob;
                    try {
                        mob = EntityType.valueOf(String.valueOf(map.getOrDefault("mob", "ZOMBIE")).toUpperCase());
                    } catch (Exception e) {
                        mob = EntityType.ZOMBIE;
                    }
                    String name = String.valueOf(map.getOrDefault("custom_name", "&4&lThe Boss"));
                    double baseHealth = getDouble(map.get("base_health"), 500.0);
                    double scale = getDouble(map.get("scale_health_per_player"), 150.0);
                    String color = String.valueOf(map.getOrDefault("bar_color", "RED"));
                    String style = String.valueOf(map.getOrDefault("bar_style", "SOLID"));

                    List<String> attrs = new ArrayList<>();
                    if (map.get("attributes") instanceof List<?> l) l.forEach(o -> attrs.add(o.toString()));

                    List<String> equipment = new ArrayList<>();
                    if (map.get("equipment") instanceof List<?> l) l.forEach(o -> equipment.add(o.toString()));

                    int enrageTime = getInt(map.get("enrage_time"), -1);
                    String enrageMessage = String.valueOf(map.getOrDefault("enrage_message", "&c&lThe Boss has become ENRAGED!"));
                    List<String> enrageAttrs = new ArrayList<>();
                    if (map.get("enrage_attributes") instanceof List<?> l) l.forEach(o -> enrageAttrs.add(o.toString()));

                    List<String> customDrops = new ArrayList<>();
                    if (map.get("custom_drops") instanceof List<?> l) l.forEach(o -> customDrops.add(o.toString()));

                    Map<Integer, BossBattleAction.PhaseData> phases = new HashMap<>();
                    Object phasesObj = map.get("phases");
                    if (phasesObj instanceof ConfigurationSection sec) {
                        for (String key : sec.getKeys(false)) {
                            try {
                                int threshold = Integer.parseInt(key);
                                BossBattleAction.PhaseData pd = new BossBattleAction.PhaseData();
                                pd.message = sec.getString(key + ".message", "");
                                pd.attributes = sec.getStringList(key + ".attributes");

                                String rMobStr = sec.getString(key + ".reinforcements.mob");
                                if (rMobStr != null) {
                                    try {
                                        pd.reinforcementMob = EntityType.valueOf(rMobStr.toUpperCase());
                                    } catch (Exception ignored) {
                                    }
                                    pd.reinforcementAmount = sec.getInt(key + ".reinforcements.amount", 1);
                                    pd.reinforcementName = sec.getString(key + ".reinforcements.custom_name", "");
                                    pd.reinforcementAttributes = sec.getStringList(key + ".reinforcements.attributes");
                                    pd.reinforcementEquipment = sec.getStringList(key + ".reinforcements.equipment");
                                }
                                phases.put(threshold, pd);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    return new BossBattleAction(loc, mob, name, baseHealth, scale, color, style, attrs, equipment, phases, enrageTime, enrageMessage, enrageAttrs, customDrops);
                }, plugin.getLanguageManager().getString("editor.actions_name.boss_battle", "Vanilla Boss Battle"), Material.WITHER_SKELETON_SKULL,
                plugin.getLanguageManager().getString("editor.actions.boss_battle", "Spawn a Vanilla boss with Healthbar, scaling, and phases."),
                bossDefaults, new HashMap<>());
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