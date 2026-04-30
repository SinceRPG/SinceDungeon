package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.managers.PartyManager;
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

    /**
     * Initializes the API instance.
     * Internal use only. Should not be called by external plugins.
     *
     * @param plugin The main plugin instance.
     */
    public static void init(SinceDungeon plugin) {
        if (instance == null) {
            instance = new SinceDungeonAPI(plugin);
        }
    }

    /**
     * Retrieves the API instance.
     *
     * @return The SinceDungeonAPI instance.
     * @throws IllegalStateException If the API is not initialized yet.
     */
    public static SinceDungeonAPI get() {
        if (instance == null) {
            throw new IllegalStateException("SinceDungeon API has not been initialized!");
        }
        return instance;
    }

    /**
     * Makes a player join a specific dungeon.
     * If the player is a party leader, the entire eligible party will be pulled in.
     *
     * @param player    The player joining (or party leader).
     * @param dungeonId The ID of the dungeon template.
     */
    public void joinDungeon(Player player, String dungeonId) {
        plugin.getDungeonManager().joinDungeon(player, dungeonId);
    }

    /**
     * Forces a player to quit their current dungeon.
     * This will trigger the standard stop and teleport sequences.
     *
     * @param player The player to forcefully quit.
     */
    public void quitDungeon(Player player) {
        plugin.getDungeonManager().quitDungeon(player);
    }

    /**
     * Forcefully stops the dungeon game associated with the given player UUID.
     * This will end the instance for everyone inside it.
     *
     * @param playerUuid The UUID of any player currently in the dungeon.
     * @param teleport   Whether to teleport the participants back to their original locations.
     */
    public void forceStopDungeon(UUID playerUuid, boolean teleport) {
        DungeonGame game = plugin.getDungeonManager().getGame(playerUuid);
        if (game != null) {
            game.stop(teleport);
        }
    }

    /**
     * Checks if a player is currently inside a running dungeon.
     *
     * @param player The player to check.
     * @return True if the player is in a dungeon, false otherwise.
     */
    public boolean isPlaying(Player player) {
        return plugin.getDungeonManager().getActiveGames().containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player is currently inside a running dungeon using their UUID.
     *
     * @param uuid The UUID of the player to check.
     * @return True if the player is in a dungeon.
     */
    public boolean isPlaying(UUID uuid) {
        return plugin.getDungeonManager().getActiveGames().containsKey(uuid);
    }

    /**
     * Gets the active dungeon game instance for a player.
     *
     * @param player The player inside the dungeon.
     * @return The DungeonGame instance, or null if not found.
     */
    public DungeonGame getGame(Player player) {
        return plugin.getDungeonManager().getGame(player.getUniqueId());
    }

    /**
     * Gets the active dungeon game instance using a player's UUID.
     *
     * @param uuid The UUID of the player.
     * @return The DungeonGame instance, or null if not found.
     */
    public DungeonGame getGame(UUID uuid) {
        return plugin.getDungeonManager().getGame(uuid);
    }

    /**
     * Retrieves an immutable map of all currently active games.
     * Useful for server monitoring plugins.
     *
     * @return Map containing player UUIDs mapped to their active DungeonGame.
     */
    public Map<UUID, DungeonGame> getAllActiveGames() {
        return Map.copyOf(plugin.getDungeonManager().getActiveGames());
    }

    /**
     * Registers a completely custom action via code for third-party integration.
     *
     * @param type          The unique ID of the action (e.g., "MY_CUSTOM_ACTION").
     * @param parser        The parser interface handling the logic.
     * @param displayName   The custom display name for the Editor GUI.
     * @param icon          The custom icon material for the Editor GUI.
     * @param description   A short description.
     * @param defaultParams Default parameters for instantiation when creating via Editor.
     * @param customPrompts Custom chat prompts for editing fields via Editor.
     */
    public void registerCustomAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaultParams, Map<String, List<String>> customPrompts) {
        plugin.getDungeonManager().registerAction(type, parser, displayName, icon, description, defaultParams, customPrompts);
        plugin.getLogger().info("[API] Registered Custom Action: " + type.toUpperCase());
    }

    /**
     * Registers a custom reward processing logic for third-party plugins.
     *
     * @param type      The unique reward type ID (e.g., "MY_VAULT_ECONOMY").
     * @param processor The interface handling the reward granting logic.
     */
    public void registerRewardProcessor(String type, RewardProcessor processor) {
        plugin.getDungeonManager().registerRewardProcessor(type, processor);
        plugin.getLogger().info("[API] Registered Reward Processor: " + type.toUpperCase());
    }

    /**
     * Registers a custom condition processing logic for third-party plugins.
     *
     * @param type      The unique condition type ID (e.g., "QUEST_COMPLETED").
     * @param processor The interface handling the condition checking logic.
     */
    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        plugin.getDungeonManager().registerConditionProcessor(type, processor);
        plugin.getLogger().info("[API] Registered Condition Processor: " + type.toUpperCase());
    }

    /**
     * Dynamically registers a dungeon template via code instead of YAML.
     *
     * @param template The structured DungeonTemplate object.
     */
    public void registerTemplate(DungeonTemplate template) {
        plugin.getDungeonManager().registerTemplate(template);
    }

    /**
     * Unregisters a loaded dungeon template from memory.
     *
     * @param dungeonId The ID of the dungeon to unregister.
     */
    public void unregisterTemplate(String dungeonId) {
        plugin.getDungeonManager().unregisterTemplate(dungeonId);
    }

    /**
     * Gets a specific loaded dungeon template.
     *
     * @param dungeonId The ID of the dungeon.
     * @return The DungeonTemplate record, or null if not found.
     */
    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    /**
     * Retrieves a set of all currently loaded and available dungeon template IDs.
     *
     * @return A Set of dungeon template IDs.
     */
    public Set<String> getAvailableTemplates() {
        return plugin.getDungeonManager().getTemplates().keySet();
    }

    /**
     * Retrieves the internal DungeonManager instance.
     * Note: Use this with caution as it exposes deep internal methods.
     *
     * @return The DungeonManager.
     */
    public DungeonManager getManager() {
        return plugin.getDungeonManager();
    }

    /**
     * Retrieves the internal PartyManager instance.
     * Allows third-party plugins to manage, query, or hook into the SinceDungeon party system.
     *
     * @return The PartyManager.
     */
    public PartyManager getPartyManager() {
        return plugin.getPartyManager();
    }

    /**
     * Returns the exact version of the SinceDungeon plugin running.
     * Useful for third-party compatibility checks.
     *
     * @return The version string (e.g. "1.0.0").
     */
    public String getPluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }
}