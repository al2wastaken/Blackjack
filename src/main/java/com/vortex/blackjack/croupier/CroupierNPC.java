package com.vortex.blackjack.croupier;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.SkinSection;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.table.BlackjackTable;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a virtual Player-NPC Croupier powered by PacketEvents.
 * Features customizable skin, head rotation, arm swing deal animation,
 * and a floating TextDisplay showing dealer card value.
 */
public class CroupierNPC {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final int entityId;
    private final UUID npcUUID;
    private final Location location;
    private String skinTexture;

    private final Set<UUID> seeingPlayers = ConcurrentHashMap.newKeySet();
    private TextDisplay scoreDisplay;

    // Skin parts: Jacket, Left/Right Sleeve, Left/Right Pants, Hat (all except cape)
    private static final byte ALL_SKIN_LAYERS = SkinSection.JACKET
            .combine(SkinSection.LEFT_SLEEVE)
            .combine(SkinSection.RIGHT_SLEEVE)
            .combine(SkinSection.LEFT_PANTS)
            .combine(SkinSection.RIGHT_PANTS)
            .combine(SkinSection.HAT)
            .getMask();

    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ACTIONS = EnumSet.of(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
    );

    public CroupierNPC(BlackjackPlugin plugin, BlackjackTable table, Location location, String skinTexture) {
        this.plugin = plugin;
        this.table = table;
        this.entityId = SpigotReflectionUtil.generateEntityId();
        this.npcUUID = UUID.randomUUID();
        this.location = location.clone();
        this.skinTexture = skinTexture != null ? skinTexture : CroupierSkin.CLASSIC_TUXEDO;

        spawnScoreDisplay();
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getNpcUUID() {
        return npcUUID;
    }

    public Location getLocation() {
        return location;
    }

    public String getSkinTexture() {
        return skinTexture;
    }

    public void setSkinTexture(String skinTexture) {
        this.skinTexture = skinTexture;
        respawnForAll();
    }

    /**
     * Spawns the floating TextDisplay above croupier's head.
     */
    private void spawnScoreDisplay() {
        World world = location.getWorld();
        if (world == null) return;

        Location displayLoc = location.clone().add(0, 2.15, 0);
        scoreDisplay = world.spawn(displayLoc, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setDefaultBackground(true);
            display.setSeeThrough(false);
            display.setPersistent(false);
            display.setText(ChatColor.GOLD + "" + ChatColor.BOLD + "KRUPİYE");
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag("blackjack-croupier-text");
            display.addScoreboardTag("blackjack-table:" + table.getTableId());
        });
    }

    /**
     * Updates the text display above croupier's head.
     */
    public void updateScoreDisplay(String text) {
        if (scoreDisplay != null && scoreDisplay.isValid()) {
            scoreDisplay.setText(text);
        }
    }

    /**
     * Sends packets to spawn the Player NPC for a given player.
     */
    public void show(Player player) {
        if (!player.isOnline()) return;
        seeingPlayers.add(player.getUniqueId());

        UserProfile profile = new UserProfile(npcUUID, ChatColor.GOLD + "Krupiye");
        if (skinTexture != null && !skinTexture.isEmpty()) {
            profile.setTextureProperties(List.of(new TextureProperty("textures", skinTexture, null)));
        }

        // 1. Add to Player Info (Tab list)
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, false, 20, GameMode.SURVIVAL, null, null
        );
        WrapperPlayServerPlayerInfoUpdate addPacket = new WrapperPlayServerPlayerInfoUpdate(ADD_ACTIONS, info);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, addPacket);

        // 2. Spawn entity after 5 ticks so skin data is recognized
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !seeingPlayers.contains(player.getUniqueId())) return;

            com.github.retrooper.packetevents.protocol.world.Location at =
                    SpigotConversionUtil.fromBukkitLocation(location);

            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    entityId,
                    npcUUID,
                    EntityTypes.PLAYER,
                    at,
                    at.getYaw(),
                    0,
                    null
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);

            // 3. Set metadata (skin layers: hat, jacket, sleeves, pants)
            int skinLayerIndex = 17;
            List<EntityData<?>> metadataList = List.of(
                    new EntityData<>(skinLayerIndex, EntityDataTypes.BYTE, ALL_SKIN_LAYERS)
            );
            WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, metadataList);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);

            // 4. Set head look & rotation
            WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(entityId, location.getYaw());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLook);

            WrapperPlayServerEntityRelativeMoveAndRotation rot = new WrapperPlayServerEntityRelativeMoveAndRotation(
                    entityId, 0, 0, 0, location.getYaw(), location.getPitch(), true
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, rot);

            // 5. Remove from tab list after 40 ticks so player list is kept clean
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    WrapperPlayServerPlayerInfoRemove removePacket =
                            new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npcUUID));
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
                }
            }, 40L);
        }, 5L);
    }

    /**
     * Hides the NPC from a given player.
     */
    public void hide(Player player) {
        seeingPlayers.remove(player.getUniqueId());
        if (!player.isOnline()) return;

        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);

        WrapperPlayServerPlayerInfoRemove removePacket =
                new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npcUUID));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
    }

    /**
     * Plays the arm swing animation (card dealing gesture) for all seeing players.
     */
    public void playArmSwing() {
        WrapperPlayServerEntityAnimation anim = new WrapperPlayServerEntityAnimation(
                entityId,
                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
        );
        for (UUID uuid : seeingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, anim);
            }
        }
    }

    public void swingArm() {
        playArmSwing();
    }

    /**
     * Updates visibility for nearby players in range (up to 36 blocks).
     */
    public void checkVisibility(Player player) {
        World world = location.getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            if (seeingPlayers.contains(player.getUniqueId())) {
                hide(player);
            }
            return;
        }

        double distSq = location.distanceSquared(player.getLocation());
        if (distSq <= 36.0 * 36.0) {
            if (!seeingPlayers.contains(player.getUniqueId())) {
                show(player);
            }
        } else {
            if (seeingPlayers.contains(player.getUniqueId())) {
                hide(player);
            }
        }
    }

    public void updateAllNearby() {
        World world = location.getWorld();
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            checkVisibility(p);
        }
    }

    public void respawnForAll() {
        for (UUID uuid : seeingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                hide(p);
                show(p);
            }
        }
    }

    public void destroy() {
        for (UUID uuid : seeingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                hide(p);
            }
        }
        seeingPlayers.clear();

        if (scoreDisplay != null && !scoreDisplay.isDead()) {
            scoreDisplay.remove();
            scoreDisplay = null;
        }
    }
}
