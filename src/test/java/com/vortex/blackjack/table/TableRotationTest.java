package com.vortex.blackjack.table;

import com.vortex.blackjack.util.GenericUtils;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableRotationTest {

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",
            "15.0, 0.0",
            "345.0, 0.0",
            "-15.0, 0.0",
            "44.9, 0.0",
            "45.0, 90.0",
            "89.0, 90.0",
            "90.0, 90.0",
            "134.9, 90.0",
            "135.0, 180.0",
            "180.0, 180.0",
            "-180.0, 180.0",
            "224.9, 180.0",
            "225.0, 270.0",
            "270.0, 270.0",
            "-90.0, 270.0",
            "314.9, 270.0",
            "315.0, 0.0"
    })
    void testSnapYawTo90(float inputYaw, float expectedYaw) {
        float actual = GenericUtils.snapYawTo90(inputYaw);
        assertEquals(expectedYaw, actual, 0.01f);
    }

    @Test
    void testRotateOffsetAt0Degrees() {
        // At yaw 0 (South, +Z): local (0, 0, 1) should remain (0, 0, 1)
        Vector rotated = GenericUtils.rotateOffset(new Vector(0, 0, 1), 0.0f);
        assertEquals(0.0, rotated.getX(), 1e-4);
        assertEquals(0.0, rotated.getY(), 1e-4);
        assertEquals(1.0, rotated.getZ(), 1e-4);

        // local (1, 0, 0) should remain (1, 0, 0)
        Vector rotatedX = GenericUtils.rotateOffset(new Vector(1, 0, 0), 0.0f);
        assertEquals(1.0, rotatedX.getX(), 1e-4);
        assertEquals(0.0, rotatedX.getY(), 1e-4);
        assertEquals(0.0, rotatedX.getZ(), 1e-4);
    }

    @Test
    void testRotateOffsetAt90Degrees() {
        // At yaw 90 (West, -X): local forward (0, 0, 1) should become (-1, 0, 0)
        Vector rotated = GenericUtils.rotateOffset(new Vector(0, 0, 1), 90.0f);
        assertEquals(-1.0, rotated.getX(), 1e-4);
        assertEquals(0.0, rotated.getY(), 1e-4);
        assertEquals(0.0, rotated.getZ(), 1e-4);

        // local right (1, 0, 0) should become (0, 0, 1) (+Z, South)
        Vector rotatedX = GenericUtils.rotateOffset(new Vector(1, 0, 0), 90.0f);
        assertEquals(0.0, rotatedX.getX(), 1e-4);
        assertEquals(0.0, rotatedX.getY(), 1e-4);
        assertEquals(1.0, rotatedX.getZ(), 1e-4);
    }

    @Test
    void testRotateOffsetAt180Degrees() {
        // At yaw 180 (North, -Z): local forward (0, 0, 1) should become (0, 0, -1)
        Vector rotated = GenericUtils.rotateOffset(new Vector(0, 0, 1), 180.0f);
        assertEquals(0.0, rotated.getX(), 1e-4);
        assertEquals(0.0, rotated.getY(), 1e-4);
        assertEquals(-1.0, rotated.getZ(), 1e-4);

        // local (1, 0, 0) should become (-1, 0, 0)
        Vector rotatedX = GenericUtils.rotateOffset(new Vector(1, 0, 0), 180.0f);
        assertEquals(-1.0, rotatedX.getX(), 1e-4);
        assertEquals(0.0, rotatedX.getY(), 1e-4);
        assertEquals(0.0, rotatedX.getZ(), 1e-4);
    }

    @Test
    void testRotateOffsetAt270Degrees() {
        // At yaw 270 (East, +X): local forward (0, 0, 1) should become (1, 0, 0)
        Vector rotated = GenericUtils.rotateOffset(new Vector(0, 0, 1), 270.0f);
        assertEquals(1.0, rotated.getX(), 1e-4);
        assertEquals(0.0, rotated.getY(), 1e-4);
        assertEquals(0.0, rotated.getZ(), 1e-4);

        // local right (1, 0, 0) should become (0, 0, -1) (-Z, North)
        Vector rotatedX = GenericUtils.rotateOffset(new Vector(1, 0, 0), 270.0f);
        assertEquals(0.0, rotatedX.getX(), 1e-4);
        assertEquals(0.0, rotatedX.getY(), 1e-4);
        assertEquals(-1.0, rotatedX.getZ(), 1e-4);
    }

    @Test
    void testCroupierPositionAcross4Orientations() {
        // Croupier local offset is (0, 0, -1.8)
        Vector localCroupier = new Vector(0, 0, -1.8);

        // Yaw 0 (South): croupier is at (0, 0, -1.8) (North of table, looking South)
        Vector r0 = GenericUtils.rotateOffset(localCroupier, 0.0f);
        assertEquals(0.0, r0.getX(), 1e-4);
        assertEquals(-1.8, r0.getZ(), 1e-4);

        // Yaw 90 (West): croupier is at (1.8, 0, 0) (East of table, looking West)
        Vector r90 = GenericUtils.rotateOffset(localCroupier, 90.0f);
        assertEquals(1.8, r90.getX(), 1e-4);
        assertEquals(0.0, r90.getZ(), 1e-4);

        // Yaw 180 (North): croupier is at (0, 0, 1.8) (South of table, looking North)
        Vector r180 = GenericUtils.rotateOffset(localCroupier, 180.0f);
        assertEquals(0.0, r180.getX(), 1e-4);
        assertEquals(1.8, r180.getZ(), 1e-4);

        // Yaw 270 (East): croupier is at (-1.8, 0, 0) (West of table, looking East)
        Vector r270 = GenericUtils.rotateOffset(localCroupier, 270.0f);
        assertEquals(-1.8, r270.getX(), 1e-4);
        assertEquals(0.0, r270.getZ(), 1e-4);
    }
}
