package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.CustomItemProvider;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.managers.InstanceManager;
import net.danh.sinceDungeon.managers.PartySystemManager;
import net.danh.sinceDungeon.managers.RewardManager;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.models.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The main API class for SinceDungeon.
 * Provides safe methods for third-party plugins to interact with Dungeons, Parties, and Custom Processors.
 */
public class SinceDungeonAPI {

    private static SinceDungeonAPI instance;
    private final SinceDungeon plugin;

    private SinceDungeonAPI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public static void init(SinceDungeon plugin) {
        if (instance == null) {
            instance = new SinceDungeonAPI(plugin);
        }
    }

    public static SinceDungeonAPI get() {
        if (instance == null) {
            throw new IllegalStateException("SinceDungeon API has not been initialized!");
        }
        return instance;
    }

    public RewardManager getRewardManager() {
        return plugin.getRewardManager();
    }

    public PartySystemManager getPartyManager() {
        return plugin.getPartyManager();
    }

    public InstanceManager getInstanceManager() {
        return plugin.getInstanceManager();
    }

    public void joinDungeon(Player player, String dungeonId) {
        plugin.getDungeonManager().joinDungeon(player, dungeonId);
    }

    public void quitDungeon(Player player) {
        plugin.getDungeonManager().quitDungeon(player);
    }

    public void forceStopDungeon(UUID playerUuid, boolean teleport) {
        DungeonGame game = plugin.getDungeonManager().getGame(playerUuid);
        if (game != null) {
            game.stop(teleport);
        }
    }

    public boolean isPlaying(Player player) {
        return plugin.getDungeonManager().getActiveGames().containsKey(player.getUniqueId());
    }

    public boolean isPlaying(UUID uuid) {
        return plugin.getDungeonManager().getActiveGames().containsKey(uuid);
    }

    public DungeonGame getGame(Player player) {
        return plugin.getDungeonManager().getGame(player.getUniqueId());
    }

    public DungeonGame getGame(UUID uuid) {
        return plugin.getDungeonManager().getGame(uuid);
    }

    public Map<UUID, DungeonGame> getAllActiveGames() {
        return Map.copyOf(plugin.getDungeonManager().getActiveGames());
    }

    public void registerCustomAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaultParams, Map<String, List<String>> customPrompts) {
        plugin.getDungeonManager().registerAction(type, parser, displayName, icon, description, defaultParams, customPrompts);
        String logMsg = plugin.getLanguageManager().getString("admin.log.api_action_registered", "[API] Registered Custom Action: <type>");
        plugin.getLogger().info(logMsg.replace("<type>", type.toUpperCase()));
    }

    public void registerRewardProcessor(String type, RewardProcessor processor) {
        plugin.getDungeonManager().registerRewardProcessor(type, processor);
        String logMsg = plugin.getLanguageManager().getString("admin.log.api_reward_registered", "[API] Registered Reward Processor: <type>");
        plugin.getLogger().info(logMsg.replace("<type>", type.toUpperCase()));
    }

    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        plugin.getDungeonManager().registerConditionProcessor(type, processor);
        String logMsg = plugin.getLanguageManager().getString("admin.log.api_condition_registered", "[API] Registered Condition Processor: <type>");
        plugin.getLogger().info(logMsg.replace("<type>", type.toUpperCase()));
    }

    public void registerTemplate(DungeonTemplate template) {
        plugin.getDungeonManager().registerTemplate(template);
    }

    public void unregisterTemplate(String dungeonId) {
        plugin.getDungeonManager().unregisterTemplate(dungeonId);
    }

    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    public Set<String> getAvailableTemplates() {
        return plugin.getDungeonManager().getTemplates().keySet();
    }

    public DungeonManager getManager() {
        return plugin.getDungeonManager();
    }

    public void registerItemProvider(String prefix, CustomItemProvider provider) {
        plugin.getDungeonManager().registerItemProvider(prefix, provider);
        String logMsg = plugin.getLanguageManager().getString("admin.log.api_item_provider_registered", "[API] Registered Custom Item Provider: <type>");
        plugin.getLogger().info(logMsg.replace("<type>", prefix.toUpperCase()));
    }

    public String getPluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }
}