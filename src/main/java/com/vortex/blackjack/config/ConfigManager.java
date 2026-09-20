package com.vortex.blackjack.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centralized configuration management with validation and caching
 */
public class ConfigManager {
    private FileConfiguration config;
    private FileConfiguration messagesConfig;
    
    // Cached values for performance
    private int minBet;
    private int maxBet;
    private long betCooldown;
    private double maxJoinDistance;
    private int maxPlayers;
    private Material tableMaterial;
    private Material chairMaterial;
    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private boolean hitSoft17;
    
    // Table styling (Roulette-style 3D models and chairs)
    private String woodType;
    private String feltColor;
    private String chairCushionColor;
    private Sound chairSitSound;
    private boolean tableHologramEnabled;
    private String tableHologramTitle;
    private String currencySymbol;
    
    public ConfigManager(FileConfiguration config, FileConfiguration messagesConfig) {
        this.config = config;
        this.messagesConfig = messagesConfig;
        loadAndValidateConfig();
    }
    
    // Backward compatibility constructor
    public ConfigManager(FileConfiguration config) {
        this.config = config;
        this.messagesConfig = config; // Use main config for messages if no separate messages config
        loadAndValidateConfig();
    }
    
    private void loadAndValidateConfig() {
        // Betting settings
        minBet = Math.max(1, config.getInt("betting.min-bet", 10));
        maxBet = Math.max(minBet, config.getInt("betting.max-bet", 10000));
        betCooldown = Math.max(0, config.getLong("betting.cooldown-ms", 2000L));
        
        // Table settings
        maxJoinDistance = Math.max(1.0, config.getDouble("table.max-join-distance", 10.0));
        maxPlayers = Math.max(1, Math.min(4, config.getInt("table.max-players", 4)));
        
        // Materials with fallbacks
        try {
            tableMaterial = Material.valueOf(config.getString("table.table-material", "GREEN_TERRACOTTA"));
        } catch (IllegalArgumentException e) {
            tableMaterial = Material.GREEN_TERRACOTTA;
        }
        
        try {
            chairMaterial = Material.valueOf(config.getString("table.chair-material", "DARK_OAK_STAIRS"));
        } catch (IllegalArgumentException e) {
            chairMaterial = Material.DARK_OAK_STAIRS;
        }

        // Table styling (Roulette-style 3D models and chairs)
        woodType = config.getString("table.wood-type", "DARK_OAK").toUpperCase();
        feltColor = config.getString("table.felt-color", "GREEN").toUpperCase();
        chairCushionColor = config.getString("table.chair-cushion-color", "RED").toUpperCase();

        String sitSoundStr = config.getString("table.sounds.sit", "BLOCK_WOODEN_TRAPDOOR_CLOSE");
        try {
            chairSitSound = Sound.valueOf(sitSoundStr);
        } catch (IllegalArgumentException e) {
            chairSitSound = Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
        }

        tableHologramEnabled = config.getBoolean("table.hologram.enabled", true);
        tableHologramTitle = ChatColor.translateAlternateColorCodes('&', 
                config.getString("table.hologram.title", "&6&lBLACKJACK"));
        
        // Audio/visual settings
        soundsEnabled = config.getBoolean("sounds.enabled", true);
        particlesEnabled = config.getBoolean("particles.enabled", true);
        
        // Game rules
        hitSoft17 = config.getBoolean("game.hit-soft-17", false);

        // Currency
        currencySymbol = config.getString("currency-symbol", "$");
    }
    
    // Getters
    public String getCurrencySymbol() { return currencySymbol != null ? currencySymbol : "$"; }
    public int getMinBet() { return minBet; }
    public int getMaxBet() { return maxBet; }
    public long getBetCooldown() { return betCooldown; }
    public double getMaxJoinDistance() { return maxJoinDistance; }
    public int getMaxPlayers() { return maxPlayers; }
    public Material getTableMaterial() { return tableMaterial; }
    public Material getChairMaterial() { return chairMaterial; }
    public String getWoodType() { return woodType; }
    public String getFeltColor() { return feltColor; }
    public String getChairCushionColor() { return chairCushionColor; }

    public Material getWoodPlanks() {
        try {
            return Material.valueOf(woodType + "_PLANKS");
        } catch (IllegalArgumentException e) {
            return Material.DARK_OAK_PLANKS;
        }
    }

    public Material getWoodSlab() {
        try {
            return Material.valueOf(woodType + "_SLAB");
        } catch (IllegalArgumentException e) {
            return Material.DARK_OAK_SLAB;
        }
    }

    public Material getFeltMaterial() {
        try {
            return Material.valueOf(feltColor + "_WOOL");
        } catch (IllegalArgumentException e) {
            return Material.GREEN_WOOL;
        }
    }

    public Material getChairCushionMaterial() {
        try {
            return Material.valueOf(chairCushionColor + "_CARPET");
        } catch (IllegalArgumentException e) {
            return Material.RED_CARPET;
        }
    }

    public Sound getChairSitSound() {
        return chairSitSound;
    }

    public boolean isTableHologramEnabled() {
        return tableHologramEnabled;
    }

    public String getTableHologramTitle() {
        return tableHologramTitle;
    }
    
    public boolean areSoundsEnabled() { return soundsEnabled; }
    public boolean areParticlesEnabled() { return particlesEnabled; }
    public boolean shouldHitSoft17() { return hitSoft17; }
    
    // Auto-leave settings
    public int getAutoLeaveTimeoutSeconds() {
        return Math.max(10, config.getInt("game.auto-leave-timeout-seconds", 30));
    }
    
    // Quick bet settings
    public java.util.List<Integer> getSmallBets() {
        return config.getIntegerList("betting.quick-bets.small");
    }
    
    public java.util.List<Integer> getMediumBets() {
        return config.getIntegerList("betting.quick-bets.medium");
    }
    
    public java.util.List<Integer> getLargeBets() {
        return config.getIntegerList("betting.quick-bets.large");
    }
    
    // Display settings
    public float getCardScale() {
        return (float) config.getDouble("display.card.scale", 0.35);
    }
    
    public double getCardSpacing() {
        return config.getDouble("display.card.spacing", 0.25);
    }
    
    public double getPlayerCardHeight() {
        return config.getDouble("display.card.player.height", 1.05);
    }
    
    public double getDealerCardHeight() {
        return config.getDouble("display.card.dealer.height", 1.2);
    }
    
    /**
     * Safely get a Sound enum from a string name with fallback
     */
    private Sound getSoundFromString(String soundName, Sound fallback) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return fallback;
        }
        
        try {
            // Use Registry API for Minecraft 1.21.3+
            NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase().replace("_", ""));
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
            
            // Fallback: try direct match with the enum constant
            if (soundName.equals("BLOCK_WOODEN_BUTTON_CLICK_ON")) return Sound.BLOCK_WOODEN_BUTTON_CLICK_ON;
            if (soundName.equals("ENTITY_PLAYER_LEVELUP")) return Sound.ENTITY_PLAYER_LEVELUP;
            if (soundName.equals("ENTITY_VILLAGER_NO")) return Sound.ENTITY_VILLAGER_NO;
            if (soundName.equals("BLOCK_NOTE_BLOCK_PLING")) return Sound.BLOCK_NOTE_BLOCK_PLING;
            
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    // Sound configuration
    public Sound getCardDealSound() {
        String soundName = config.getString("sounds.card-deal.sound", "BLOCK_WOODEN_BUTTON_CLICK_ON");
        return getSoundFromString(soundName, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
    }
    
    public float getCardDealVolume() {
        return (float) config.getDouble("sounds.card-deal.volume", 1.0);
    }
    
    public float getCardDealPitch() {
        return (float) config.getDouble("sounds.card-deal.pitch", 1.2);
    }
    
    public Sound getWinSound() {
        String soundName = config.getString("sounds.win.sound", "ENTITY_PLAYER_LEVELUP");
        return getSoundFromString(soundName, Sound.ENTITY_PLAYER_LEVELUP);
    }
    
    public Sound getLoseSound() {
        String soundName = config.getString("sounds.lose.sound", "ENTITY_VILLAGER_NO");
        return getSoundFromString(soundName, Sound.ENTITY_VILLAGER_NO);
    }
    
    public Sound getPushSound() {
        String soundName = config.getString("sounds.push.sound", "BLOCK_NOTE_BLOCK_PLING");
        return getSoundFromString(soundName, Sound.BLOCK_NOTE_BLOCK_PLING);
    }
    
    // Particle configuration
    public Particle getWinParticle() {
        try {
            return Particle.valueOf(config.getString("particles.win.type", "HAPPY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.HAPPY_VILLAGER;
        }
    }
    
    public Particle getLoseParticle() {
        try {
            return Particle.valueOf(config.getString("particles.lose.type", "ANGRY_VILLAGER"));
        } catch (IllegalArgumentException e) {
            return Particle.ANGRY_VILLAGER;
        }
    }
    
    // Message handling
    public boolean hasMessage(String path) {
        return messagesConfig.contains(path) || config.contains("messages." + path);
    }

    public String getMessage(String path) {
        String message;
        // Try messages config first, then fall back to main config with "messages." prefix
        if (messagesConfig.contains(path)) {
            message = messagesConfig.getString(path);
        } else {
            message = config.getString("messages." + path);
        }
        
        if (message == null) {
            message = "&cMessage not found: " + path;
        }
        
        message = message.replace("%currency%", getCurrencySymbol());
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String formatMessage(String path, Object... args) {
        String message = getMessage(path);
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                message = message.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
            }
        }
        return message;
    }

    public List<String> getMessageList(String path, List<String> defaultList) {
        List<String> list = messagesConfig.contains(path) ? messagesConfig.getStringList(path) : null;
        if (list == null || list.isEmpty()) {
            list = defaultList;
        }
        if (list == null) return Collections.emptyList();
        List<String> colored = new ArrayList<>(list.size());
        for (String s : list) {
            s = s.replace("%currency%", getCurrencySymbol());
            colored.add(ChatColor.translateAlternateColorCodes('&', s));
        }
        return colored;
    }

    public List<String> formatMessageList(String path, List<String> defaultList, Object... args) {
        List<String> list = getMessageList(path, defaultList);
        List<String> formatted = new ArrayList<>(list.size());
        for (String line : list) {
            String s = line;
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 < args.length) {
                    s = s.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
                }
            }
            formatted.add(s);
        }
        return formatted;
    }

    // -------------------------------------------------------------------------
    // Display & Formatting Helpers
    // -------------------------------------------------------------------------

    public String getGameActionBarMessage(boolean showDoubleDown) {
        String msg = getMessage("actionbar.game-controls");
        if (showDoubleDown && isDoubleDownEnabled()) {
            msg += getMessage("actionbar.game-controls-doubledown");
        }
        return msg;
    }

    public String getCountdownActionBarMessage(int secondsRemaining) {
        return formatMessage("actionbar.countdown", "seconds", secondsRemaining);
    }

    public String getTurnBossBarTitle(String playerName, int secondsRemaining) {
        return formatMessage("bossbar.turn", "player", playerName, "seconds", secondsRemaining);
    }

    public String getDealerDrawingBossBarTitle() {
        return getMessage("bossbar.dealer-drawing");
    }

    public String formatHandDisplay(int val, boolean isBusted, boolean isBlackjack) {
        if (isBusted) {
            return formatMessage("table-display.hand-busted", "value", val);
        } else if (isBlackjack) {
            return formatMessage("table-display.hand-blackjack", "value", val);
        } else if (val == 21) {
            return formatMessage("table-display.hand-twentyone", "value", val);
        } else {
            return formatMessage("table-display.hand-value", "value", val);
        }
    }

    public String getDeskControls(boolean canDouble) {
        return canDouble ? getMessage("table-display.desk-controls-doubledown") : getMessage("table-display.desk-controls");
    }

    public String formatDealerScoreDisplay(int val, boolean isBusted, boolean isTwentyOne, boolean isInitial) {
        if (isInitial) {
            return formatMessage("table-display.dealer-score", "value", val);
        } else if (isBusted) {
            return formatMessage("table-display.dealer-score-busted", "value", val);
        } else if (isTwentyOne) {
            return formatMessage("table-display.dealer-score-twentyone", "value", val);
        } else {
            return formatMessage("table-display.dealer-score-final", "value", val);
        }
    }

    public String getCroupierDisplayName() {
        return getMessage("table-display.croupier-name");
    }

    public String getSkinDisplayName(String key) {
        if (key == null) key = "classic";
        String path = "table-settings-gui.skins." + key.toLowerCase();
        if (hasMessage(path)) {
            return getMessage(path);
        }
        return key;
    }

    public String getFeltDisplayName(Material mat) {
        if (mat == null) return "Green";
        String name = mat.name().replace("_CONCRETE", "").replace("_WOOL", "").toLowerCase();
        String path = "table-settings-gui.felts." + name;
        if (hasMessage(path)) {
            return getMessage(path);
        }
        return mat.name();
    }

    public List<String> getHologramRules(int currentPlayers, int capacity) {
        List<String> lines = new ArrayList<>();
        lines.add(formatMessage("hologram-rules.line1"));
        lines.add(formatMessage("hologram-rules.line2"));
        lines.add(formatMessage("hologram-rules.line3"));
        lines.add(formatMessage("hologram-rules.line4", "players", currentPlayers, "capacity", capacity));
        return lines;
    }

    public List<String> getAdminVersionNotification(String current, String latest, String downloadUrl) {
        return formatMessageList("version-info.admin-notify", Collections.emptyList(),
                "current", current, "latest", latest, "url", downloadUrl);
    }

    // -------------------------------------------------------------------------
    // Feature Toggles (config.yml -> features.*)
    // -------------------------------------------------------------------------

    public boolean isFeatureEnabled(String featureName, boolean defaultValue) {
        return config.getBoolean("features." + featureName, defaultValue);
    }

    public boolean isLeaveConfirmGuiEnabled() {
        return isFeatureEnabled("leave-confirm-gui", true);
    }

    public boolean isDoubleDownEnabled() {
        return isFeatureEnabled("double-down", true);
    }

    public boolean isQuickBetsEnabled() {
        return isFeatureEnabled("quick-bets", true);
    }

    public boolean isInteractiveChatButtonsEnabled() {
        return isFeatureEnabled("interactive-chat-buttons", true);
    }

    public boolean isChairSittingEnabled() {
        return isFeatureEnabled("chair-sitting", true);
    }

    public boolean isAutoLeaveInactivityEnabled() {
        return isFeatureEnabled("auto-leave-inactivity", true);
    }

    public boolean isAutoLeaveDistanceEnabled() {
        return isFeatureEnabled("auto-leave-distance", true);
    }

    public boolean isStatsTrackerEnabled() {
        return isFeatureEnabled("stats-tracker", true);
    }

    public boolean isVersionCheckerEnabled() {
        return isFeatureEnabled("version-checker", true);
    }

    // -------------------------------------------------------------------------
    // Hologram & Broadcast Templates (messages.yml)
    // -------------------------------------------------------------------------

    public String getHologramStatusInProgress() {
        return getMessage("hologram.status-in-progress");
    }

    public String getHologramStatusReady() {
        return getMessage("hologram.status-ready");
    }

    public String getHologramMinBet(int minBet) {
        return formatMessage("hologram.min-bet", "min_bet", minBet);
    }

    public String getHologramMaxBet(int maxBet) {
        return formatMessage("hologram.max-bet", "max_bet", maxBet);
    }

    public String getHologramPlayers(int current, int max) {
        return formatMessage("hologram.players", "current", current, "max", max);
    }

    public String getHologramHint() {
        return getMessage("hologram.hint");
    }

    public String getLeaveReason(String key) {
        return getMessage("reasons." + key);
    }

    public String getDealerPrefix() {
        return getMessage("dealer-prefix");
    }

    public String formatDealerHandBroadcast(String handDisplay, String valueDisplay) {
        return formatMessage("dealer-hand-broadcast",
                "dealer_prefix", getDealerPrefix(),
                "hand", handDisplay,
                "value", valueDisplay);
    }

    public void reload(FileConfiguration newConfig, FileConfiguration newMessagesConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
        if (newMessagesConfig != null) {
            this.messagesConfig = newMessagesConfig;
        }
        loadAndValidateConfig();
    }
    
    // Backward compatibility reload method
    public void reload(FileConfiguration newConfig) {
        reload(newConfig, newConfig);
    }
    
    // Performance settings
    public int getStatsSaveInterval() {
        return config.getInt("performance.stats-save-interval", 3);
    }
    
    // Game settings
    public boolean shouldRefundOnLeave() {
        return config.getBoolean("game-settings.refund-on-leave", true);
    }
    
    // Button configuration methods
    public String getButtonText(String buttonName) {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("buttons." + buttonName + ".text", "&7[" + buttonName.toUpperCase() + "]"));
    }
    
    public String getButtonCommand(String buttonName) {
        String command = messagesConfig.getString("buttons." + buttonName + ".command", getDefaultButtonCommand(buttonName));
        return normalizeBlackjackCommand(command);
    }

    private String getDefaultButtonCommand(String buttonName) {
        return switch (buttonName) {
            case "double-down" -> "/bj doubledown";
            case "play-again" -> "/bj start";
            case "leave-table" -> "/bj leave";
            case "custom-bet" -> "/bj bet ";
            default -> "/bj " + buttonName;
        };
    }

    private String normalizeBlackjackCommand(String command) {
        if (command == null || command.isBlank()) {
            return "/bj";
        }

        String raw = command.startsWith("/") ? command.substring(1) : command;
        String loweredRaw = raw.toLowerCase();
        if (loweredRaw.equals("bj") || loweredRaw.startsWith("bj ")
            || loweredRaw.equals("blackjack") || loweredRaw.startsWith("blackjack ")) {
            return command;
        }

        int firstWhitespace = findFirstWhitespace(raw);
        String action = firstWhitespace < 0 ? raw : raw.substring(0, firstWhitespace);
        String arguments = firstWhitespace < 0 ? "" : raw.substring(firstWhitespace);

        return switch (action.toLowerCase()) {
            case "createtable", "settable", "removetable", "join", "leave", "start", "hit", "stand",
                "doubledown", "bet", "stats", "reload" -> "/bj " + action.toLowerCase() + arguments;
            case "dd" -> "/bj doubledown" + arguments;
            case "bjversion", "version" -> "/bj version" + arguments;
            default -> command;
        };
    }

    private int findFirstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
    
    public String getButtonHover(String buttonName) {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("buttons." + buttonName + ".hover", "Click to " + buttonName));
    }
    
    public String getBetColorByAmount(int amount) {
        if (amount >= 5000) {
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.huge-bet-color", "&d"));
        } else if (amount >= 1000) {
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.large-bet-color", "&c"));
        } else if (amount >= 100) {
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.medium-bet-color", "&e"));
        } else {
            return ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("buttons.small-bet-color", "&a"));
        }
    }
    
    public String getGameActionPrompt() {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("game-action-prompt", "&7Your turn: "));
    }
    
    public String getGameActionSeparator() {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("game-action-separator", "&7 | "));
    }
    
    public String getPostGamePrompt() {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("post-game-prompt", "&7Choose: "));
    }
    
    // Betting category labels
    public String getBettingCategoryLabel(String category) {
        return ChatColor.translateAlternateColorCodes('&', 
            messagesConfig.getString("betting-category-" + category, "&7" + category.substring(0, 1).toUpperCase() + category.substring(1) + ": "));
    }
}
