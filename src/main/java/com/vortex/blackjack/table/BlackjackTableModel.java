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
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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

        // ---------------------------------------------------------------------
        // 1. Central Body Felt (3.0m wide, 2.7m deep, 0.15m thick) at Y + 0.75
        // ---------------------------------------------------------------------
        Location feltLoc = centerLocation.clone().add(0, 0.75, 0);
        BlockDisplay mainFelt = world.spawn(feltLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-1.5f, 0.0f, -1.35f),
                    new AxisAngle4f(),
                    new Vector3f(3.0f, 0.15f, 2.7f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(mainFelt);
        feltDisplays.add(mainFelt);

        // ---------------------------------------------------------------------
        // 2. Left Wing Felt (0.95m wide, 1.8m deep)
        // ---------------------------------------------------------------------
        BlockDisplay leftWing = world.spawn(feltLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-2.45f, 0.0f, -1.35f),
                    new AxisAngle4f(),
                    new Vector3f(0.95f, 0.15f, 1.8f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(leftWing);
        feltDisplays.add(leftWing);

        // ---------------------------------------------------------------------
        // 3. Right Wing Felt (0.95m wide, 1.8m deep)
        // ---------------------------------------------------------------------
        BlockDisplay rightWing = world.spawn(feltLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(1.5f, 0.0f, -1.35f),
                    new AxisAngle4f(),
                    new Vector3f(0.95f, 0.15f, 1.8f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(rightWing);
        feltDisplays.add(rightWing);

        // ---------------------------------------------------------------------
        // 4. Chamfered Front-Left Corner (Angled 45 degrees)
        // ---------------------------------------------------------------------
        Location leftChamferLoc = centerLocation.clone().add(-1.75, 0.75, 0.75);
        BlockDisplay leftChamfer = world.spawn(leftChamferLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-0.55f, 0.0f, -0.55f),
                    new AxisAngle4f((float) Math.toRadians(45.0), 0.0f, 1.0f, 0.0f),
                    new Vector3f(1.1f, 0.15f, 1.1f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(leftChamfer);
        feltDisplays.add(leftChamfer);

        // ---------------------------------------------------------------------
        // 5. Chamfered Front-Right Corner (Angled -45 degrees)
        // ---------------------------------------------------------------------
        Location rightChamferLoc = centerLocation.clone().add(1.75, 0.75, 0.75);
        BlockDisplay rightChamfer = world.spawn(rightChamferLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-0.55f, 0.0f, -0.55f),
                    new AxisAngle4f((float) Math.toRadians(-45.0), 0.0f, 1.0f, 0.0f),
                    new Vector3f(1.1f, 0.15f, 1.1f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(rightChamfer);
        feltDisplays.add(rightChamfer);

        // ---------------------------------------------------------------------
        // 6. Polished Wooden Railing / Base Border (Slightly lower at Y + 0.65)
        // ---------------------------------------------------------------------
        Location rimLoc = centerLocation.clone().add(0, 0.65, 0);
        BlockDisplay rimDisplay = world.spawn(rimLoc, BlockDisplay.class, display -> {
            display.setBlock(woodPlanks.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-2.55f, 0.0f, -1.45f),
                    new AxisAngle4f(),
                    new Vector3f(5.1f, 0.15f, 2.9f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(rimDisplay);

        // ---------------------------------------------------------------------
        // 7. Sturdy Casino Pedestals (2 Legs)
        // ---------------------------------------------------------------------
        Location leg1Loc = centerLocation.clone().add(-1.4, 0.0, -0.1);
        BlockDisplay leg1 = world.spawn(leg1Loc, BlockDisplay.class, display -> {
            display.setBlock(woodPlanks.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-0.35f, 0.0f, -0.35f),
                    new AxisAngle4f(),
                    new Vector3f(0.7f, 0.65f, 0.7f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(leg1);

        Location leg2Loc = centerLocation.clone().add(1.4, 0.0, -0.1);
        BlockDisplay leg2 = world.spawn(leg2Loc, BlockDisplay.class, display -> {
            display.setBlock(woodPlanks.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-0.35f, 0.0f, -0.35f),
                    new AxisAngle4f(),
                    new Vector3f(0.7f, 0.65f, 0.7f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(leg2);

        // ---------------------------------------------------------------------
        // 8. Broad Interaction Hitbox (covers the 5x3 table surface)
        // ---------------------------------------------------------------------
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
