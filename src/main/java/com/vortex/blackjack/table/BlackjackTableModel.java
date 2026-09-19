package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;

/**
 * 5x3 Chamfered/Trapezoid 3D Casino Table Model using BlockDisplay components.
 * Matches the user's casino blueprint:
 * - 5 blocks wide, 3 blocks deep.
 * - Flat 5-block dealer side (Z = -1.5).
 * - 45-degree chamfered corners on front-left and front-right edges.
 * - Sturdy pedestals and polished wooden railing.
 * - Interaction hitbox spanning the table surface.
 * - Legacy center hologram eliminated in favor of Croupier & private player displays.
 */
public class BlackjackTableModel {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final Location centerLocation;

    private final List<Entity> modelEntities = new ArrayList<>();
    private final List<BlockDisplay> feltDisplays = new ArrayList<>();
    private Interaction tableInteraction;

    public BlackjackTableModel(BlackjackPlugin plugin, BlackjackTable table, Location centerLocation) {
        this.plugin = plugin;
        this.table = table;
        this.centerLocation = TableManager.normalizeLocation(centerLocation);
    }

    /**
     * Spawns the 5x3 chamfered table structure.
     */
    public void spawn(Material woodPlanks, Material woodSlab, Material feltMaterial) {
        destroy();

        World world = centerLocation.getWorld();
        if (world == null) return;

        String tableTag = "blackjack-table:" + table.getTableId();

        // Keep the felt and wooden base on the same six-sided outline.
        for (Transformation part : TableSurfaceGeometry.rim()) {
            spawnPart(world, woodPlanks, part, tableTag);
        }
        for (Transformation part : TableSurfaceGeometry.felt()) {
            feltDisplays.add(spawnPart(world, feltMaterial, part, tableTag));
        }

        for (float x : new float[] {-1.4f, 1.4f}) {
            spawnPart(world, woodPlanks,
                    TableSurfaceGeometry.box(x, 0, -0.1f, 0.7f, 0.65f, 0.7f, 0), tableTag);
        }

        // Broad interaction hitbox covering the table surface.
        Location interLoc = centerLocation.clone().add(0, 0.4, 0);
        tableInteraction = world.spawn(interLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(4.8f);
            inter.setInteractionHeight(0.9f);
            inter.setPersistent(false);
            inter.addScoreboardTag("blackjack-entity");
            inter.addScoreboardTag("blackjack-table-hitbox");
            inter.addScoreboardTag(tableTag);
        });
        modelEntities.add(tableInteraction);
    }

    private BlockDisplay spawnPart(World world, Material material, Transformation transformation, String tableTag) {
        Location origin = centerLocation.clone();
        // Saved locations have yaw 180, but these parts already use world axes.
        origin.setYaw(0.0f);
        origin.setPitch(0.0f);
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, part -> {
            part.setBlock(material.createBlockData());
            part.setTransformation(transformation);
            part.setBillboard(Display.Billboard.FIXED);
            part.setPersistent(false);
            part.addScoreboardTag("blackjack-entity");
            part.addScoreboardTag("blackjack-table-model");
            part.addScoreboardTag(tableTag);
        });
        modelEntities.add(display);
        return display;
    }

    /**
     * Updates the felt material in real time without destroying the entire table.
     */
    public void updateFelt(Material newFelt) {
        for (BlockDisplay display : feltDisplays) {
            if (display != null && display.isValid()) {
                display.setBlock(newFelt.createBlockData());
            }
        }
    }

    /**
     * Center hologram is replaced with Croupier NPC score display and private player displays.
     */
    public void updateHologram() {
        // No-op
    }

    /**
     * Cleans up and removes all model entities.
     */
    public void destroy() {
        for (Entity entity : modelEntities) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        modelEntities.clear();
        feltDisplays.clear();
        tableInteraction = null;
    }

    public List<Entity> getModelEntities() {
        return modelEntities;
    }

    public Interaction getTableInteraction() {
        return tableInteraction;
    }

    public Location getCenterLocation() {
        return centerLocation;
    }
}
