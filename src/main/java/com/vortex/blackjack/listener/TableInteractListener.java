package com.vortex.blackjack.listener;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.chair.BlackjackChair;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

/**
 * Handles interactions with Blackjack tables and chairs:
 * - Right-clicking specific chairs sits the player directly into that seat.
 * - Right-clicking the table body or hologram sits the player in the nearest available chair.
 * - Pressing Shift (sneak/dismount) gracefully ejects the player and removes them from the table.
 * - Protects chair armor stands from being damaged or manipulated.
 */
public class TableInteractListener implements Listener {

    private final BlackjackPlugin plugin;
    private final TableManager tableManager;

    public TableInteractListener(BlackjackPlugin plugin, TableManager tableManager) {
        this.plugin = plugin;
        this.tableManager = tableManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleEntityInteraction(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        Set<String> tags = entity.getScoreboardTags();
        if (!tags.contains("blackjack-entity")) {
            return;
        }

        event.setCancelled(true);

        // If chair sitting is disabled in config, ignore click to sit
        if (!plugin.getConfigManager().isChairSittingEnabled()) {
            return;
        }

        // Disallow interacting while sneaking (used for other mechanics)
        if (player.isSneaking()) {
            return;
        }

        // Prevent joining if player is already inside a vehicle
        if (player.isInsideVehicle()) {
            player.sendMessage(plugin.getConfigManager().getMessage("inside-vehicle"));
            return;
        }

        // Extract table from tag
        BlackjackTable table = findTableFromTags(tags);
        if (table == null) {
            table = tableManager.findNearestTable(entity.getLocation());
        }

        if (table == null) {
            return;
        }

        // Check if player clicked a specific chair
        Integer seatIndex = findSeatIndexFromTags(tags);
        if (seatIndex != null) {
            table.addPlayer(player, seatIndex);
        } else {
            // Clicked table body, interaction hitbox, or hologram -> find nearest available seat
            table.addPlayer(player, -1);
        }
    }

    private static final Set<java.util.UUID> confirmedLeaves = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void markConfirmedLeave(java.util.UUID uuid) {
        confirmedLeaves.add(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof ArmorStand stand)) return;

        Set<String> tags = stand.getScoreboardTags();
        if (!tags.contains("blackjack-seat")) return;

        // If player already confirmed to leave via the warning GUI, allow clean dismount
        if (confirmedLeaves.remove(player.getUniqueId())) {
            return;
        }

        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            Integer bet = plugin.getPlayerBets().get(player);
            if (plugin.getConfigManager().isLeaveConfirmGuiEnabled() && bet != null && bet > 0) {
                // If LeaveConfirmGUI is already open, ignore repeated dismount events
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.vortex.blackjack.gui.LeaveConfirmGUI) {
                    event.setCancelled(true);
                    return;
                }

                // Player placed a bet! Cancel dismount to keep player seated and open confirmation GUI
                event.setCancelled(true);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        if (stand.isValid() && !stand.getPassengers().contains(player)) {
                            stand.addPassenger(player);
                        }
                        new com.vortex.blackjack.gui.LeaveConfirmGUI(plugin, table, player, bet).open();
                    }
                });
                return;
            }

            // Remove player cleanly from table
            table.removePlayer(player, plugin.getConfigManager().getLeaveReason("chair-dismount"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof com.vortex.blackjack.gui.LeaveConfirmGUI gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof com.vortex.blackjack.gui.LeaveConfirmGUI gui) {
            gui.handleClose(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof com.vortex.blackjack.gui.LeaveConfirmGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains("blackjack-entity")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains("blackjack-entity")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getScoreboardTags().contains("blackjack-entity")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            // If the teleport destination is far from the table, remove them
            if (event.getTo() != null && event.getTo().distance(table.getCenterLocation()) > table.getSettings().getMaxJoinDistance(plugin.getConfigManager())) {
                table.removePlayer(player, plugin.getConfigManager().getLeaveReason("teleported"));
            }
        }
    }

    private BlackjackTable findTableFromTags(Set<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith("blackjack-table:")) {
                String id = tag.substring("blackjack-table:".length());
                return tableManager.getTableById(id);
            }
        }
        return null;
    }

    private Integer findSeatIndexFromTags(Set<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith("blackjack-chair:")) {
                try {
                    return Integer.parseInt(tag.substring("blackjack-chair:".length()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
