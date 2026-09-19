package com.vortex.blackjack.croupier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages croupier skin presets and custom textures.
 */
public class CroupierSkin {

    // Default predefined casino croupier skin textures (Base64 textures)
    public static final String CLASSIC_TUXEDO = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmM2Mzk3NWY3MDBlMjg5ODFiYzAwYTU0NDgzYzEzNWQ2Y2JjOWY3NjViZDg5Y2E2ZGVhNWQ1MGUyZDU4Zjc3MSJ9fX0=";
    public static final String LADY_CROUPIER = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWRiYTU1ODhiODg1OGI2ZGE5M2RhYmZkOTIxZTQ1MmQ0OGMwMjBhNWRkNGViYWIyNTQ4MDk4Mzg4OTdhZDgzMCJ9fX0=";
    public static final String MAFIA_BOSS = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGZhYjVmMjg1YWE0NTMwNzgxOTI2MGExZDRiZjkyYjI4NjU3MTE4NzI2MmMyMTRmMTU5MWY2MmNkMzgyZTgifX19";
    public static final String CASUAL_DEALER = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTgzMTg4ZDAxMzExZjcxZTk1ZDE1MjI4NTAxYjIxNDY3MDkxMTZlMzgxNmMyMTA1ODFmNWJhODdmMjRlMzUifX19";

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
        if (key == null) return CLASSIC_TUXEDO;
        return PRESETS.getOrDefault(key.toLowerCase(), key);
    }
}
