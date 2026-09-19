package com.vortex.blackjack.table;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableSurfaceGeometryTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void surfaceFillsExactlyTheSixSidedOutline(boolean rim) {
        List<Transformation> parts = rim ? TableSurfaceGeometry.rim() : TableSurfaceGeometry.felt();
        List<Matrix4f> inverses = parts.stream().map(p -> matrix(p).invert()).toList();
        float halfWidth = rim ? 2.55f : 2.45f;
        float frontHalfWidth = rim ? 1.60f : 1.50f;
        float halfDepth = rim ? 1.45f : 1.35f;

        // Check actual transformed vertices, not just the dimensions supplied to the builder.
        for (Transformation part : parts) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    Vector3f vertex = matrix(part).transformPosition(new Vector3f(x, 0, z));
                    assertTrue(inOutline(vertex.x, vertex.z, halfWidth, frontHalfWidth, halfDepth),
                            "Part protrudes outside outline: " + vertex);
                }
            }
        }

        // Sample both the interior and exterior to catch holes, reversed corners,
        // asymmetry and the old rectangular base extending under the cutouts.
        for (int ix = -145; ix <= 145; ix++) {
            for (int iz = -90; iz <= 90; iz++) {
                float x = ix * 0.02f;
                float z = iz * 0.02f;
                boolean actual = inverses.stream().anyMatch(m -> covers(m, x, z));
                assertEquals(inOutline(x, z, halfWidth, frontHalfWidth, halfDepth), actual,
                        "Incorrect coverage at " + x + ", " + z);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlappingPartsHaveDistinctTopPlanes(boolean rim) {
        List<Transformation> parts = rim ? TableSurfaceGeometry.rim() : TableSurfaceGeometry.felt();
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                Matrix4f a = matrix(parts.get(i)).invert();
                Matrix4f b = matrix(parts.get(j)).invert();
                for (int ix = -25; ix <= 25; ix++) {
                    for (int iz = -15; iz <= 15; iz++) {
                        float x = ix * 0.1f + 0.003f;
                        float z = iz * 0.1f + 0.003f;
                        if (covers(a, x, z) && covers(b, x, z)) {
                            float topA = parts.get(i).getTranslation().y + parts.get(i).getScale().y;
                            float topB = parts.get(j).getTranslation().y + parts.get(j).getScale().y;
                            assertTrue(Math.abs(topA - topB) > 0.0001f, "Coplanar overlapping faces");
                        }
                    }
                }
            }
        }
    }

    private static Matrix4f matrix(Transformation part) {
        return new Matrix4f().translation(part.getTranslation())
                .rotate(part.getLeftRotation()).scale(part.getScale()).rotate(part.getRightRotation());
    }

    private static boolean covers(Matrix4f inverse, float x, float z) {
        Vector3f local = inverse.transformPosition(new Vector3f(x, 0, z));
        return local.x >= -0.00001f && local.x <= 1.00001f
                && local.z >= -0.00001f && local.z <= 1.00001f;
    }

    private static boolean inOutline(float x, float z, float halfWidth, float frontHalfWidth, float halfDepth) {
        return Math.abs(x) <= halfWidth + 0.00001f && Math.abs(z) <= halfDepth + 0.00001f
                && Math.abs(x) + z <= frontHalfWidth + halfDepth + 0.00001f;
    }
}
