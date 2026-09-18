package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the 3D visual table model, table surface, and dynamic floating hologram.
 * Eliminates legacy world block destruction by rendering an entity-based casino table.
 */
public class BlackjackTableModel {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final Location centerLocation;

    private final List<Entity> modelEntities = new ArrayList<>();
    private TextDisplay hologramDisplay;
    private Interaction tableInteraction;

    public BlackjackTableModel(BlackjackPlugin plugin, BlackjackTable table, Location centerLocation) {
        this.plugin = plugin;
        this.table = table;
        this.centerLocation = TableManager.normalizeLocation(centerLocation);
    }

    /**
     * Spawns the 3D table structure using BlockDisplay and ArmorStand components,
     * along with an interaction hitbox and floating status hologram.
     */
    public void spawn(Material woodPlanks, Material woodSlab, Material feltMaterial) {
        destroy();

        World world = centerLocation.getWorld();
        if (world == null) return;

        String tableTag = "blackjack-table:" + table.getTableId();

        // 1. Central Table Felt (Green/Red Casino Surface)
        // Table top dimensions: 2.2m x 2.2m x 0.15m height at y + 0.75
        Location feltLoc = centerLocation.clone().add(0, 0.75, 0);
        BlockDisplay feltDisplay = world.spawn(feltLoc, BlockDisplay.class, display -> {
            display.setBlock(feltMaterial.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-1.1f, 0.0f, -1.1f),
                    new AxisAngle4f(),
                    new Vector3f(2.2f, 0.15f, 2.2f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(feltDisplay);

        // 2. Outer Wooden Rim / Railing (Slightly larger than felt for casino table look)
        Location rimLoc = centerLocation.clone().add(0, 0.65, 0);
        BlockDisplay rimDisplay = world.spawn(rimLoc, BlockDisplay.class, display -> {
            display.setBlock(woodPlanks.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-1.25f, 0.0f, -1.25f),
                    new AxisAngle4f(),
                    new Vector3f(2.5f, 0.15f, 2.5f),
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

        // 3. Central Pedestal / Base Pillar
        Location baseLoc = centerLocation.clone().add(0, 0.0, 0);
        BlockDisplay baseDisplay = world.spawn(baseLoc, BlockDisplay.class, display -> {
            display.setBlock(woodPlanks.createBlockData());
            Transformation t = new Transformation(
                    new Vector3f(-0.4f, 0.0f, -0.4f),
                    new AxisAngle4f(),
                    new Vector3f(0.8f, 0.65f, 0.8f),
                    new AxisAngle4f()
            );
            display.setTransformation(t);
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-table-model");
            display.addScoreboardTag(tableTag);
        });
        modelEntities.add(baseDisplay);

        // 4. Clickable Table Interaction Hitbox (lets player click table to join)
        Location interLoc = centerLocation.clone().add(0, 0.4, 0);
        tableInteraction = world.spawn(interLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(2.4f);
            inter.setInteractionHeight(0.9f);
            inter.setPersistent(false);
            inter.addScoreboardTag("blackjack-entity");
            inter.addScoreboardTag("blackjack-table-hitbox");
            inter.addScoreboardTag(tableTag);
        });
        modelEntities.add(tableInteraction);

        // 5. Floating Informational Hologram above the table
        if (plugin.getConfigManager().isTableHologramEnabled()) {
            Location holoLoc = centerLocation.clone().add(0, 1.85, 0);
            hologramDisplay = world.spawn(holoLoc, TextDisplay.class, text -> {
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setPersistent(false);
                text.setSeeThrough(false);
                text.setDefaultBackground(true);
                text.addScoreboardTag("blackjack-entity");
                text.addScoreboardTag("blackjack-hologram");
                text.addScoreboardTag(tableTag);
            });
            modelEntities.add(hologramDisplay);

            updateHologram();
        }
    }

    /**
     * Dynamically updates the floating hologram text with current table stats.
     */
    public void updateHologram() {
        if (hologramDisplay == null || !hologramDisplay.isValid()) return;

        int current = table.getPlayerCount();
        int max = table.getSettings().getMaxPlayers(plugin.getConfigManager());
        int minBet = table.getSettings().getMinBet(plugin.getConfigManager());
        int maxBet = table.getSettings().getMaxBet(plugin.getConfigManager());
        boolean inProgress = table.isGameInProgress();

        ConfigManager cfg = plugin.getConfigManager();
        String title = cfg.getTableHologramTitle();
        String status = inProgress ? cfg.getHologramStatusInProgress() : cfg.getHologramStatusReady();

        String text = title + "\n"
                + cfg.getHologramMinBet(minBet)
                + ChatColor.DARK_GRAY + " | "
                + cfg.getHologramMaxBet(maxBet) + "\n"
                + cfg.getHologramPlayers(current, max) + "\n"
                + cfg.getHologramHint() + "\n"
                + status;

        hologramDisplay.setText(text);
    }

    /**
     * Cleans up and deletes all model entities and the hologram.
     */
    public void destroy() {
        for (Entity entity : modelEntities) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        modelEntities.clear();
        hologramDisplay = null;
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
