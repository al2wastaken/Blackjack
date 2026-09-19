package com.vortex.blackjack.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.gui.BettingGUI;
import com.vortex.blackjack.gui.SignGUI;
import com.vortex.blackjack.gui.TableSettingsGUI;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketEvents listener handling:
 * 1. PLAYER_INPUT & STEER_VEHICLE: Space key detection while seated to reopen BettingGUI.
 * 2. UPDATE_SIGN: Captures virtual SignGUI text input for custom bets.
 * 3. INTERACT_ENTITY: Shift + right-click on Croupier NPC to open admin settings.
 */
public class PacketEventsListener extends PacketListenerAbstract {

    private final BlackjackPlugin plugin;
    private final Map<UUID, Long> jumpCooldown = new ConcurrentHashMap<>();

    public PacketEventsListener(BlackjackPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        PacketTypeCommon type = event.getPacketType();

        // 1. Space key press to change bet (PLAYER_INPUT in 1.21.2+, STEER_VEHICLE in earlier)
        if (type == PacketType.Play.Client.PLAYER_INPUT) {
            WrapperPlayClientPlayerInput input = new WrapperPlayClientPlayerInput(event);
            if (input.isJump()) {
                handleSpaceKeyPress(player);
            }
        } else if (type == PacketType.Play.Client.STEER_VEHICLE) {
            WrapperPlayClientSteerVehicle steer = new WrapperPlayClientSteerVehicle(event);
            if (steer.isJump()) {
                handleSpaceKeyPress(player);
            }
        }
        // 2. Custom Bet Sign Submission
        else if (type == PacketType.Play.Client.UPDATE_SIGN) {
            WrapperPlayClientUpdateSign updateSign = new WrapperPlayClientUpdateSign(event);
            if (SignGUI.handleSignUpdate(player, updateSign)) {
                event.setCancelled(true);
            }
        }
        // 3. Right clicking Croupier NPC
        else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getHand() == InteractionHand.MAIN_HAND
                    && interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT) {
                handleEntityInteraction(player, interact.getEntityId());
            }
        }
    }

    private void handleSpaceKeyPress(Player player) {
        long now = System.currentTimeMillis();
        Long last = jumpCooldown.get(player.getUniqueId());
        if (last != null && (now - last) < 500) return; // 500ms debounce
        jumpCooldown.put(player.getUniqueId(), now);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            BlackjackTable table = plugin.getTableManager().getPlayerTable(player);
            if (table == null) return;

            // Only allow changing bet before cards are dealt
            if (table.isGameInProgress()) {
                player.sendMessage("§cOyun devam ederken bahsinizi değiştiremezsiniz!");
                return;
            }

            // Open BettingGUI
            new BettingGUI(plugin, table, player).open();
        });
    }

    private void handleEntityInteraction(Player player, int entityId) {
        if (!player.hasPermission("blackjack.admin") || !player.isSneaking()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            // Find table whose croupier matches entityId
            for (BlackjackTable table : plugin.getTableManager().getTables()) {
                if (table.getCroupierNPC() != null && table.getCroupierNPC().getEntityId() == entityId) {
                    new TableSettingsGUI(plugin, table, player).open();
                    return;
                }
            }
        });
    }
}
