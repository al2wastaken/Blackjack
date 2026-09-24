package com.vortex.blackjack.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramRulesTest {

    @Test
    void testHologramRulesAsListWithCustomLines() {
        String yaml = """
            hologram-rules:
              - "&6&lCUSTOM TITLE"
              - "&eRule 1"
              - "&eRule 2"
              - "&eRule 3"
              - "&aPlayers: %players%/%capacity%"
              - "&bFooter Note"
            """;
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        YamlConfiguration config = new YamlConfiguration();
        ConfigManager manager = new ConfigManager(config, messages);

        List<String> rules = manager.getHologramRules(2, 4);
        assertEquals(6, rules.size());
        assertEquals("§6§lCUSTOM TITLE", rules.get(0));
        assertEquals("§eRule 1", rules.get(1));
        assertEquals("§aPlayers: 2/4", rules.get(4));
        assertEquals("§bFooter Note", rules.get(5));
    }

    @Test
    void testHologramRulesFallbackToLegacySection() {
        String yaml = """
            hologram-rules:
              line1: "&6&lTITLE"
              line2: "&7Desc"
              line3: "%players%/%capacity%"
            """;
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        YamlConfiguration config = new YamlConfiguration();
        ConfigManager manager = new ConfigManager(config, messages);

        List<String> rules = manager.getHologramRules(1, 4);
        assertEquals(3, rules.size());
        assertEquals("§6§lTITLE", rules.get(0));
        assertEquals("§7Desc", rules.get(1));
        assertEquals("1/4", rules.get(2));
    }
}
