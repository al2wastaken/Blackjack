package com.vortex.blackjack.table;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.chair.BlackjackChair;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.croupier.CroupierNPC;
import com.vortex.blackjack.croupier.CroupierSkin;
import com.vortex.blackjack.game.BlackjackEngine;
import com.vortex.blackjack.gui.BettingGUI;
import com.vortex.blackjack.gui.SignGUI;
import com.vortex.blackjack.model.Card;
import com.vortex.blackjack.model.Deck;
import com.vortex.blackjack.util.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single blackjack table with game logic, 5x3 physical table model,
 * PacketEvents Player-NPC Croupier, private hand TextDisplays, and 4-chair layout.
 */
public class BlackjackTable {
    // The felt is a BlockDisplay beginning at Y=0.75 with 0.15 blocks of height
    // (TableSurfaceGeometry.felt), so its top is Y=0.90 relative to centerLoc.
    // ItemDisplay card models need clear air above that surface to avoid being
    // clipped by the felt or the wooden rim.
    private static final double CARD_SURFACE_Y_OFFSET = 1.00;
    // Card displays are 0.35 blocks wide. Match that width exactly so cards
    // touch edge-to-edge without a visible gap or model overlap.
    private static final double PLAYER_CARD_SPACING = 0.30;
    private final BlackjackPlugin plugin;
    private final TableManager tableManager;
    private final ConfigManager configManager;
    private final ChatUtils chatUtils;
    private final BlackjackEngine gameEngine;
    private final Location centerLoc;
    private final TableSettings settings;
    private final String tableId;

    // 3D Visual Model & Chairs (Roulette architecture)
    private final BlackjackTableModel tableModel;
    private final List<BlackjackChair> chairs = new ArrayList<>();

    // PacketEvents Croupier Player NPC
    private CroupierNPC croupierNPC;

    // Countdown task & remaining seconds
    private BukkitTask countdownTask;
    private int countdownRemaining = 0;
    private BukkitTask turnTimeoutTask;
    private static final long TURN_TIMEOUT_TICKS = 10L * 20L;
    private static final int TURN_TIMEOUT_SECONDS = 10;
    private int turnSecondsRemaining;
    private final Map<UUID, BossBar> turnBossBars = new ConcurrentHashMap<>();

    // Game state
    private final List<Player> players = new ArrayList<>();
    private final Map<Player, List<Card>> playerHands = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerSeats = new ConcurrentHashMap<>();
    private final Set<Player> finishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<Player> doubleDownPlayers = ConcurrentHashMap.newKeySet();
    private boolean gameInProgress = false;
    private boolean settlingResults = false;
    // Becomes true with the first physical/virtual card dealt in a round.
    // Before that point players are still in the betting-selection phase.
    private boolean cardsHaveBeenDealt = false;
    private Player currentPlayer;
    private List<Card> dealerHand = new ArrayList<>();
    private Deck deck = new Deck();
    private final Map<Player, Integer> roundBets = new ConcurrentHashMap<>();

    // Display entities
    private final Map<Player, List<ItemDisplay>> playerCardDisplays = new ConcurrentHashMap<>();
    private final List<ItemDisplay> dealerCardDisplays = new ArrayList<>();
    private final Map<Player, TextDisplay> playerPrivateDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    // Auto-leave tracking
    private final Map<Player, Long> gameEndTimes = new ConcurrentHashMap<>();
    private BukkitTask autoLeaveTask;

    public BlackjackTable(BlackjackPlugin plugin, TableManager tableManager,
                          ConfigManager configManager, Location centerLoc,
                          TableSettings settings) {
        this.plugin = plugin;
        this.tableManager = tableManager;
        this.configManager = configManager;
        this.chatUtils = new ChatUtils(configManager);
        this.gameEngine = new BlackjackEngine();
        this.centerLoc = TableManager.normalizeLocation(centerLoc);
        this.settings = settings;
        this.tableId = this.centerLoc.getWorld().getName() + "_" + this.centerLoc.getBlockX() + "_" + this.centerLoc.getBlockY() + "_" + this.centerLoc.getBlockZ();
        purgeTrackedDisplays();

        // 1. Initialize 5x3 chamfered table model (BlockDisplay/Interaction)
        Material felt = settings.getFeltMaterial();
        this.tableModel = new BlackjackTableModel(plugin, this, this.centerLoc);
        this.tableModel.spawn(configManager.getWoodPlanks(), configManager.getWoodSlab(), felt != null ? felt : configManager.getFeltMaterial());

        // 2. Initialize 4 physical chairs matching the user's casino blueprint
        initChairs();

        // 3. Initialize PacketEvents Croupier NPC at (x=0, z=-1.8, yaw=0)
        Location croupierLoc = centerLoc.clone().add(0, 0, -1.8);
        croupierLoc.setYaw(0.0f);
        croupierLoc.setPitch(0.0f);
        String skinTexture = CroupierSkin.getPresetOrDefault(settings.getCroupierSkin());
        this.croupierNPC = new CroupierNPC(plugin, this, croupierLoc, skinTexture);
        updateCroupierIdleDisplay();
        this.croupierNPC.updateAllNearby();
    }

    public TableSettings getSettings() {
        return settings;
    }

    public String getTableId() {
        return tableId;
    }

    public BlackjackTableModel getTableModel() {
        return tableModel;
    }

    public List<BlackjackChair> getChairs() {
        return chairs;
    }

    /**
     * Initializes the 4 physical chairs matching the 5x3 chamfered casino blueprint.
     */
    public void initChairs() {
        for (BlackjackChair chair : chairs) {
            chair.destroy();
        }
        chairs.clear();

        double tableX = centerLoc.getX();
        double tableY = centerLoc.getY();
        double tableZ = centerLoc.getZ();

        // 4 Chairs matching the 5x3 chamfered casino layout:
        // Seat 0: Left Angled corner (x=-2.3, z=1.4, yaw=135°)
        chairs.add(new BlackjackChair(plugin, this, 0, new Location(centerLoc.getWorld(), tableX - 2.3, tableY, tableZ + 1.4), 135.0f));
        // Seat 1: Front Left (x=-0.8, z=2.0, yaw=180°) - matches card pivot X (-0.8)
        chairs.add(new BlackjackChair(plugin, this, 1, new Location(centerLoc.getWorld(), tableX - 0.8, tableY, tableZ + 2.0), 180.0f));
        // Seat 2: Front Right (x=0.8, z=2.0, yaw=180°) - matches card pivot X (+0.8)
        chairs.add(new BlackjackChair(plugin, this, 2, new Location(centerLoc.getWorld(), tableX + 0.8, tableY, tableZ + 2.0), 180.0f));
        // Seat 3: Right Angled corner (x=2.3, z=1.4, yaw=225°)
        chairs.add(new BlackjackChair(plugin, this, 3, new Location(centerLoc.getWorld(), tableX + 2.3, tableY, tableZ + 1.4), 225.0f));

        for (BlackjackChair chair : chairs) {
            chair.spawn(configManager.getWoodPlanks(), configManager.getWoodSlab(), configManager.getChairCushionMaterial());
        }
    }

    /**
     * Starts the 15-second in-seat countdown when players sit down.
     */
    public void startCountdown() {
        if (countdownTask != null || gameInProgress || settlingResults) return;

        countdownRemaining = settings.getCountdownSeconds();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (players.isEmpty()) {
                cancelCountdown();
                return;
            }

            // Send countdown actionbar and ticking sound to seated players
            for (Player p : players) {
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(configManager.getCountdownActionBarMessage(countdownRemaining)));
                if (countdownRemaining <= 5 && countdownRemaining > 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }

            if (countdownRemaining <= 0) {
                cancelCountdown();
                onCountdownComplete();
                return;
            }

            countdownRemaining--;
        }, 20L, 20L);
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        countdownRemaining = 0;
    }

    private void onCountdownComplete() {
        int minBet = settings.getMinBet(configManager);
        List<Player> toRemove = new ArrayList<>();

        for (Player p : new ArrayList<>(players)) {
            Integer bet = plugin.getPlayerBets().get(p);
            if (bet == null || bet <= 0) {
                // Player waited and hasn't placed a bet
                double balance = plugin.getEconomyProvider().getBalance(p.getUniqueId()).doubleValue();
                if (balance >= minBet) {
                    plugin.getEconomyProvider().subtract(p.getUniqueId(), BigDecimal.valueOf(minBet));
                    plugin.getPlayerBets().put(p, minBet);
                    roundBets.put(p, minBet);
                    p.sendMessage(configManager.formatMessage("table-events.auto-bet-countdown", "min_bet", minBet));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
                } else {
                    toRemove.add(p);
                }
            }
        }

        for (Player p : toRemove) {
            p.sendMessage(configManager.formatMessage("table-events.removed-cannot-afford-min", "min_bet", minBet));
            removePlayer(p, configManager.getLeaveReason("insufficient-funds"), false);
        }

        if (!players.isEmpty()) {
            startGame();
        }
    }
    
    /**
     * Add a player to this table, finding the nearest available chair.
     */
    public boolean addPlayer(Player player) {
        return addPlayer(player, -1);
    }

    /**
     * Add a player to this table in a specific preferred chair.
     * If preferredSeatNumber is -1, the closest unoccupied chair is selected.
     */
    public boolean addPlayer(Player player, int preferredSeatNumber) {
        synchronized (this) {
            if (players.contains(player)) {
                player.sendMessage(configManager.getMessage("already-at-table"));
                return false;
            }
            
            if (tableManager.getPlayerTable(player) != null) {
                player.sendMessage(configManager.getMessage("already-at-table"));
                return false;
            }

            if (player.isInsideVehicle()) {
                player.sendMessage(configManager.getMessage("inside-vehicle"));
                return false;
            }
            
            if (players.size() >= 4) {
                player.sendMessage(configManager.getMessage("table-full"));
                return false;
            }
            
            if (gameInProgress) {
                player.sendMessage(configManager.getMessage("game-in-progress"));
                return false;
            }

            if (settlingResults) {
                player.sendMessage(configManager.getMessage("betting-locked"));
                return false;
            }
            
            if (player.getLocation().distance(centerLoc) > settings.getMaxJoinDistance(configManager)) {
                player.sendMessage(configManager.getMessage("too-far"));
                return false;
            }
            
            int seatNumber;
            if (preferredSeatNumber >= 0 && preferredSeatNumber < chairs.size()) {
                BlackjackChair preferredChair = chairs.get(preferredSeatNumber);
                if (preferredChair.isOccupied()) {
                    player.sendMessage(configManager.getMessage("seat-taken"));
                    return false;
                }
                seatNumber = preferredSeatNumber;
            } else {
                seatNumber = getNearestAvailableSeat(player.getLocation());
                if (seatNumber == -1) {
                    player.sendMessage(configManager.getMessage("no-seats"));
                    return false;
                }
            }

            BlackjackChair chair = chairs.get(seatNumber);
            
            try {
                // Add player to table
                players.add(player);
                playerSeats.put(player, seatNumber);
                playerHands.put(player, new ArrayList<>());
                playerCardDisplays.put(player, new ArrayList<>());
                tableManager.setPlayerTable(player, this);
                
                // Sit player in chair (Roulette style - native ArmorStand sitting, camera alignment, sound)
                chair.sit(player);

                if (croupierNPC != null) {
                    croupierNPC.show(player);
                }
                
                broadcastTableMessage(configManager.formatMessage("player-joined-table", "player", player.getName()));
                updateCroupierIdleDisplay();

                // Immediately open BettingGUI for the newly seated player
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && players.contains(player)) {
                        new BettingGUI(plugin, this, player).open();
                    }
                }, 3L);

                // Start countdown if first player or not running
                if (!gameInProgress && countdownTask == null) {
                    startCountdown();
                }

                return true;
            } catch (Exception e) {
                // Cleanup on error
                players.remove(player);
                playerSeats.remove(player);
                playerHands.remove(player);
                playerCardDisplays.remove(player);
                tableManager.setPlayerTable(player, null);
                chair.eject(player);
                
                player.sendMessage(configManager.getMessage("join-error"));
                plugin.getLogger().severe("Error adding player to table: " + e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Remove a player from this table
     */
    public void removePlayer(Player player) {
        removePlayer(player, configManager.getLeaveReason("normal"), false);
    }
    
    /**
     * Remove a player from this table with custom reason
     */
    public void removePlayer(Player player, String reason) {
        removePlayer(player, reason, false);
    }

    /**
     * Remove a player from this table with custom reason and optional force-forfeit of active bet
     */
    public void removePlayer(Player player, String reason, boolean forceForfeit) {
        synchronized (this) {
            if (!players.contains(player)) return;
            int removedPlayerIndex = players.indexOf(player);
            boolean wasCurrentPlayer = player.equals(currentPlayer);
            
            Integer betAmount = plugin.getPlayerBets().get(player);

            // Safely eject player from chair
            Integer seatNumber = playerSeats.get(player);
            if (seatNumber != null && seatNumber < chairs.size()) {
                chairs.get(seatNumber).eject(player);
            }
            
            // Cleanup player data
            if (player.isOnline()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof BettingGUI) {
                    player.closeInventory();
                }
                SignGUI.clear(player);
            }
            players.remove(player);
            removeTurnBossBar(player);
            if (!gameInProgress && !settlingResults) {
                updateCroupierIdleDisplay();
            }
            playerSeats.remove(player);
            playerHands.remove(player);
            finishedPlayers.remove(player);
            doubleDownPlayers.remove(player);
            tableManager.setPlayerTable(player, null);
            
            // Handle bet refund vs forfeit:
            // Case 1: Dismount before game starts -> 100% full refund!
            if (!gameInProgress && !settlingResults && betAmount != null && betAmount > 0) {
                plugin.getPlayerBets().remove(player);
                roundBets.remove(player);
                plugin.getEconomyProvider().add(player.getUniqueId(), BigDecimal.valueOf(betAmount));
                player.sendMessage(configManager.formatMessage("left-table-bet-refunded", "amount", betAmount));
            }
            // Once the croupier begins dealing, an early departure always
            // forfeits the active bet. This intentionally overrides the
            // optional refund-on-leave setting for an already-started round.
            else if ((forceForfeit || cardsHaveBeenDealt || settlingResults) && betAmount != null && betAmount > 0) {
                plugin.getPlayerBets().remove(player);
                roundBets.remove(player);
                player.sendMessage(configManager.formatMessage("left-table-bet-forfeit", "amount", betAmount));
            }
            // Case 3: Mid-game leave with refund config setting
            else if (gameInProgress && configManager.shouldRefundOnLeave() && betAmount != null && betAmount > 0) {
                plugin.getPlayerBets().remove(player);
                roundBets.remove(player);
                plugin.getEconomyProvider().add(player.getUniqueId(), BigDecimal.valueOf(betAmount));
                player.sendMessage(configManager.formatMessage("left-table-bet-refunded", "amount", betAmount));
            }
            // Case 4: Mid-game leave without refund
            else if (gameInProgress && betAmount != null && betAmount > 0) {
                plugin.getPlayerBets().remove(player);
                roundBets.remove(player);
                player.sendMessage(configManager.formatMessage("left-table-bet-forfeit", "amount", betAmount));
            } else {
                if (betAmount != null && betAmount > 0) {
                    plugin.getPlayerBets().remove(player);
                    roundBets.remove(player);
                }
                player.sendMessage(configManager.getMessage("left-table"));
            }
            
            // Remove player's card displays
            List<ItemDisplay> cardDisplays = playerCardDisplays.remove(player);
            if (cardDisplays != null) {
                cardDisplays.forEach(this::removeTrackedDisplay);
            }

            // Remove player's private text display
            TextDisplay privDisplay = playerPrivateDisplays.remove(player);
            if (privDisplay != null && !privDisplay.isDead()) {
                privDisplay.remove();
            }
            
            // Handle game state
            if (players.isEmpty()) {
                cancelCountdown();
                cancelTurnTimeout();
                resetGameState();
                clearAllDisplays();
                resetCroupierLabel();
            } else if (gameInProgress && wasCurrentPlayer) {
                // The next seat shifts into the removed player's former index.
                // Start there so a departure never restarts the order at seat 0.
                selectNextTurn(removedPlayerIndex);
            }
        }
    }
    
    /**
     * Remove all players from the table
     */
    public void removeAllPlayers() {
        synchronized (this) {
            List<Player> playersToRemove = new ArrayList<>(players);
            for (Player player : playersToRemove) {
                removePlayer(player);
            }
        }
    }
    
    /**
     * Start a new game at this table
     */
    public void startGame() {
        synchronized (this) {
            if (gameInProgress) {
                broadcastTableMessage(configManager.getMessage("game-in-progress"));
                return;
            }
            
            if (players.isEmpty()) {
                broadcastTableMessage(configManager.getMessage("game-error-no-players"));
                return;
            }
            
            // Check if all players have placed bets
            java.util.Map<org.bukkit.entity.Player, Integer> playerBets = plugin.getPlayerBets();
            java.util.List<org.bukkit.entity.Player> playersWithoutBets = new java.util.ArrayList<>();
            
            for (org.bukkit.entity.Player player : players) {
                Integer bet = playerBets.get(player);
                if (bet == null || bet <= 0) {
                    playersWithoutBets.add(player);
                }
            }
            
            if (!playersWithoutBets.isEmpty()) {
                for (org.bukkit.entity.Player player : playersWithoutBets) {
                    player.sendMessage(configManager.getMessage("bet-required"));
                }
                broadcastTableMessage(configManager.getMessage("game-error-all-must-bet"));
                return;
            }
            
            // Initialize game
            gameInProgress = true;
            settlingResults = false;
            cardsHaveBeenDealt = false;
            deck = new Deck();
            clearAllDisplays();
            finishedPlayers.clear();
            doubleDownPlayers.clear();
            roundBets.clear();
            closeBettingGUIsForAllPlayers();
            
            // Prepare empty hands. The initial cards are then dealt one by one
            // so every card has a matching croupier animation and sound.
            for (Player player : players) {
                playerHands.put(player, new ArrayList<>());
                roundBets.put(player, plugin.getPlayerBets().getOrDefault(player, 0));
            }
            dealerHand = new ArrayList<>();
            dealInitialCardsSequentially(new ArrayList<>(players), 0, 0);
        }
    }

    /**
     * Deals two rounds in casino order: every seated player, then the croupier.
     * A small delay between cards lets the arm animation and the newly placed
     * card be visible instead of all displays appearing at once.
     */
    private void dealInitialCardsSequentially(List<Player> dealingOrder, int round, int playerIndex) {
        if (!gameInProgress || settlingResults) {
            return;
        }

        if (round >= 2) {
            beginFirstTurnAfterInitialDeal();
            return;
        }

        if (playerIndex < dealingOrder.size()) {
            Player player = dealingOrder.get(playerIndex);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!gameInProgress || settlingResults) return;
                if (players.contains(player)) {
                    cardsHaveBeenDealt = true;
                    closeBettingGUIsForAllPlayers();
                    List<Card> hand = playerHands.computeIfAbsent(player, ignored -> new ArrayList<>());
                    hand.add(deck.drawCard());
                    if (croupierNPC != null) {
                        croupierNPC.lookAt(player.getLocation());
                        croupierNPC.swingArm();
                    }
                    playCardSound(player.getLocation());
                    updateCardDisplays(player, hand);
                }
                dealInitialCardsSequentially(dealingOrder, round, playerIndex + 1);
            }, 12L);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameInProgress || settlingResults) return;
            cardsHaveBeenDealt = true;
            closeBettingGUIsForAllPlayers();
            dealerHand.add(deck.drawCard());
            if (croupierNPC != null) {
                croupierNPC.lookAt(centerLoc);
                croupierNPC.swingArm();
            }
            playCardSound(centerLoc);
            updateDealerDisplays();
            dealInitialCardsSequentially(dealingOrder, round + 1, 0);
        }, 12L);
    }

    private void beginFirstTurnAfterInitialDeal() {
        if (!gameInProgress || players.isEmpty()) {
            return;
        }

        // A natural Blackjack is final as soon as the two opening cards are
        // dealt. Mark it complete before assigning a turn, so the player
        // cannot hit, stand, or double down on a finished hand.
        for (Player player : players) {
            List<Card> hand = playerHands.get(player);
            if (hand != null && hand.size() == 2 && gameEngine.calculateHandValue(hand) == 21) {
                finishedPlayers.add(player);
            }
        }

        if (finishedPlayers.size() >= players.size()) {
            endGame();
            return;
        }

        currentPlayer = players.stream()
                .filter(player -> !finishedPlayers.contains(player))
                .findFirst()
                .orElse(null);
        if (currentPlayer == null) {
            endGame();
            return;
        }
        updateAllPlayerPrivateDisplays();
        broadcastTableMessage(configManager.formatMessage("game-started", "player", currentPlayer.getName()));
        chatUtils.sendGameActionBar(currentPlayer, true);
        startTurnTimeout(currentPlayer);
    }
    
    /**
     * Player hits (takes another card)
     */
    public void hit(Player player) {
        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) {
                return;
            }
            
            List<Card> hand = playerHands.get(player);
            Card newCard = deck.drawCard();
            hand.add(newCard);
            
            playCardSound(player.getLocation());
            updateCardDisplays(player, hand);
            
            int value = gameEngine.calculateHandValue(hand);
            // Don't send individual hand value - it's already shown in updateCardDisplays
            
            if (gameEngine.isBusted(hand)) {
                finishedPlayers.add(player);
                broadcastTableMessage(configManager.formatMessage("player-busts", "player", player.getName()));
                playLoseSound(player);
                nextTurn();
            } else if (value == 21) {
                finishedPlayers.add(player);
                broadcastTableMessage(configManager.formatMessage("player-hits-21", "player", player.getName()));
                playWinSound(player);
                nextTurn();
            } else {
                // Send action buttons again (no doubledown after hitting)
                chatUtils.sendGameActionBar(player, false);
                // A successful hit starts a fresh ten-second decision window.
                startTurnTimeout(player);
            }
        }
    }
    
    /**
     * Player stands (ends their turn)
     */
    public void stand(Player player) {
        synchronized (this) {
            if (!gameInProgress || !player.equals(currentPlayer)) {
                return;
            }
            
            finishedPlayers.add(player);
            int value = gameEngine.calculateHandValue(playerHands.get(player));
            broadcastTableMessage(configManager.formatMessage("player-stands", 
                "player", player.getName(), 
                "value", formatHandValue(value)));
            nextTurn();
        }
    }
    
    /**
     * Player doubles down (doubles bet, gets exactly one more card, then stands)
     */
    public void doubleDown(Player player) {
        synchronized (this) {
            if (!configManager.isDoubleDownEnabled()) {
                player.sendMessage(configManager.getMessage("double-down-disabled"));
                return;
            }

            if (!gameInProgress || !player.equals(currentPlayer)) {
                return;
            }
            
            // Check if double down is allowed (only on first 2 cards)
            List<Card> hand = playerHands.get(player);
            if (hand.size() != 2) {
                player.sendMessage(configManager.getMessage("double-down-first-two-cards"));
                return;
            }
            
            // Check if player has already doubled down
            if (doubleDownPlayers.contains(player)) {
                player.sendMessage(configManager.getMessage("double-down-already-used"));
                return;
            }
            
            // Check if player has sufficient funds
            Integer currentBet = plugin.getPlayerBets().get(player);
            if (currentBet == null) {
                currentBet = 0;
            }
            
            if (!plugin.getEconomyProvider().hasEnough(player.getUniqueId(), java.math.BigDecimal.valueOf(currentBet))) {
                player.sendMessage(configManager.getMessage("double-down-insufficient-funds"));
                return;
            }
            
            // Double the bet
            plugin.getEconomyProvider().subtract(player.getUniqueId(), java.math.BigDecimal.valueOf(currentBet));
            int doubledBet = currentBet * 2;
            plugin.getPlayerBets().put(player, doubledBet);
            // Payouts use roundBets, not the temporary betting map. Keep the
            // round total in sync so a 10 -> 20 double down pays from 20.
            roundBets.put(player, doubledBet);
            
            // Mark player as doubled down
            doubleDownPlayers.add(player);
            
            // Deal exactly one card
            Card newCard = deck.drawCard();
            hand.add(newCard);
            
            playCardSound(player.getLocation());
            updateCardDisplays(player, hand);
            
            int value = gameEngine.calculateHandValue(hand);
            broadcastTableMessage(configManager.formatMessage("player-doubles-down", 
                "player", player.getName(), 
                "value", formatHandValue(value)));
            
            // Player is automatically done after double down
            finishedPlayers.add(player);
            
            if (gameEngine.isBusted(hand)) {
                broadcastTableMessage(configManager.formatMessage("player-busts", "player", player.getName()));
                playLoseSound(player);
            } else if (value == 21) {
                broadcastTableMessage(configManager.formatMessage("player-hits-21", "player", player.getName()));
                playWinSound(player);
            }
            
            nextTurn();
        }
    }
    
    private void nextTurn() {
        cancelTurnTimeout();
        if (finishedPlayers.size() >= players.size()) {
            endGame();
            return;
        }

        int currentIndex = players.indexOf(currentPlayer);
        selectNextTurn(currentIndex + 1);
    }

    /** Selects the first active player at or after {@code nextIndex}. */
    private void selectNextTurn(int nextIndex) {
        cancelTurnTimeout();
        if (players.isEmpty() || finishedPlayers.size() >= players.size()) {
            endGame();
            return;
        }

        int attempts = 0;
        do {
            currentPlayer = players.get(Math.floorMod(nextIndex + attempts, players.size()));
            attempts++;
        } while (finishedPlayers.contains(currentPlayer));
        
        if (currentPlayer != null && !finishedPlayers.contains(currentPlayer)) {
            broadcastTableMessage(configManager.formatMessage("player-turn", "player", currentPlayer.getName()));
            
            List<Card> hand = playerHands.get(currentPlayer);
            boolean canDoubleDown = hand != null && hand.size() == 2 && !doubleDownPlayers.contains(currentPlayer);
            chatUtils.sendGameActionBar(currentPlayer, canDoubleDown);
            updateAllPlayerPrivateDisplays();
            startTurnTimeout(currentPlayer);
        } else {
            endGame();
        }
    }

    private void startTurnTimeout(Player player) {
        cancelTurnTimeout();
        turnSecondsRemaining = TURN_TIMEOUT_SECONDS;
        updatePlayerTurnBossBars(player);
        turnTimeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (BlackjackTable.this) {
                if (!gameInProgress || settlingResults || !player.equals(currentPlayer) || !players.contains(player)) {
                    return;
                }
                turnSecondsRemaining--;
                if (turnSecondsRemaining > 0) {
                    updatePlayerTurnBossBars(player);
                    return;
                }
                finishedPlayers.add(player);
                broadcastTableMessage(configManager.formatMessage("table-events.player-turn-timeout", "player", player.getName()));
                nextTurn();
            }
        }, 20L, 20L);
    }

    private void cancelTurnTimeout() {
        if (turnTimeoutTask != null) {
            turnTimeoutTask.cancel();
            turnTimeoutTask = null;
        }
    }

    private void updatePlayerTurnBossBars(Player activePlayer) {
        String title = configManager.getTurnBossBarTitle(activePlayer.getName(), turnSecondsRemaining);
        double progress = Math.max(0.0, Math.min(1.0, turnSecondsRemaining / (double) TURN_TIMEOUT_SECONDS));
        for (Player viewer : players) {
            if (!viewer.isOnline()) continue;
            BossBar bar = turnBossBars.computeIfAbsent(viewer.getUniqueId(), ignored ->
                    Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID));
            bar.setTitle(title);
            bar.setColor(viewer.equals(activePlayer) ? BarColor.GREEN : BarColor.RED);
            bar.setProgress(progress);
            if (!bar.getPlayers().contains(viewer)) bar.addPlayer(viewer);
            bar.setVisible(true);
        }
    }

    private void showCroupierTurnBossBars() {
        String title = configManager.getDealerDrawingBossBarTitle();
        for (Player viewer : players) {
            if (!viewer.isOnline()) continue;
            BossBar bar = turnBossBars.computeIfAbsent(viewer.getUniqueId(), ignored ->
                    Bukkit.createBossBar(title, BarColor.WHITE, BarStyle.SOLID));
            bar.setTitle(title);
            bar.setColor(BarColor.WHITE);
            bar.setProgress(1.0);
            if (!bar.getPlayers().contains(viewer)) bar.addPlayer(viewer);
            bar.setVisible(true);
        }
    }

    private void removeTurnBossBar(Player player) {
        BossBar bar = turnBossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    private void clearTurnBossBars() {
        for (BossBar bar : turnBossBars.values()) {
            bar.removeAll();
        }
        turnBossBars.clear();
    }
    
    private void endGame() {
        synchronized (this) {
            if (!gameInProgress || settlingResults) return;
            cancelTurnTimeout();
            
            gameInProgress = false;
            settlingResults = true;
            showCroupierTurnBossBars();
            
            // Remove turn hints from player displays
            updateAllPlayerPrivateDisplays();
            
            boolean anyValidPlayers = players.stream()
                .anyMatch(p -> !gameEngine.isBusted(playerHands.get(p)));
            
            // STEP 1: Reveal dealer hole card with Croupier arm swing and sound after 10 ticks
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (croupierNPC != null) {
                    croupierNPC.swingArm();
                }
                playCardSound(centerLoc);
                updateDealerDisplays();

                int initialVal = gameEngine.calculateHandValue(dealerHand);
                broadcastTableMessage(configManager.formatMessage("table-events.dealer-initial-hand", "hand", formatHand(dealerHand), "value", initialVal));

                // STEP 2: Cinematic paced dealer draw loop (1.5s per card)
                runDealerDrawStep(anyValidPlayers);
            }, 10L);
        }
    }

    private void runDealerDrawStep(boolean anyValidPlayers) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (anyValidPlayers && gameEngine.dealerShouldHit(dealerHand, configManager.shouldHitSoft17())) {
                Card newCard = deck.drawCard();
                dealerHand.add(newCard);

                if (croupierNPC != null) {
                    croupierNPC.swingArm();
                }
                playCardSound(centerLoc);
                updateDealerDisplays();

                int currentVal = gameEngine.calculateHandValue(dealerHand);
                broadcastTableMessage(configManager.formatMessage("table-events.dealer-draw-card", "card", formatCard(newCard), "value", currentVal));

                // Wait 1.5 seconds (30 ticks) before next card or finish check
                runDealerDrawStep(anyValidPlayers);
            } else {
                // Dealer turn finished!
                int dealerValue = gameEngine.calculateHandValue(dealerHand);
                updateDealerDisplays();

                broadcastTableMessage(configManager.formatDealerHandBroadcast(formatHand(dealerHand), formatHandValue(dealerValue)));

                // Settle payouts after 1 second
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    finishRoundAndPayouts(dealerValue);
                }, 20L);
            }
        }, 30L); // 30 ticks = 1.5s delay
    }

    private void finishRoundAndPayouts(int dealerValue) {
        synchronized (this) {
            for (Player player : new ArrayList<>(players)) {
                if (player.isOnline()) {
                    handlePayout(player, dealerValue);
                } else {
                    removePlayer(player);
                }
            }

            resetGameState();
            settlingResults = false;
            roundBets.clear();
            clearTurnBossBars();

            updateCroupierIdleDisplay();

            if (!players.isEmpty()) {
                broadcastTableMessage(configManager.getMessage("game-ended"));
                // Automatically prompt and start countdown for next round if players remain seated
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!gameInProgress && !settlingResults && !players.isEmpty() && countdownTask == null) {
                        for (Player p : players) {
                            p.sendMessage(configManager.getMessage("table-events.round-preparing-hint"));
                            if (p.isOnline() && players.contains(p)) {
                                new BettingGUI(plugin, this, p).open();
                            }
                        }
                        startCountdown();
                    }
                }, 60L);
            }
        }
    }

    /**
     * Closes the betting GUI and cancels sign editor sessions for all seated players.
     * Prevents players from modifying bets after cards begin dealing or game starts.
     */
    public void closeBettingGUIsForAllPlayers() {
        for (Player p : players) {
            if (p != null && p.isOnline()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof BettingGUI) {
                    p.closeInventory();
                    p.sendMessage(configManager.getMessage("betting-gui.messages.game-in-progress"));
                }
                SignGUI.clear(p);
            }
        }
    }
    
    private void resetGameState() {
        cancelTurnTimeout();
        gameInProgress = false;
        cardsHaveBeenDealt = false;
        currentPlayer = null;
        finishedPlayers.clear();
        doubleDownPlayers.clear();
        playerHands.clear();
        dealerHand.clear();
        deck = new Deck();
    }

    private void resetCroupierLabel() {
        updateCroupierIdleDisplay();
    }

    private void updateCroupierIdleDisplay() {
        if (croupierNPC == null) return;
        int capacity = chairs.isEmpty() ? 4 : chairs.size();
        List<String> rules = configManager.getHologramRules(players.size(), capacity);
        croupierNPC.updateScoreDisplay(String.join("\n", rules.get(0), rules.get(1), rules.get(2), "", rules.get(3)));
    }
    
    private void handlePayout(Player player, int dealerValue) {
        List<Card> playerHand = playerHands.get(player);
        BlackjackEngine.GameResult result = gameEngine.determineResult(playerHand, dealerHand);
        
        // Get the player's bet amount
        Integer betAmount = roundBets.get(player);
        if (betAmount == null) {
            betAmount = 0;
        }
        
        switch (result) {
            case PLAYER_BLACKJACK:
                // Blackjack pays 3:2
                int blackjackPayout = (int) (betAmount * 2.5); // bet + 1.5x bet = 2.5x bet
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(blackjackPayout));
                broadcastTableMessage(configManager.formatMessage("player-blackjack", 
                    "player", player.getName(), 
                    "payout", String.valueOf(blackjackPayout)));
                playWinSound(player);
                updatePlayerStats(player, true, (double) blackjackPayout);
                break;
            case PLAYER_WIN:
            case DEALER_BUST:
                // Regular win pays 2:1 (bet back + equal amount)
                int winPayout = betAmount * 2;
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(winPayout));
                broadcastTableMessage(configManager.formatMessage("player-wins", 
                    "player", player.getName(), 
                    "payout", String.valueOf(winPayout)));
                playWinSound(player);
                updatePlayerStats(player, true, (double) betAmount);
                break;
            case DEALER_WIN:
            case DEALER_BLACKJACK:
            case PLAYER_BUST:
                // Player loses their bet (already taken when bet was placed)
                broadcastTableMessage(configManager.formatMessage("player-loses", 
                    "player", player.getName(), 
                    "amount", String.valueOf(betAmount)));
                playLoseSound(player);
                updatePlayerStats(player, false, (double) -betAmount);
                break;
            case PUSH:
                // Push - return bet to player
                plugin.getEconomyProvider().add(player.getUniqueId(), java.math.BigDecimal.valueOf(betAmount));
                broadcastTableMessage(configManager.formatMessage("player-push", 
                    "player", player.getName(), 
                    "amount", String.valueOf(betAmount)));
                player.playSound(player.getLocation(), configManager.getPushSound(), 1.0F, 1.0F);
                updatePlayerStats(player, null, 0.0); // Push doesn't count as win or loss
                break;
        }
        
        // Clear the bet
        plugin.getPlayerBets().remove(player);
    }
    
    private void updatePlayerStats(Player player, Boolean won, double winnings) {
        if (!configManager.isStatsTrackerEnabled()) {
            return;
        }

        com.vortex.blackjack.model.PlayerStats stats = plugin.getPlayerStats().get(player.getUniqueId());
        if (stats == null) {
            stats = new com.vortex.blackjack.model.PlayerStats();
            plugin.getPlayerStats().put(player.getUniqueId(), stats);
        }
        
        if (won == null) {
            // Push - use the increment method
            stats.incrementPushes();
        } else if (won) {
            // Win - use the increment method which also handles streaks
            stats.incrementWins();
            stats.addWinnings(winnings);
            
            // Check for blackjack
            List<Card> playerHand = playerHands.get(player);
            if (playerHand.size() == 2 && gameEngine.calculateHandValue(playerHand) == 21) {
                stats.incrementBlackjacks();
            }
        } else {
            // Loss - use the increment method which also handles streaks
            stats.incrementLosses();
            stats.addWinnings(winnings); // winnings will be negative
            
            // Check for bust
            List<Card> playerHand = playerHands.get(player);
            if (gameEngine.calculateHandValue(playerHand) > 21) {
                stats.incrementBusts();
            }
        }
    }
    
    public int getNearestAvailableSeat(Location loc) {
        double closestDist = Double.MAX_VALUE;
        int closestSeat = -1;

        for (int i = 0; i < chairs.size(); i++) {
            BlackjackChair chair = chairs.get(i);
            if (!chair.isOccupied()) {
                double dist = chair.getChairLocation().distanceSquared(loc);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestSeat = i;
                }
            }
        }
        return closestSeat;
    }

    private int getNextAvailableSeatNumber() {
        Set<Integer> takenSeats = new HashSet<>(playerSeats.values());
        for (int i = 0; i < configManager.getMaxPlayers(); i++) {
            if (!takenSeats.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public Integer getPlayerSeat(Player player) {
        return playerSeats.get(player);
    }

    public BlackjackChair getChairForPlayer(Player player) {
        Integer seat = playerSeats.get(player);
        if (seat != null && seat >= 0 && seat < chairs.size()) {
            return chairs.get(seat);
        }
        return null;
    }
    
    public Location getSeatLocation(int seatNumber) {
        if (seatNumber >= 0 && seatNumber < chairs.size()) {
            Location loc = chairs.get(seatNumber).getChairLocation().clone();
            loc.setY(centerLoc.getY());
            return loc;
        }
        switch (seatNumber) {
            case 0:
                return centerLoc.clone().add(1.8, 0.0, 0.0);
            case 1:
                return centerLoc.clone().add(0.0, 0.0, 1.8);
            case 2:
                return centerLoc.clone().add(-1.8, 0.0, 0.0);
            case 3:
                return centerLoc.clone().add(0.0, 0.0, -1.8);
            default:
                return null;
        }
    }
    
    private Transformation createCardTransformation(boolean isDealer, int seatNumber) {
        float xRotation = (float) (Math.PI / 2); // Lay flat on the table
        float zRotation = cardRotation(isDealer, seatNumber);

        return new Transformation(
            new Vector3f(0.0f, 0.0f, 0.0f),
            new AxisAngle4f(xRotation, 1.0f, 0.0f, 0.0f),
            new Vector3f(0.35f, 0.35f, 0.35f),
            new AxisAngle4f(zRotation, 0.0f, 0.0f, 1.0f)
        );
    }

    private float cardRotation(boolean isDealer, int seatNumber) {
        if (isDealer) {
            return 0.0f; // Facing players (+Z)
        }
        return switch (seatNumber) {
            // Corner players face the table from the opposite direction, so
            // their cards need the diagonal angle plus 180 degrees.
            case 0 -> (float) Math.toRadians(225.0);
            case 1, 2 -> (float) Math.PI;
            case 3 -> (float) Math.toRadians(135.0);
            default -> (float) Math.PI;
        };
    }
    
    private ItemDisplay createCardDisplay(Location loc, Card card, boolean isDealer, int seatNumber) {
        World world = loc.getWorld();
        Location displayLoc = loc.clone();
        return world.spawn(displayLoc, ItemDisplay.class, display -> {
            if (card != null) {
                String cardIdentifier = card.getCardIdentifier();
                ItemStack cardItem = new ItemStack(Material.CLOCK);
                ItemMeta meta = cardItem.getItemMeta();
                meta.setItemModel(new NamespacedKey("playing_cards", "card/" + cardIdentifier.toLowerCase()));
                cardItem.setItemMeta(meta);
                display.setItemStack(cardItem);
            } else {
                ItemStack cardBack = new ItemStack(Material.CLOCK);
                ItemMeta meta = cardBack.getItemMeta();
                meta.setItemModel(new NamespacedKey("playing_cards", "card/back"));
                cardBack.setItemMeta(meta);
                display.setItemStack(cardBack);
            }

            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.addScoreboardTag("blackjack-card");
            display.addScoreboardTag("blackjack-entity");
            display.addScoreboardTag(getTableDisplayTag());
            Transformation transform = createCardTransformation(isDealer, seatNumber);
            display.setTransformation(transform);
        });
    }
    
    private String getCardIdentifier(Card card) {
        String suit = switch (card.getSuit()) {
            case "♠" -> "s";
            case "♥" -> "h";
            case "♦" -> "d";
            case "♣" -> "c";
            default -> throw new IllegalArgumentException("Invalid suit: " + card.getSuit());
        };
        
        String rank = switch (card.getRank()) {
            case "A" -> "1";
            case "J" -> "j";
            case "Q" -> "q";
            case "K" -> "k";
            default -> card.getRank().toLowerCase();
        };
        
        return suit + rank;
    }
    
    private void sendPlayerMessage(Player player, String message) {
        // Always use compact mode - no config needed
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);
        
        // Bypass cooldown for critical messages (payouts, results, dealer final hand, doubledown)
        boolean isCriticalMessage = message.contains("WINS!") || message.contains("KAZANDI!") || 
                                  message.contains("BLACKJACK!") || 
                                  message.contains("loses") || message.contains("kaybetti") || 
                                  message.contains("PUSH") || message.contains("BERABERE") ||
                                  message.contains("DOUBLES DOWN") || message.contains("İKİYE KATLADI") ||
                                  (message.startsWith("Dealer: ") || message.startsWith("Kasa: ")) && message.contains("|");
        
        // Only send if it's been more than 1.5 seconds since last message, OR if it's a critical message
        if (isCriticalMessage || lastTime == null || currentTime - lastTime > 1500) {
            // Check if message is already formatted (contains color codes or special characters)
            if (message.contains("§") || message.contains("&") || isCriticalMessage) {
                // Game notifications belong in the action bar, not chat.
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(message));
            } else {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                configManager.formatMessage("table-message-broadcast", "message", message)));
            }
            lastMessageTime.put(playerId, currentTime);
        }
    }
    
    private void broadcastTableMessage(String message) {
        // Send to all players at the table with spam reduction
        for (Map.Entry<Player, Integer> entry : playerSeats.entrySet()) {
            Player player = entry.getKey();
            if (player != null && player.isOnline()) {
                sendPlayerMessage(player, message);
            }
        }
    }
    
    private String formatHand(List<Card> hand) {
        StringBuilder handStr = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0) handStr.append(" ");
            handStr.append(formatCard(hand.get(i)));
        }
        return handStr.toString();
    }
    
    private String formatCard(Card card) {
        ChatColor suitColor;
        String suit = card.getSuit();
        
        // Color code by suit
        switch (suit) {
            case "♥", "♦" -> suitColor = ChatColor.RED;           // Hearts and Diamonds = Red
            case "♠", "♣" -> suitColor = ChatColor.DARK_GRAY;     // Spades and Clubs = Dark Gray
            default -> suitColor = ChatColor.WHITE;
        }
        
        return suitColor + card.getRank() + suit + ChatColor.RESET;
    }
    
    private String formatHandValue(int value) {
        ChatColor valueColor;
        if (value == 21) {
            valueColor = ChatColor.GOLD;          // 21 = Gold
        } else if (value > 21) {
            valueColor = ChatColor.RED;           // Bust = Red  
        } else if (value >= 18) {
            valueColor = ChatColor.GREEN;         // Good hand = Green
        } else {
            valueColor = ChatColor.YELLOW;        // Normal = Yellow
        }
        
        return "" + ChatColor.BOLD + valueColor + "Value: " + value + ChatColor.RESET;
    }
    
    public void broadcastToTable(String message) {
        broadcastTableMessage(message);
    }
    
    private void playCardSound(Location loc) {
        if (configManager.areSoundsEnabled()) {
            loc.getWorld().playSound(loc, configManager.getCardDealSound(), 
                configManager.getCardDealVolume(), configManager.getCardDealPitch());
        }
    }
    
    private void playWinSound(Player player) {
        if (configManager.areSoundsEnabled()) {
            player.playSound(player.getLocation(), configManager.getWinSound(), 1.0F, 1.0F);
        }
        
        if (configManager.areParticlesEnabled()) {
            player.spawnParticle(configManager.getWinParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5);
        }
    }
    
    private void playLoseSound(Player player) {
        if (configManager.areSoundsEnabled()) {
            player.playSound(player.getLocation(), configManager.getLoseSound(), 1.0F, 1.0F);
        }
        
        if (configManager.areParticlesEnabled()) {
            player.spawnParticle(configManager.getLoseParticle(), 
                player.getLocation().add(0.0, 2.0, 0.0), 10, 0.5, 0.5, 0.5);
        }
    }

    private String getTableDisplayTag() {
        return "blackjack-table:" + centerLoc.getWorld().getName() + ":" + centerLoc.getBlockX() + ":" + centerLoc.getBlockY() + ":" + centerLoc.getBlockZ();
    }

    private void removeTrackedDisplay(ItemDisplay display) {
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private void purgeTrackedDisplays() {
        if (centerLoc.getWorld() == null) {
            return;
        }

        String tableTag = getTableDisplayTag();
        for (Entity entity : centerLoc.getWorld().getNearbyEntities(centerLoc, 8.0, 4.0, 8.0, entity ->
            entity.getScoreboardTags().contains("blackjack-card") &&
            entity.getScoreboardTags().contains(tableTag))) {
            entity.remove();
        }
    }
    
    public Location getPlayerCardBaseLocation(int seatNumber) {
        double tableX = centerLoc.getX();
        double tableY = centerLoc.getY() + CARD_SURFACE_Y_OFFSET;
        double tableZ = centerLoc.getZ();
        return switch (seatNumber) {
            // Pull corner groups diagonally toward the upper table edge.
            case 0 -> new Location(centerLoc.getWorld(), tableX - 1.90, tableY, tableZ + 0.55);
            case 1 -> new Location(centerLoc.getWorld(), tableX - 0.8, tableY, tableZ + 1.05);
            case 2 -> new Location(centerLoc.getWorld(), tableX + 0.8, tableY, tableZ + 1.05);
            case 3 -> new Location(centerLoc.getWorld(), tableX + 1.90, tableY, tableZ + 0.55);
            default -> new Location(centerLoc.getWorld(), tableX, tableY, tableZ + 1.05);
        };
    }

    private void updateCardDisplays(Player player, List<Card> hand) {
        Integer seatNumber = playerSeats.get(player);
        if (seatNumber == null) return;

        List<ItemDisplay> oldDisplays = playerCardDisplays.get(player);
        if (oldDisplays != null) {
            for (ItemDisplay display : oldDisplays) {
                removeTrackedDisplay(display);
            }
            oldDisplays.clear();
        }

        playerCardDisplays.putIfAbsent(player, new ArrayList<>());
        Location baseLoc = getPlayerCardBaseLocation(seatNumber);
        double startOffset = -((hand.size() - 1) * PLAYER_CARD_SPACING) / 2.0;
        // The card's local X axis is its short, side-to-side edge after it is
        // laid flat. Spread cards along that axis. The old corner-specific
        // offsets followed the card's long axis, so cards at seats 0 and 4
        // appeared stacked and skewed into one another.
        double rotation = cardRotation(false, seatNumber);

        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            double offset = startOffset + i * PLAYER_CARD_SPACING;
            Location spawnLoc = baseLoc.clone().add(
                    offset * Math.cos(rotation), 0, offset * Math.sin(rotation));

            ItemDisplay display = createCardDisplay(spawnLoc, card, false, seatNumber);
            playerCardDisplays.get(player).add(display);
        }

        updatePlayerPrivateDisplay(player, hand);
    }

    public void updatePlayerPrivateDisplay(Player player, List<Card> hand) {
        if (!player.isOnline()) return;
        Integer seatNumber = playerSeats.get(player);
        if (seatNumber == null) return;

        Location cardBase = getPlayerCardBaseLocation(seatNumber);
        Location textLoc = cardBase.clone().add(0, 0.35, 0);

        TextDisplay display = playerPrivateDisplays.get(player);
        if (display == null || display.isDead() || !display.isValid()) {
            display = centerLoc.getWorld().spawn(textLoc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(), new Vector3f(0.2f, 0.2f, 0.2f), new AxisAngle4f()));
                d.setPersistent(false);
                d.addScoreboardTag("blackjack-entity");
                d.addScoreboardTag(getTableDisplayTag());
            });
            playerPrivateDisplays.put(player, display);
        }

        int val = gameEngine.calculateHandValue(hand);
        boolean isBusted = gameEngine.isBusted(hand);
        boolean isBlackjack = (val == 21 && hand.size() == 2);
        String text = configManager.formatHandDisplay(val, isBusted, isBlackjack);

        if (gameInProgress && player.equals(currentPlayer)) {
            boolean canDouble = hand.size() == 2 && !doubleDownPlayers.contains(player) && configManager.isDoubleDownEnabled();
            text += "\n" + configManager.getDeskControls(canDouble);
        }

        display.setText(text);

        TextDisplay finalDisplay = display;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hideEntity(plugin, finalDisplay);
            } else {
                online.showEntity(plugin, finalDisplay);
            }
        }
    }

    public void updateAllPlayerPrivateDisplays() {
        for (Player p : players) {
            List<Card> hand = playerHands.get(p);
            if (hand != null && !hand.isEmpty()) {
                updatePlayerPrivateDisplay(p, hand);
            }
        }
    }

    private void updateDealerDisplays() {
        for (ItemDisplay display : dealerCardDisplays) {
            removeTrackedDisplay(display);
        }
        dealerCardDisplays.clear();

        if (dealerHand.isEmpty()) {
            updateCroupierIdleDisplay();
            return;
        }

        Location baseDisplayLoc = centerLoc.clone().add(0, CARD_SURFACE_Y_OFFSET, -0.6);
        double cardSpacing = 0.30;
        double startX = -((dealerHand.size() - 1) * cardSpacing) / 2.0;

        for (int i = 0; i < dealerHand.size(); i++) {
            Card card = dealerHand.get(i);
            Card displayCard = (gameInProgress && !settlingResults && i > 0) ? null : card;
            Location spawnLoc = baseDisplayLoc.clone().add(startX + (i * cardSpacing), 0, 0);
            ItemDisplay display = createCardDisplay(spawnLoc, displayCard, true, 2);
            dealerCardDisplays.add(display);
        }

        if (croupierNPC != null) {
            if (gameInProgress && !settlingResults && dealerHand.size() >= 2) {
                Card visibleCard = dealerHand.get(0);
                croupierNPC.updateScoreDisplay(configManager.formatDealerScoreDisplay(visibleCard.getValue(), false, false, true));
            } else {
                int total = gameEngine.calculateHandValue(dealerHand);
                croupierNPC.updateScoreDisplay(configManager.formatDealerScoreDisplay(total, total > 21, total == 21, false));
            }
        }
    }
    
    private void clearAllDisplays() {
        purgeTrackedDisplays();

        for (List<ItemDisplay> cardDisplays : playerCardDisplays.values()) {
            if (cardDisplays != null) {
                cardDisplays.forEach(this::removeTrackedDisplay);
            }
        }
        playerCardDisplays.clear();

        for (ItemDisplay display : dealerCardDisplays) {
            removeTrackedDisplay(display);
        }
        dealerCardDisplays.clear();

        for (TextDisplay display : playerPrivateDisplays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        playerPrivateDisplays.clear();
    }
    
    public CroupierNPC getCroupierNPC() {
        return croupierNPC;
    }

    public void setPlayerRoundBet(Player player, int amount) {
        roundBets.put(player, amount);
        plugin.getPlayerBets().put(player, amount);
    }

    public void updateFeltMaterial(Material newFelt) {
        settings.setFeltMaterial(newFelt);
        if (tableModel != null) {
            tableModel.updateFelt(newFelt);
        }
    }

    /**
     * Cleanup all resources for this table
     */
    public void cleanup() {
        cancelTurnTimeout();
        clearTurnBossBars();
        if (croupierNPC != null) {
            croupierNPC.destroy();
            croupierNPC = null;
        }
        if (tableModel != null) {
            tableModel.destroy();
        }
        for (BlackjackChair chair : chairs) {
            chair.destroy();
        }
        chairs.clear();
        purgeTrackedDisplays();
        clearAllDisplays();
        players.clear();
        playerHands.clear();
        playerSeats.clear();
        finishedPlayers.clear();
        doubleDownPlayers.clear();
        playerCardDisplays.clear();
        dealerCardDisplays.clear();
        playerPrivateDisplays.clear();
        lastMessageTime.clear();
        roundBets.clear();
        settlingResults = false;
    }
    
    // Getters
    public Location getCenterLocation() { return centerLoc; }
    public List<Player> getPlayers() { return new ArrayList<>(players); }
    public boolean isGameInProgress() { return gameInProgress; }
    public boolean isBettingLocked() { return gameInProgress || settlingResults || cardsHaveBeenDealt; }
    public boolean hasCardsBeenDealt() { return cardsHaveBeenDealt; }
    public boolean isSettlingResults() { return settlingResults; }
    
    // PlaceholderAPI support methods
    public int getPlayerCount() { return players.size(); }
    public int getAvailableSeats() { return configManager.getMaxPlayers() - players.size(); }
    public boolean isFull() { return players.size() >= configManager.getMaxPlayers(); }
    public Location getLocation() { return centerLoc; }
    
    public boolean hasPlayerHand(Player player) { return playerHands.containsKey(player); }
    public int getPlayerHandValue(Player player) { 
        List<Card> hand = playerHands.get(player);
        return hand != null ? gameEngine.calculateHandValue(hand) : 0;
    }
    public int getPlayerHandSize(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null ? hand.size() : 0;
    }
    
    public boolean isPlayerTurn(Player player) { return currentPlayer == player; }
    public boolean isPlayerFinished(Player player) { return finishedPlayers.contains(player); }
    public boolean hasPlayerBlackjack(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && hand.size() == 2 && gameEngine.calculateHandValue(hand) == 21;
    }
    public boolean isPlayerBusted(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && gameEngine.calculateHandValue(hand) > 21;
    }
    public boolean canPlayerDoubleDown(Player player) {
        List<Card> hand = playerHands.get(player);
        return hand != null && hand.size() == 2 && !doubleDownPlayers.contains(player);
    }
    public boolean hasPlayerDoubledDown(Player player) { return doubleDownPlayers.contains(player); }
    
    public int getDealerVisibleValue() {
        if (dealerHand.isEmpty()) return 0;
        // Only show first card during game
        if (gameInProgress && dealerHand.size() >= 2) {
            List<Card> visibleCards = new ArrayList<>();
            visibleCards.add(dealerHand.get(0));
            return gameEngine.calculateHandValue(visibleCards);
        }
        return gameEngine.calculateHandValue(dealerHand);
    }
    public int getDealerCardCount() { return dealerHand.size(); }
    
    private void sendGameEndButtons() {
        for (Player player : players) {
            chatUtils.sendGameEndOptions(player);
        }
    }

    public boolean canStartGame() {
        if (gameInProgress || settlingResults || players.isEmpty()) {
            return false;
        }
        
        // Check if all players have bets
        java.util.Map<org.bukkit.entity.Player, Integer> playerBets = plugin.getPlayerBets();
        for (org.bukkit.entity.Player player : players) {
            Integer bet = playerBets.get(player);
            if (bet == null || bet <= 0) {
                return false;
            }
        }
        return true;
    }
    
    private void startAutoLeaveTimer() {
        // Cancel any existing auto-leave task
        if (autoLeaveTask != null) {
            autoLeaveTask.cancel();
        }
        
        // Record the game end time for all players
        long gameEndTime = System.currentTimeMillis();
        for (Player player : players) {
            gameEndTimes.put(player, gameEndTime);
        }
        
        // Start the auto-leave checker task
        autoLeaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAutoLeave, 20L * 5L, 20L * 5L); // Check every 5 seconds
    }
    
    private void checkAutoLeave() {
        if (gameInProgress || players.size() <= 1) {
            // Cancel auto-leave if game is in progress or only 1 player left
            if (autoLeaveTask != null) {
                autoLeaveTask.cancel();
                autoLeaveTask = null;
            }
            gameEndTimes.clear();
            return;
        }

        if (!configManager.isAutoLeaveInactivityEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        int timeoutMs = configManager.getAutoLeaveTimeoutSeconds() * 1000;
        
        List<Player> playersToRemove = new ArrayList<>();
        for (Player player : new ArrayList<>(players)) {
            Long gameEndTime = gameEndTimes.get(player);
            if (gameEndTime != null && (currentTime - gameEndTime) >= timeoutMs) {
                playersToRemove.add(player);
            }
        }
        
        // Remove inactive players
        for (Player player : playersToRemove) {
            if (player.isOnline()) {
                player.sendMessage(configManager.getMessage("auto-left-inactive"));
            }
            removePlayer(player, configManager.getLeaveReason("inactive"));
            gameEndTimes.remove(player);
        }
        
        // Cancel auto-leave task if no more players or only 1 left
        if (players.size() <= 1) {
            if (autoLeaveTask != null) {
                autoLeaveTask.cancel();
                autoLeaveTask = null;
            }
            gameEndTimes.clear();
        }
    }
    
    public void cancelAutoLeaveTimer() {
        if (autoLeaveTask != null) {
            autoLeaveTask.cancel();
            autoLeaveTask = null;
        }
        gameEndTimes.clear();
    }
}
