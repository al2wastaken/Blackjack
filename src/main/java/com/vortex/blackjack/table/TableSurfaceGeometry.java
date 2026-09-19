package com.vortex.blackjack.table;

import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;

/** Local coordinates: dealer at negative Z, players at positive Z. */
final class TableSurfaceGeometry {
    private TableSurfaceGeometry() {}

    static List<Transformation> felt() {
        return chamferedSurface(2.45f, 1.50f, 1.35f, 0.75f, 0.15f);
    }

    static List<Transformation> rim() {
        return chamferedSurface(2.55f, 1.60f, 1.45f, 0.65f, 0.15f);
    }

    private static List<Transformation> chamferedSurface(float halfWidth, float frontHalfWidth,
                                                        float halfDepth, float y, float thickness) {
        float cut = halfWidth - frontHalfWidth;
        float shoulderZ = halfDepth - cut;
        float diamondSide = cut * (float) Math.sqrt(2.0);

        // Only the outer front quadrant of each diamond is exposed. The other
        // quadrants tuck inside the rectangles, producing a continuous 45-degree edge.
        // Lower the diamonds' top faces slightly to avoid coplanar flicker.
        return List.of(
                box(0, y, 0, frontHalfWidth * 2, thickness, halfDepth * 2, 0),
                box(-(halfWidth + frontHalfWidth) / 2, y, -cut / 2,
                        cut, thickness, halfDepth * 2 - cut, 0),
                box((halfWidth + frontHalfWidth) / 2, y, -cut / 2,
                        cut, thickness, halfDepth * 2 - cut, 0),
                box(-frontHalfWidth, y, shoulderZ, diamondSide, thickness - 0.001f,
                        diamondSide, (float) Math.PI / 4),
                box(frontHalfWidth, y, shoulderZ, diamondSide, thickness - 0.001f,
                        diamondSide, (float) Math.PI / 4)
        );
    }

    static Transformation box(float centerX, float bottomY, float centerZ,
                              float width, float height, float depth, float angle) {
        // BlockDisplay's cube starts at (0,0,0). Rotate its half extents too,
        // so rotation is around the requested center instead of a cube corner.
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new Transformation(
                new Vector3f(centerX - (cos * width + sin * depth) / 2,
                        bottomY, centerZ - (-sin * width + cos * depth) / 2),
                new AxisAngle4f(angle, 0, 1, 0),
                new Vector3f(width, height, depth),
                new AxisAngle4f()
        );
    }
}
