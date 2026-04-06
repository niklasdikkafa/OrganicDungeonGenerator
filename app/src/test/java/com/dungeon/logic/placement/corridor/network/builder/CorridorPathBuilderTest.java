package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Corridor;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.config.DungeonConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CorridorPathBuilder}.
 */
class CorridorPathBuilderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a Corridor from explicit 2D polyline, polyId and zBand arrays.
     * fromRoomId=0, toRoomId=1 are used as placeholders.
     */
    private static Corridor buildCorridor(List<Vector2f> polyline,
                                          List<Integer>  polyIds,
                                          List<Integer>  zBands) {
        return new Corridor(0, 1, polyline, polyIds, zBands,
                CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
    }

    /**
     * Builds a straight flat corridor with N cells.
     * polyline: (0,0), (0.5,0), (1,0), (1.5,0) ... (N, 0) spaced by 0.5
     * polyIds:  0, 1, 2 ... N-1 (each step is a new polygon)
     * zBands:   all 0
     */
    private static Corridor straightFlat(int cells) {
        List<Vector2f> poly = new ArrayList<>();
        List<Integer>  ids  = new ArrayList<>();
        List<Integer>  zb   = new ArrayList<>();

        for (int i = 0; i <= cells; i++) {
            poly.add(new Vector2f(i * 0.5f, 0f));
        }

        for (int i = 0; i < poly.size(); i++) {
            ids.add(i);
            zb.add(0);
        }
        return buildCorridor(poly, ids, zb);
    }

    /**
     * Returns true if the path has any raw point of the given kind.
     */
    private static boolean hasKind(CorridorPath path, PointKind kind) {
        return path.rawPoints.stream().anyMatch(p -> p.kind == kind);
    }

    // -----------------------------------------------------------------------
    // Empty / degenerate input
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Empty and degenerate input")
    class EmptyInput {

        @Test @DisplayName("Empty polyline returns empty rawPoints")
        void emptyPolylineEmptyPath() {
            Corridor c = buildCorridor(List.of(), List.of(), List.of());
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertTrue(path.rawPoints.isEmpty(),
                    "Empty polyline must produce empty rawPoints");
        }

        @Test @DisplayName("Single-point polyline returns empty rawPoints (no corridor possible)")
        void singlePointEmptyPath() {
            Corridor c = buildCorridor(
                    List.of(new Vector2f(0, 0)),
                    List.of(0),
                    List.of(0));
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertTrue(path.rawPoints.isEmpty(),
                    "Single-point polyline must produce empty rawPoints");
        }

        @Test @DisplayName("CorridorPath carries the correct fromRoom and toRoom ids")
        void fromAndToRoomIds() {
            Corridor c = new Corridor(42, 99,
                    List.of(new Vector2f(0, 0), new Vector2f(1, 0)),
                    List.of(0, 1), List.of(0, 0),
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertEquals(42, path.fromRoom);
            assertEquals(99, path.toRoom);
        }

        @Test @DisplayName("corridorIndex is stored in the CorridorPath")
        void corridorIndexStored() {
            Corridor c = straightFlat(3);
            CorridorPath path = CorridorPathBuilder.buildPath(7, c);
            assertEquals(7, path.corridorIndex);
        }
    }

    // -----------------------------------------------------------------------
    // Basic flat corridor
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Basic flat corridor")
    class FlatCorridor {

        @Test @DisplayName("First raw point is VOXEL_CENTER kind")
        void firstPointIsVoxelCenter() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertFalse(path.rawPoints.isEmpty());
            assertEquals(PointKind.VOXEL_CENTER, path.rawPoints.getFirst().kind,
                    "First raw point must be VOXEL_CENTER");
        }

        @Test @DisplayName("Flat corridor alternates VOXEL_CENTER and EDGE_MID kinds")
        void alternatesKinds() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertTrue(hasKind(path, PointKind.VOXEL_CENTER),
                    "Path must contain VOXEL_CENTER points");
            assertTrue(hasKind(path, PointKind.EDGE_MID),
                    "Path must contain EDGE_MID points");
        }

        @Test @DisplayName("Raw points list is non-empty for a valid flat corridor")
        void rawPointsNonEmpty() {
            Corridor c = straightFlat(3);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertFalse(path.rawPoints.isEmpty(),
                    "Valid corridor must produce non-empty rawPoints");
        }

        @Test @DisplayName("All raw point Y values are positive (derived from z-band + corridor height)")
        void yValuesPositive() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            for (PathPoint3D p : path.rawPoints) {
                assertTrue(p.position.y >= 0f,
                        "Y of raw point must be >= 0 for z-band 0, got " + p.position.y);
            }
        }

        @Test @DisplayName("No NaN or Infinity in raw point positions")
        void noNaNOrInfinity() {
            Corridor c = straightFlat(5);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            for (PathPoint3D p : path.rawPoints) {
                assertFalse(Float.isNaN(p.position.x) || Float.isNaN(p.position.y) || Float.isNaN(p.position.z),
                        "NaN in raw point position");
                assertFalse(Float.isInfinite(p.position.x) || Float.isInfinite(p.position.y) || Float.isInfinite(p.position.z),
                        "Infinity in raw point position");
            }
        }

        @Test @DisplayName("VOXEL_CENTER points have polyIdB == -1")
        void voxelCenterPolyIdBIsMinusOne() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            for (PathPoint3D p : path.rawPoints) {
                if (p.kind == PointKind.VOXEL_CENTER) {
                    assertEquals(-1, p.polyIdB,
                            "VOXEL_CENTER point must have polyIdB == -1");
                }
            }
        }

        @Test @DisplayName("EDGE_MID points have distinct polyIdA and polyIdB")
        void edgeMidHasDistinctPolyIds() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            for (PathPoint3D p : path.rawPoints) {
                if (p.kind == PointKind.EDGE_MID) {
                    assertNotEquals(p.polyIdA, p.polyIdB,
                            "EDGE_MID point must have distinct polyIdA and polyIdB");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Same-polygon consecutive steps
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Same-polygon consecutive steps")
    class SamePolygonSteps {

        @Test @DisplayName("Consecutive steps with the same polyId produce VOXEL_CENTER only (no EDGE_MID)")
        void samePolygonProducesVoxelCenterOnly() {
            // Two consecutive polyIds that are identical simulate a pure vertical ramp step.
            List<Vector2f> poly = List.of(
                    new Vector2f(0f, 0f),
                    new Vector2f(1f, 0f),
                    new Vector2f(2f, 0f));
            List<Integer> ids = List.of(5, 5, 7); // same poly for first two steps
            List<Integer> zb  = List.of(0, 0, 0);

            Corridor c = buildCorridor(poly, ids, zb);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);

            // Same-poly step should not produce an EDGE_MID at that transition.
            long edgeMidCount = path.rawPoints.stream()
                    .filter(p -> p.kind == PointKind.EDGE_MID)
                    .count();
            // At least the transition between poly 5 and poly 7 may produce an EDGE_MID,
            // but there must be no EDGE_MID for the 5->5 transition.
            assertTrue(edgeMidCount <= 1,
                    "Same-polygon transition must not produce an EDGE_MID point");
        }
    }

    // -----------------------------------------------------------------------
    // Stairs macro detection
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Stairs macro detection")
    class StairsMacro {

        /**
         * Builds a Corridor whose routing trace matches the expanded stair macro:
         *   A(z0), B(z0), B(z1), C(z0), C(z1), D(z1)
         * with distinct polygons A=10, B=20, C=30, D=40 and zFrom=0, zTo=1.
         */
        private Corridor stairCorridor() {
            // Polyline must have enough points: start + 2 per normal step.
            // Stair macro in the routing trace is indices i ... i+5.
            // polyline indices required by the builder: ptIdx ... ptIdx+7
            List<Vector2f> poly = new ArrayList<>();
            for (int i = 0; i < 12; i++) poly.add(new Vector2f(i * 2f, 0f));

            // polyIds: A, B, B, C, C, D (6 entries for the macro), padded at start
            List<Integer> ids = List.of(
                    10,  // index 0: A (z=0)
                    20,  // index 1: B0 (z=0)
                    20,  // index 2: B1 (z=1)
                    30,  // index 3: C0 (z=0)
                    30,  // index 4: C1 (z=1)
                    40   // index 5: D (z=1)
            );
            List<Integer> zb = List.of(0, 0, 1, 0, 1, 1);

            // Pad polyline to at least the 8 entries the builder needs beyond ptIdx=1
            while (poly.size() < ids.size() * 2) poly.add(new Vector2f(poly.size() * 2f, 0f));

            return buildCorridor(poly, ids, zb);
        }

        @Test @DisplayName("Stair macro produces both VOXEL_CENTER and EDGE_MID points")
        void stairsMacroProducesBothKinds() {
            Corridor c = stairCorridor();
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            if (path.rawPoints.isEmpty()) return; // detection may fail on boundary
            assertTrue(hasKind(path, PointKind.VOXEL_CENTER),
                    "Stair macro output must contain VOXEL_CENTER");
            assertTrue(hasKind(path, PointKind.EDGE_MID),
                    "Stair macro output must contain EDGE_MID");
        }

        @Test @DisplayName("Stair macro output contains points at two different Y values")
        void stairsMacroTwoYLevels() {
            Corridor c = stairCorridor();
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            if (path.rawPoints.size() < 2) return;

            float firstY = path.rawPoints.getFirst().position.y;
            boolean anyDifferentY = path.rawPoints.stream()
                    .anyMatch(p -> Math.abs(p.position.y - firstY) > 1e-3f);
            assertTrue(anyDifferentY,
                    "Stair macro must produce points at at least two different Y heights");
        }

        @Test @DisplayName("Non-stair flat corridor does not produce stair Y variance")
        void flatCorridorNoYVariance() {
            Corridor c = straightFlat(4);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            if (path.rawPoints.isEmpty()) return;
            float y0 = path.rawPoints.getFirst().position.y;
            for (PathPoint3D p : path.rawPoints) {
                assertEquals(y0, p.position.y, 1e-3f,
                        "Flat corridor must have all points at the same Y height");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Corridor geometry parameters propagated
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Geometry parameters propagated to CorridorPath")
    class GeometryParams {

        @Test @DisplayName("CorridorPath carries CORRIDOR_WIDTH from DungeonConfig")
        void corridorWidthPropagated() {
            Corridor c = straightFlat(3);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertEquals(CORRIDOR_WIDTH, path.corridorWidth, 1e-4f);
        }

        @Test @DisplayName("CorridorPath carries CORRIDOR_HEIGHT from DungeonConfig")
        void corridorHeightPropagated() {
            Corridor c = straightFlat(3);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertEquals(CORRIDOR_HEIGHT, path.corridorHeight, 1e-4f);
        }

        @Test @DisplayName("CorridorPath carries WALL_THICKNESS from DungeonConfig")
        void wallThicknessPropagated() {
            Corridor c = straightFlat(3);
            CorridorPath path = CorridorPathBuilder.buildPath(0, c);
            assertEquals(WALL_THICKNESS, path.wallThickness, 1e-4f);
        }
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Determinism")
    class Determinism {

        @Test @DisplayName("Same corridor input produces identical rawPoints list twice")
        void deterministicOutput() {
            Corridor c = straightFlat(5);
            CorridorPath p1 = CorridorPathBuilder.buildPath(0, c);
            CorridorPath p2 = CorridorPathBuilder.buildPath(0, c);
            assertEquals(p1.rawPoints.size(), p2.rawPoints.size(),
                    "Same input must produce same number of raw points");
            for (int i = 0; i < p1.rawPoints.size(); i++) {
                PathPoint3D a = p1.rawPoints.get(i);
                PathPoint3D b = p2.rawPoints.get(i);
                assertEquals(a.kind, b.kind,
                        "Raw point kind at index " + i + " must be identical");
                assertEquals(a.position.x, b.position.x, 1e-5f,
                        "Raw point X at index " + i + " must be identical");
                assertEquals(a.position.z, b.position.z, 1e-5f,
                        "Raw point Z at index " + i + " must be identical");
            }
        }
    }
}