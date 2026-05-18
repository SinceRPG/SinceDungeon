package net.danh.sinceDungeon.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.SchedulerCompat;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages the database connection for SinceDungeon.
 * Supports SQLite (local) and MySQL (remote).
 */
public class DatabaseManager {

    private final SinceDungeon plugin;
    private final String type;
    private HikariDataSource dataSource;

    public DatabaseManager(SinceDungeon plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfigFile().getString("database.type", "sqlite").toLowerCase();
    }

    /**
     * Opens the database connection and creates tables if they don't exist.
     */
    public boolean connect() {
        HikariConfig config = new HikariConfig();

        config.setMaximumPoolSize(plugin.getConfigFile().getInt("database.pool.max-size", 10));
        config.setMinimumIdle(plugin.getConfigFile().getInt("database.pool.min-idle", 2));
        config.setMaxLifetime(plugin.getConfigFile().getInt("database.pool.max-lifetime", 1800000));
        config.setConnectionTimeout(plugin.getConfigFile().getInt("database.pool.timeout", 5000));
        config.setPoolName("SinceDungeon-Pool");

        if (type.equals("mysql")) {
            String host = plugin.getConfigFile().getString("database.host", "localhost");
            int port = plugin.getConfigFile().getInt("database.port", 3306);
            String database = plugin.getConfigFile().getString("database.database", "sincedungeonpremium");
            String username = plugin.getConfigFile().getString("database.username", "root");
            String password = plugin.getConfigFile().getString("database.password", "");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.db_mysql_init", "[Database] Initializing MySQL connection pool via HikariCP..."));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);
            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.db_sqlite_init", "[Database] Initializing SQLite database..."));
        }

        try {
            dataSource = new HikariDataSource(config);
            if (!createTables()) {
                disconnect();
                return false;
            }
            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.db_connected", "[Database] Successfully connected!"));
            return true;
        } catch (Exception e) {
            String errorMsg = plugin.getLanguageManager().getString("admin.log.db_error_hikari", "[Database] Failed to initialize: <error>");
            plugin.getLogger().severe(errorMsg.replace("<error>", e.getMessage()));
            return false;
        }
    }

    public CompletableFuture<Boolean> connectAsync() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SchedulerCompat.runAsync(plugin, () -> future.complete(connect()));
        return future;
    }

    /**
     * Creates the required tables if they don't exist.
     */
    private boolean createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS top_fastest (
                        dungeon_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        time_seconds INT NOT NULL,
                        recorded_at BIGINT NOT NULL,
                        PRIMARY KEY (dungeon_id, player_uuid)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS party_top_fastest (
                        record_id VARCHAR(36) PRIMARY KEY,
                        dungeon_id VARCHAR(64) NOT NULL,
                        members_names TEXT NOT NULL,
                        time_seconds INT NOT NULL,
                        recorded_at BIGINT NOT NULL
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS top_kills (
                        dungeon_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        kill_count INT NOT NULL,
                        recorded_at BIGINT NOT NULL,
                        PRIMARY KEY (dungeon_id, player_uuid)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS top_clears (
                        dungeon_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        clear_count INT NOT NULL,
                        recorded_at BIGINT NOT NULL,
                        PRIMARY KEY (dungeon_id, player_uuid)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_lives (
                        uuid VARCHAR(36) PRIMARY KEY,
                        current_lives INT NOT NULL,
                        max_lives INT NOT NULL,
                        regen_amount INT NOT NULL DEFAULT -1,
                        regen_interval INT NOT NULL DEFAULT -1,
                        last_regen BIGINT NOT NULL
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_cooldowns (
                        uuid VARCHAR(36) NOT NULL,
                        dungeon_id VARCHAR(64) NOT NULL,
                        expire_time BIGINT NOT NULL,
                        PRIMARY KEY (uuid, dungeon_id)
                    )
                    """);

            try {
                stmt.execute("ALTER TABLE player_lives ADD COLUMN regen_amount INT DEFAULT -1");
                stmt.execute("ALTER TABLE player_lives ADD COLUMN regen_interval INT DEFAULT -1");
            } catch (SQLException ignored) {
            }

            return true;
        } catch (SQLException e) {
            String errorMsg = plugin.getLanguageManager().getString("admin.log.db_error_tables", "[Database] Failed to create tables: <error>");
            plugin.getLogger().severe(errorMsg.replace("<error>", e.getMessage()));
            return false;
        }
    }

    /**
     * Closes the database connection gracefully.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.db_closed", "[Database] Database pool closed."));
        }
    }

    /**
     * Ensures the connection is alive, reconnecting if needed.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource is null or closed.");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Executes the SQL DELETE commands to wipe all records for a specific map asynchronously.
     */
    public void resetLeaderboard(String map) {
        SchedulerCompat.runAsync(plugin, () -> {
            String sqlFastest = "DELETE FROM top_fastest WHERE dungeon_id = ?";
            String sqlPartyFastest = "DELETE FROM party_top_fastest WHERE dungeon_id = ?";
            String sqlKills = "DELETE FROM top_kills WHERE dungeon_id = ?";
            String sqlClears = "DELETE FROM top_clears WHERE dungeon_id = ?";

            try (Connection conn = this.getConnection()) {

                try (PreparedStatement ps = conn.prepareStatement(sqlFastest)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlPartyFastest)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlKills)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlClears)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }

                String successMsg = plugin.getLanguageManager().getString("admin.log.db_reset_success", "[Database] Successfully wiped all leaderboard records for map: <map>");
                plugin.getLogger().info(successMsg.replace("<map>", map));

            } catch (SQLException e) {
                String errorMsg = plugin.getLanguageManager().getString("admin.log.db_reset_error", "[Database] Failed to wipe leaderboard for map <map>!");
                plugin.getLogger().log(Level.SEVERE, errorMsg.replace("<map>", map), e);
            }
        });
    }

    /**
     * Executes the SQL DELETE commands to wipe all records for a specific player asynchronously.
     * If map is provided, wipes only their records on that map. Otherwise, wipes their records across all maps.
     */
    public void resetPlayerLeaderboard(UUID playerUuid, String map) {
        SchedulerCompat.runAsync(plugin, () -> {
            String condition = (map == null) ? "player_uuid = ?" : "player_uuid = ? AND dungeon_id = ?";
            String sqlFastest = "DELETE FROM top_fastest WHERE " + condition;
            String sqlKills = "DELETE FROM top_kills WHERE " + condition;
            String sqlClears = "DELETE FROM top_clears WHERE " + condition;

            try (Connection conn = this.getConnection()) {

                try (PreparedStatement ps = conn.prepareStatement(sqlFastest)) {
                    ps.setString(1, playerUuid.toString());
                    if (map != null) ps.setString(2, map);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlKills)) {
                    ps.setString(1, playerUuid.toString());
                    if (map != null) ps.setString(2, map);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlClears)) {
                    ps.setString(1, playerUuid.toString());
                    if (map != null) ps.setString(2, map);
                    ps.executeUpdate();
                }

                String logMsg = plugin.getLanguageManager().getString("admin.log.db_reset_player", "[Database] Successfully wiped leaderboard records for player <uuid> on map: <map>")
                        .replace("<uuid>", playerUuid.toString())
                        .replace("<map>", map == null ? "ALL" : map);
                plugin.getLogger().info(logMsg);

            } catch (SQLException e) {
                String errorMsg = plugin.getLanguageManager().getString("admin.log.db_reset_player_error", "[Database] Failed to wipe leaderboard for player <uuid>!")
                        .replace("<uuid>", playerUuid.toString());
                plugin.getLogger().log(Level.SEVERE, errorMsg, e);
            }
        });
    }
}
