package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Manages the database connection for SinceDungeon.
 * Supports SQLite (local) and MySQL (remote).
 */
public class DatabaseManager {

    private final SinceDungeon plugin;
    private final String type;
    // MySQL settings
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public DatabaseManager(SinceDungeon plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfigFile().getString("database.type", "sqlite").toLowerCase();
        this.host = plugin.getConfigFile().getString("database.host", "localhost");
        this.port = plugin.getConfigFile().getInt("database.port", 3306);
        this.database = plugin.getConfigFile().getString("database.database", "sincedungeon");
        this.username = plugin.getConfigFile().getString("database.username", "root");
        this.password = plugin.getConfigFile().getString("database.password", "");
    }

    /**
     * Opens the database connection and creates tables if they don't exist.
     */
    public void connect() {
        try {
            if (type.equals("mysql")) {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
                connection = DriverManager.getConnection(url, username, password);
                plugin.getLogger().info("[Database] Connected to MySQL database: " + database);
            } else {
                // Default: SQLite
                File dbFile = new File(plugin.getDataFolder(), "data.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                plugin.getLogger().info("[Database] Connected to SQLite database: data.db");
            }
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to connect to database: " + e.getMessage());
        }
    }

    /**
     * Creates the required tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
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
                // Columns already exist
            }

            plugin.getLogger().info("[Database] Tables verified/created successfully.");
        }
    }

    /**
     * Closes the database connection gracefully.
     */
    public void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("[Database] Connection closed.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Error closing connection.", e);
            }
        }
    }

    /**
     * Ensures the connection is alive, reconnecting if needed.
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().warning("[Database] Connection was closed. Reconnecting...");
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Database] Failed to check connection state: " + e.getMessage());
        }
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
