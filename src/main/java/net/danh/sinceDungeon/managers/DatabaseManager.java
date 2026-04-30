package net.danh.sinceDungeon.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.danh.sinceDungeon.SinceDungeon;

import java.io.File;
import java.sql.Connection;
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

        // General Hikari Settings for performance
        config.setMaximumPoolSize(plugin.getConfigFile().getInt("database.pool.max-size", 10));
        config.setMinimumIdle(plugin.getConfigFile().getInt("database.pool.min-idle", 2));
        config.setMaxLifetime(plugin.getConfigFile().getInt("database.pool.max-lifetime", 1800000)); // 30 mins
        config.setConnectionTimeout(plugin.getConfigFile().getInt("database.pool.timeout", 5000)); // 5 secs
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

            // MySQL specific optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            plugin.getLogger().info("[Database] Initializing MySQL connection pool via HikariCP...");

        } else {
            // Default: SQLite
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite doesn't need a large pool, 1 connection is safer to prevent "database is locked" errors
            config.setMaximumPoolSize(1);
            plugin.getLogger().info("[Database] Initializing SQLite database...");
        }

        try {
            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info("[Database] Successfully connected!");
        } catch (Exception e) {
            plugin.getLogger().severe("[Database] Failed to initialize HikariCP: " + e.getMessage());
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
            plugin.getLogger().info("[Database] Hikari pool closed.");
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
}