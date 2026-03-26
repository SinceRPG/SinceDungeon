package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The main API class for SinceDungeon.
 */
public class SinceDungeonAPI {

    private static SinceDungeonAPI instance;
    private final SinceDungeon plugin;

    private SinceDungeonAPI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the API instance.
     *
     * @param plugin The main plugin instance.
     */
    public static void init(SinceDungeon plugin) {
        instance = new SinceDungeonAPI(plugin);
    }

    /**
     * Retrieves the API instance.
     *
     * @return The SinceDungeonAPI instance.
     * @throws IllegalStateException If the API is not initialized.
     */
    public static SinceDungeonAPI get() {
        if (instance == null) {
            throw new IllegalStateException("SinceDungeon API has not been initialized!");
        }
        return instance;
    }

    /**
     * Makes a player join a specific dungeon.
     *
     * @param player    The player joining.
     * @param dungeonId The ID of the dungeon.
     */
    public void joinDungeon(Player player, String dungeonId) {
        plugin.getDungeonManager().joinDungeon(player, dungeonId);
    }

    /**
     * Forces a player to quit their current dungeon.
     *
     * @param player The player to forcefully quit.
     */
    public void quitDungeon(Player player) {
        plugin.getDungeonManager().quitDungeon(player);
    }

    /**
     * Checks if a player is currently inside a dungeon.
     *
     * @param player The player to check.
     * @return True if the player is in a dungeon, false otherwise.
     */
    public boolean isPlaying(Player player) {
        return plugin.getDungeonManager().getActiveGames().containsKey(player.getUniqueId());
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
     * Registers a completely custom action via code.
     *
     * @param type          The ID of the action.
     * @param parser        The parser class handling the logic.
     * @param displayName   The custom display name for the GUI.
     * @param icon          The custom icon material for the GUI.
     * @param description   A short description.
     * @param defaultParams Default parameters for instantiation.
     * @param customPrompts Custom chat prompts for editing fields.
     */
    public void registerCustomAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaultParams, Map<String, List<String>> customPrompts) {
        plugin.getDungeonManager().registerAction(type, parser, displayName, icon, description, defaultParams, customPrompts);
        plugin.getLogger().info("[API] Registered Custom Action: " + type);
    }

    /**
     * Registers a custom reward processing logic.
     *
     * @param type      The reward type ID.
     * @param processor The interface handling the reward.
     */
    public void registerRewardProcessor(String type, RewardProcessor processor) {
        plugin.getDungeonManager().registerRewardProcessor(type, processor);
        plugin.getLogger().info("[API] Registered Reward Processor: " + type.toUpperCase());
    }

    /**
     * Registers a custom condition processing logic.
     *
     * @param type      The condition type ID.
     * @param processor The interface handling the condition.
     */
    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        plugin.getDungeonManager().registerConditionProcessor(type, processor);
        plugin.getLogger().info("[API] Registered Condition Processor: " + type.toUpperCase());
    }

    /**
     * Dynamically registers a dungeon template via code instead of YAML.
     *
     * @param template The dungeon template.
     */
    public void registerTemplate(DungeonTemplate template) {
        plugin.getDungeonManager().registerTemplate(template);
    }

    /**
     * Unregisters a dungeon template.
     *
     * @param dungeonId The ID of the dungeon.
     */
    public void unregisterTemplate(String dungeonId) {
        plugin.getDungeonManager().unregisterTemplate(dungeonId);
    }

    /**
     * Gets a specific dungeon template.
     *
     * @param dungeonId The ID of the dungeon.
     * @return The dungeon template, or null if not found.
     */
    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    /**
     * Retrieves a set of all currently available dungeon templates.
     *
     * @return A set of dungeon IDs.
     */
    public Set<String> getAvailableTemplates() {
        return plugin.getDungeonManager().getTemplates().keySet();
    }

    /**
     * Retrieves the internal DungeonManager instance.
     *
     * @return The DungeonManager.
     */
    public DungeonManager getManager() {
        return plugin.getDungeonManager();
    }
}