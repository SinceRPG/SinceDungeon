package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LivesManager {
    private final SinceDungeon plugin;
    private final Map<UUID, PlayerLives> cache = new ConcurrentHashMap<>();

    public LivesManager(SinceDungeon plugin) {
        this.plugin = plugin;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (PlayerLives lives : cache.values()) {
                if (lives.isModified()) {
                    saveToDatabase(lives);
                    lives.setModified(false);
                }
            }
        }, 6000L, 6000L);
    }

    public void loadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {

                int defaultMax = plugin.getConfigFile().getInt("lives.default-max-lives", 3);
                int defaultCurrent = plugin.getConfigFile().getInt("lives.default-start-lives", 3);

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT current_lives, max_lives, regen_amount, regen_interval, last_regen FROM player_lives WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        PlayerLives data;
                        if (rs.next()) {
                            data = new PlayerLives(uuid, rs.getInt("current_lives"), rs.getInt("max_lives"), rs.getInt("regen_amount"), rs.getInt("regen_interval"), rs.getLong("last_regen"));
                        } else {
                            data = new PlayerLives(uuid, defaultCurrent, defaultMax, -1, -1, System.currentTimeMillis());
                            data.setModified(true);
                        }

                        calculateRegeneration(data);
                        cache.put(uuid, data);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load lives for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        PlayerLives data = cache.remove(uuid);
        if (data != null && data.isModified()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToDatabase(data));
        }
    }

    private void saveToDatabase(PlayerLives data) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {

            String sql = plugin.getConfigFile().getString("database.type", "sqlite").equalsIgnoreCase("mysql")
                    ? "INSERT INTO player_lives (uuid, current_lives, max_lives, regen_amount, regen_interval, last_regen) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE current_lives=VALUES(current_lives), max_lives=VALUES(max_lives), regen_amount=VALUES(regen_amount), regen_interval=VALUES(regen_interval), last_regen=VALUES(last_regen)"
                    : "INSERT OR REPLACE INTO player_lives (uuid, current_lives, max_lives, regen_amount, regen_interval, last_regen) VALUES (?,?,?,?,?,?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getUuid().toString());
                ps.setInt(2, data.getCurrentLives());
                ps.setInt(3, data.getMaxLives());
                ps.setInt(4, data.getCustomRegenAmount());
                ps.setInt(5, data.getCustomRegenInterval());
                ps.setLong(6, data.getLastRegen());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save lives for " + data.getUuid() + ": " + e.getMessage());
        }
    }

    private void calculateRegeneration(PlayerLives data) {
        if (data.getCurrentLives() >= data.getMaxLives()) {
            data.setLastRegen(System.currentTimeMillis());
            return;
        }

        int regenIntervalSecs = data.getCustomRegenInterval() != -1 ? data.getCustomRegenInterval() : plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600);
        int regenAmount = data.getCustomRegenAmount() != -1 ? data.getCustomRegenAmount() : plugin.getConfigFile().getInt("lives.regen-amount", 1);

        if (regenIntervalSecs <= 0 || regenAmount <= 0) return;

        long regenIntervalMillis = regenIntervalSecs * 1000L;
        long now = System.currentTimeMillis();
        long diff = now - data.getLastRegen();

        if (diff >= regenIntervalMillis) {
            int cycles = (int) (diff / regenIntervalMillis);
            int newLives = Math.min(data.getMaxLives(), data.getCurrentLives() + (cycles * regenAmount));

            int recovered = newLives - data.getCurrentLives();
            if (recovered > 0) {
                data.setCurrentLives(newLives);
                data.setLastRegen(now - (diff % regenIntervalMillis));
                data.setModified(true);

                Player p = Bukkit.getPlayer(data.getUuid());
                if (p != null && p.isOnline()) {
                    String msg = plugin.getLanguageManager().getString("lives.regenerated")
                            .replace("<amount>", String.valueOf(recovered))
                            .replace("<current>", String.valueOf(newLives))
                            .replace("<max>", String.valueOf(data.getMaxLives()));
                    p.sendMessage(net.danh.sinceDungeon.utils.ColorUtils.parseWithPrefix(msg));
                }
            }
        }
    }

    public PlayerLives getLives(UUID uuid) {
        PlayerLives data = cache.get(uuid);
        if (data != null) {
            calculateRegeneration(data);
        }
        return data;
    }

    public boolean hasEnoughLives(UUID uuid, int required) {
        PlayerLives data = getLives(uuid);
        return data != null && data.getCurrentLives() >= required;
    }

    public void addLives(UUID uuid, int amount) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setCurrentLives(Math.min(data.getMaxLives(), data.getCurrentLives() + amount));
            data.setModified(true);
        }
    }

    public void setLives(UUID uuid, int amount) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setCurrentLives(Math.min(data.getMaxLives(), Math.max(0, amount)));
            data.setModified(true);
        }
    }

    public void removeLives(UUID uuid, int amount) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setCurrentLives(Math.max(0, data.getCurrentLives() - amount));
            data.setModified(true);
        }
    }

    public void addMaxLives(UUID uuid, int amount) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setMaxLives(data.getMaxLives() + amount);
            data.setModified(true);
        }
    }

    public void setCustomRegenAmount(UUID uuid, int amount) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setCustomRegenAmount(amount);
            data.setModified(true);
        }
    }

    public void setCustomRegenInterval(UUID uuid, int seconds) {
        PlayerLives data = getLives(uuid);
        if (data != null) {
            data.setCustomRegenInterval(seconds);
            data.setModified(true);
        }
    }

    public void forceSaveAll() {
        for (PlayerLives lives : cache.values()) {
            if (lives.isModified()) {
                saveToDatabase(lives);
            }
        }
    }

    public static class PlayerLives {
        private final UUID uuid;
        private int currentLives;
        private int maxLives;
        private int customRegenAmount;
        private int customRegenInterval;
        private long lastRegen;
        private boolean modified = false;

        public PlayerLives(UUID uuid, int currentLives, int maxLives, int customRegenAmount, int customRegenInterval, long lastRegen) {
            this.uuid = uuid;
            this.currentLives = currentLives;
            this.maxLives = maxLives;
            this.customRegenAmount = customRegenAmount;
            this.customRegenInterval = customRegenInterval;
            this.lastRegen = lastRegen;
        }

        public UUID getUuid() {
            return uuid;
        }

        public int getCurrentLives() {
            return currentLives;
        }

        public void setCurrentLives(int currentLives) {
            this.currentLives = currentLives;
        }

        public int getMaxLives() {
            return maxLives;
        }

        public void setMaxLives(int maxLives) {
            this.maxLives = maxLives;
        }

        public int getCustomRegenAmount() {
            return customRegenAmount;
        }

        public void setCustomRegenAmount(int customRegenAmount) {
            this.customRegenAmount = customRegenAmount;
        }

        public int getCustomRegenInterval() {
            return customRegenInterval;
        }

        public void setCustomRegenInterval(int customRegenInterval) {
            this.customRegenInterval = customRegenInterval;
        }

        public long getLastRegen() {
            return lastRegen;
        }

        public void setLastRegen(long lastRegen) {
            this.lastRegen = lastRegen;
        }

        public boolean isModified() {
            return modified;
        }

        public void setModified(boolean modified) {
            this.modified = modified;
        }
    }
}