package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.impl.*;
import net.danh.sinceDungeon.system.PAPIHook;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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

    public DungeonManager(SinceDungeon plugin) {
        this.plugin = plugin;
        registerDefaultActions();
        loadTemplates();
    }

    public void registerAction(String type, ActionParser parser, Material icon, String description, Map<String, Object> defaults) {
        String key = type.toUpperCase();
        actionParsers.put(key, parser);
        actionMeta.put(key, new ActionMeta(icon, description, defaults));
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

                if (msgObj instanceof String) {
                    msgs.add((String) msgObj);
                } else if (msgObj instanceof List) {
                    msgs.addAll((List<String>) msgObj);
                }
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
        spawnDefaults.put("mob", "ZOMBIE");
        spawnDefaults.put("amount", 1);
        spawnDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));

        registerAction("SPAWN_WAVE", map -> {
                    String mobStr = (String) map.getOrDefault("mob", "ZOMBIE");
                    EntityType mob;
                    try {
                        mob = EntityType.valueOf(mobStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        mob = EntityType.ZOMBIE;
                    }
                    int amount = getInt(map.get("amount"), 1);
                    List<Vector> v = parseLocList(map.get("locations"));
                    return new SpawnWaveAction(mob, amount, v);
                }, Material.ZOMBIE_HEAD,
                plugin.getMessagesFile().getString("editor.actions.spawn_wave", "Spawn Vanilla Mobs"),
                spawnDefaults);

        Map<String, Object> reachDefaults = new HashMap<>();
        reachDefaults.put("target", "0,0,0");
        reachDefaults.put("radius", 3.0);

        registerAction("REACH_LOCATION", map -> {
                    String targetStr = (String) map.getOrDefault("target", "0,0,0");
                    Vector target = DungeonLoader.parseVector(targetStr);
                    double radius = getDouble(map.get("radius"), 3.0);
                    return new ReachLocationAction(target, radius);
                }, Material.COMPASS,
                plugin.getMessagesFile().getString("editor.actions.reach_location", "Reach Location"),
                reachDefaults);

        Map<String, Object> chestDefaults = new HashMap<>();
        chestDefaults.put("location", "0,0,0");
        chestDefaults.put("items", new HashMap<>());

        registerAction("LOOT_CHEST", map -> {
                    String locStr = (String) map.getOrDefault("location", "0,0,0");
                    Vector loc = DungeonLoader.parseVector(locStr);
                    Map<Integer, String> itemsConfig = new HashMap<>();

                    Object itemsObj = map.get("items");

                    if (itemsObj instanceof ConfigurationSection) {
                        ConfigurationSection section = (ConfigurationSection) itemsObj;
                        for (String key : section.getKeys(false)) {
                            try {
                                int slot = Integer.parseInt(key);
                                String val = section.getString(key);
                                if (val != null) itemsConfig.put(slot, val);
                            } catch (Exception ignored) {
                            }
                        }
                    } else if (itemsObj instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) itemsObj;
                        for (Map.Entry<?, ?> entry : m.entrySet()) {
                            try {
                                int slot = Integer.parseInt(entry.getKey().toString());
                                String val = entry.getValue().toString();
                                itemsConfig.put(slot, val);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    return new LootChestAction(loc, itemsConfig);
                }, Material.CHEST,
                plugin.getMessagesFile().getString("editor.actions.loot_chest", "Loot Chest"),
                chestDefaults);

        Map<String, Object> wallDefaults = new HashMap<>();
        wallDefaults.put("trigger", "0,0,0");
        wallDefaults.put("corner1", "0,0,0");
        wallDefaults.put("corner2", "0,0,0");

        registerAction("BREAK_WALL", map -> new SmartBreakWallAction(
                        DungeonLoader.parseVector((String) map.getOrDefault("trigger", "0,0,0")),
                        DungeonLoader.parseVector((String) map.getOrDefault("corner1", "0,0,0")),
                        DungeonLoader.parseVector((String) map.getOrDefault("corner2", "0,0,0"))
                ), Material.IRON_PICKAXE,
                plugin.getMessagesFile().getString("editor.actions.break_wall", "Break Wall"),
                wallDefaults);

        Map<String, Object> mmDefaults = new HashMap<>();
        mmDefaults.put("mob", "SkeletonKing");
        mmDefaults.put("amount", 1);
        mmDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));

        registerAction("MYTHIC_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), 1);
                    String mob = (String) map.getOrDefault("mob", "SkeletonKing");
                    return new MythicMobWaveAction(mob, amount, v);
                }, Material.WITHER_SKELETON_SKULL,
                plugin.getMessagesFile().getString("editor.actions.mythic_wave", "MythicMobs Boss"),
                mmDefaults);
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
                    if (t != null) {
                        templates.put(id, t);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading template " + id + ": " + e.getMessage());
                }
            }
        }
    }

    public void joinDungeon(Player p, String id) {
        if (activeGames.containsKey(p.getUniqueId())) {
            String msg = plugin.getMessagesFile().getString("error.already_in");
            p.sendMessage(ColorUtils.parseWithPrefix(msg));
            return;
        }

        DungeonTemplate tmpl = templates.get(id);
        if (tmpl == null) {
            String msg = plugin.getMessagesFile().getString("error.file_not_found");
            p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<file>", id)));
            return;
        }

        if (!tmpl.isPublic() && !p.hasPermission("SinceDungeon.admin")) {
            String msg = plugin.getMessagesFile().getString("error.dungeon_maintenance");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            return;
        }

        for (DungeonTemplate.Condition cond : tmpl.conditions()) {
            if (!PAPIHook.checkCondition(p, cond.requirement())) {
                if (cond.failMessage() != null && !cond.failMessage().isEmpty()) {
                    p.sendMessage(ColorUtils.parseWithPrefix("<red>" + cond.failMessage()));
                } else {
                    String msg = plugin.getMessagesFile().getString("error.condition_fail");
                    if (msg != null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<condition>", cond.requirement())));
                    }
                }
                return;
            }
        }

        DungeonGame game = new DungeonGame(plugin, p, tmpl);
        activeGames.put(p.getUniqueId(), game);

        try {
            game.startLobby();
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting dungeon lobby for " + p.getName());
            e.printStackTrace();
            activeGames.remove(p.getUniqueId());
            String msg = plugin.getMessagesFile().getString("error.init_failed");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
        }
    }

    public void quitDungeon(Player p) {
        if (activeGames.containsKey(p.getUniqueId())) {
            activeGames.get(p.getUniqueId()).stop(true);
        }
    }

    public void dispatchEvent(Player p, Event event) {
        if (p == null) return;
        DungeonGame game = activeGames.get(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            game.onEvent(event);
        }
    }

    public void stopAllGames() {
        for (DungeonGame game : new ArrayList<>(activeGames.values())) {
            game.forceShutdown();
        }
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

    public record ActionMeta(Material icon, String description, Map<String, Object> defaults) {
    }
}