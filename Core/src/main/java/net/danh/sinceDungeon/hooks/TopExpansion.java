package net.danh.sinceDungeon.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager.TopCategory;
import net.danh.sinceDungeon.managers.TopManager.TopEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI Expansion for SinceDungeon leaderboards.
 * Uses an asynchronous caching system to prevent database query lag on scoreboard updates.
 */
public class TopExpansion extends PlaceholderExpansion {

    private final SinceDungeon plugin;
    private final Map<String, List<TopEntry>> cache = new ConcurrentHashMap<>();

    public TopExpansion(SinceDungeon plugin) {
        this.plugin = plugin;
        startCacheTask();
    }

    /**
     * Starts an asynchronous repeating task to refresh leaderboard caches.
     * Updates data every 5 minutes (6000 ticks) to maintain peak server performance.
     */
    private void startCacheTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String mapId : plugin.getDungeonManager().getTemplates().keySet()) {
                for (TopCategory category : TopCategory.values()) {
                    String cacheKey = mapId + "_" + category.name();
                    List<TopEntry> entries = plugin.getTopManager().getTop(mapId, category, 10);
                    cache.put(cacheKey, entries);
                }
            }
        }, 0L, 6000L); // 5 minutes
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sincedungeontop";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Allows Expansion to persist across PAPI reloads
    }

    /**
     * Handles the placeholder parsing logic based on memory-cached database results.
     * Format: %sincedungeontop_<category>_<map>_<rank>_<type>%
     * Example: %sincedungeontop_fastest_zombie_crypt_1_name%
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split("_");
        if (args.length < 4) return null;

        String catStr = args[0].toLowerCase();
        TopCategory category = parseCategory(catStr);
        if (category == null) return null;

        String type = args[args.length - 1].toLowerCase(); // name or value
        int rank;
        try {
            rank = Integer.parseInt(args[args.length - 2]);
        } catch (NumberFormatException e) {
            return null;
        }

        // Safely reconstruct the map ID in case the ID contains underscores
        StringBuilder mapIdBuilder = new StringBuilder();
        for (int i = 1; i < args.length - 2; i++) {
            mapIdBuilder.append(args[i]);
            if (i < args.length - 3) mapIdBuilder.append("_");
        }
        String mapId = mapIdBuilder.toString();

        String cacheKey = mapId + "_" + category.name();
        List<TopEntry> entries = cache.get(cacheKey);

        String emptyName = plugin.getLanguageManager().getString("top.papi_empty_name", "None");
        String emptyValue = plugin.getLanguageManager().getString("top.papi_empty_value", "-");

        if (entries == null || rank > entries.size() || rank < 1) {
            return type.equals("name") ? emptyName : emptyValue;
        }

        TopEntry entry = entries.get(rank - 1);

        if (type.equals("name")) {
            return entry.playerName();
        } else if (type.equals("value")) {
            if (category == TopCategory.FASTEST_TIME || category == TopCategory.PARTY_FASTEST_TIME) {
                return formatTime((int) entry.value());
            } else {
                return String.valueOf(entry.value());
            }
        }

        return null;
    }

    /**
     * Converts a string identifier into the corresponding database TopCategory.
     */
    private TopCategory parseCategory(String s) {
        return switch (s) {
            case "fastest" -> TopCategory.FASTEST_TIME;
            case "partyfastest" -> TopCategory.PARTY_FASTEST_TIME;
            case "kills" -> TopCategory.MOST_KILLS;
            case "clears" -> TopCategory.MOST_CLEARS;
            default -> null;
        };
    }

    /**
     * Formats integer seconds into a clean MM:SS string representation.
     */
    private String formatTime(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}