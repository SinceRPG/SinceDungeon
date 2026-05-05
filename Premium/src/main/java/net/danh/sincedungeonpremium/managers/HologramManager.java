package net.danh.sincedungeonpremium.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager.TopCategory;
import net.danh.sinceDungeon.managers.TopManager.TopEntry;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Premium-Exclusive Manager: Holographic Leaderboards
 * Responsibilities:
 * - Integrates with DecentHolograms API to render 3D top player leaderboards in the world.
 * - Schedules asynchronous updates pulling direct data from the SinceDungeon Core Database.
 * - Handles creation, movement, and deletion of physical holograms seamlessly.
 * - Adheres to zero-hardcoding paradigms by pulling template texts from messages.yml.
 */
public class HologramManager {

    private final SinceDungeonPremium plugin;

    public HologramManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    /**
     * Premium Feature: In-Game Hologram Setup
     * Creates a new hologram at the exact location the player is standing.
     *
     * @param player   The admin player setting up the hologram.
     * @param mapId    The ID of the dungeon map (e.g., example_dungeon).
     * @param category The leaderboard category (e.g., FASTEST_TIME, MOST_KILLS).
     */
    public void createHologramInGame(Player player, String mapId, String category) {
        String holoId = "holo_" + System.currentTimeMillis();
        Location loc = player.getLocation();
        String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();

        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".map", mapId);
        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".category", category.toUpperCase());
        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".location", locStr);

        try {
            plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
            plugin.getFileManager().sendMessage(player, "admin.holo_created");
            updateAllHolograms(); // Force an immediate visual refresh
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save hologram to config!");
            plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
        }
    }

    /**
     * Premium Feature: Move Hologram
     * Relocates an existing hologram to the player's current location without resetting its settings.
     *
     * @param player The admin player moving the hologram.
     * @param holoId The unique configuration ID of the hologram.
     */
    public void moveHologramInGame(Player player, String holoId) {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null && holos.contains(holoId)) {
            Location loc = player.getLocation();
            String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();

            plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".location", locStr);
            try {
                plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.getFileManager().sendMessage(player, "admin.holo_moved");
                updateAllHolograms(); // Force a positional refresh immediately
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save config after moving hologram!");
                plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
            }
        } else {
            plugin.getFileManager().sendMessage(player, "admin.holo_not_found");
        }
    }

    /**
     * Premium Feature: In-Game Hologram Deletion
     * Removes an active hologram from the world and erases its entry from config.yml.
     *
     * @param player The admin player removing the hologram.
     * @param holoId The unique configuration ID of the hologram.
     */
    public void deleteHologramInGame(Player player, String holoId) {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null && holos.contains(holoId)) {
            plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId, null);
            try {
                plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
                if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                    DHAPI.removeHologram(holoId);
                }
                plugin.getFileManager().sendMessage(player, "admin.holo_deleted");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save config after deleting hologram!");
                plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
            }
        } else {
            plugin.getFileManager().sendMessage(player, "admin.holo_not_found");
        }
    }

    public void startUpdater() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().warning("DecentHolograms not installed. Holographic Leaderboards disabled.");
            return;
        }

        int updateInterval = plugin.getFileManager().getConfig().getInt("hologram-leaderboard.update-interval-seconds", 300) * 20;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateAllHolograms, 100L, updateInterval);
    }

    /**
     * Iterates through all saved hologram locations in config and asynchronously fetches
     * updated leaderboard records from the database, then dispatches rendering tasks.
     */
    public void updateAllHolograms() {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos == null) return;

        for (String key : holos.getKeys(false)) {
            String mapId = holos.getString(key + ".map");
            String categoryStr = holos.getString(key + ".category");
            String locStr = holos.getString(key + ".location");

            if (mapId == null || categoryStr == null || locStr == null) continue;

            TopCategory category;
            try {
                category = TopCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception e) {
                continue;
            }

            List<TopEntry> topEntries = SinceDungeon.getPlugin().getTopManager().getTop(mapId, category, 10);

            Bukkit.getScheduler().runTask(plugin, () -> renderHologram(key, mapId, category, locStr, topEntries));
        }
    }

    /**
     * Physically renders or updates the lines of a DecentHologram instance in the world.
     * Incorporates dynamic localized category names retrieved from the Core LanguageManager.
     *
     * @param holoId     The unique identifier of the hologram.
     * @param mapId      The dungeon map ID being displayed.
     * @param category   The leaderboard category to fetch the localized name.
     * @param locStr     The serialized location string.
     * @param topEntries The fetched list of top records.
     */
    private void renderHologram(String holoId, String mapId, TopCategory category, String locStr, List<TopEntry> topEntries) {
        String[] parts = locStr.split(",");
        if (parts.length < 4) return;

        Location loc = new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));

        List<String> lines = new ArrayList<>();
        lines.add(plugin.getFileManager().getMessageRaw("holograms.header"));
        lines.add(plugin.getFileManager().getMessageRaw("holograms.map_line").replace("<map>", mapId));

        String catName = "";
        switch (category) {
            case FASTEST_TIME ->
                    catName = SinceDungeon.getPlugin().getLanguageManager().getString("top.category_time", "Solo Fastest Clears");
            case PARTY_FASTEST_TIME ->
                    catName = SinceDungeon.getPlugin().getLanguageManager().getString("top.category_party_time", "Party Fastest Clears");
            case MOST_KILLS ->
                    catName = SinceDungeon.getPlugin().getLanguageManager().getString("top.category_kills", "Most Kills");
            case MOST_CLEARS ->
                    catName = SinceDungeon.getPlugin().getLanguageManager().getString("top.category_clears", "Most Clears");
        }

        lines.add(plugin.getFileManager().getMessageRaw("holograms.category_line").replace("<category>", catName));
        lines.add("");

        int rank = 1;
        String format = plugin.getFileManager().getMessageRaw("holograms.format");

        for (TopEntry entry : topEntries) {
            String valueStr = String.valueOf(entry.value());
            lines.add(format.replace("<rank>", String.valueOf(rank))
                    .replace("<player>", entry.playerName())
                    .replace("<value>", valueStr));
            rank++;
        }

        if (topEntries.isEmpty()) {
            lines.add(plugin.getFileManager().getMessageRaw("holograms.empty"));
        }

        if (DHAPI.getHologram(holoId) == null) {
            DHAPI.createHologram(holoId, loc, lines);
        } else {
            DHAPI.setHologramLines(DHAPI.getHologram(holoId), lines);
        }
    }

    public void clearAllHolograms() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) return;
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null) {
            for (String key : holos.getKeys(false)) {
                if (DHAPI.getHologram(key) != null) {
                    DHAPI.removeHologram(key);
                }
            }
        }
    }
}