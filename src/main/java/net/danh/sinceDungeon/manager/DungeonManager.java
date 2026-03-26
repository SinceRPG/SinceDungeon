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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager for handling active dungeon sessions, action registries, and processors.
 * Integrated with the Party System for high-concurrency group instance instantiation.
 */
public class DungeonManager {
    private final SinceDungeon plugin;
    private final Map<UUID, DungeonGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, DungeonTemplate> templates = new ConcurrentHashMap<>();

    private final Map<String, ActionParser> actionParsers = new ConcurrentHashMap<>();
    private final Map<String, ActionMeta> actionMeta = new ConcurrentHashMap<>();

    private final Map<String, RewardProcessor> rewardProcessors = new ConcurrentHashMap<>();
    private final Map<String, ConditionProcessor> conditionProcessors = new ConcurrentHashMap<>();

    /**
     * Constructs the DungeonManager.
     *
     * @param plugin The main plugin instance.
     */
    public DungeonManager(SinceDungeon plugin) {
        this.plugin = plugin;
        registerDefaultActions();
        registerDefaultProcessors();
        loadTemplates();
    }

    /**
     * Registers a new dungeon template into memory.
     *
     * @param template The template to register.
     */
    public void registerTemplate(DungeonTemplate template) {
        if (template != null && template.id() != null) templates.put(template.id(), template);
    }

    /**
     * Unregisters a dungeon template from memory.
     *
     * @param id The ID of the template.
     */
    public void unregisterTemplate(String id) {
        templates.remove(id);
    }

    /**
     * Registers a custom reward processor.
     *
     * @param type      The reward identifier string.
     * @param processor The processor logic interface.
     */
    public void registerRewardProcessor(String type, RewardProcessor processor) {
        rewardProcessors.put(type.toUpperCase(), processor);
    }

    /**
     * Gets a registered reward processor.
     *
     * @param type The reward identifier string.
     * @return The RewardProcessor if found, null otherwise.
     */
    public RewardProcessor getRewardProcessor(String type) {
        return rewardProcessors.get(type.toUpperCase());
    }

    /**
     * Registers a custom condition processor.
     *
     * @param type      The condition identifier string.
     * @param processor The processor logic interface.
     */
    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        conditionProcessors.put(type.toUpperCase(), processor);
    }

    private void registerDefaultProcessors() {
        registerConditionProcessor("PAPI", PAPIHook::checkCondition);

        registerRewardProcessor("COMMAND", (p, val, displayName) -> {
            String cmd = PAPIHook.setPlaceholders(p, val).replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            String msg = plugin.getMessagesFile().getString("reward.messages.received_custom");
            if (displayName != null && msg != null) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            }
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
            org.bukkit.Location dropLoc = p.getLocation();
            DungeonGame game = getGame(p.getUniqueId());
            if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
                dropLoc = game.getPlayer().getLocation();
            }
            for (ItemStack drop : left.values()) dropLoc.getWorld().dropItem(dropLoc, drop);
            String fullMsg = plugin.getMessagesFile().getString("reward.messages.inventory_full");
            if (fullMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(fullMsg));
        }
        String msg = plugin.getMessagesFile().getString("reward.messages.received_item");
        if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
    }

    /**
     * Registers a custom action type in the system.
     *
     * @param type          The ID of the action.
     * @param parser        The parser class.
     * @param displayName   The display name for the editor.
     * @param icon          The icon for the editor.
     * @param description   The description.
     * @param defaults      The default parameters.
     * @param customPrompts Custom editing prompts.
     */
    public void registerAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaults, Map<String, List<String>> customPrompts) {
        String key = type.toUpperCase();
        actionParsers.put(key, parser);
        actionMeta.put(key, new ActionMeta(displayName, icon, description, defaults, customPrompts != null ? customPrompts : new HashMap<>()));
    }

    /**
     * Gets all registered action types.
     *
     * @return Set of action string IDs.
     */
    public Set<String> getRegisteredActions() {
        return actionMeta.keySet();
    }

    /**
     * Gets the metadata of a specific action.
     *
     * @param type The action ID.
     * @return The ActionMeta record.
     */
    public ActionMeta getActionMeta(String type) {
        return actionMeta.get(type.toUpperCase());
    }

    /**
     * Creates a DungeonAction instance from raw map data.
     *
     * @param type The type of action to create.
     * @param data The map configuration data.
     * @return The parsed DungeonAction, or null.
     */
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
                }, "Spawn Vanilla Mob", Material.ZOMBIE_HEAD,
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
                }, "Reach Checkpoint", Material.COMPASS,
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
                }, "Loot Treasure Chest", Material.CHEST,
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
                ), "Break Wall via Block", Material.IRON_PICKAXE,
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
                }, "Spawn Mythic Boss", Material.WITHER_SKELETON_SKULL,
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

    /**
     * Reloads all dungeon configurations and running states.
     */
    public void reload() {
        stopAllGames();
        templates.clear();
        loadTemplates();
        plugin.getLogger().info("Dungeon templates reloaded!");
    }

    private void loadTemplates() {
        java.io.File folder = new java.io.File(plugin.getDataFolder(), "dungeons");
        if (!folder.exists()) folder.mkdirs();

        java.io.File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (java.io.File f : files) {
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

    /**
     * Evaluates party membership and initiates the DungeonGame sequence for all eligible members.
     *
     * @param p  The player initiating the command.
     * @param id The dungeon ID to join.
     */
    public void joinDungeon(Player p, String id) {
        PartyManager.Party party = plugin.getPartyManager().getParty(p.getUniqueId());
        Set<Player> participants = new HashSet<>();
        int originalPartySize = 1;

        if (party != null) {
            if (!party.getLeader().equals(p.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                return;
            }
            originalPartySize = party.getMembers().size();
            double maxDist = plugin.getConfigFile().getDouble("party.max-join-distance", 50.0);
            participants.addAll(plugin.getPartyManager().getEligibleMembers(party, maxDist));

            // UX CẢI THIỆN: Báo cho Leader biết nếu có thành viên bị bỏ lại vì đứng quá xa
            if (participants.size() < originalPartySize) {
                p.sendMessage(ColorUtils.parseWithPrefix("<yellow>Cảnh báo: Có " + (originalPartySize - participants.size()) + " thành viên đứng quá xa và sẽ không được kéo vào Dungeon!"));
            }
        } else {
            participants.add(p);
        }

        for (Player participant : participants) {
            if (activeGames.containsKey(participant.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.already_in")));
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
                        p.sendMessage(ColorUtils.parseWithPrefix("<red>Thành viên " + participant.getName() + " không đạt điều kiện. Hủy quá trình vào Dungeon."));
                    }
                    return;
                }
            }
        }

        DungeonStartEvent startEvent = new DungeonStartEvent(p, tmpl);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) return;

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

    /**
     * Forces a player to quit their active dungeon session.
     *
     * @param p The player.
     */
    public void quitDungeon(Player p) {
        if (activeGames.containsKey(p.getUniqueId())) activeGames.get(p.getUniqueId()).stop(true);
    }

    /**
     * Dispatches global bukkit events to the specific dungeon game handled by a player.
     *
     * @param p     The player.
     * @param event The Bukkit Event.
     */
    public void dispatchEvent(Player p, Event event) {
        if (p == null) return;
        DungeonGame game = activeGames.get(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            game.onEvent(event);
        }
    }

    /**
     * Forcefully stops all running games. Used on server shutdown/reload.
     */
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

    /**
     * Represents the metadata configuration for editor display of custom actions.
     */
    public record ActionMeta(String displayName, Material icon, String description, Map<String, Object> defaults,
                             Map<String, List<String>> customPrompts) {
    }
}