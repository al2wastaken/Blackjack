package com.vortex.blackjack.database;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.TableSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages database connection pooling using HikariCP, supporting both SQLite and MySQL.
 * Handles persistent storage for Blackjack tables and player statistics.
 */
public class DatabaseManager {

    private final BlackjackPlugin plugin;
    private HikariDataSource dataSource;
    private String databaseType;

    public DatabaseManager(BlackjackPlugin plugin) {
        this.plugin = plugin;
    }

    public DatabaseManager(BlackjackPlugin plugin, com.vortex.blackjack.config.ConfigManager configManager) {
        this(plugin);
    }

    /**
     * Initializes the connection pool and creates database tables if they do not exist.
     */
    public boolean initialize() {
        FileConfiguration config = plugin.getConfig();
        databaseType = config.getString("database.type", "SQLITE").toUpperCase();

        try {
            HikariConfig hikariConfig = new HikariConfig();

            if ("MYSQL".equals(databaseType)) {
                String host = config.getString("database.mysql.host", "localhost");
                int port = config.getInt("database.mysql.port", 3306);
                String database = config.getString("database.mysql.database", "blackjack");
                String username = config.getString("database.mysql.username", "root");
                String password = config.getString("database.mysql.password", "");
                boolean ssl = config.getBoolean("database.mysql.ssl", false);

                String jdbcUrl = String.format(
                        "jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4&serverTimezone=UTC",
                        host, port, database, ssl
                );

                hikariConfig.setPoolName("Blackjack-MySQL-Pool");
                hikariConfig.setJdbcUrl(jdbcUrl);
                hikariConfig.setUsername(username);
                hikariConfig.setPassword(password);
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            } else {
                // SQLite default
                databaseType = "SQLITE";
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }

                String fileName = config.getString("database.sqlite.file", "blackjack.db");
                File dbFile = new File(dataFolder, fileName);

                hikariConfig.setPoolName("Blackjack-SQLite-Pool");
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");
            }

            // Pool tuning
            int maxPoolSize = config.getInt("database.pool.maximum-pool-size", "SQLITE".equals(databaseType) ? 5 : 10);
            int minIdle = config.getInt("database.pool.minimum-idle", 2);
            long connTimeout = config.getLong("database.pool.connection-timeout-ms", 30000L);
            long idleTimeout = config.getLong("database.pool.idle-timeout-ms", 600000L);
            long maxLifetime = config.getLong("database.pool.max-lifetime-ms", 1800000L);

            hikariConfig.setMaximumPoolSize(Math.max(1, maxPoolSize));
            hikariConfig.setMinimumIdle(Math.max(1, minIdle));
            hikariConfig.setConnectionTimeout(connTimeout);
            hikariConfig.setIdleTimeout(idleTimeout);
            hikariConfig.setMaxLifetime(maxLifetime);

            this.dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("HikariCP connection pool initialized successfully (" + databaseType + ")!");

            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database connection pool (" + databaseType + ")", e);
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database datasource is not initialized or closed.");
        }
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Tables schema
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS blackjack_tables (" +
                            "id VARCHAR(64) PRIMARY KEY, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "min_bet INT, " +
                            "max_bet INT, " +
                            "max_players INT, " +
                            "max_join_distance DOUBLE, " +
                            "croupier_skin VARCHAR(255) DEFAULT 'classic', " +
                            "countdown_seconds INT DEFAULT 15, " +
                            "felt_color VARCHAR(32) DEFAULT 'GREEN_CONCRETE', " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );

            // Migrations for existing tables
            try {
                stmt.executeUpdate("ALTER TABLE blackjack_tables ADD COLUMN croupier_skin VARCHAR(255) DEFAULT 'classic'");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE blackjack_tables ADD COLUMN countdown_seconds INT DEFAULT 15");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE blackjack_tables ADD COLUMN felt_color VARCHAR(32) DEFAULT 'GREEN_CONCRETE'");
            } catch (SQLException ignored) {}

            // Player stats schema
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS blackjack_player_stats (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(32), " +
                            "hands_won INT DEFAULT 0, " +
                            "hands_lost INT DEFAULT 0, " +
                            "hands_pushed INT DEFAULT 0, " +
                            "blackjacks INT DEFAULT 0, " +
                            "busts INT DEFAULT 0, " +
                            "current_streak INT DEFAULT 0, " +
                            "best_streak INT DEFAULT 0, " +
                            "total_winnings DOUBLE DEFAULT 0.0, " +
                            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Blackjack Tables CRUD
    // -------------------------------------------------------------------------

    public List<TableRecord> loadAllTables() {
        List<TableRecord> result = new ArrayList<>();
        String query = "SELECT id, world, x, y, z, yaw, pitch, min_bet, max_bet, max_players, max_join_distance, croupier_skin, countdown_seconds, felt_color FROM blackjack_tables";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");

                Integer minBet = (Integer) rs.getObject("min_bet");
                Integer maxBet = (Integer) rs.getObject("max_bet");
                Integer maxPlayers = (Integer) rs.getObject("max_players");
                Double maxDist = (Double) rs.getObject("max_join_distance");
                String croupierSkin = rs.getString("croupier_skin");
                Integer countdown = (Integer) rs.getObject("countdown_seconds");
                String feltColor = rs.getString("felt_color");

                result.add(new TableRecord(id, world, x, y, z, yaw, pitch, minBet, maxBet, maxPlayers, maxDist,
                        croupierSkin != null ? croupierSkin : "classic",
                        countdown != null ? countdown : 15,
                        feltColor != null ? feltColor : "GREEN_CONCRETE"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading tables from database", e);
        }
        return result;
    }

    public void saveTable(TableRecord table) {
        String upsert;
        if ("MYSQL".equals(databaseType)) {
            upsert = "INSERT INTO blackjack_tables (id, world, x, y, z, yaw, pitch, min_bet, max_bet, max_players, max_join_distance, croupier_skin, countdown_seconds, felt_color) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE min_bet = VALUES(min_bet), max_bet = VALUES(max_bet), " +
                    "max_players = VALUES(max_players), max_join_distance = VALUES(max_join_distance), " +
                    "croupier_skin = VALUES(croupier_skin), countdown_seconds = VALUES(countdown_seconds), felt_color = VALUES(felt_color)";
        } else {
            upsert = "INSERT OR REPLACE INTO blackjack_tables (id, world, x, y, z, yaw, pitch, min_bet, max_bet, max_players, max_join_distance, croupier_skin, countdown_seconds, felt_color) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, table.getId());
            ps.setString(2, table.getWorld());
            ps.setDouble(3, table.getX());
            ps.setDouble(4, table.getY());
            ps.setDouble(5, table.getZ());
            ps.setFloat(6, table.getYaw());
            ps.setFloat(7, table.getPitch());
            ps.setObject(8, table.getMinBet());
            ps.setObject(9, table.getMaxBet());
            ps.setObject(10, table.getMaxPlayers());
            ps.setObject(11, table.getMaxJoinDistance());
            ps.setString(12, table.getCroupierSkin());
            ps.setObject(13, table.getCountdownSeconds());
            ps.setString(14, table.getFeltColor());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving table " + table.getId() + " to database", e);
        }
    }

    public void deleteTable(String id) {
        String sql = "DELETE FROM blackjack_tables WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting table " + id + " from database", e);
        }
    }

    // -------------------------------------------------------------------------
    // Player Stats CRUD
    // -------------------------------------------------------------------------

    public PlayerStats loadPlayerStats(UUID uuid) {
        String sql = "SELECT hands_won, hands_lost, hands_pushed, blackjacks, busts, current_streak, best_streak, total_winnings " +
                "FROM blackjack_player_stats WHERE uuid = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerStats stats = new PlayerStats();
                    stats.setHandsWon(rs.getInt("hands_won"));
                    stats.setHandsLost(rs.getInt("hands_lost"));
                    stats.setHandsPushed(rs.getInt("hands_pushed"));
                    stats.setBlackjacks(rs.getInt("blackjacks"));
                    stats.setBusts(rs.getInt("busts"));
                    stats.setCurrentStreak(rs.getInt("current_streak"));
                    stats.setBestStreak(rs.getInt("best_streak"));
                    stats.setTotalWinnings(rs.getDouble("total_winnings"));
                    return stats;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading stats for " + uuid + " from database", e);
        }
        return null;
    }

    public void savePlayerStats(UUID uuid, PlayerStats stats) {
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(uuid);
        String name = player != null ? player.getName() : null;
        savePlayerStats(uuid, name, stats);
    }

    public void savePlayerStats(UUID uuid, String playerName, PlayerStats stats) {
        String upsert;
        if ("MYSQL".equals(databaseType)) {
            upsert = "INSERT INTO blackjack_player_stats (uuid, player_name, hands_won, hands_lost, hands_pushed, " +
                    "blackjacks, busts, current_streak, best_streak, total_winnings, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), hands_won = VALUES(hands_won), " +
                    "hands_lost = VALUES(hands_lost), hands_pushed = VALUES(hands_pushed), blackjacks = VALUES(blackjacks), " +
                    "busts = VALUES(busts), current_streak = VALUES(current_streak), best_streak = VALUES(best_streak), " +
                    "total_winnings = VALUES(total_winnings), last_updated = CURRENT_TIMESTAMP";
        } else {
            upsert = "INSERT OR REPLACE INTO blackjack_player_stats (uuid, player_name, hands_won, hands_lost, hands_pushed, " +
                    "blackjacks, busts, current_streak, best_streak, total_winnings, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setInt(3, stats.getHandsWon());
            ps.setInt(4, stats.getHandsLost());
            ps.setInt(5, stats.getHandsPushed());
            ps.setInt(6, stats.getBlackjacks());
            ps.setInt(7, stats.getBusts());
            ps.setInt(8, stats.getCurrentStreak());
            ps.setInt(9, stats.getBestStreak());
            ps.setDouble(10, stats.getTotalWinnings());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving stats for " + uuid + " to database", e);
        }
    }

    public void saveAllPlayerStats(Map<UUID, PlayerStats> statsMap) {
        if (statsMap == null || statsMap.isEmpty()) return;

        String upsert;
        if ("MYSQL".equals(databaseType)) {
            upsert = "INSERT INTO blackjack_player_stats (uuid, player_name, hands_won, hands_lost, hands_pushed, " +
                    "blackjacks, busts, current_streak, best_streak, total_winnings, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), hands_won = VALUES(hands_won), " +
                    "hands_lost = VALUES(hands_lost), hands_pushed = VALUES(hands_pushed), blackjacks = VALUES(blackjacks), " +
                    "busts = VALUES(busts), current_streak = VALUES(current_streak), best_streak = VALUES(best_streak), " +
                    "total_winnings = VALUES(total_winnings), last_updated = CURRENT_TIMESTAMP";
        } else {
            upsert = "INSERT OR REPLACE INTO blackjack_player_stats (uuid, player_name, hands_won, hands_lost, hands_pushed, " +
                    "blackjacks, busts, current_streak, best_streak, total_winnings, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        }

        try (Connection conn = getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                for (Map.Entry<UUID, PlayerStats> entry : statsMap.entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerStats stats = entry.getValue();
                    org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
                    String name = p != null ? p.getName() : null;

                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setInt(3, stats.getHandsWon());
                    ps.setInt(4, stats.getHandsLost());
                    ps.setInt(5, stats.getHandsPushed());
                    ps.setInt(6, stats.getBlackjacks());
                    ps.setInt(7, stats.getBusts());
                    ps.setInt(8, stats.getCurrentStreak());
                    ps.setInt(9, stats.getBestStreak());
                    ps.setDouble(10, stats.getTotalWinnings());

                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error batch saving player stats to database", e);
        }
    }

    // -------------------------------------------------------------------------
    // Legacy File Migration
    // -------------------------------------------------------------------------

    /**
     * Migrates legacy YAML tables and stats into database if DB is currently empty.
     */
    public void migrateLegacyData(File configFile, File statsFile) {
        FileConfiguration config = (configFile != null && configFile.exists())
                ? YamlConfiguration.loadConfiguration(configFile)
                : plugin.getConfig();
        migrateLegacyData(config, statsFile);
    }

    /**
     * Migrates legacy YAML tables and stats into database if DB is currently empty.
     */
    public void migrateLegacyData(FileConfiguration legacyConfig, File statsFile) {
        try {
            // Check if tables table is empty
            if (loadAllTables().isEmpty() && legacyConfig.contains("tables")) {
                ConfigurationSection tablesSec = legacyConfig.getConfigurationSection("tables");
                if (tablesSec != null) {
                    int tableCount = 0;
                    for (String worldName : tablesSec.getKeys(false)) {
                        ConfigurationSection worldSec = tablesSec.getConfigurationSection(worldName);
                        if (worldSec == null) continue;
                        for (String locKey : worldSec.getKeys(false)) {
                            String[] parts = locKey.split("_");
                            if (parts.length != 3) continue;
                            int x = Integer.parseInt(parts[0]);
                            int y = Integer.parseInt(parts[1]);
                            int z = Integer.parseInt(parts[2]);

                            TableSettings s = new TableSettings();
                            if (worldSec.contains(locKey + ".min-bet")) s.setMinBet(worldSec.getInt(locKey + ".min-bet"));
                            if (worldSec.contains(locKey + ".max-bet")) s.setMaxBet(worldSec.getInt(locKey + ".max-bet"));
                            if (worldSec.contains(locKey + ".max-players")) s.setMaxPlayers(worldSec.getInt(locKey + ".max-players"));
                            if (worldSec.contains(locKey + ".max-join-distance")) s.setMaxJoinDistance(worldSec.getDouble(locKey + ".max-join-distance"));

                            String id = worldName + "_" + x + "_" + y + "_" + z;
                            TableRecord rec = new TableRecord(id, worldName, x + 0.5, y, z + 0.5, 180.0f, 0.0f,
                                    s.getRawMinBet(), s.getRawMaxBet(), s.getRawMaxPlayers(), s.getRawMaxJoinDistance());
                            saveTable(rec);
                            tableCount++;
                        }
                    }
                    if (tableCount > 0) {
                        plugin.getLogger().info("Successfully migrated " + tableCount + " legacy tables to database!");
                    }
                }
            }

            // Check if stats file exists and migrate stats
            if (statsFile != null && statsFile.exists()) {
                FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);
                if (statsConfig.contains("players")) {
                    ConfigurationSection playersSec = statsConfig.getConfigurationSection("players");
                    if (playersSec != null) {
                        int statsCount = 0;
                        for (String uuidStr : playersSec.getKeys(false)) {
                            try {
                                UUID uuid = UUID.fromString(uuidStr);
                                if (loadPlayerStats(uuid) == null) {
                                    String path = "players." + uuidStr + ".";
                                    PlayerStats s = new PlayerStats();
                                    s.setHandsWon(statsConfig.getInt(path + "handsWon", 0));
                                    s.setHandsLost(statsConfig.getInt(path + "handsLost", 0));
                                    s.setHandsPushed(statsConfig.getInt(path + "handsPushed", 0));
                                    s.setCurrentStreak(statsConfig.getInt(path + "currentStreak", 0));
                                    s.setBestStreak(statsConfig.getInt(path + "bestStreak", 0));
                                    s.setTotalWinnings(statsConfig.getDouble(path + "totalWinnings", 0.0));
                                    s.setBlackjacks(statsConfig.getInt(path + "blackjacks", 0));
                                    s.setBusts(statsConfig.getInt(path + "busts", 0));

                                    savePlayerStats(uuid, null, s);
                                    statsCount++;
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                        if (statsCount > 0) {
                            plugin.getLogger().info("Successfully migrated " + statsCount + " player stats from stats.yml to database!");
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error migrating legacy files to database", e);
        }
    }

    /**
     * Safely closes the Hikari connection pool on plugin shutdown.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public String getDatabaseType() {
        return databaseType;
    }
}
