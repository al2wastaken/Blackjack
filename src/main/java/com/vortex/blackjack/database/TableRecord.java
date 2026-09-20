package com.vortex.blackjack.database;

import com.vortex.blackjack.table.TableSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Data transfer object representing a persistent Blackjack table in the database.
 */
public class TableRecord {

    private final String id;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private Integer minBet;
    private Integer maxBet;
    private Integer maxPlayers;
    private Double maxJoinDistance;
    private String croupierSkin;
    private Integer countdownSeconds;
    private String feltColor;

    public TableRecord(String id, String world, double x, double y, double z, float yaw, float pitch,
                       Integer minBet, Integer maxBet, Integer maxPlayers, Double maxJoinDistance) {
        this(id, world, x, y, z, yaw, pitch, minBet, maxBet, maxPlayers, maxJoinDistance, "classic", 15, "GREEN_CONCRETE");
    }

    public TableRecord(String id, String world, double x, double y, double z, float yaw, float pitch,
                       Integer minBet, Integer maxBet, Integer maxPlayers, Double maxJoinDistance,
                       String croupierSkin, Integer countdownSeconds, String feltColor) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.maxPlayers = maxPlayers;
        this.maxJoinDistance = maxJoinDistance;
        this.croupierSkin = croupierSkin;
        this.countdownSeconds = countdownSeconds;
        this.feltColor = feltColor;
    }

    public static TableRecord fromLocationAndSettings(Location loc, TableSettings settings) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        String id = worldName + "_" + bx + "_" + by + "_" + bz;

        return new TableRecord(
                id,
                worldName,
                bx + 0.5,
                by,
                bz + 0.5,
                180.0f,
                0.0f,
                settings.getRawMinBet(),
                settings.getRawMaxBet(),
                settings.getRawMaxPlayers(),
                settings.getRawMaxJoinDistance(),
                settings.getCroupierSkin(),
                settings.getCountdownSeconds(),
                settings.getFeltMaterial().name()
        );
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public TableSettings toTableSettings() {
        TableSettings s = new TableSettings();
        s.setMinBet(minBet);
        s.setMaxBet(maxBet);
        s.setMaxPlayers(maxPlayers);
        s.setMaxJoinDistance(maxJoinDistance);
        s.setCroupierSkin(croupierSkin);
        s.setCountdownSeconds(countdownSeconds);
        if (feltColor != null) {
            try {
                String matName = feltColor.replace("_WOOL", "_CONCRETE");
                s.setFeltMaterial(org.bukkit.Material.valueOf(matName));
            } catch (Exception ignored) {}
        }
        return s;
    }

    public String getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Integer getMinBet() {
        return minBet;
    }

    public void setMinBet(Integer minBet) {
        this.minBet = minBet;
    }

    public Integer getMaxBet() {
        return maxBet;
    }

    public void setMaxBet(Integer maxBet) {
        this.maxBet = maxBet;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Double getMaxJoinDistance() {
        return maxJoinDistance;
    }

    public void setMaxJoinDistance(Double maxJoinDistance) {
        this.maxJoinDistance = maxJoinDistance;
    }

    public String getCroupierSkin() {
        return croupierSkin;
    }

    public void setCroupierSkin(String croupierSkin) {
        this.croupierSkin = croupierSkin;
    }

    public Integer getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(Integer countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
    }

    public String getFeltColor() {
        return feltColor;
    }

    public void setFeltColor(String feltColor) {
        this.feltColor = feltColor;
    }
}
