package net.danh.sincedungeonpremium.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager;
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
 * - Adheres to zero-hardcoding paradigms by pulling template texts from messages.yml.
 */
public class HologramManager {

    private final SinceDungeonPremium plugin;

    public HologramManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    /**
     * Premium Feature: In-Game Hologram Setup
     * Creates a new hologram at the exact location the player is standing,
     * automatically writes the coordinates to the config.yml, and forces a refresh.
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

    public void startUpdater() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().warning("DecentHolograms not installed. Holographic Leaderboards disabled.");
            return;
        }

        int updateInterval = plugin.getFileManager().getConfig().getInt("hologram-leaderboard.update-interval-seconds", 300) * 20;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateAllHolograms, 100L, updateInterval);
    }

    public void updateAllHolograms() {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos == null) return;

        for (String key : holos.getKeys(false)) {
            String mapId = holos.getString(key + ".map");
            String categoryStr = holos.getString(key + ".category");
            String locStr = holos.getString(key + ".location");

            if (mapId == null || categoryStr == null || locStr == null) continue;

            TopManager.TopCategory category;
            try {
                category = TopManager.TopCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception e) {
                continue;
            }

            List<TopManager.TopEntry> topEntries = SinceDungeon.getPlugin().getTopManager().getTop(mapId, category, 10);

            Bukkit.getScheduler().runTask(plugin, () -> renderHologram(key, mapId, locStr, topEntries));
        }
    }

    private void renderHologram(String holoId, String mapId, String locStr, List<TopManager.TopEntry> topEntries) {
        String[] parts = locStr.split(",");
        if (parts.length < 4) return;

        Location loc = new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));

        List<String> lines = new ArrayList<>();
        lines.add(plugin.getFileManager().getMessageRaw("holograms.header"));
        lines.add(plugin.getFileManager().getMessageRaw("holograms.map_line").replace("<map>", mapId));
        lines.add("");

        int rank = 1;
        String format = plugin.getFileManager().getMessageRaw("holograms.format");

        for (TopManager.TopEntry entry : topEntries) {
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