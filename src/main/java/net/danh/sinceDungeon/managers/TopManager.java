package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages dungeon leaderboard (top) records.
 * Tracks: fastest clear time, most kills per run, and most total clears.
 */
public class TopManager {

    private final SinceDungeon plugin;
    private final DatabaseManager db;

    public TopManager(SinceDungeon plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * Wipes the leaderboard for a given map.
     * Since this manager fetches directly from the DB, no local cache needs clearing.
     *
     * @param map The map identifier to reset.
     */
    public void resetLeaderboard(String map) {
        // Hand over the execution to DatabaseManager
        plugin.getDatabaseManager().resetLeaderboard(map);
    }

    /**
     * Saves a clear time record for a player. Only updates if the new time is FASTER.
     */
    public void saveClearTime(String dungeonId, UUID playerUuid, String playerName, int timeSeconds) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {

                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT time_seconds FROM top_fastest WHERE dungeon_id = ? AND player_uuid = ?")) {
                    check.setString(1, dungeonId);
                    check.setString(2, playerUuid.toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            int existing = rs.getInt("time_seconds");
                            if (timeSeconds >= existing) return;
                        }
                    }
                }

                String sql = plugin.getConfigFile().getString("database.type", "sqlite").equalsIgnoreCase("mysql")
                        ? "INSERT INTO top_fastest (dungeon_id, player_uuid, player_name, time_seconds, recorded_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), time_seconds=VALUES(time_seconds), recorded_at=VALUES(recorded_at)"
                        : "INSERT OR REPLACE INTO top_fastest (dungeon_id, player_uuid, player_name, time_seconds, recorded_at) VALUES (?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dungeonId);
                    ps.setString(2, playerUuid.toString());
                    ps.setString(3, playerName);
                    ps.setInt(4, timeSeconds);
                    ps.setLong(5, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[TopManager] Failed to save clear time.", e);
            }
        });
    }

    /**
     * Saves a kills record for a player. Only updates if the new count is HIGHER.
     */
    public void saveKills(String dungeonId, UUID playerUuid, String playerName, int kills) {
        if (kills <= 0) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {

                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT kill_count FROM top_kills WHERE dungeon_id = ? AND player_uuid = ?")) {
                    check.setString(1, dungeonId);
                    check.setString(2, playerUuid.toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            int existing = rs.getInt("kill_count");
                            if (kills <= existing) return;
                        }
                    }
                }

                String sql = plugin.getConfigFile().getString("database.type", "sqlite").equalsIgnoreCase("mysql")
                        ? "INSERT INTO top_kills (dungeon_id, player_uuid, player_name, kill_count, recorded_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), kill_count=VALUES(kill_count), recorded_at=VALUES(recorded_at)"
                        : "INSERT OR REPLACE INTO top_kills (dungeon_id, player_uuid, player_name, kill_count, recorded_at) VALUES (?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dungeonId);
                    ps.setString(2, playerUuid.toString());
                    ps.setString(3, playerName);
                    ps.setInt(4, kills);
                    ps.setLong(5, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[TopManager] Failed to save kill count.", e);
            }
        });
    }

    /**
     * Increments the clear count for a player by 1.
     */
    public void incrementClears(String dungeonId, UUID playerUuid, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                int existing = 0;

                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT clear_count FROM top_clears WHERE dungeon_id = ? AND player_uuid = ?")) {
                    check.setString(1, dungeonId);
                    check.setString(2, playerUuid.toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) existing = rs.getInt("clear_count");
                    }
                }

                int newCount = existing + 1;
                String sql = plugin.getConfigFile().getString("database.type", "sqlite").equalsIgnoreCase("mysql")
                        ? "INSERT INTO top_clears (dungeon_id, player_uuid, player_name, clear_count, recorded_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), clear_count=VALUES(clear_count), recorded_at=VALUES(recorded_at)"
                        : "INSERT OR REPLACE INTO top_clears (dungeon_id, player_uuid, player_name, clear_count, recorded_at) VALUES (?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dungeonId);
                    ps.setString(2, playerUuid.toString());
                    ps.setString(3, playerName);
                    ps.setInt(4, newCount);
                    ps.setLong(5, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[TopManager] Failed to increment clear count.", e);
            }
        });
    }

    /**
     * Retrieves the top entries for a category, sorted appropriately.
     * This is synchronous - call it from an async context or use CompletableFuture.
     *
     * @param dungeonId The dungeon ID to query.
     * @param category  The category to retrieve.
     * @param limit     Maximum number of results.
     * @return List of TopEntry records.
     */
    public List<TopEntry> getTop(String dungeonId, TopCategory category, int limit) {
        List<TopEntry> results = new ArrayList<>();
        if (!db.isConnected()) return results;

        try (Connection conn = db.getConnection()) {
            String table;
            String valueCol;
            String order;

            switch (category) {
                case FASTEST_TIME -> {
                    table = "top_fastest";
                    valueCol = "time_seconds";
                    order = "ASC";
                }
                case MOST_KILLS -> {
                    table = "top_kills";
                    valueCol = "kill_count";
                    order = "DESC";
                }
                case MOST_CLEARS -> {
                    table = "top_clears";
                    valueCol = "clear_count";
                    order = "DESC";
                }
                default -> {
                    return results;
                }
            }

            String sql = "SELECT player_uuid, player_name, " + valueCol + ", recorded_at FROM " + table
                    + " WHERE dungeon_id = ? ORDER BY " + valueCol + " " + order + " LIMIT ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dungeonId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new TopEntry(
                                rs.getString("player_uuid"),
                                rs.getString("player_name"),
                                rs.getLong(valueCol),
                                rs.getLong("recorded_at")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[TopManager] Failed to query top " + category, e);
        }

        return results;
    }

    public enum TopCategory {
        FASTEST_TIME, MOST_KILLS, MOST_CLEARS
    }

    public record TopEntry(String playerUuid, String playerName, long value, long recordedAt) {
    }
}