package net.danh.sincedungeonpremium.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium-Exclusive Manager: Holographic Leaderboards
 * Responsibilities:
 * - Integrates with DecentHolograms API to render 3D top player leaderboards in the world.
 * - Schedules asynchronous updates pulling direct data from the SinceDungeon Core Database.
 */
public class HologramManager {

    private final SinceDungeonPremium plugin;

    public HologramManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    public void startUpdater() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().warning("DecentHolograms not installed. Holographic Leaderboards disabled.");
            return;
        }

        int updateInterval = plugin.getFileManager().getConfig().getInt("hologram-leaderboard.update-interval-seconds", 300) * 20;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateAllHolograms, 100L, updateInterval);
    }

    private void updateAllHolograms() {
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
        lines.add("&6&lSinceDungeon Leaderboard");
        lines.add("&eMap: &f" + mapId);
        lines.add("");

        int rank = 1;
        for (TopManager.TopEntry entry : topEntries) {
            String valueStr = String.valueOf(entry.value());
            lines.add("&e#" + rank + " &f" + entry.playerName() + " &8- &a" + valueStr);
            rank++;
        }

        if (topEntries.isEmpty()) {
            lines.add("&7No records found yet.");
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