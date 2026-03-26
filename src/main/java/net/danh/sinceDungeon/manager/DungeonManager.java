package net.danh.sinceDungeon.manager;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.impl.*;
import net.danh.sinceDungeon.api.events.DungeonStartEvent;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.system.MMOItemsHook;
import net.danh.sinceDungeon.system.PAPIHook;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {
    private final SinceDungeon plugin;
    private final Map<UUID, DungeonGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, DungeonTemplate> templates = new HashMap<>();

    private final Map<String, ActionParser> actionParsers = new HashMap<>();
    private final Map<String, ActionMeta> actionMeta = new HashMap<>();

    // API Registries
    private final Map<String, RewardProcessor> rewardProcessors = new HashMap<>();
    private final Map<String, ConditionProcessor> conditionProcessors = new HashMap<>();

    public DungeonManager(SinceDungeon plugin) {
        this.plugin = plugin;
        registerDefaultActions();
        registerDefaultProcessors();
        loadTemplates();
    }

    // ============ [API] REGISTRIES ============
    public void registerTemplate(DungeonTemplate template) {
        if (template != null && template.id() != null) templates.put(template.id(), template);
    }

    public void unregisterTemplate(String id) {
        templates.remove(id);
    }

    public void registerRewardProcessor(String type, RewardProcessor processor) {
        rewardProcessors.put(type.toUpperCase(), processor);
    }

    public RewardProcessor getRewardProcessor(String type) {
        return rewardProcessors.get(type.toUpperCase());
    }

    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        conditionProcessors.put(type.toUpperCase(), processor);
    }
    // ==========================================

    private void registerDefaultProcessors() {
        // 1. PAPI Condition (Mặc định nếu không có tiền tố)
        registerConditionProcessor("PAPI", (player, value) -> PAPIHook.checkCondition(player, value));

        // 2. COMMAND Reward
        registerRewardProcessor("COMMAND", (p, val, displayName) -> {
            String cmd = PAPIHook.setPlaceholders(p, val).replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            String msg = plugin.getMessagesFile().getString("reward.messages.received_custom");
            if (displayName != null && msg != null) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            }
        });

        // 3. ITEM Reward
        registerRewardProcessor("ITEM", (p, val, displayName) -> {
            try {
                String parsedVal = PAPIHook.setPlaceholders(p, val);
                String[] parts = parsedVal.split(":");
                Material mat = Material.valueOf(parts[0].toUpperCase());
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                ItemStack item = new ItemStack(mat, amount);
                handleItemDrop(p, item, displayName == null || displayName.isEmpty() ? mat.name() + " x" + amount : displayName);
            } catch (Exception e) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.error").replace("<item>", val)));
            }
        });

        // 4. MMOITEM Reward
        registerRewardProcessor("MMOITEM", (p, val, displayName) -> {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.not_installed")));
                return;
            }
            try {
                String[] parts = PAPIHook.setPlaceholders(p, val).split(":");
                String mType = parts[0];
                String mId = parts[1];
                int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                ItemStack item = MMOItemsHook.getMMOItem(mType, mId, amount);
                if (item != null) {
                    if (displayName == null) {
                        NBTItem nbtItem = NBTItem.get(item);
                        if (nbtItem.hasTag("MMOITEMS_NAME")) {
                            displayName = ColorUtils.convertLegacyToMiniMessage(nbtItem.getString("MMOITEMS_NAME"));
                        } else {
                            displayName = (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) ? ColorUtils.convertLegacyToMiniMessage(item.getItemMeta().getDisplayName()) : mId;
                        }
                    }
                    handleItemDrop(p, item, displayName);
                } else {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.mmoitems.not_found").replace("<type>", mType).replace("<id>", mId)));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void handleItemDrop(Player p, ItemStack item, String displayName) {
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
        if (!left.isEmpty()) {
            Location dropLoc = p.getLocation();
            DungeonGame game = getGame(p.getUniqueId());
            if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
                dropLoc = game.getOldLocation();
            }
            for (ItemStack drop : left.values()) dropLoc.getWorld().dropItem(dropLoc, drop);
            String fullMsg = plugin.getMessagesFile().getString("reward.messages.inventory_full");
            if (fullMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(fullMsg));
        }
        String msg = plugin.getMessagesFile().getString("reward.messages.received_item");
        if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
    }

    public void registerAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaults, Map<String, List<String>> customPrompts) {
        String key = type.toUpperCase();
        actionParsers.put(key, parser);
        actionMeta.put(key, new ActionMeta(displayName, icon, description, defaults, customPrompts != null ? customPrompts : new HashMap<>()));
    }

    public Set<String> getRegisteredActions() {
        return actionMeta.keySet();
    }

    public ActionMeta getActionMeta(String type) {
        return actionMeta.get(type.toUpperCase());
    }

    public DungeonAction createAction(String type, Map<String, Object> data) {
        if (type == null) return null;
        ActionParser parser = actionParsers.get(type.toUpperCase());

        try {
            DungeonAction action = parser != null ? parser.parse(data) : null;

            if (action != null && data.containsKey("start_message")) {
                Object msgObj = data.get("start_message");
                List<String> msgs = new ArrayList<>();
                if (msgObj instanceof String) msgs.add((String) msgObj);
                else if (msgObj instanceof List) msgs.addAll((List<String>) msgObj);
                action.setStartMessages(msgs);
            }
            return action;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create action " + type + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void registerDefaultActions() {
        Map<String, Object> spawnDefaults = new HashMap<>();
        spawnDefaults.put("mob", plugin.getConfigFile().getString("action-defaults.spawn_wave.mob", "ZOMBIE"));
        spawnDefaults.put("amount", plugin.getConfigFile().getInt("action-defaults.spawn_wave.amount", 1));
        spawnDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));

        registerAction("SPAWN_WAVE", map -> {
                    String mobStr = String.valueOf(map.getOrDefault("mob", spawnDefaults.get("mob")));
                    EntityType mob;
                    try {
                        mob = EntityType.valueOf(mobStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        mob = EntityType.ZOMBIE;
                    }
                    int amount = getInt(map.get("amount"), (int) spawnDefaults.get("amount"));
                    List<Vector> v = parseLocList(map.get("locations"));
                    return new SpawnWaveAction(mob, amount, v);
                }, "Triệu hồi Quái vật (Vanilla)", Material.ZOMBIE_HEAD,
                plugin.getMessagesFile().getString("editor.actions.spawn_wave", "Spawn Vanilla Mobs"),
                spawnDefaults, new HashMap<>());

        Map<String, Object> reachDefaults = new HashMap<>();
        reachDefaults.put("target", "0,0,0");
        reachDefaults.put("radius", plugin.getConfigFile().getDouble("action-defaults.reach_location.radius", 3.0));

        registerAction("REACH_LOCATION", map -> {
                    String targetStr = String.valueOf(map.getOrDefault("target", "0,0,0"));
                    Vector target = DungeonLoader.parseVector(targetStr);
                    double radius = getDouble(map.get("radius"), (double) reachDefaults.get("radius"));
                    return new ReachLocationAction(target, radius);
                }, "Điểm Kiểm Tra (Reach)", Material.COMPASS,
                plugin.getMessagesFile().getString("editor.actions.reach_location", "Reach Location"),
                reachDefaults, new HashMap<>());

        Map<String, Object> chestDefaults = new HashMap<>();
        chestDefaults.put("location", "0,0,0");
        chestDefaults.put("items", new HashMap<>());

        registerAction("LOOT_CHEST", map -> {
                    String locStr = String.valueOf(map.getOrDefault("location", "0,0,0"));
                    Vector loc = DungeonLoader.parseVector(locStr);
                    Map<Integer, String> itemsConfig = new HashMap<>();

                    Object itemsObj = map.get("items");

                    if (itemsObj instanceof ConfigurationSection section) {
                        for (String key : section.getKeys(false)) {
                            try {
                                int slot = Integer.parseInt(key);
                                String val = section.getString(key);
                                if (val != null) itemsConfig.put(slot, val);
                            } catch (Exception ignored) {
                            }
                        }
                    } else if (itemsObj instanceof Map m) {
                        for (Object rawKey : m.keySet()) {
                            try {
                                int slot = Integer.parseInt(rawKey.toString());
                                String val = m.get(rawKey).toString();
                                itemsConfig.put(slot, val);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    return new LootChestAction(loc, itemsConfig);
                }, "Mở Rương Kho Báu", Material.CHEST,
                plugin.getMessagesFile().getString("editor.actions.loot_chest", "Loot Chest"),
                chestDefaults, new HashMap<>());

        Map<String, Object> wallDefaults = new HashMap<>();
        wallDefaults.put("trigger", "0,0,0");
        wallDefaults.put("corner1", "0,0,0");
        wallDefaults.put("corner2", "0,0,0");

        registerAction("BREAK_WALL", map -> new SmartBreakWallAction(
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("trigger", "0,0,0"))),
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner1", "0,0,0"))),
                        DungeonLoader.parseVector(String.valueOf(map.getOrDefault("corner2", "0,0,0")))
                ), "Phá Tường Bằng Block", Material.IRON_PICKAXE,
                plugin.getMessagesFile().getString("editor.actions.break_wall", "Break Wall"),
                wallDefaults, new HashMap<>());

        Map<String, Object> mmDefaults = new HashMap<>();
        mmDefaults.put("mob", plugin.getConfigFile().getString("action-defaults.mythic_wave.mob", "SkeletonKing"));
        mmDefaults.put("amount", plugin.getConfigFile().getInt("action-defaults.mythic_wave.amount", 1));
        mmDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));

        registerAction("MYTHIC_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), (int) mmDefaults.get("amount"));
                    String mob = String.valueOf(map.getOrDefault("mob", mmDefaults.get("mob")));
                    return new MythicMobWaveAction(mob, amount, v);
                }, "Triệu hồi Boss (MythicMobs)", Material.WITHER_SKELETON_SKULL,
                plugin.getMessagesFile().getString("editor.actions.mythic_wave", "MythicMobs Boss"),
                mmDefaults, new HashMap<>());
    }

    private int getInt(Object obj, int def) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private double getDouble(Object obj, double def) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private List<Vector> parseLocList(Object obj) {
        List<Vector> v = new ArrayList<>();
        if (obj instanceof List) {
            for (Object s : (List<?>) obj) v.add(DungeonLoader.parseVector(s.toString()));
        } else if (obj instanceof String) {
            v.add(DungeonLoader.parseVector((String) obj));
        }
        return v;
    }

    public void reload() {
        stopAllGames();
        templates.clear();
        loadTemplates();
        plugin.getLogger().info("Dungeon templates reloaded!");
    }

    private void loadTemplates() {
        File folder = new File(plugin.getDataFolder(), "dungeons");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace(".yml", "");
                try {
                    DungeonTemplate t = DungeonLoader.loadTemplate(plugin, id);
                    if (t != null) templates.put(id, t);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading template " + id + ": " + e.getMessage());
                }
            }
        }
    }

    public void joinDungeon(Player p, String id) {
        if (activeGames.containsKey(p.getUniqueId())) {
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.already_in")));
            return;
        }

        DungeonTemplate tmpl = templates.get(id);
        if (tmpl == null) {
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.file_not_found").replace("<file>", id)));
            return;
        }

        if (!tmpl.isPublic() && !p.hasPermission("SinceDungeon.admin")) {
            String msg = plugin.getMessagesFile().getString("error.dungeon_maintenance");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            return;
        }

        // Tích hợp API Condition check
        for (DungeonTemplate.Condition cond : tmpl.conditions()) {
            String req = cond.requirement();
            String type = "PAPI";
            String value = req;

            // Nếu người dùng cấu hình "CUSTOM_PLUGIN:điều_kiện"
            if (req.contains(":") && !req.contains(";") && conditionProcessors.containsKey(req.split(":")[0].toUpperCase())) {
                String[] parts = req.split(":", 2);
                type = parts[0].toUpperCase();
                value = parts[1];
            }

            ConditionProcessor processor = conditionProcessors.getOrDefault(type, conditionProcessors.get("PAPI"));

            if (processor != null && !processor.check(p, value)) {
                if (cond.failMessage() != null && !cond.failMessage().isEmpty()) {
                    p.sendMessage(ColorUtils.parseWithPrefix("<red>" + cond.failMessage()));
                } else {
                    String msg = plugin.getMessagesFile().getString("error.condition_fail");
                    if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<condition>", req)));
                }
                return;
            }
        }

        DungeonStartEvent startEvent = new DungeonStartEvent(p, tmpl);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) return;

        DungeonGame game = new DungeonGame(plugin, p, tmpl);
        activeGames.put(p.getUniqueId(), game);

        try {
            game.startLobby();
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting dungeon lobby for " + p.getName());
            e.printStackTrace();
            activeGames.remove(p.getUniqueId());
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.init_failed")));
        }
    }

    public void quitDungeon(Player p) {
        if (activeGames.containsKey(p.getUniqueId())) activeGames.get(p.getUniqueId()).stop(true);
    }

    public void dispatchEvent(Player p, Event event) {
        if (p == null) return;
        DungeonGame game = activeGames.get(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            game.onEvent(event);
        }
    }

    public void stopAllGames() {
        for (DungeonGame game : new ArrayList<>(activeGames.values())) game.forceShutdown();
        activeGames.clear();
    }

    public DungeonGame getGame(UUID uuid) {
        return activeGames.get(uuid);
    }

    public Map<UUID, DungeonGame> getActiveGames() {
        return activeGames;
    }

    public Map<String, DungeonTemplate> getTemplates() {
        return templates;
    }

    public void removeGame(UUID uuid) {
        activeGames.remove(uuid);
    }

    public record ActionMeta(String displayName, Material icon, String description, Map<String, Object> defaults,
                             Map<String, List<String>> customPrompts) {
    }
}