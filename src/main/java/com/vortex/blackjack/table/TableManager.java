package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages blackjack tables - creation, removal, and lookup
 */
public class TableManager {
    private final BlackjackPlugin plugin;
    private final ConfigManager configManager;
    private final Map<Location, BlackjackTable> tables = new ConcurrentHashMap<>();
    private final Map<Player, BlackjackTable> playerTables = new ConcurrentHashMap<>();

    public TableManager(BlackjackPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Purges any leftover or orphan entities from previous server sessions.
     */
    public void purgeAllOrphanEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Set<String> tags = entity.getScoreboardTags();
                if (tags.contains("blackjack-entity") || tags.contains("blackjack-card") || tags.contains("blackjack-seat")) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Normalizes a table location so that:
     * - X and Z are centered on the nearest block (+0.5)
     * - Y is the block level
     * - Yaw is always 180.0f (facing North)
     * - Pitch is always 0.0f (level)
     */
    public static Location normalizeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        return new Location(
                loc.getWorld(),
                loc.getBlockX() + 0.5,
                loc.getBlockY(),
                loc.getBlockZ() + 0.5,
                180.0f,
                0.0f
        );
    }

    /**
     * Load tables from configuration on startup
     */
    public void loadTablesFromConfig() {
        // Clean any leftover orphan entities before recreating tables
        purgeAllOrphanEntities();

        if (plugin.getDatabaseManager() != null) {
            List<com.vortex.blackjack.database.TableRecord> dbTables = plugin.getDatabaseManager().loadAllTables();
            if (!dbTables.isEmpty()) {
                for (com.vortex.blackjack.database.TableRecord record : dbTables) {
                    Location loc = record.toLocation();
                    if (loc == null) {
                        plugin.getLogger().warning("World not found for table ID: " + record.getId() + " (" + record.getWorld() + ")");
                        continue;
                    }
                    createTable(loc, record.toTableSettings(), false);
                }
                plugin.getLogger().info("Loaded " + tables.size() + " blackjack tables from database (" + plugin.getDatabaseManager().getDatabaseType() + ")");
                return;
            }
        }

        if (!plugin.getConfig().contains("tables")) {
            return;
        }

        for (String worldName : plugin.getConfig().getConfigurationSection("tables").getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                continue;
            }

            ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("tables." + worldName);
            for (String locString : worldSection.getKeys(false)) {
                try {
                    String[] parts = locString.split("_");
                    if (parts.length != 3) continue;

                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);

                    TableSettings settings = loadSettingsFromConfig(worldSection, locString);
                    Location loc = new Location(world, x + 0.5, y, z + 0.5, 180.0f, 0.0f);
                    createTable(loc, settings, false); // Don't save to config again
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid table location format: " + locString);
                }
            }
        }

        plugin.getLogger().info("Loaded " + tables.size() + " blackjack tables");
    }

    /**
     * Create a new blackjack table at the specified location using global default settings.
     */
    public boolean createTable(Location centerLoc) {
        return createTable(centerLoc, new TableSettings(), true);
    }

    /**
     * Create a new blackjack table at the specified location with per-table settings.
     */
    public boolean createTable(Location centerLoc, TableSettings settings) {
        return createTable(centerLoc, settings, true);
    }

    private boolean createTable(Location centerLoc, TableSettings settings, boolean saveToConfig) {
        if (centerLoc == null || centerLoc.getWorld() == null) return false;

        Location normLoc = normalizeLocation(centerLoc);

        // Check if table already exists at this location or block
        for (Location loc : tables.keySet()) {
            if (loc.getWorld().equals(normLoc.getWorld()) && loc.distanceSquared(normLoc) < 1.0) {
                return false; // Table already exists
            }
        }

        World world = normLoc.getWorld();

        // Ensure chunk is loaded
        world.getChunkAt(normLoc).load();

        // Save to config if requested
        if (saveToConfig) {
            saveTableToConfig(normLoc, settings);
        }

        // Create table object (spawns 3D casino table model and Roulette-style chairs)
        BlackjackTable table = new BlackjackTable(plugin, this, configManager, normLoc, settings);
        tables.put(normLoc, table);

        return true;
    }

    /**
     * Get a table by its unique table id string.
     */
    public BlackjackTable getTableById(String id) {
        for (BlackjackTable table : tables.values()) {
            if (table.getTableId().equals(id)) {
                return table;
            }
        }
        return null;
    }

    /**
     * Remove a table at the specified location
     */
    public boolean removeTable(Location tableLoc) {
        if (tableLoc == null) return false;
        Location normLoc = normalizeLocation(tableLoc);

        BlackjackTable table = tables.remove(normLoc);
        if (table == null) {
            table = tables.remove(tableLoc);
        }
        if (table == null) {
            // Try searching by nearest table within 1 block
            for (Map.Entry<Location, BlackjackTable> entry : tables.entrySet()) {
                if (entry.getKey().getWorld().equals(tableLoc.getWorld()) && entry.getKey().distanceSquared(tableLoc) < 1.0) {
                    normLoc = entry.getKey();
                    table = tables.remove(normLoc);
                    break;
                }
            }
        }
        if (table == null) return false;

        // Remove all players from the table
        table.removeAllPlayers();
        table.cleanup();

        // Remove from database
        if (plugin.getDatabaseManager() != null) {
            String id = normLoc.getWorld().getName() + "_" + normLoc.getBlockX() + "_" + normLoc.getBlockY() + "_" + normLoc.getBlockZ();
            plugin.getDatabaseManager().deleteTable(id);
        }

        // Remove from config
        String worldName = normLoc.getWorld().getName();
        int centerX = normLoc.getBlockX();
        int centerY = normLoc.getBlockY();
        int centerZ = normLoc.getBlockZ();

        if (plugin.getConfig().contains("tables." + worldName)) {
            ConfigurationSection tablesSection = plugin.getConfig().getConfigurationSection("tables." + worldName);
            String key = centerX + "_" + centerY + "_" + centerZ;
            tablesSection.set(key, null);
            plugin.saveConfig();
        }

        return true;
    }

    /**
     * Find the nearest table to a player's location.
     * Each table is checked against its own max-join-distance setting.
     */
    public BlackjackTable findNearestTable(Location playerLoc) {
        double closestDistance = Double.MAX_VALUE;
        BlackjackTable closestTable = null;

        for (Map.Entry<Location, BlackjackTable> entry : tables.entrySet()) {
            Location tableLoc = entry.getKey();
            if (!tableLoc.getWorld().equals(playerLoc.getWorld())) {
                continue;
            }

            BlackjackTable table = entry.getValue();
            double distance = tableLoc.distance(playerLoc);
            double tableMaxDist = table.getSettings().getMaxJoinDistance(configManager);

            if (distance <= tableMaxDist && distance < closestDistance) {
                closestDistance = distance;
                closestTable = table;
            }
        }

        return closestTable;
    }

    /**
     * Get the table a player is currently at
     */
    public BlackjackTable getPlayerTable(Player player) {
        return playerTables.get(player);
    }

    /**
     * Set which table a player is at
     */
    public void setPlayerTable(Player player, BlackjackTable table) {
        if (table == null) {
            playerTables.remove(player);
        } else {
            playerTables.put(player, table);
        }
    }

    /**
     * Remove player from any table they're at
     */
    public void removePlayerFromTable(Player player) {
        removePlayerFromTable(player, configManager.getLeaveReason("normal"));
    }

    /**
     * Remove player from any table they're at with custom reason
     */
    public void removePlayerFromTable(Player player, String reason) {
        BlackjackTable table = playerTables.remove(player);
        if (table != null) {
            table.removePlayer(player, reason);
        }
    }

    /**
     * Get all tables
     */
    public Map<Location, BlackjackTable> getAllTables() {
        return tables;
    }

    /**
     * Persist updated settings for an existing table (used by /bj settable).
     */
    public void saveTableSettings(BlackjackTable table) {
        table.initChairs();
        saveTableToConfig(table.getCenterLocation(), table.getSettings());
    }

    /**
     * Cleanup all tables
     */
    public void cleanup() {
        for (BlackjackTable table : tables.values()) {
            table.cleanup();
        }
        tables.clear();
        playerTables.clear();
    }

    // -------------------------------------------------------------------------
    // Config persistence helpers
    // -------------------------------------------------------------------------

    private String buildTablePath(Location loc) {
        return "tables." + loc.getWorld().getName() + "."
                + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private void saveTableToConfig(Location loc, TableSettings settings) {
        if (plugin.getDatabaseManager() != null) {
            com.vortex.blackjack.database.TableRecord record = com.vortex.blackjack.database.TableRecord.fromLocationAndSettings(loc, settings);
            plugin.getDatabaseManager().saveTable(record);
        }

        String path = buildTablePath(loc);
        if (settings.getRawMinBet() == null && settings.getRawMaxBet() == null
                && settings.getRawMaxPlayers() == null
                && settings.getRawMaxJoinDistance() == null) {
            // No overrides — use compact boolean form
            plugin.getConfig().set(path, true);
        } else {
            // Store sub-keys; null values are omitted (Bukkit skips null sets)
            plugin.getConfig().set(path + ".min-bet",           settings.getRawMinBet());
            plugin.getConfig().set(path + ".max-bet",           settings.getRawMaxBet());
            plugin.getConfig().set(path + ".max-players",       settings.getRawMaxPlayers());
            plugin.getConfig().set(path + ".max-join-distance", settings.getRawMaxJoinDistance());
        }
        plugin.saveConfig();
    }

    private TableSettings loadSettingsFromConfig(ConfigurationSection worldSection, String locKey) {
        Object raw = worldSection.get(locKey);
        if (raw instanceof Boolean) {
            return new TableSettings(); // Legacy format — all fields use global defaults
        }
        ConfigurationSection sec = worldSection.getConfigurationSection(locKey);
        if (sec == null) {
            return new TableSettings();
        }
        Integer minBet   = sec.contains("min-bet")           ? sec.getInt("min-bet")             : null;
        Integer maxBet   = sec.contains("max-bet")           ? sec.getInt("max-bet")              : null;
        Integer maxP     = sec.contains("max-players")       ? sec.getInt("max-players")          : null;
        Double  maxDist  = sec.contains("max-join-distance") ? sec.getDouble("max-join-distance") : null;
        return new TableSettings(minBet, maxBet, maxP, maxDist);
    }
}
