package net.danh.sinceDungeon.manager;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.impl.*;
import net.danh.sinceDungeon.api.events.DungeonStartEvent;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.party.PartyManager;
import net.danh.sinceDungeon.system.MMOItemsHook;
import net.danh.sinceDungeon.system.PAPIHook;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {
    private final SinceDungeon plugin;
    private final Map<UUID, DungeonGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, DungeonTemplate> templates = new ConcurrentHashMap<>();

    private final Map<String, ActionParser> actionParsers = new ConcurrentHashMap<>();
    private final Map<String, ActionMeta> actionMeta = new ConcurrentHashMap<>();

    private final Map<String, RewardProcessor> rewardProcessors = new ConcurrentHashMap<>();
    private final Map<String, ConditionProcessor> conditionProcessors = new ConcurrentHashMap<>();

    public DungeonManager(SinceDungeon plugin) {
        this.plugin = plugin;
        registerDefaultActions();
        registerDefaultProcessors();
        loadTemplatesAsync().join();
    }

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

    private void registerDefaultProcessors() {
        registerConditionProcessor("PAPI", PAPIHook::checkCondition);

        registerRewardProcessor("COMMAND", (p, val, displayName) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String cmd = PAPIHook.setPlaceholders(p, val).replace("%player%", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                String msg = plugin.getMessagesFile().getString("reward.messages.received_custom");
                if (displayName != null && msg != null) {
                    p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
                }
            });
        });

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
            // VÁ LỖI LOGIC: Bắt buộc rơi đồ ở vị trí hiện tại của NGƯỜI CHƠI BỊ ĐẦY TÚI.
            // Không được phép ném đồ về vị trí của Party Leader.
            org.bukkit.Location dropLoc = p.getLocation();

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
                }, plugin.getMessagesFile().getString("editor.actions_name.spawn_wave", "Spawn Vanilla Mob"), Material.ZOMBIE_HEAD,
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
                }, plugin.getMessagesFile().getString("editor.actions_name.reach_location", "Reach Checkpoint"), Material.COMPASS,
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
                }, plugin.getMessagesFile().getString("editor.actions_name.loot_chest", "Loot Treasure Chest"), Material.CHEST,
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
                ), plugin.getMessagesFile().getString("editor.actions_name.break_wall", "Break Wall via Block"), Material.IRON_PICKAXE,
                plugin.getMessagesFile().getString("editor.actions.break_wall", "Break Wall"),
                wallDefaults, new HashMap<>());

        Map<String, Object> mmDefaults = new HashMap<>();
        mmDefaults.put("mob", plugin.getConfigFile().getString("action-defaults.mythic_wave.mob", "SkeletonKing"));
        mmDefaults.put("amount", plugin.getConfigFile().getInt("action-defaults.mythic_wave.amount", 1));
        mmDefaults.put("level", plugin.getConfigFile().getInt("action-defaults.mythic_wave.level", 1));
        mmDefaults.put("locations", new ArrayList<>(Collections.singletonList("0,0,0")));

        registerAction("MYTHIC_WAVE", map -> {
                    List<Vector> v = parseLocList(map.get("locations"));
                    int amount = getInt(map.get("amount"), (int) mmDefaults.get("amount"));
                    int level = getInt(map.get("level"), (int) mmDefaults.get("level"));
                    String mob = String.valueOf(map.getOrDefault("mob", mmDefaults.get("mob")));
                    return new MythicMobWaveAction(mob, amount, level, v);
                }, plugin.getMessagesFile().getString("editor.actions_name.mythic_wave", "Spawn Mythic Boss"), Material.WITHER_SKELETON_SKULL,
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

    public CompletableFuture<Void> reload() {
        stopAllGames();
        templates.clear();
        return loadTemplatesAsync().thenRun(() -> {
            plugin.getLogger().info("Dungeon templates reloaded asynchronously with Multi-Threading!");
        });
    }

    private CompletableFuture<Void> loadTemplatesAsync() {
        File folder = new File(plugin.getDataFolder(), "dungeons");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (File f : files) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String id = f.getName().replace(".yml", "");
                try {
                    DungeonTemplate t = DungeonLoader.loadTemplate(plugin, id);
                    if (t != null) templates.put(id, t);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading template " + id + ": " + e.getMessage());
                }
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void joinDungeon(Player p, String id) {
        PartyManager.Party party = plugin.getPartyManager().getParty(p.getUniqueId());
        Set<Player> participants = new HashSet<>();

        int offlineCount = 0;
        int deadCount = 0;
        int farCount = 0;

        if (party != null) {
            if (!party.getLeader().equals(p.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                return;
            }

            double maxDist = plugin.getConfigFile().getDouble("party.max-join-distance", 50.0);

            for (UUID uid : party.getMembers()) {
                Player mem = Bukkit.getPlayer(uid);
                if (mem == null || !mem.isOnline()) {
                    offlineCount++;
                } else if (mem.isDead()) {
                    // VÁ LỖI LOGIC: Bắt chính xác trạng thái người đang "Ngỏm" (Dead) để báo tin chuẩn xác
                    deadCount++;
                } else {
                    if (maxDist > 0 && (!mem.getWorld().equals(p.getWorld()) || mem.getLocation().distanceSquared(p.getLocation()) > maxDist * maxDist)) {
                        farCount++;
                    } else {
                        participants.add(mem);
                    }
                }
            }

            if (offlineCount > 0) {
                String warnMsg = plugin.getMessagesFile().getString("party.offline_left_behind", "<yellow>Warning: <count> member(s) are Offline and were left behind!");
                p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(offlineCount))));
            }
            if (deadCount > 0) {
                String warnMsg = plugin.getMessagesFile().getString("party.dead_left_behind", "<yellow>Warning: <count> member(s) are Dead and were left behind!");
                p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(deadCount))));
            }
            if (farCount > 0) {
                String warnMsg = plugin.getMessagesFile().getString("party.distance_warning", "<yellow>Warning: <count> member(s) are too far away and were left behind!");
                p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(farCount))));
            }
        } else {
            participants.add(p);
        }

        // ... (phần code chặn người đang chơi và kiểm tra điều kiện giữ nguyên)
        for (Player participant : participants) {
            if (activeGames.containsKey(participant.getUniqueId())) {
                String errorMsg = plugin.getMessagesFile().getString("error.member_already_in", "<red>Thành viên <player> đang ở trong một Dungeon khác! Không thể bắt đầu.");
                p.sendMessage(ColorUtils.parseWithPrefix(errorMsg.replace("<player>", participant.getName())));
                return;
            }
        }

        DungeonTemplate tmpl = templates.get(id);
        if (tmpl == null) {
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.file_not_found").replace("<file>", id)));
            return;
        }

        if (!tmpl.isPublic() && !p.hasPermission("SinceDungeon.admin")) {
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.dungeon_maintenance")));
            return;
        }

        for (Player participant : participants) {
            for (DungeonTemplate.Condition cond : tmpl.conditions()) {
                String req = cond.requirement();
                String type = "PAPI";
                String value = req;

                if (req.contains(":") && !req.contains(";") && conditionProcessors.containsKey(req.split(":")[0].toUpperCase())) {
                    String[] parts = req.split(":", 2);
                    type = parts[0].toUpperCase();
                    value = parts[1];
                }

                ConditionProcessor processor = conditionProcessors.getOrDefault(type, conditionProcessors.get("PAPI"));

                if (processor != null && !processor.check(participant, value)) {
                    if (cond.failMessage() != null && !cond.failMessage().isEmpty()) {
                        participant.sendMessage(ColorUtils.parseWithPrefix("<red>" + cond.failMessage()));
                    } else {
                        String msg = plugin.getMessagesFile().getString("error.condition_fail");
                        if (msg != null)
                            participant.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<condition>", req)));
                    }

                    if (!participant.equals(p)) {
                        String failMsg = plugin.getMessagesFile().getString("party.member_failed_condition", "<red>Thành viên <player> không đạt điều kiện. Hủy quá trình vào Dungeon.");
                        p.sendMessage(ColorUtils.parseWithPrefix(failMsg.replace("<player>", participant.getName())));
                    }
                    return;
                }
            }
        }

        DungeonStartEvent startEvent = new DungeonStartEvent(p, tmpl, participants);
        Bukkit.getPluginManager().callEvent(startEvent);

        if (startEvent.isCancelled() || participants.isEmpty()) return;

        DungeonGame game = new DungeonGame(plugin, p, participants, tmpl);

        for (Player participant : participants) {
            activeGames.put(participant.getUniqueId(), game);
        }

        try {
            game.startLobby();
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting dungeon lobby for " + p.getName());
            e.printStackTrace();
            for (Player participant : participants) {
                activeGames.remove(participant.getUniqueId());
            }
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
        for (DungeonGame game : new HashSet<>(activeGames.values())) {
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

    public record ActionMeta(String displayName, Material icon, String description, Map<String, Object> defaults,
                             Map<String, List<String>> customPrompts) {
    }
}