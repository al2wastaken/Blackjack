package com.vortex.blackjack.gui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Virtual PacketEvents-powered Sign GUI for custom bet inputs.
 * Opens an in-game sign text editor without creating persistent world blocks.
 */
public class SignGUI {

    private static final Map<UUID, SignSession> activeSessions = new ConcurrentHashMap<>();

    public record SignSession(Location location, Consumer<String[]> callback) {}

    /**
     * Opens a virtual sign editor for the player.
     *
     * @param plugin The Blackjack plugin
     * @param player The player to open the sign for
     * @param callback Callback executed with the entered lines
     */
    public static void open(BlackjackPlugin plugin, Player player, Consumer<String[]> callback) {
        Location playerLoc = player.getLocation();
        // Position the virtual sign 2 blocks above or in front of the player
        Location signLoc = playerLoc.clone().add(0, 2, 0);

        activeSessions.put(player.getUniqueId(), new SignSession(signLoc, callback));

        // 1. Send fake block change to client as OAK_SIGN
        player.sendBlockChange(signLoc, Material.OAK_SIGN.createBlockData());

        // 2. Send OpenSignEditor packet
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Vector3i pos = new Vector3i(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ());
            WrapperPlayServerOpenSignEditor openPacket = new WrapperPlayServerOpenSignEditor(pos, true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, openPacket);
        }, 2L);
    }

    /**
     * Handles the sign update packet from PacketEvents.
     */
    public static boolean handleSignUpdate(Player player, WrapperPlayClientUpdateSign packet) {
        SignSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return false;

        // Reset the fake block back to world block
        Location loc = session.location();
        player.sendBlockChange(loc, loc.getBlock().getBlockData());

        // Get entered lines
        String[] lines = packet.getTextLines();

        // Run callback on Bukkit main thread
        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("Blackjack"), () -> {
            session.callback().accept(lines);
        });

        return true;
    }

    public static void clear(Player player) {
        SignSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            Location loc = session.location();
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }
}
