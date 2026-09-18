package com.vortex.blackjack.chair;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a physical 3D casino chair using the EXACT 1:1 Roulette plugin architecture.
 *
 * In Roulette, each chair is composed of 3 model armor stands + 1 vehicle armor stand:
 * 1. CHAIR_1 (Base/Leg): Wood Planks block, small: true, marker: true, offset y: -0.69, yaw: yaw + 90.0 (perpendicular)
 * 2. CHAIR_2 (Seat): Wood Slab, small: false (normal size), marker: true, offset y: -0.95, yaw: yaw
 * 3. CHAIR_3 (Cushion): Carpet, small: false (normal size), marker: true, offset y: -0.64, yaw: yaw
 * 4. Seat Vehicle: Invisible armor stand, small: false, marker: false, offset y: -1.25 (-0.95 - 0.30 in 1.20.2+)
 */
public class BlackjackChair {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final int seatIndex;
    private final Location chairLocation;
    private final float yaw;

    // Visual ArmorStands matching Roulette 1:1
    private ArmorStand baseStand;    // Roulette CHAIR_1 (block, small: true, y: -0.69, yaw: yaw + 90.0)
    private ArmorStand slabStand;    // Roulette CHAIR_2 (slab, small: false, y: -0.95, yaw: yaw)
    private ArmorStand cushionStand; // Roulette CHAIR_3 (carpet, small: false, y: -0.64, yaw: yaw)

    // Dedicated invisible vehicle stand for sitting (Roulette spawnChairStand: y: -1.25)
    private ArmorStand seatStand;

    private final List<Entity> allEntities = new ArrayList<>();

    public BlackjackChair(BlackjackPlugin plugin, BlackjackTable table, int seatIndex, Location chairLocation, float yaw) {
        this.plugin = plugin;
        this.table = table;
        this.seatIndex = seatIndex;
        this.chairLocation = chairLocation.clone();
        this.yaw = yaw;
        this.chairLocation.setYaw(yaw);
        this.chairLocation.setPitch(0.0f);
    }

    /**
     * Spawns all visual parts of the chair and the vehicle seat matching Roulette 1:1.
     */
    public void spawn(Material woodPlanks, Material woodSlab, Material carpetMaterial) {
        destroy(); // Ensure any previous entities are cleared

        World world = chairLocation.getWorld();
        if (world == null) return;

        String tableTag = "blackjack-table:" + table.getTableId();
        String seatTag = "blackjack-chair:" + seatIndex;

        // 1. Layer 1: Base / Leg (Planks Block) - EXACT Roulette CHAIR_1
        // Offset y: -0.69, small: true, marker: true, yaw: yaw + 90.0 (perpendicular)
        float baseYaw = (yaw + 90.0f) % 360.0f;
        Location baseLoc = chairLocation.clone().add(0, -0.69, 0);
        baseLoc.setYaw(baseYaw);
        baseLoc.setPitch(0.0f);
        baseStand = world.spawn(baseLoc, ArmorStand.class, stand -> {
            configureArmorStand(stand, true, true); // small: true!
            stand.setHeadPose(new EulerAngle(0, 0, 0));
            EntityEquipment eq = stand.getEquipment();
            if (eq != null) eq.setHelmet(new ItemStack(woodPlanks));
            lockSlots(stand);
            stand.addScoreboardTag("blackjack-entity");
            stand.addScoreboardTag("blackjack-chair-visual");
            stand.addScoreboardTag(tableTag);
            stand.addScoreboardTag(seatTag);
        });
        allEntities.add(baseStand);

        // 2. Layer 2: Slab Seat (Wood Slab) - EXACT Roulette CHAIR_2
        // Offset y: -0.95, small: false (normal size), marker: true, yaw: yaw
        Location slabLoc = chairLocation.clone().add(0, -0.95, 0);
        slabLoc.setYaw(yaw);
        slabLoc.setPitch(0.0f);
        slabStand = world.spawn(slabLoc, ArmorStand.class, stand -> {
            configureArmorStand(stand, true, false); // small: false!
            stand.setHeadPose(new EulerAngle(0, 0, 0));
            EntityEquipment eq = stand.getEquipment();
            if (eq != null) eq.setHelmet(new ItemStack(woodSlab));
            lockSlots(stand);
            stand.addScoreboardTag("blackjack-entity");
            stand.addScoreboardTag("blackjack-chair-visual");
            stand.addScoreboardTag(tableTag);
            stand.addScoreboardTag(seatTag);
        });
        allEntities.add(slabStand);

        // 3. Layer 3: Carpet Cushion (Soft Seat Pad) - EXACT Roulette CHAIR_3
        // Offset y: -0.64, small: false (normal size in Roulette!), marker: true, yaw: yaw
        Location cushionLoc = chairLocation.clone().add(0, -0.64, 0);
        cushionLoc.setYaw(yaw);
        cushionLoc.setPitch(0.0f);
        cushionStand = world.spawn(cushionLoc, ArmorStand.class, stand -> {
            configureArmorStand(stand, true, false); // small: false!
            stand.setHeadPose(new EulerAngle(0, 0, 0));
            EntityEquipment eq = stand.getEquipment();
            if (eq != null) eq.setHelmet(new ItemStack(carpetMaterial));
            lockSlots(stand);
            stand.addScoreboardTag("blackjack-entity");
            stand.addScoreboardTag("blackjack-chair-visual");
            stand.addScoreboardTag(tableTag);
            stand.addScoreboardTag(seatTag);
        });
        allEntities.add(cushionStand);

        // 4. Dedicated Invisible Vehicle Stand for player sitting - EXACT Roulette spawnChairStand
        // Roulette offset: CHAIR_2 (-0.95) minus 0.30 in 1.20.2+ = -1.25
        Location seatLoc = chairLocation.clone().add(0, -1.25, 0);
        seatLoc.setYaw(yaw);
        seatLoc.setPitch(0.0f);
        seatStand = world.spawn(seatLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setMarker(false); // marker must be false to allow passengers!
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCustomName("blackjack_seat_" + seatIndex);
            stand.setCustomNameVisible(false);

            // Hide vehicle hearts in player HUD (exact Roulette attribute setting)
            try {
                org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH");
                org.bukkit.attribute.AttributeInstance ai = stand.getAttribute(attr);
                if (ai != null) ai.setBaseValue(1.0);
            } catch (Exception ignored) {}

            lockSlots(stand);
            stand.addScoreboardTag("blackjack-entity");
            stand.addScoreboardTag("blackjack-seat");
            stand.addScoreboardTag(tableTag);
            stand.addScoreboardTag(seatTag);
        });
        allEntities.add(seatStand);
    }

    private void configureArmorStand(ArmorStand stand, boolean invisible, boolean small) {
        stand.setInvisible(invisible);
        stand.setSmall(small);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setPersistent(false);
        stand.setMarker(true);
        stand.setCustomNameVisible(false);
    }

    /**
     * Locks all equipment slots on an armor stand to prevent manipulation (Roulette style).
     */
    public static void lockSlots(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType type : ArmorStand.LockType.values()) {
                stand.addEquipmentLock(slot, type);
            }
        }
    }

    /**
     * Checks whether a player is currently sitting in this chair.
     */
    public boolean isOccupied() {
        if (seatStand == null || !seatStand.isValid()) return false;
        return !seatStand.getPassengers().isEmpty();
    }

    /**
     * Gets the player currently sitting in this chair, or null if empty.
     */
    public Player getSittingPlayer() {
        if (seatStand == null || !seatStand.isValid()) return null;
        for (Entity passenger : seatStand.getPassengers()) {
            if (passenger instanceof Player p) return p;
        }
        return null;
    }

    /**
     * Sits a player in this chair (Roulette fixChairCameraAndSit style).
     * Fixes the player's camera angle to face the center of the table and plays the chair sound.
     */
    public boolean sit(Player player) {
        if (seatStand == null || !seatStand.isValid()) {
            return false;
        }

        if (isOccupied()) {
            return false;
        }

        // Align player camera facing table center (Roulette fixChairCamera style: seat stand + 0.25)
        Location sitTeleport = seatStand.getLocation().clone().add(0, 0.25, 0);
        sitTeleport.setYaw(yaw);
        sitTeleport.setPitch(0.0f);
        player.teleport(sitTeleport);

        // Mount the invisible seat ArmorStand
        seatStand.addPassenger(player);

        // Play swap/sit chair sound (Roulette style)
        Sound chairSound = plugin.getConfigManager().getChairSitSound();
        if (chairSound != null) {
            player.playSound(chairLocation, chairSound, 1.0f, 1.0f);
        }

        return true;
    }

    /**
     * Safely ejects the sitting player and teleports them behind the chair at floor level.
     */
    public void eject(Player player) {
        if (seatStand != null && seatStand.isValid()) {
            seatStand.removePassenger(player);
        }

        // Calculate safe landing spot behind the chair at floor level
        double rad = Math.toRadians(yaw);
        double backX = Math.sin(rad) * 1.0;
        double backZ = -Math.cos(rad) * 1.0;
        Location safeExit = chairLocation.clone().add(backX, 0.0, backZ);
        safeExit.setYaw(yaw);
        safeExit.setPitch(0.0f);

        // Small delay to let vehicle dismount packet settle
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(safeExit);
            }
        });
    }

    /**
     * Destroys all ArmorStands associated with this chair.
     */
    public void destroy() {
        for (Entity entity : allEntities) {
            if (entity != null && !entity.isDead()) {
                entity.eject();
                entity.remove();
            }
        }
        allEntities.clear();
        baseStand = null;
        slabStand = null;
        cushionStand = null;
        seatStand = null;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public Location getChairLocation() {
        return chairLocation;
    }

    public float getYaw() {
        return yaw;
    }

    public ArmorStand getSeatStand() {
        return seatStand;
    }

    public List<Entity> getAllEntities() {
        return allEntities;
    }
}
