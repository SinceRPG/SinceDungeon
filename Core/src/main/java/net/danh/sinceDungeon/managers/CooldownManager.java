package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.SchedulerCompat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player re-entry cooldowns for specific dungeons.
 * Syncs asynchronously with the database to persist across restarts.
 */
public class CooldownManager {

    private final SinceDungeon plugin;
    private final Map<String, Map<UUID, Long>> cooldownCache = new ConcurrentHashMap<>();

    public CooldownManager(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the active cooldowns from the database into memory.
     */
    public void loadCooldowns() {
        loadCooldownsAsync();
    }

    public CompletableFuture<Void> loadCooldownsAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerCompat.runAsync(plugin, () -> {
            String sql = "SELECT * FROM player_cooldowns WHERE expire_time > ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String dungeonId = rs.getString("dungeon_id");
                        long expireTime = rs.getLong("expire_time");

                        cooldownCache.computeIfAbsent(dungeonId, k -> new ConcurrentHashMap<>()).put(uuid, expireTime);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, plugin.getLanguageManager().getString("admin.log.cooldown_load_error", "[CooldownManager] Failed to load cooldowns."), e);
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    /**
     * Sets a cooldown for a specific player and dungeon.
     *
     * @param uuid       The player's UUID.
     * @param dungeonId  The dungeon template ID.
     * @param expireTime The absolute epoch time in milliseconds when the cooldown expires.
     */
    public void setCooldown(UUID uuid, String dungeonId, long expireTime) {
        cooldownCache.computeIfAbsent(dungeonId, k -> new ConcurrentHashMap<>()).put(uuid, expireTime);

        SchedulerCompat.runAsync(plugin, () -> {
            String sql = plugin.getConfigFile().getString("database.type", "sqlite").equalsIgnoreCase("mysql")
                    ? "INSERT INTO player_cooldowns (uuid, dungeon_id, expire_time) VALUES (?,?,?) ON DUPLICATE KEY UPDATE expire_time=VALUES(expire_time)"
                    : "INSERT OR REPLACE INTO player_cooldowns (uuid, dungeon_id, expire_time) VALUES (?,?,?)";

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, dungeonId);
                ps.setLong(3, expireTime);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[CooldownManager] Failed to save cooldown.", e);
            }
        });
    }

    /**
     * Removes an active cooldown for a specific player and map.
     *
     * @param uuid      The player's UUID.
     * @param dungeonId The dungeon template ID.
     */
    public void resetCooldown(UUID uuid, String dungeonId) {
        Map<UUID, Long> dungeonCooldowns = cooldownCache.get(dungeonId);
        if (dungeonCooldowns != null) {
            dungeonCooldowns.remove(uuid);
        }

        SchedulerCompat.runAsync(plugin, () -> {
            String sql = "DELETE FROM player_cooldowns WHERE uuid = ? AND dungeon_id = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, dungeonId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[CooldownManager] Failed to reset cooldown.", e);
            }
        });
    }

    /**
     * Resets all dungeon cooldowns for a specific player.
     *
     * @param uuid The player's UUID.
     */
    public void resetAllCooldowns(UUID uuid) {
        for (Map<UUID, Long> mapCooldowns : cooldownCache.values()) {
            mapCooldowns.remove(uuid);
        }

        SchedulerCompat.runAsync(plugin, () -> {
            String sql = "DELETE FROM player_cooldowns WHERE uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[CooldownManager] Failed to reset all cooldowns.", e);
            }
        });
    }

    /**
     * Reduces all active dungeon cooldowns for a player by a specific amount of time.
     *
     * @param uuid    The player's UUID.
     * @param seconds The amount of time in seconds to reduce.
     */
    public void reduceAllCooldowns(UUID uuid, int seconds) {
        long reductionMillis = seconds * 1000L;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Map<UUID, Long>> entry : cooldownCache.entrySet()) {
            Map<UUID, Long> mapCooldowns = entry.getValue();
            if (mapCooldowns.containsKey(uuid)) {
                long newTime = mapCooldowns.get(uuid) - reductionMillis;
                if (newTime <= now) {
                    mapCooldowns.remove(uuid);
                } else {
                    mapCooldowns.put(uuid, newTime);
                }
            }
        }

        SchedulerCompat.runAsync(plugin, () -> {
            String updateSql = "UPDATE player_cooldowns SET expire_time = expire_time - ? WHERE uuid = ?";
            String deleteSql = "DELETE FROM player_cooldowns WHERE expire_time <= ?";

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setLong(1, reductionMillis);
                    psUpdate.setString(2, uuid.toString());
                    psUpdate.executeUpdate();
                }

                try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                    psDelete.setLong(1, now);
                    psDelete.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[CooldownManager] Failed to reduce cooldowns.", e);
            }
        });
    }

    /**
     * Checks if a player has ANY active dungeon cooldowns.
     * Useful for preventing the consumption of items if they aren't needed.
     *
     * @param uuid The player's UUID.
     * @return True if the player has at least one active cooldown.
     */
    public boolean hasAnyCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        for (Map<UUID, Long> mapCooldowns : cooldownCache.values()) {
            if (mapCooldowns.containsKey(uuid)) {
                if (mapCooldowns.get(uuid) > now) {
                    return true;
                } else {
                    mapCooldowns.remove(uuid); // Cleanup expired cache passively
                }
            }
        }
        return false;
    }

    /**
     * Checks if a player is currently on cooldown for a specific dungeon.
     *
     * @param uuid      The player's UUID.
     * @param dungeonId The dungeon template ID.
     * @return True if the player is still on cooldown.
     */
    public boolean isOnCooldown(UUID uuid, String dungeonId) {
        Map<UUID, Long> dungeonCooldowns = cooldownCache.get(dungeonId);
        if (dungeonCooldowns == null || !dungeonCooldowns.containsKey(uuid)) return false;

        long expireTime = dungeonCooldowns.get(uuid);
        if (System.currentTimeMillis() >= expireTime) {
            dungeonCooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Retrieves the remaining cooldown time formatted as HH:MM:SS.
     *
     * @param uuid      The player's UUID.
     * @param dungeonId The dungeon template ID.
     * @return Formatted string of remaining time.
     */
    public String getRemainingTimeFormatted(UUID uuid, String dungeonId) {
        Map<UUID, Long> dungeonCooldowns = cooldownCache.get(dungeonId);
        if (dungeonCooldowns == null || !dungeonCooldowns.containsKey(uuid)) return "00:00";

        long remainingSecs = (dungeonCooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
        if (remainingSecs <= 0) return "00:00";

        long h = remainingSecs / 3600;
        long m = (remainingSecs % 3600) / 60;
        long s = remainingSecs % 60;

        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
    }
}
