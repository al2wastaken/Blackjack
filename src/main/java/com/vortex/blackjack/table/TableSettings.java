package com.vortex.blackjack.table;

import com.vortex.blackjack.config.ConfigManager;

/**
 * Per-table settings overrides. Null fields fall back to the global ConfigManager values.
 */
public class TableSettings {

    private Integer minBet;
    private Integer maxBet;
    private Integer maxPlayers;
    private Double  maxJoinDistance;
    private String  croupierSkin;
    private Integer countdownSeconds;
    private org.bukkit.Material feltMaterial;

    /** All-nulls constructor — every field resolves to the global config default. */
    public TableSettings() {}

    /** Full constructor used when loading persisted settings from config. */
    public TableSettings(Integer minBet, Integer maxBet,
                         Integer maxPlayers, Double maxJoinDistance) {
        this.minBet          = minBet;
        this.maxBet          = maxBet;
        this.maxPlayers      = maxPlayers;
        this.maxJoinDistance = maxJoinDistance;
    }

    public TableSettings(Integer minBet, Integer maxBet,
                         Integer maxPlayers, Double maxJoinDistance,
                         String croupierSkin, Integer countdownSeconds,
                         org.bukkit.Material feltMaterial) {
        this.minBet          = minBet;
        this.maxBet          = maxBet;
        this.maxPlayers      = maxPlayers;
        this.maxJoinDistance = maxJoinDistance;
        this.croupierSkin    = croupierSkin;
        this.countdownSeconds = countdownSeconds;
        this.feltMaterial    = feltMaterial;
    }

    // -------------------------------------------------------------------------
    // Resolved getters — always return a usable value
    // -------------------------------------------------------------------------

    public int getMinBet(ConfigManager cfg) {
        return minBet != null ? minBet : cfg.getMinBet();
    }

    public int getMaxBet(ConfigManager cfg) {
        return maxBet != null ? maxBet : cfg.getMaxBet();
    }

    public int getMaxPlayers(ConfigManager cfg) {
        return maxPlayers != null ? maxPlayers : cfg.getMaxPlayers();
    }

    public double getMaxJoinDistance(ConfigManager cfg) {
        return maxJoinDistance != null ? maxJoinDistance : cfg.getMaxJoinDistance();
    }

    public String getCroupierSkin() {
        return croupierSkin != null ? croupierSkin : "classic";
    }

    public int getCountdownSeconds() {
        return countdownSeconds != null && countdownSeconds > 0 ? countdownSeconds : 15;
    }

    public org.bukkit.Material getFeltMaterial() {
        return feltMaterial != null ? feltMaterial : org.bukkit.Material.GREEN_WOOL;
    }

    // -------------------------------------------------------------------------
    // Raw nullable getters (for serialisation — null means "not set")
    // -------------------------------------------------------------------------

    public Integer getRawMinBet()          { return minBet; }
    public Integer getRawMaxBet()          { return maxBet; }
    public Integer getRawMaxPlayers()      { return maxPlayers; }
    public Double  getRawMaxJoinDistance() { return maxJoinDistance; }
    public String  getRawCroupierSkin()    { return croupierSkin; }
    public Integer getRawCountdownSeconds(){ return countdownSeconds; }
    public org.bukkit.Material getRawFeltMaterial() { return feltMaterial; }

    // -------------------------------------------------------------------------
    // Setters (used by /bj settable)
    // -------------------------------------------------------------------------

    public void setMinBet(Integer v)         { this.minBet          = v; }
    public void setMaxBet(Integer v)         { this.maxBet          = v; }
    public void setMaxPlayers(Integer v)     { this.maxPlayers      = v; }
    public void setMaxJoinDistance(Double v) { this.maxJoinDistance = v; }
    public void setCroupierSkin(String v)    { this.croupierSkin    = v; }
    public void setCountdownSeconds(Integer v){ this.countdownSeconds = v; }
    public void setFeltMaterial(org.bukkit.Material v) { this.feltMaterial = v; }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Returns an error description if the current settings are invalid, or null if OK.
     */
    public String validate(ConfigManager cfg) {
        int lo = getMinBet(cfg);
        int hi = getMaxBet(cfg);
        if (lo > hi) return cfg.formatMessage("settings-validation.min-greater-max", "min", lo, "max", hi);
        if (maxPlayers != null && (maxPlayers < 1 || maxPlayers > 8))
            return cfg.getMessage("settings-validation.players-range");
        if (maxJoinDistance != null && maxJoinDistance < 1.0)
            return cfg.getMessage("settings-validation.min-distance");
        return null;
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses named-argument tokens of the form "key:value" starting at {@code startIndex}.
     * Recognised keys: min-bet, max-bet, max-players, max-join-distance.
     * On error writes a description into {@code errorOut} and returns null.
     */
    public static TableSettings parseArgs(String[] tokens, int startIndex,
                                          ConfigManager cfg, StringBuilder errorOut) {
        TableSettings s = new TableSettings();
        for (int i = startIndex; i < tokens.length; i++) {
            String tok   = tokens[i];
            int    colon = tok.indexOf(':');
            if (colon < 0) {
                errorOut.append(cfg.formatMessage("settings-validation.format-error", "token", tok));
                return null;
            }
            String key = tok.substring(0, colon).toLowerCase();
            String val = tok.substring(colon + 1);
            try {
                switch (key) {
                    case "min-bet"            -> s.setMinBet(parsePositiveInt(val));
                    case "max-bet"            -> s.setMaxBet(parsePositiveInt(val));
                    case "max-players"        -> s.setMaxPlayers(parsePositiveInt(val));
                    case "max-join-distance"  -> s.setMaxJoinDistance(parsePositiveDouble(val));
                    default -> {
                        errorOut.append(cfg.formatMessage("settings-validation.unknown-setting", "key", key));
                        return null;
                    }
                }
            } catch (NumberFormatException e) {
                errorOut.append(cfg.formatMessage("settings-validation.invalid-value", "key", key, "val", val));
                return null;
            }
        }
        String err = s.validate(cfg);
        if (err != null) {
            errorOut.append(err);
            return null;
        }
        return s;
    }

    private static int parsePositiveInt(String s) {
        int v = Integer.parseInt(s);
        if (v <= 0) throw new NumberFormatException("must be positive");
        return v;
    }

    private static double parsePositiveDouble(String s) {
        double v = Double.parseDouble(s);
        if (v <= 0) throw new NumberFormatException("must be positive");
        return v;
    }
}
