package com.vortex.blackjack.croupier;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CroupierProtocolTest {
    // Protocol fixtures: PrismarineJS/minecraft-data data/pc/<version>/entities.json,
    // player.metadataKeys.indexOf("player_mode_customisation").
    // In the modern layout, index 17 is player_absorption and requires FLOAT.
    @ParameterizedTest
    @CsvSource({"V_1_21_4,17", "V_1_21_8,17", "V_1_21_9,16", "V_1_21_11,16"})
    void skinByteUsesTheCorrectProtocolField(ServerVersion version, int expectedIndex) {
        assertEquals(expectedIndex, CroupierNPC.skinLayerIndex(version));
    }
}
