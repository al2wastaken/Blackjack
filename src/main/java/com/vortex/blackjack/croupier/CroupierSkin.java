package com.vortex.blackjack.croupier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages croupier skin presets and custom textures.
 * Each skin preset stores both the texture value AND the Mojang signature,
 * which is required for 1.21.x+ clients to accept the PlayerInfoUpdate packet.
 */
public class CroupierSkin {

    // Default Mojang-signed skin texture (DannoBannannoXD — same as Roulette uses)
    public static final String DEFAULT_TEXTURE =
            "eyJ0aW1lc3RhbXAiOjE1ODgwNjg2NjE4NDIsInByb2ZpbGVJZCI6IjMzZWJkMzJiYjMzOTRhZDlhYzY3MGM5NmM1NDliYTdlIiwicHJvZmlsZU5hbWUiOiJEYW5ub0JhbmFubm9YRCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzEzNzA5NzI0OWM0ZGNiOGU2YzY1ZjBlN2U3NTc3YmI3NzRjNWZjMjc0MTFhMjkwYWM4MGVkZmRmODFlNjk3YiJ9fX0=";
    public static final String DEFAULT_SIGNATURE =
            "ShX80ZOUh6r67Qq+r8dvFkN7kqEUaUIB0JMdWTYFc0HZk/tqGvkRtExLgak5AWDA1Y2ruleJdCIE6eB851jhmKJG7zi9Zvzcfysb513MY14p2RdL8ZqX5NcC+0Qds2h/0ePlHD/uE3He+Kx43vs4GPl/SfciwlNjlURCeVpJ3MzRhUastaVwFFOFECNacY6HsT9Q6vEr7hLv9wLPvo5DpDU6FvS4v8KLlSGlgTpnayX61cQSeQyHbqabgBglTocp2NFs9YFjVbvq5WtLbsra5GLK+s+43/fN5NP4yBFAr08ZFu22YMeL6w51fDAPwZ2Gk4HunoPQMrhrRQkDN3RBSjALZyASHqVa49BKcJ+RNw08fLcSBYfUUZXDQabWHcqOVOlvE/5kusshcvbR86BWgYQfh5ObjGCt3P5fJ/1Dx3xNb6UKWOjl86ufkPfAhhPeUqYj/l6IAhm849oNl8q+r6nR2A641ibZySk7ZOX+Rr4lh67SgDIy1dPy2VQyHoHDIT4Joq3RNQZR+TwGRWd33EbakM6apDMMcuTxVm8lXMgYP89rBWNeEDsYbJ6L+NsypRfRfCgzap14bQ5vLZisXP1txcMUoUPv7KWJZ1CGmAI0VeODSTEZN73J0icWoniGZE74Eqvf+JGrHMF5keELN6IgQ1CIkMZO7OhxBqgi0o0=";

    // Preset skin keys (all use the default signed texture for now)
    public static final String CLASSIC_TUXEDO = DEFAULT_TEXTURE;
    public static final String LADY_CROUPIER = DEFAULT_TEXTURE;
    public static final String MAFIA_BOSS = DEFAULT_TEXTURE;
    public static final String CASUAL_DEALER = DEFAULT_TEXTURE;

    private static final Map<String, String> PRESETS = new HashMap<>();

    static {
        PRESETS.put("classic", CLASSIC_TUXEDO);
        PRESETS.put("lady", LADY_CROUPIER);
        PRESETS.put("mafia", MAFIA_BOSS);
        PRESETS.put("casual", CASUAL_DEALER);
    }

    public static Map<String, String> getPresets() {
        return Collections.unmodifiableMap(PRESETS);
    }

    public static String getPresetOrDefault(String key) {
        if (key == null) return DEFAULT_TEXTURE;
        return PRESETS.getOrDefault(key.toLowerCase(), key);
    }

    /**
     * Returns the Mojang signature for the given texture.
     * If the texture matches the default, returns the default signature.
     * Custom user-provided textures without a signature will use the default signed skin.
     */
    public static String getSignatureForTexture(String texture) {
        if (texture == null || texture.equals(DEFAULT_TEXTURE)) {
            return DEFAULT_SIGNATURE;
        }
        // Custom textures without a known signature — fall back to default
        return DEFAULT_SIGNATURE;
    }
}
