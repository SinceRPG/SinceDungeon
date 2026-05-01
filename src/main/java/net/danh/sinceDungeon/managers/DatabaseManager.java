package net.danh.sinceDungeon.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.danh.sinceDungeon.SinceDungeon;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

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
    public void connect() {
        HikariConfig config = new HikariConfig();

        config.setMaximumPoolSize(plugin.getConfigFile().getInt("database.pool.max-size", 10));
        config.setMinimumIdle(plugin.getConfigFile().getInt("database.pool.min-idle", 2));
        config.setMaxLifetime(plugin.getConfigFile().getInt("database.pool.max-lifetime", 1800000));
        config.setConnectionTimeout(plugin.getConfigFile().getInt("database.pool.timeout", 5000));
        config.setPoolName("SinceDungeon-Pool");

        if (type.equals("mysql")) {
            String host = plugin.getConfigFile().getString("database.host", "localhost");
            int port = plugin.getConfigFile().getInt("database.port", 3306);
            String database = plugin.getConfigFile().getString("database.database", "sincedungeon");
            String username = plugin.getConfigFile().getString("database.username", "root");
            String password = plugin.getConfigFile().getString("database.password", "");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.db_mysql_init"));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);
            plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.db_sqlite_init"));
        }

        try {
            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.db_connected"));
        } catch (Exception e) {
            String errorMsg = plugin.getMessagesFile().getString("admin.log.db_error_hikari", "[Database] Failed to initialize: <error>");
            plugin.getLogger().severe(errorMsg.replace("<error>", e.getMessage()));
        }
    }

    /**
     * Creates the required tables if they don't exist.
     */
    private void createTables() {
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

            // Auto-patch existing tables for older versions
            try {
                stmt.execute("ALTER TABLE player_lives ADD COLUMN regen_amount INT DEFAULT -1");
                stmt.execute("ALTER TABLE player_lives ADD COLUMN regen_interval INT DEFAULT -1");
            } catch (SQLException ignored) {
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to create tables: " + e.getMessage());
        }
    }

    /**
     * Closes the database connection gracefully.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info(plugin.getMessagesFile().getString("admin.log.db_closed"));
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
     * Clears records from top_fastest, top_kills, and top_clears.
     *
     * @param map The map identifier (dungeon_id) to wipe.
     */
    public void resetLeaderboard(String map) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sqlFastest = "DELETE FROM top_fastest WHERE dungeon_id = ?";
            String sqlKills = "DELETE FROM top_kills WHERE dungeon_id = ?";
            String sqlClears = "DELETE FROM top_clears WHERE dungeon_id = ?";

            try (Connection conn = this.getConnection()) {

                // Delete from top_fastest
                try (PreparedStatement ps = conn.prepareStatement(sqlFastest)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }

                // Delete from top_kills
                try (PreparedStatement ps = conn.prepareStatement(sqlKills)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }

                // Delete from top_clears
                try (PreparedStatement ps = conn.prepareStatement(sqlClears)) {
                    ps.setString(1, map);
                    ps.executeUpdate();
                }

                plugin.getLogger().info("[Database] Successfully wiped all leaderboard records for map: " + map);
            } catch (SQLException e) {
                plugin.getLogger().severe("[Database] Failed to wipe leaderboard for map " + map + "!");
                e.printStackTrace();
            }
        });
    }
}