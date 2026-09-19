package com.vortex.blackjack;

import com.vortex.blackjack.commands.CommandManager;
import com.vortex.blackjack.config.ConfigFileUpdater;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.database.DatabaseManager;
import com.vortex.blackjack.economy.EconomyProvider;
import com.vortex.blackjack.economy.VaultEconomyProvider;
import com.vortex.blackjack.integration.BlackjackPlaceholderExpansion;
import com.vortex.blackjack.model.PlayerStats;
import com.vortex.blackjack.table.BlackjackTable;
import com.vortex.blackjack.table.TableManager;
import com.vortex.blackjack.table.TableSettings;
import com.vortex.blackjack.util.AsyncUtils;
import com.vortex.blackjack.util.GenericUtils;
import com.vortex.blackjack.util.VersionChecker;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main Blackjack plugin class
 */
public class BlackjackPlugin extends JavaPlugin implements Listener {
    
    // Core managers - each handles a specific responsibility
    private ConfigManager configManager;
    private TableManager tableManager;
    private CommandManager commandManager;
    private AsyncUtils asyncUtils;
    private EconomyProvider economyProvider;
    private DatabaseManager databaseManager;
    
    // GSit integration
    private boolean gSitEnabled = false;
    
    // PlaceholderAPI integration
    private BlackjackPlaceholderExpansion placeholderExpansion;
    
    // Version checker
    private VersionChecker versionChecker;
    private com.vortex.blackjack.listener.PacketEventsListener packetListener;
    
    // Player data - thread-safe collections
    private final Map<Player, Integer> playerBets = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerPersistentBets = new ConcurrentHashMap<>(); // Keeps bet amount for "Play Again"
    private final Map<Player, Long> lastBetTime = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    
    // Files
    private File statsFile;
    
    @Override
    public void onEnable() {
        // Create and update configuration files before managers read them.
        saveDefaultConfig();
        ConfigFileUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        reloadConfig();
        
        File messagesFile = new File(getDataFolder(), "messages.yml");
        FileConfiguration messagesConfig = ConfigFileUpdater.update(this, "messages.yml", messagesFile);

        if (!messagesFile.exists()) {
            getLogger().info("Creating default messages.yml file");
            createDefaultMessagesFile(messagesFile);
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        }
        
        configManager = new ConfigManager(getConfig(), messagesConfig);
        
        // Initialize DatabaseManager
        databaseManager = new DatabaseManager(this, configManager);
        try {
            databaseManager.initialize();
            databaseManager.migrateLegacyData(new File(getDataFolder(), "config.yml"), new File(getDataFolder(), "stats.yml"));
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Initialize core managers
        tableManager = new TableManager(this, configManager);
        commandManager = new CommandManager(this);
        asyncUtils = new AsyncUtils(this);
        versionChecker = new VersionChecker(this);
        
        // Initialize economy provider - Vault with EssentialsX fallback
        economyProvider = initializeEconomyProvider();
        
        if (economyProvider == null || !economyProvider.isAvailable()) {
            boolean vaultInstalled = getServer().getPluginManager().getPlugin("Vault") != null;
            if (!vaultInstalled) {
                getLogger().severe("Vault is required. Install Vault plus a Vault-compatible economy plugin, then restart the server.");
            } else {
                getLogger().severe("Vault was found, but no economy provider was registered. Install a Vault-compatible economy plugin (EssentialsX, EconomyAPI, CMI, HexaEcon, etc.).");
            }
            getLogger().severe("Disabling Blackjack...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Native Roulette-style sitting system
        getLogger().info("Roulette-style 3D table models and native ArmorStand chair seating enabled!");
        
        // Check for PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new BlackjackPlaceholderExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI found! Extensive placeholder support enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholder support disabled.");
        }
        
        // Initialize files
        statsFile = new File(getDataFolder(), "stats.yml");
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new com.vortex.blackjack.listener.TableInteractListener(this, tableManager), this);
        
        // Register command prefix aliases: /blackjack and /bj
        commandManager.registerCommands();
        
        // Load tables from config
        tableManager.loadTablesFromConfig();
        
        // Start version checking if enabled
        if (configManager.isVersionCheckerEnabled()) {
            versionChecker.checkForUpdates();
        }
        
        // Schedule periodic stats saving - configurable interval
        int statsSaveInterval = configManager.getStatsSaveInterval();
        long ticks = statsSaveInterval * 20L; // Convert seconds to ticks (20 ticks = 1 second)
        asyncUtils.scheduleRepeating("stats-autosave", this::savePlayerStats, ticks, ticks);
        // The standalone PacketEvents dependency owns its API lifecycle.
        packetListener = new com.vortex.blackjack.listener.PacketEventsListener(this);
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                .registerListener(packetListener);

        getLogger().info("Blackjack enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        if (packetListener != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                    .unregisterListener(packetListener);
            packetListener = null;
        }

        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        
        // Cancel all async tasks
        if (asyncUtils != null) {
            asyncUtils.cancelAllTasks();
        }
        
        // Cleanup and refund bets
        if (tableManager != null) {
            refundAllBets();
            tableManager.cleanup();
        }
        
        // Save player stats
        savePlayerStats();
        
        // Close database pool
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("Blackjack plugin disabled!");
    }
    
    /**
     * Create a default messages.yml file with all the required messages
     */
    private void createDefaultMessagesFile(File messagesFile) {
        try {
            if (!messagesFile.getParentFile().exists()) {
                messagesFile.getParentFile().mkdirs();
            }
            
            FileConfiguration messagesConfig = new YamlConfiguration();
            
            // Add all the default messages
            messagesConfig.set("prefix", "&8[&6Blackjack&8] &r");
            messagesConfig.set("no-permission", "&cBu işlemi yapmak için yetkiniz yok!");
            messagesConfig.set("player-only-command", "&cBu komut yalnızca oyuncular tarafından kullanılabilir!");
            
            // Table management
            messagesConfig.set("table-created", "&aBlackjack masası oluşturuldu!");
            messagesConfig.set("table-created-with-settings", "&aMasa oluşturuldu! &7(min: &e%min_bet%&7, maks: &e%max_bet%&7, oyuncular: &e%max_players%&7, mesafe: &e%max_join_distance%&7)");
            messagesConfig.set("createtable-invalid-arg", "&cGeçersiz argüman: %error%");
            messagesConfig.set("table-removed", "&cBlackjack masası kaldırıldı!");
            messagesConfig.set("table-already-exists", "&cBu konumda zaten bir masa var!");
            messagesConfig.set("table-remove-failed", "&cMasa kaldırılamadı!");
            messagesConfig.set("no-table-nearby", "&cYakında masa bulunamadı!");
            messagesConfig.set("settable-usage", "&eKullanım: /bj settable <ayar> <değer>");
            messagesConfig.set("settable-unknown-setting", "&cBilinmeyen ayar '%setting%'. Geçerli ayarlar: min-bet, max-bet, max-players, max-join-distance");
            messagesConfig.set("settable-invalid-value", "&cGeçersiz değer: '%value%'");
            messagesConfig.set("settable-validation-error", "&cDoğrulama başarısız: %error%");
            messagesConfig.set("settable-updated", "&aEn yakın masadaki &e%setting% &aayarı &e%value% &aolarak güncellendi.");
            
            // Player table status
            messagesConfig.set("already-at-table", "&cZaten bir masadasınız! Mevcut masadan ayrılmak için /bj leave kullanın.");
            messagesConfig.set("not-at-table", "&cBir masada değilsiniz! Oynamak için bir masanın yakınında /bj join yazın veya sandalyeye tıklayın.");
            messagesConfig.set("auto-left-table", "&eMasadan çok uzaklaştığınız için masadan otomatik olarak çıkarıldınız.");
            messagesConfig.set("auto-left-inactive", "&eDiğer oyuncuların oynayabilmesi için hareketsizlik nedeniyle masadan çıkarıldınız.");
            messagesConfig.set("left-table", "&aMasadan ayrıldınız.");
            messagesConfig.set("left-table-bet-refunded", "&aMasadan ayrıldınız ve $%amount% tutarındaki bahsiniz iade edildi.");
            messagesConfig.set("left-table-bet-forfeit", "&cOyun devam ederken ayrıldığınız için $%amount% tutarındaki bahsiniz yandı.");
            
            // Table joining
            messagesConfig.set("table-full", "&cBu masa dolu!");
            messagesConfig.set("too-far", "&cKatılmak için masaya çok uzaksınız!");
            messagesConfig.set("no-seats", "&cUygun boş sandalye yok!");
            messagesConfig.set("seat-taken", "&cBu sandalye zaten dolu!");
            messagesConfig.set("inside-vehicle", "&cBir araca binerken masaya katılamazsınız!");
            messagesConfig.set("join-error", "&cMasaya katılırken bir hata oluştu. Lütfen tekrar deneyin.");
            messagesConfig.set("game-in-progress", "&cAktif bir oyun sırasında bu işlem yapılamaz!");
            
            // Betting
            messagesConfig.set("bet-required", "&cOyunun başlayabilmesi için önce bahis koymalısınız! Kullanım: /bj bet <miktar>");
            messagesConfig.set("invalid-bet", "&cGeçersiz bahis miktarı! %min_bet% ile %max_bet% arasında olmalıdır.");
            messagesConfig.set("insufficient-funds", "&c$%amount% tutarında bahis koymak için yeterli paranız yok!");
            messagesConfig.set("bet-cooldown", "&cBahsinizi tekrar değiştirmeden önce lütfen biraz bekleyin.");
            messagesConfig.set("bet-set", "&aBahsiniz $%amount% olarak ayarlandı!");
            messagesConfig.set("bet-already-set", "&eBahsiniz zaten $%amount%!");
            messagesConfig.set("betting-locked", "&cMevcut el oynanırken veya sonuçlanırken bahisler kilitlidir.");
            messagesConfig.set("bet-refunded", "&a$%amount% tutarındaki bahsiniz iade edildi.");
            messagesConfig.set("bet-refunded-shutdown", "&aSunucu kapanması nedeniyle bahis iade edildi: $%amount%");
            messagesConfig.set("bet-reduced-refunded", "&aBahis $%amount% tutarına düşürüldü ve $%refund% iade edildi!");
            messagesConfig.set("auto-bet-placed", "&aOtomatik bahis yapıldı: $%amount%");
            messagesConfig.set("invalid-amount", "&cGeçersiz miktar!");
            messagesConfig.set("bet-usage", "&cKullanım: /bj bet <miktar>");
            messagesConfig.set("bet-failed", "&cBahis işlemi gerçekleştirilemedi!");
            messagesConfig.set("bet-refund-failed", "&cBahis iadesi gerçekleştirilemedi!");
            messagesConfig.set("error-refund", "&cBahis iadesi yapılırken bir hata oluştu. Lütfen bir yetkiliye danışın!");
            messagesConfig.set("error-payout", "&cKazanç ödemesi yapılırken bir hata oluştu. Lütfen bir yetkiliye danışın!");
            
            // Quick bet menu
            messagesConfig.set("quick-bet-header", "&6&l=== Hızlı Bahis Menüsü ===");
            messagesConfig.set("quick-bet-description", "&7Bahis yapmak için bir miktara tıklayın:");
            messagesConfig.set("quick-bet-border", "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            messagesConfig.set("quick-bet-title", "&6&l                         HIZLI BAHİS");
            
            // Game flow
            messagesConfig.set("game-started", "&aOyun başladı! Sıra &b%player%&a'de");
            messagesConfig.set("your-turn", "&aSıra sizde!");
            messagesConfig.set("hand-value", "&aElinizin değeri: %value%");
            messagesConfig.set("dealer-card", "&aKasanın görünen kartı: %card% | Değer: %value%");
            messagesConfig.set("dealer-value", "&aKasanın son el değeri: %value%");
            
            // Game actions
            messagesConfig.set("player-busts", "&c%player% battı (21'i aştı)!");
            messagesConfig.set("player-stands", "&a%player% %value% değerinde pas dedi!");
            messagesConfig.set("player-wins", "&a%player% kazandı ve $%amount% aldı!");
            messagesConfig.set("player-loses", "&c%player% $%amount% tutarındaki bahsini kaybetti!");
            messagesConfig.set("player-pushes", "&e%player% berabere kaldı ve $%amount% bahsini geri aldı!");
            messagesConfig.set("player-blackjack", "&6%player% BLACKJACK yaptı ve $%amount% kazandı!");
            
            // Double down
            messagesConfig.set("double-down-first-two-cards", "&cYalnızca ilk iki kartınız varken ikiye katlayabilirsiniz!");
            messagesConfig.set("double-down-already-used", "&cZaten bahsinizi ikiye katladınız!");
            messagesConfig.set("double-down-insufficient-funds", "&cBahsi ikiye katlamak için yeterli paranız yok!");
            
            // Hand display
            messagesConfig.set("hand-display", "&bEl: %hand% | %hand_value%");
            messagesConfig.set("dealer-shows", "&aKasa: %card% | %value%");
            
            // Statistics
            messagesConfig.set("stats-header", "&6=== Blackjack İstatistikleri ===");
            messagesConfig.set("stats-other-player-header", "&6=== %player% Blackjack İstatistikleri ===");
            messagesConfig.set("stats-hands-won", "&eKazanılan Eller: &a%value%");
            messagesConfig.set("stats-hands-lost", "&eKaybedilen Eller: &c%value%");
            messagesConfig.set("stats-hands-pushed", "&eBerabere Bitenler: &7%value%");
            messagesConfig.set("stats-blackjacks", "&eBlackjack Sayısı: &6%value%");
            messagesConfig.set("stats-busts", "&eBatışlar: &c%value%");
            messagesConfig.set("stats-win-rate", "&eKazanma Oranı: &a%%%value%");
            messagesConfig.set("stats-current-streak", "&eMevcut Seri: &b%value%");
            messagesConfig.set("stats-best-streak", "&eEn İyi Seri: &a%value%");
            messagesConfig.set("stats-total-winnings", "&eToplam Kazanç: &2$%value%");
            messagesConfig.set("stats-no-permission", "&cDiğer oyuncuların istatistiklerini kontrol etmek için yetkiniz yok!");
            messagesConfig.set("stats-player-not-found", "&cOyuncu bulunamadı: %player%");
            messagesConfig.set("stats-none-found", "&cİstatistik bulunamadı!");
            messagesConfig.set("stats-none-found-player", "&c%player% için istatistik bulunamadı!");
            
            // Configuration
            messagesConfig.set("config-reloaded", "&aYapılandırma yeniden yüklendi!");
            
            // Help command
            messagesConfig.set("help-header", "&rKullanılabilir Komutlar:");
            messagesConfig.set("help-admin-create", "&e/bj createtable [min-bet:<n>] [max-bet:<n>] [max-players:<n>] [max-join-distance:<n>] &7- Blackjack masası oluşturur");
            messagesConfig.set("help-admin-settable", "&e/bj settable <ayar> <değer> &7- En yakın masanın ayarlarını değiştirir");
            messagesConfig.set("help-admin-remove", "&e/bj removetable &7- En yakın masayı kaldırır");
            messagesConfig.set("help-admin-reload", "&e/bj reload &7- Yapılandırmayı yeniden yükler");
            messagesConfig.set("help-admin-version", "&e/bj version &7- Eklenti sürümünü ve durumunu kontrol eder");
            messagesConfig.set("help-join", "&e/bj join &7- En yakın masaya katılır");
            messagesConfig.set("help-leave", "&e/bj leave &7- Mevcut masadan ayrılır");
            messagesConfig.set("help-bet", "&e/bj bet <miktar> &7- Bahis koyar veya bahsi değiştirir");
            messagesConfig.set("help-start", "&e/bj start &7- Yeni bir oyun başlatır");
            messagesConfig.set("help-hit", "&e/bj hit &7- Bir kart daha çeker");
            messagesConfig.set("help-stand", "&e/bj stand &7- Sıranızı bitirir (pas)");
            messagesConfig.set("help-stats", "&e/bj stats &7- İstatistiklerinizi görüntüler");
            messagesConfig.set("help-stats-others", "&e/bj stats <oyuncu> &7- Başka bir oyuncunun istatistiklerini görüntüler");
            
            // Table broadcast messages
            messagesConfig.set("player-left-during-turn", "&c%player% sırası kendisine geldiğinde %reason%.");
            messagesConfig.set("player-left-table", "&c%player% %reason%.");
            
            // Game action prompts
            messagesConfig.set("game-action-prompt", "&7Hamleniz: ");
            messagesConfig.set("game-action-separator", "&7 | ");
            messagesConfig.set("post-game-prompt", "&7Seçim: ");
            
            // Betting category labels
            messagesConfig.set("betting-category-small", "&7Düşük: ");
            messagesConfig.set("betting-category-medium", "&7Orta: ");
            messagesConfig.set("betting-category-large", "&7Yüksek: ");
            
            // Button configuration
            messagesConfig.set("buttons.hit.text", "&a&l[KART ÇEK]");
            messagesConfig.set("buttons.hit.command", "/bj hit");
            messagesConfig.set("buttons.hit.hover", "&eBir kart daha çekmek için tıklayın");
            
            messagesConfig.set("buttons.stand.text", "&c&l[PAS]");
            messagesConfig.set("buttons.stand.command", "/bj stand");
            messagesConfig.set("buttons.stand.hover", "&eSıranızı bitirmek için tıklayın");
            
            messagesConfig.set("buttons.double-down.text", "&6&l[İKİYE KATLA]");
            messagesConfig.set("buttons.double-down.command", "/bj doubledown");
            messagesConfig.set("buttons.double-down.hover", "&eBahsinizi ikiye katlayıp tek bir kart çekmek için tıklayın");
            
            messagesConfig.set("buttons.play-again.text", "&a&l[Tekrar Oyna]");
            messagesConfig.set("buttons.play-again.command", "/bj start");
            messagesConfig.set("buttons.play-again.hover", "&eYeni bir oyun başlatmak için tıklayın");
            
            messagesConfig.set("buttons.leave-table.text", "&c&l[Masadan Ayrıl]");
            messagesConfig.set("buttons.leave-table.command", "/bj leave");
            messagesConfig.set("buttons.leave-table.hover", "&eMasadan ayrılmak için tıklayın");
            
            messagesConfig.set("buttons.custom-bet.text", "&b&l[ÖZEL BAHİS]");
            messagesConfig.set("buttons.custom-bet.command", "/bj bet ");
            messagesConfig.set("buttons.custom-bet.hover", "&eÖzel bir miktar girmek için tıklayın");
            
            // Button color configurations
            messagesConfig.set("buttons.small-bet-color", "&a");    // Green for small bets
            messagesConfig.set("buttons.medium-bet-color", "&e");   // Yellow for medium bets
            messagesConfig.set("buttons.large-bet-color", "&c");    // Red for large bets
            messagesConfig.set("buttons.huge-bet-color", "&d");     // Pink for huge bets
            
            messagesConfig.save(messagesFile);
            
        } catch (IOException e) {
            getLogger().severe("Could not create default messages.yml: " + e.getMessage());
        }
    }
    
    /**
     * Initialize economy provider
     */
    private EconomyProvider initializeEconomyProvider() {
        VaultEconomyProvider provider = new VaultEconomyProvider(this);
        
        if (provider.isEnabled()) {
            getLogger().info("Economy: " + provider.getProviderName());
            return provider;
        }
        
        // Try delayed initialization for late-loading economy plugins
        getServer().getScheduler().runTaskLater(this, () -> {
            if (provider.reconnect()) {
                economyProvider = provider;
                getLogger().info("Economy (delayed): " + provider.getProviderName());
            } else {
                getLogger().warning("No economy plugin found! Install Vault + an economy plugin (EssentialsX, HexaEcon, etc.)");
            }
        }, 40L);
        
        return provider; // Return even if not enabled, might work after delay
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is admin and notify about updates if enabled
        if (configManager.isVersionCheckerEnabled() && player.hasPermission("blackjack.admin")) {
            versionChecker.notifyAdmin(player);
        }

        // Asynchronously load player stats into cache if stats tracker is enabled
        if (configManager.isStatsTrackerEnabled() && databaseManager != null) {
            UUID uuid = player.getUniqueId();
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                PlayerStats loaded = databaseManager.loadPlayerStats(uuid);
                PlayerStats stats = loaded != null ? loaded : new PlayerStats();
                getServer().getScheduler().runTask(this, () -> {
                    // Ignore old login completions and preserve newer cached data.
                    if (player.isOnline() && getServer().getPlayer(uuid) == player) {
                        playerStats.putIfAbsent(uuid, stats);
                    }
                });
            });
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Remove from bet tracking
        lastBetTime.remove(player);
        playerPersistentBets.remove(player);
        
        // Remove from table if they're at one
        if (tableManager != null) {
            tableManager.removePlayerFromTable(player, configManager.getLeaveReason("disconnected"));
            for (BlackjackTable table : tableManager.getTables()) {
                if (table.getCroupierNPC() != null) {
                    table.getCroupierNPC().hide(player);
                }
            }
        }
        
        // Asynchronously persist player stats to DB on quit if enabled
        if (configManager.isStatsTrackerEnabled() && databaseManager != null) {
            PlayerStats stats = playerStats.remove(player.getUniqueId());
            if (stats != null) {
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    databaseManager.savePlayerStats(player.getUniqueId(), stats);
                });
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!configManager.isAutoLeaveDistanceEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        
        // Only check if player moved to a different block (optimization)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && 
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // Check if player is at a table
        if (tableManager != null) {
            BlackjackTable table = tableManager.getPlayerTable(player);
            if (table != null) {
                double distance = player.getLocation().distance(table.getCenterLocation());
                double maxDistance = table.getSettings().getMaxJoinDistance(configManager);
                
                if (distance > maxDistance) {
                    // Player moved too far from table, auto-leave
                    table.removePlayer(player, configManager.getLeaveReason("moved-away"));
                    player.sendMessage(configManager.getMessage("auto-left-table")
                        .replace("%distance%", String.format("%.1f", distance))
                        .replace("%max_distance%", String.format("%.1f", maxDistance)));
                }
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only-command"));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        return switch (action) {
            case "createtable" -> handleCreateTable(player, args);
            case "settable" -> handleSetTable(player, args);
            case "removetable" -> handleRemoveTable(player);
            case "reload" -> handleReload(player);
            case "version" -> handleVersion(player);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }
    
    // Command handlers - clean and focused methods
    
    private boolean handleCreateTable(Player player, String[] args) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        StringBuilder err = new StringBuilder();
        TableSettings settings = TableSettings.parseArgs(args, 1, configManager, err);
        if (settings == null) {
            player.sendMessage(configManager.formatMessage("createtable-invalid-arg", "error", err.toString()));
            return true;
        }

        Location tableLoc = com.vortex.blackjack.table.TableManager.normalizeLocation(player.getLocation());
        if (tableManager.createTable(tableLoc, settings)) {
            player.sendMessage(configManager.formatMessage("table-created-with-settings",
                "min_bet",          settings.getMinBet(configManager),
                "max_bet",          settings.getMaxBet(configManager),
                "max_players",      settings.getMaxPlayers(configManager),
                "max_join_distance", settings.getMaxJoinDistance(configManager)));
        } else {
            player.sendMessage(configManager.getMessage("table-already-exists"));
        }
        return true;
    }

    private boolean handleSetTable(Player player, String[] args) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        // /bj settable <setting> <value>
        if (args.length < 3) {
            player.sendMessage(configManager.getMessage("settable-usage"));
            return true;
        }

        BlackjackTable table = tableManager.findNearestTable(player.getLocation());
        if (table == null) {
            player.sendMessage(configManager.getMessage("no-table-nearby"));
            return true;
        }

        String setting = args[1].toLowerCase();
        String value   = args[2];
        TableSettings s = table.getSettings();

        try {
            switch (setting) {
                case "min-bet"            -> s.setMinBet(parsePositiveInt(value));
                case "max-bet"            -> s.setMaxBet(parsePositiveInt(value));
                case "max-players"        -> s.setMaxPlayers(Math.max(1, Math.min(8, parsePositiveInt(value))));
                case "max-join-distance"  -> s.setMaxJoinDistance(parsePositiveDouble(value));
                default -> {
                    player.sendMessage(configManager.formatMessage("settable-unknown-setting", "setting", setting));
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.formatMessage("settable-invalid-value", "value", value));
            return true;
        }

        String err = s.validate(configManager);
        if (err != null) {
            player.sendMessage(configManager.formatMessage("settable-validation-error", "error", err));
            return true;
        }

        tableManager.saveTableSettings(table);
        player.sendMessage(configManager.formatMessage("settable-updated", "setting", setting, "value", value));
        return true;
    }

    private int parsePositiveInt(String s) {
        int v = Integer.parseInt(s);
        if (v <= 0) throw new NumberFormatException("must be positive");
        return v;
    }

    private double parsePositiveDouble(String s) {
        double v = Double.parseDouble(s);
        if (v <= 0) throw new NumberFormatException("must be positive");
        return v;
    }
    
    private boolean handleRemoveTable(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        
        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());
        if (nearestTable != null) {
            if (tableManager.removeTable(nearestTable.getCenterLocation())) {
                player.sendMessage(configManager.getMessage("table-removed"));
            } else {
                player.sendMessage(configManager.getMessage("table-remove-failed"));
            }
        } else {
            player.sendMessage(configManager.getMessage("no-table-nearby"));
        }
        return true;
    }
    
    private boolean handleJoin(Player player) {
        if (tableManager.getPlayerTable(player) != null) {
            player.sendMessage(configManager.getMessage("already-at-table"));
            return true;
        }
        
        BlackjackTable nearestTable = tableManager.findNearestTable(player.getLocation());
        if (nearestTable != null) {
            nearestTable.addPlayer(player);
        } else {
            player.sendMessage(configManager.getMessage("no-table-nearby"));
        }
        return true;
    }
    
    private boolean handleLeave(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            Integer bet = playerBets.get(player);
            if (configManager.isLeaveConfirmGuiEnabled() && bet != null && bet > 0) {
                new com.vortex.blackjack.gui.LeaveConfirmGUI(this, table, player, bet).open();
                return true;
            }

            // Remove player from table (this will handle bet refunding automatically if needed)
            table.removePlayer(player, configManager.getLeaveReason("normal"));
            
            // Clear persistent bet when leaving
            playerPersistentBets.remove(player);
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleStart(Player player) {
        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null) {
            if (table.isBettingLocked()) {
                player.sendMessage(configManager.getMessage("betting-locked"));
                return true;
            }

            // Auto-bet if player has a persistent bet amount but no current bet
            Integer currentBet = playerBets.get(player);
            Integer persistentBet = playerPersistentBets.get(player);
            
            if ((currentBet == null || currentBet == 0) && persistentBet != null && persistentBet > 0) {
                // Attempt to place the persistent bet automatically
                if (processBet(player, persistentBet)) {
                    player.sendMessage(configManager.formatMessage("auto-bet-placed", "amount", persistentBet));
                }
            }
            
            table.startGame();
        } else {
            player.sendMessage(configManager.getMessage("not-at-table"));
        }
        return true;
    }
    
    private boolean handleHit(Player player) {
        return GenericUtils.handleTableAction(player, tableManager, configManager, "hit", 
            table -> table.hit(player));
    }
    
    private boolean handleStand(Player player) {
        return GenericUtils.handleTableAction(player, tableManager, configManager, "stand", 
            table -> table.stand(player));
    }
    
    private boolean handleDoubleDown(Player player) {
        if (!configManager.isDoubleDownEnabled()) {
            player.sendMessage(configManager.getMessage("double-down-disabled"));
            return true;
        }
        return GenericUtils.handleTableAction(player, tableManager, configManager, "doubledown", 
            table -> table.doubleDown(player));
    }
    
    private boolean handleBet(Player player, String[] args) {
        if (tableManager.getPlayerTable(player) == null) {
            player.sendMessage(configManager.getMessage("not-at-table"));
            return true;
        }

        BlackjackTable table = tableManager.getPlayerTable(player);
        if (table != null && table.isBettingLocked()) {
            player.sendMessage(configManager.getMessage("betting-locked"));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(configManager.getMessage("bet-usage"));
            return true;
        }
        
        Integer amount = GenericUtils.parseIntegerArgument(args[1], player, configManager, "invalid-amount");
        if (amount == null) {
            return true;
        }
        return processBet(player, amount);
    }
    
    private boolean handleStats(Player player, String[] args) {
        if (!configManager.isStatsTrackerEnabled()) {
            player.sendMessage(configManager.getMessage("stats-disabled"));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        String targetName = player.getName();
        
        // Check if admin is checking another player's stats
        if (args.length > 1) {
            if (!player.hasPermission("blackjack.stats.others")) {
                player.sendMessage(configManager.getMessage("stats-no-permission"));
                return true;
            }
            
            targetName = args[1];
            Player targetPlayer = getServer().getPlayer(targetName);
            if (targetPlayer != null) {
                targetUUID = targetPlayer.getUniqueId();
                targetName = targetPlayer.getName();
            } else {
                try {
                    org.bukkit.OfflinePlayer[] offlinePlayers = getServer().getOfflinePlayers();
                    org.bukkit.OfflinePlayer offlinePlayer = null;
                    for (org.bukkit.OfflinePlayer op : offlinePlayers) {
                        if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                            offlinePlayer = op;
                            break;
                        }
                    }
                    if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                        targetUUID = offlinePlayer.getUniqueId();
                        targetName = offlinePlayer.getName();
                    } else {
                        player.sendMessage(configManager.formatMessage("stats-player-not-found", "player", targetName));
                        return true;
                    }
                } catch (Exception ex) {
                    player.sendMessage(configManager.formatMessage("stats-player-not-found", "player", targetName));
                    return true;
                }
            }
        }
        
        // Load stats for the target player from memory or database
        PlayerStats stats = playerStats.get(targetUUID);
        if (stats == null && databaseManager != null) {
            stats = databaseManager.loadPlayerStats(targetUUID);
            if (stats != null && targetUUID.equals(player.getUniqueId())) {
                playerStats.put(targetUUID, stats);
            }
        }
        if (stats == null) {
            stats = new PlayerStats();
        }
        
        if (stats.getTotalHands() == 0) {
            if (targetUUID.equals(player.getUniqueId())) {
                player.sendMessage(configManager.getMessage("stats-none-found"));
            } else {
                player.sendMessage(configManager.formatMessage("stats-none-found-player", "player", targetName));
            }
            return true;
        }
        
        // Use generic stats display method
        GenericUtils.sendStatsToPlayer(player, stats, configManager, targetName, 
            targetUUID.equals(player.getUniqueId()));
        
        return true;
    }
    
    private boolean handleReload(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }
        
        ConfigFileUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        reloadConfig();
        
        File messagesFile = new File(getDataFolder(), "messages.yml");
        FileConfiguration messagesConfig = ConfigFileUpdater.update(this, "messages.yml", messagesFile);
        
        configManager.reload(getConfig(), messagesConfig);
        player.sendMessage(configManager.getMessage("config-reloaded"));
        return true;
    }

    private boolean handleVersion(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        player.sendMessage("§6§l=== Blackjack Eklenti Sürüm Bilgisi ===");
        player.sendMessage("§fEklenti: §aBlackjack");
        player.sendMessage("§fGeliştirici: §bDefectiveVortex");
        player.sendMessage("§fMevcut Sürüm: §a" + versionChecker.getCurrentVersion());

        if (versionChecker.getLatestVersion() != null) {
            player.sendMessage("§fEn Son Sürüm: §a" + versionChecker.getLatestVersion());
        }

        player.sendMessage(versionChecker.getVersionStatus());
        player.sendMessage("§7GitHub: §9https://github.com/DefectiveVortex/Blackjack");
        return true;
    }
    
    // Betting system - improved and thread-safe
    
    private boolean processBet(Player player, int amount) {
        // Resolve per-table bet limits (fall back to global config if not at a table)
        BlackjackTable playerTable = tableManager.getPlayerTable(player);
        int effectiveMin = playerTable != null
                ? playerTable.getSettings().getMinBet(configManager) : configManager.getMinBet();
        int effectiveMax = playerTable != null
                ? playerTable.getSettings().getMaxBet(configManager) : configManager.getMaxBet();

        // Validate bet amount
        if (amount < effectiveMin || amount > effectiveMax) {
            player.sendMessage(configManager.formatMessage("invalid-bet",
                "min_bet", effectiveMin, "max_bet", effectiveMax));
            return true;
        }
        
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        long lastBet = lastBetTime.getOrDefault(player, 0L);
        if (currentTime - lastBet < configManager.getBetCooldown()) {
            player.sendMessage(configManager.getMessage("bet-cooldown"));
            return true;
        }
        
        // Check if player has enough money
        if (!economyProvider.hasEnough(player.getUniqueId(), BigDecimal.valueOf(amount))) {
            player.sendMessage(configManager.formatMessage("insufficient-funds", "amount", amount));
            return true;
        }
        
        // Process the bet
        int previousBet = playerBets.getOrDefault(player, 0);
        int difference = amount - previousBet;
        
        if (difference > 0) {
            // Taking more money
            if (economyProvider.subtract(player.getUniqueId(), BigDecimal.valueOf(difference))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage(configManager.formatMessage("bet-set", "amount", amount));
                
                // Auto-start game if player is at table and no game in progress
                BlackjackTable table = tableManager.getPlayerTable(player);
                if (table != null && !table.isGameInProgress() && previousBet == 0) {
                    // First bet placed, try to start game
                    this.getServer().getScheduler().runTaskLater(this, () -> {
                        if (table.canStartGame()) {
                            table.startGame();
                        }
                    }, 20L); // 1 second delay to allow other players to bet
                }
            } else {
                player.sendMessage(configManager.getMessage("bet-failed"));
            }
        } else if (difference < 0) {
            // Refunding some money
            int refund = -difference;
            if (economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(refund))) {
                playerBets.put(player, amount);
                playerPersistentBets.put(player, amount); // Store for "Play Again"
                lastBetTime.put(player, currentTime);
                player.sendMessage(configManager.formatMessage("bet-reduced-refunded", "amount", amount, "refund", refund));
            } else {
                player.sendMessage(configManager.getMessage("bet-refund-failed"));
            }
        } else {
            player.sendMessage(configManager.formatMessage("bet-already-set", "amount", amount));
        }
        
        return true;
    }
    
    private void refundAllBets() {
        for (Map.Entry<Player, Integer> entry : playerBets.entrySet()) {
            Player player = entry.getKey();
            Integer amount = entry.getValue();
            
            if (amount != null && amount > 0) {
                BlackjackTable table = tableManager.getPlayerTable(player);
                if (table == null || !table.isGameInProgress()) {
                    economyProvider.add(player.getUniqueId(), BigDecimal.valueOf(amount));
                    if (player.isOnline()) {
                        player.sendMessage(configManager.formatMessage("bet-refunded-shutdown", "amount", amount));
                    }
                }
            }
        }
        playerBets.clear();
    }
    
    // Player statistics system
    
    
    private void savePlayerStats() {
        if (!configManager.isStatsTrackerEnabled() || playerStats.isEmpty()) {
            return;
        }
        
        if (databaseManager != null) {
            databaseManager.saveAllPlayerStats(playerStats);
            return;
        }

        FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            GenericUtils.savePlayerStats(statsConfig, entry.getKey(), entry.getValue());
        }
        try {
            if (!statsFile.getParentFile().exists()) {
                statsFile.getParentFile().mkdirs();
            }
            statsConfig.save(statsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save player statistics: " + e.getMessage());
        }
    }
    
    private void sendHelp(Player player) {
        if (!player.hasPermission("blackjack.admin")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return;
        }

        player.sendMessage(configManager.getMessage("prefix") + configManager.getMessage("help-header"));
        player.sendMessage(configManager.getMessage("help-admin-create"));
        player.sendMessage(configManager.getMessage("help-admin-settable"));
        player.sendMessage(configManager.getMessage("help-admin-remove"));
        player.sendMessage(configManager.getMessage("help-admin-reload"));
        player.sendMessage(configManager.getMessage("help-admin-version"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("blackjack")) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("blackjack.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("createtable");
            completions.add("settable");
            completions.add("removetable");
            completions.add("reload");
            completions.add("version");
            return filterCompletions(completions, args[0]);
        }

        if (args[0].equalsIgnoreCase("createtable") && args.length >= 2) {
            List<String> settingKeys = new ArrayList<>();
            settingKeys.add("min-bet:");
            settingKeys.add("max-bet:");
            settingKeys.add("max-players:");
            settingKeys.add("max-join-distance:");

            List<String> alreadyUsed = new ArrayList<>();
            for (int i = 1; i < args.length - 1; i++) {
                int colon = args[i].indexOf(':');
                if (colon > 0) {
                    alreadyUsed.add(args[i].substring(0, colon) + ":");
                }
            }
            settingKeys.removeAll(alreadyUsed);
            return filterCompletions(settingKeys, args[args.length - 1]);
        }

        if (sender instanceof Player player && args[0].equalsIgnoreCase("settable")) {
            if (args.length == 2) {
                List<String> settings = new ArrayList<>();
                settings.add("min-bet");
                settings.add("max-bet");
                settings.add("max-players");
                settings.add("max-join-distance");
                return filterCompletions(settings, args[1]);
            }

            if (args.length == 3) {
                String currentValue = getCurrentTableSettingValue(player, args[1]);
                if (currentValue != null) {
                    return filterCompletions(List.of(currentValue), args[2]);
                }
            }
        }

        return Collections.emptyList();
    }

    private String getCurrentTableSettingValue(Player player, String setting) {
        BlackjackTable table = tableManager.findNearestTable(player.getLocation());
        if (table == null) {
            return null;
        }

        TableSettings settings = table.getSettings();
        return switch (setting.toLowerCase()) {
            case "min-bet" -> String.valueOf(settings.getMinBet(configManager));
            case "max-bet" -> String.valueOf(settings.getMaxBet(configManager));
            case "max-players" -> String.valueOf(settings.getMaxPlayers(configManager));
            case "max-join-distance" -> String.valueOf(settings.getMaxJoinDistance(configManager));
            default -> null;
        };
    }

    private List<String> filterCompletions(List<String> completions, String partial) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(partial, completions, result);
        Collections.sort(result);
        return result;
    }
    
    // Getters for managers (used by other classes)
    public ConfigManager getConfigManager() { return configManager; }
    public TableManager getTableManager() { return tableManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyProvider getEconomyProvider() { return economyProvider; }
    public AsyncUtils getAsyncUtils() { return asyncUtils; }
    
    // Player data getters
    public Map<Player, Integer> getPlayerBets() { return playerBets; }
    public Map<Player, Integer> getPlayerPersistentBets() { return playerPersistentBets; }
    public Map<UUID, PlayerStats> getPlayerStats() { return playerStats; }
    public boolean isGSitEnabled() { return gSitEnabled; }
    
    public VersionChecker getVersionChecker() { return versionChecker; }
}
