package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.api.events.DungeonStartEvent;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.CustomItemProvider;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.models.DungeonTemplate;
import net.danh.sinceDungeon.utils.BungeeUtils;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.DefaultRegistry;
import net.danh.sinceDungeon.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

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
    private final Object joinLock = new Object();

    private final Set<UUID> transitioningPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingCrossServerGames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, CustomItemProvider> customItemProviders = new ConcurrentHashMap<>();

    public DungeonManager(SinceDungeon plugin) {
        this.plugin = plugin;
        DefaultRegistry.registerAll(plugin, this);

        Map<String, DungeonTemplate> initialTemplates = loadTemplatesAsync().join();
        this.templates.putAll(initialTemplates);

        String msg = plugin.getLanguageManager().getString("admin.log.dungeon_loaded", "Loaded <count> Dungeon templates!");
        plugin.getLogger().info(msg.replace("<count>", String.valueOf(initialTemplates.size())));
    }

    public void addPendingCrossServerGame(UUID leader, String templateId) {
        pendingCrossServerGames.put(leader, templateId);

        int timeoutSeconds = plugin.getConfigFile().getInt("cross-server.transfer-timeout-seconds", 30);
        long timeoutTicks = timeoutSeconds * 20L;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingCrossServerGames.containsKey(leader)) {
                pendingCrossServerGames.remove(leader);
                plugin.getPartyManager().getProvider().disbandParty(leader);

                String logMsg = plugin.getLanguageManager().getString("admin.log.cross_server_timeout_cancel");
                if (logMsg != null) {
                    plugin.getLogger().warning(logMsg.replace("<player>", leader.toString()));
                }
            }
        }, timeoutTicks);
    }

    public void handleCrossServerReady(UUID leaderUuid, String targetServer) {
        if (!pendingRequests.containsKey(leaderUuid)) return;

        pendingRequests.remove(leaderUuid);

        Player leader = Bukkit.getPlayer(leaderUuid);
        if (leader == null || !leader.isOnline()) return;

        Set<UUID> members = plugin.getPartyManager().getProvider().getMembers(leaderUuid);

        String foundMsg = plugin.getLanguageManager().getString("cross_server.found");
        if (foundMsg != null)
            leader.sendMessage(ColorUtils.parseWithPrefix(foundMsg.replace("<server>", targetServer)));

        if (members != null && !members.isEmpty()) {
            for (UUID memId : members) {
                Player mem = Bukkit.getPlayer(memId);
                if (mem != null && mem.isOnline()) {
                    BungeeUtils.sendPlayerToServer(mem, targetServer);
                }
            }
        } else {
            BungeeUtils.sendPlayerToServer(leader, targetServer);
        }
    }

    public void checkPendingCrossServerJoin(Player p) {
        if (pendingCrossServerGames.containsKey(p.getUniqueId())) {
            String templateId = pendingCrossServerGames.remove(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                joinDungeonLocal(p, templateId);
            }, 40L);
        }
    }

    public void joinDungeon(Player p, String id) {
        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false)) {

            if (plugin.getPartyManager().getProvider().hasParty(p.getUniqueId())
                    && !plugin.getPartyManager().getProvider().isLeader(p.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
                return;
            }

            if (pendingRequests.containsKey(p.getUniqueId())) {
                String spamMsg = plugin.getLanguageManager().getString("cross_server.already_searching");
                if (spamMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(spamMsg));
                return;
            }

            String searchingMsg = plugin.getLanguageManager().getString("cross_server.searching", "&eSearching for an available Node Server to initialize the Dungeon...");
            p.sendMessage(ColorUtils.parseWithPrefix(searchingMsg));

            pendingRequests.put(p.getUniqueId(), System.currentTimeMillis());

            String partyDataRaw = p.getUniqueId().toString() + "~" + p.getName();
            Set<UUID> members = plugin.getPartyManager().getProvider().getMembers(p.getUniqueId());
            if (members != null && !members.isEmpty()) {
                partyDataRaw = members.stream()
                        .map(uuid -> uuid.toString() + "~" + plugin.getPartyManager().getProvider().getMemberName(uuid))
                        .reduce((a, b) -> a + "," + b).orElse(partyDataRaw);
            }

            plugin.getRedisManager().requestDungeonServer(id, p.getUniqueId(), partyDataRaw);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pendingRequests.containsKey(p.getUniqueId())) {
                    pendingRequests.remove(p.getUniqueId());
                    if (p.isOnline()) {
                        String timeoutMsg = plugin.getLanguageManager().getString("cross_server.timeout", "&cNo available Dungeon servers found. Please try again later!");
                        p.sendMessage(ColorUtils.parseWithPrefix(timeoutMsg));
                    }
                }
            }, 100L);

            return;
        }

        joinDungeonLocal(p, id);
    }

    public void addTransitioning(UUID uuid) {
        transitioningPlayers.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeTransitioning(uuid), 200L);
    }

    public void removeTransitioning(UUID uuid) {
        transitioningPlayers.remove(uuid);
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

    private void handleItemDrop(Player p, ItemStack item, String displayName) {
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
        if (!left.isEmpty()) {
            Location dropLoc = p.getLocation();
            DungeonGame game = getGame(p.getUniqueId());
            String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");

            if (game != null && dropLoc.getWorld() != null && dropLoc.getWorld().equals(game.getWorld())) {
                Location safeLoc = game.getSavedLocation(p.getUniqueId());
                if (safeLoc != null && safeLoc.getWorld() != null) {
                    dropLoc = safeLoc;
                }
            }

            if (dropLoc.getWorld() != null && dropLoc.getWorld().getName().startsWith(prefix)) {
                dropLoc = Bukkit.getWorlds().getFirst().getSpawnLocation();
            }

            for (ItemStack drop : left.values()) {
                dropLoc.getWorld().dropItem(dropLoc, drop);
            }

            String fullMsg = plugin.getLanguageManager().getString("reward.messages.inventory_full");
            if (fullMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(fullMsg));
        }
        String msg = plugin.getLanguageManager().getString("reward.messages.received_item");
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

            if (action != null) {
                action.setActionType(type);

                if (data.containsKey("time_limit")) {
                    action.setTimeLimitSeconds(getInt(data.get("time_limit"), -1));
                }
                if (data.containsKey("time_penalty")) {
                    action.setTimeLimitPenalty(getInt(data.get("time_penalty"), 1));
                }

                if (data.containsKey("start_message")) {
                    Object msgObj = data.get("start_message");
                    List<String> msgs = new ArrayList<>();
                    if (msgObj instanceof String) msgs.add((String) msgObj);
                    else if (msgObj instanceof List) msgs.addAll((List<String>) msgObj);
                    action.setStartMessages(msgs);
                }
                if (data.containsKey("notifications")) {
                    Object notifObj = data.get("notifications");
                    Map<String, Boolean> notifMap = new HashMap<>();
                    if (notifObj instanceof ConfigurationSection sec) {
                        for (String k : sec.getKeys(false)) {
                            notifMap.put(k, sec.getBoolean(k, true));
                        }
                    } else if (notifObj instanceof Map<?, ?> m) {
                        for (Map.Entry<?, ?> entry : m.entrySet()) {
                            try {
                                notifMap.put(entry.getKey().toString(), Boolean.parseBoolean(entry.getValue().toString()));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    action.setNotifications(notifMap);
                }
            }
            return action;
        } catch (Exception e) {
            String logMsg = plugin.getLanguageManager().getString("admin.log.action_create_fail", "Failed to create action <type>: <error>");
            plugin.getLogger().warning(logMsg.replace("<type>", type).replace("<error>", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }

    private int getInt(Object obj, int def) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    public CompletableFuture<Void> reload() {
        stopAllGames();
        return loadTemplatesAsync().thenAccept(newTemplates -> Bukkit.getScheduler().runTask(plugin, () -> {
            templates.clear();
            templates.putAll(newTemplates);
            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.dungeon_reloaded"));
        }));
    }

    private CompletableFuture<Map<String, DungeonTemplate>> loadTemplatesAsync() {
        File folder = new File(plugin.getDataFolder(), "dungeons");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return CompletableFuture.completedFuture(new HashMap<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, DungeonTemplate> bufferMap = new ConcurrentHashMap<>();

        for (File f : files) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String id = f.getName().replace(".yml", "");
                try {
                    DungeonTemplate t = DungeonLoader.loadTemplate(plugin, id);
                    if (t != null) bufferMap.put(id, t);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading template " + id + ": " + e.getMessage());
                }
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> bufferMap);
    }

    private void joinDungeonLocal(Player p, String id) {
        synchronized (joinLock) {
            Set<Player> participants = new HashSet<>();

            int offlineCount = 0;
            int deadCount = 0;
            int farCount = 0;

            if (plugin.getPartyManager().getProvider().hasParty(p.getUniqueId())) {
                if (!plugin.getPartyManager().getProvider().isLeader(p.getUniqueId())) {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
                    return;
                }

                double maxDist = plugin.getConfigFile().getDouble("party.max-join-distance", 50.0);

                for (UUID uid : plugin.getPartyManager().getProvider().getMembers(p.getUniqueId())) {
                    Player mem = Bukkit.getPlayer(uid);
                    if (mem == null || !mem.isOnline()) {
                        offlineCount++;
                    } else if (mem.isDead()) {
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
                    String warnMsg = plugin.getLanguageManager().getString("party.offline_left_behind", "<yellow>Warning: <count> member(s) are Offline and were left behind!");
                    p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(offlineCount))));
                }
                if (deadCount > 0) {
                    String warnMsg = plugin.getLanguageManager().getString("party.dead_left_behind", "<yellow>Warning: <count> member(s) are Dead and were left behind!");
                    p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(deadCount))));
                }
                if (farCount > 0) {
                    String warnMsg = plugin.getLanguageManager().getString("party.distance_warning", "<yellow>Warning: <count> member(s) are too far away and were left behind!");
                    p.sendMessage(ColorUtils.parseWithPrefix(warnMsg.replace("<count>", String.valueOf(farCount))));
                }

                for (UUID uid : plugin.getPartyManager().getProvider().getMembers(p.getUniqueId())) {
                    Player leftBehind = Bukkit.getPlayer(uid);
                    if (leftBehind != null && leftBehind.isOnline() && !participants.contains(leftBehind)) {
                        leftBehind.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.member_failed_condition", "&cYou were left behind because you didn't meet the entry requirements or were too far away!")));
                    }
                }
            } else {
                participants.add(p);
            }

            for (Player participant : participants) {
                if (activeGames.containsKey(participant.getUniqueId())) {
                    String errorMsg = plugin.getLanguageManager().getString("error.member_already_in", "<red>Member <player> is currently in another Dungeon! Cannot start.");
                    p.sendMessage(ColorUtils.parseWithPrefix(errorMsg.replace("<player>", participant.getName())));
                    return;
                }

                if (transitioningPlayers.contains(participant.getUniqueId())) {
                    String transMsg = plugin.getLanguageManager().getString("error.transition_processing", "<red>System is processing data, please try again in a moment!");
                    p.sendMessage(ColorUtils.parseWithPrefix(transMsg));
                    return;
                }

                if (plugin.getCooldownManager().isOnCooldown(participant.getUniqueId(), id)) {
                    String formattedTime = plugin.getCooldownManager().getRemainingTimeFormatted(participant.getUniqueId(), id);
                    if (participant.equals(p)) {
                        String msg = plugin.getLanguageManager().getString("error.on_cooldown", "&cYou are on cooldown! Please wait: &e<time>");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<time>", formattedTime)));
                    } else {
                        String leaderMsg = plugin.getLanguageManager().getString("error.party_member_on_cooldown", "&cCannot start! Member <player> is on cooldown for: &e<time>");
                        p.sendMessage(ColorUtils.parseWithPrefix(leaderMsg.replace("<player>", participant.getName()).replace("<time>", formattedTime)));

                        String childMsg = plugin.getLanguageManager().getString("error.on_cooldown", "&cYou are on cooldown! Please wait: &e<time>");
                        participant.sendMessage(ColorUtils.parseWithPrefix(childMsg.replace("<time>", formattedTime)));
                    }
                    return;
                }
            }

            DungeonTemplate tmpl = templates.get(id);
            if (tmpl == null) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.file_not_found").replace("<file>", id)));
                return;
            }

            ConditionProcessor conditionProcessor = conditionProcessors.get("PAPI");
            if (conditionProcessor != null && tmpl.conditions() != null && !tmpl.conditions().isEmpty()) {
                for (Player participant : participants) {
                    for (DungeonTemplate.Condition cond : tmpl.conditions()) {
                        if (!conditionProcessor.check(participant, cond.requirement())) {
                            if (cond.failMessage() != null && !cond.failMessage().isEmpty()) {
                                participant.sendMessage(ColorUtils.parseWithPrefix(cond.failMessage()));
                            }
                            if (!participant.equals(p)) {
                                String leaderMsg = plugin.getLanguageManager().getString("party.member_failed_condition", "&cMember <player> does not meet the condition. Aborting Dungeon entry.");
                                p.sendMessage(ColorUtils.parseWithPrefix(leaderMsg.replace("<player>", participant.getName())));
                            }
                            return;
                        }
                    }
                }
            }

            int maxPlayers = tmpl.settings().maxPlayers();
            if (maxPlayers > 0 && participants.size() > maxPlayers) {
                String msg = plugin.getLanguageManager().getString("error.exceed_max_players", "&cThis dungeon allows a maximum of <max> players! Your party is too large.");
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<max>", String.valueOf(maxPlayers))));
                return;
            }

            String reqItemStr = tmpl.settings().requiredItem();
            ItemStack parsedReqItem = null;
            if (reqItemStr != null && !reqItemStr.equalsIgnoreCase("NONE") && !reqItemStr.isEmpty()) {
                parsedReqItem = ItemBuilder.parseDynamicItem(reqItemStr);
                if (parsedReqItem != null) {
                    for (Player participant : participants) {
                        if (!participant.getInventory().containsAtLeast(parsedReqItem, parsedReqItem.getAmount())) {
                            String msg = plugin.getLanguageManager().getString("error.missing_required_item", "&cYou lack the required item to enter: <item>")
                                    .replace("<item>", reqItemStr);
                            participant.sendMessage(ColorUtils.parseWithPrefix(msg));

                            if (!participant.equals(p)) {
                                String leaderMsg = plugin.getLanguageManager().getString("error.party_member_missing_item", "&cMember <player> lacks the required entry item.")
                                        .replace("<player>", participant.getName());
                                p.sendMessage(ColorUtils.parseWithPrefix(leaderMsg));
                            }
                            return;
                        }
                    }
                }
            }

            int reqLives = tmpl.settings().requiredLivesToJoin();
            if (reqLives > 0) {
                for (Player participant : participants) {
                    if (!plugin.getLivesManager().hasEnoughLives(participant.getUniqueId(), reqLives)) {
                        LivesManager.PlayerLives lives = plugin.getLivesManager().getLives(participant.getUniqueId());
                        int current = lives != null ? lives.getCurrentLives() : 0;

                        String msg = plugin.getLanguageManager().getString("lives.not_enough")
                                .replace("<required>", String.valueOf(reqLives))
                                .replace("<current>", String.valueOf(current));

                        participant.sendMessage(ColorUtils.parseWithPrefix(msg));
                        if (!participant.equals(p)) {
                            String leaderMsg = plugin.getLanguageManager().getString("lives.party_member_not_enough");
                            if (leaderMsg != null) {
                                p.sendMessage(ColorUtils.parseWithPrefix(leaderMsg.replace("<player>", participant.getName())));
                            }
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
                String logErr = plugin.getLanguageManager().getString("admin.log.lobby_error", "Error starting dungeon lobby for <player>");
                plugin.getLogger().severe(logErr.replace("<player>", p.getName()));
                e.printStackTrace();
                for (Player participant : participants) {
                    activeGames.remove(participant.getUniqueId());
                }
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.init_failed")));
            }
        }
    }

    public void quitDungeon(Player p) {
        if (activeGames.containsKey(p.getUniqueId())) activeGames.get(p.getUniqueId()).stop(true);
    }

    public void cancelPendingRequest(UUID uuid) {
        if (pendingRequests.containsKey(uuid)) {
            pendingRequests.remove(uuid);
            String logMsg = plugin.getLanguageManager().getString("admin.log.cross_server_request_cancelled_quit", "[CrossServer] Cancelled pending request for <player>.");
            if (logMsg != null && !logMsg.isEmpty()) {
                plugin.getLogger().info(logMsg.replace("<player>", uuid.toString()));
            }
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
        for (DungeonGame game : new HashSet<>(activeGames.values())) {
            game.forceShutdown();
        }
        activeGames.clear();
    }

    public void registerItemProvider(String prefix, CustomItemProvider provider) {
        customItemProviders.put(prefix.toUpperCase(), provider);
    }

    public CustomItemProvider getItemProvider(String prefix) {
        return customItemProviders.get(prefix.toUpperCase());
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