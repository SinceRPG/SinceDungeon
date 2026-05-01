package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
                plugin.getLogger().log(Level.WARNING, "[CooldownManager] Failed to load cooldowns.", e);
            }
        });
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
     * Removes an active cooldown for a player.
     *
     * @param uuid      The player's UUID.
     * @param dungeonId The dungeon template ID.
     */
    public void resetCooldown(UUID uuid, String dungeonId) {
        Map<UUID, Long> dungeonCooldowns = cooldownCache.get(dungeonId);
        if (dungeonCooldowns != null) {
            dungeonCooldowns.remove(uuid);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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