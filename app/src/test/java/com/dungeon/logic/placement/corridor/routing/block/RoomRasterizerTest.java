package com.dungeon.logic.placement.corridor.routing.block;

import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RoomRasterizer}.
 */
class RoomRasterizerTest {

    // -----------------------------------------------------------------------
    // Shared grid (built once)
    // -----------------------------------------------------------------------

    private static GridIndex INDEX;

    @BeforeAll
    static void buildGrid() {
        BaseGrid grid = new BaseGridBuilder()
                .build(new BaseGridConfig(5, 4f), 42L);
        INDEX = new GridIndex(grid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link RoomVolume2_5D} whose {@link RoomVolume2_5D#footprint()}
     * returns the given polygon.
     *
     * <p>{@link RoomVolume2_5D} is a record that delegates {@code footprint()} to
     * {@link Room#getOuterCorners()}. We therefore build a real {@link Room} where
     * {@code outerCorners == footprint} and {@code innerCorners} is a slightly
     * shrunk version of the same shape (so the Room constructor does not reject it).
     * {@code zBandIndex=0, zBandHeightBands=1} keeps the vertical span minimal.</p>
     */
    private static RoomVolume2_5D vol(List<Vector2f> outerFootprint) {
        List<Vector2f> inner = shrink(outerFootprint, 0.9f);
        Vector2f center = centroid(outerFootprint);
        Room room = new Room(inner, outerFootprint, center,
                0.3f, 0.3f, 0, 1);
        return new RoomVolume2_5D(room);
    }

    /** Axis-aligned square centered at origin with half-size {@code h}. */
    private static List<Vector2f> square(float h) {
        return List.of(
                new Vector2f(-h, -h),
                new Vector2f( h, -h),
                new Vector2f( h,  h),
                new Vector2f(-h,  h)
        );
    }

    /** Equilateral-ish triangle centered near origin. */
    private static List<Vector2f> triangle() {
        return List.of(
                new Vector2f( 0f,  6f),
                new Vector2f(-6f, -3f),
                new Vector2f( 6f, -3f)
        );
    }

    /** A tiny square far outside the routing grid. */
    private static List<Vector2f> tinyFarSquare() {
        float cx = 500f, cy = 500f, h = 0.5f;
        return List.of(
                new Vector2f(cx - h, cy - h),
                new Vector2f(cx + h, cy - h),
                new Vector2f(cx + h, cy + h),
                new Vector2f(cx - h, cy + h)
        );
    }

    /** Scales each vertex toward the centroid by {@code factor}. */
    private static List<Vector2f> shrink(List<Vector2f> poly, float factor) {
        if (poly == null || poly.isEmpty()) return List.of();
        Vector2f c = centroid(poly);
        return poly.stream()
                .map(p -> new Vector2f(
                        c.x + (p.x - c.x) * factor,
                        c.y + (p.y - c.y) * factor))
                .toList();
    }

    private static Vector2f centroid(List<Vector2f> poly) {
        if (poly == null || poly.isEmpty()) return new Vector2f(0, 0);
        float cx = 0, cy = 0;
        for (Vector2f p : poly) { cx += p.x; cy += p.y; }
        return new Vector2f(cx / poly.size(), cy / poly.size());
    }

    // -----------------------------------------------------------------------
    // Null / degenerate inputs
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Null and degenerate inputs")
    class DegenerateInputs {

        @Test
        @DisplayName("Returns empty list when outer corners list is empty")
        void emptyOuterCorners() {
            Room room = new Room(List.of(), List.of(), new Vector2f(),
                    0.3f, 0.3f, 0, 1);
            List<Integer> result = RoomRasterizer.rasterRoom(
                    INDEX, new RoomVolume2_5D(room), 0f);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty list when outer corners has fewer than 3 vertices")
        void twoVertexFootprint() {
            List<Vector2f> two = List.of(new Vector2f(0, 0), new Vector2f(5, 0));
            Room room = new Room(
                    List.of(new Vector2f(0.5f, 0f), new Vector2f(4.5f, 0f)),
                    two,
                    new Vector2f(2.5f, 0f),
                    0.3f, 0.3f, 0, 1);
            List<Integer> result = RoomRasterizer.rasterRoom(
                    INDEX, new RoomVolume2_5D(room), 0f);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Does not throw for a degenerate (collinear) polygon")
        void collinearPolygon() {
            List<Vector2f> collinear = List.of(
                    new Vector2f(0, 0),
                    new Vector2f(5, 0),
                    new Vector2f(10, 0)
            );
            assertDoesNotThrow(() -> RoomRasterizer.rasterRoom(
                    INDEX, vol(collinear), 0f));
        }
    }

    // -----------------------------------------------------------------------
    // Basic containment
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Basic containment")
    class BasicContainment {

        @Test
        @DisplayName("Large square at grid center returns at least one cell")
        void largeSquareReturnsCells() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(square(15f)), 0f);
            assertFalse(result.isEmpty(),
                    "A large square covering the grid center must capture at least one cell");
        }

        @Test
        @DisplayName("Returned IDs are valid polygon indices")
        void returnedIdsAreValidIndices() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(square(15f)), 0f);
            int polyCount = INDEX.polys.size();
            for (int id : result) {
                assertTrue(id >= 0 && id < polyCount,
                        "ID " + id + " is out of bounds [0, " + polyCount + ")");
            }
        }

        @Test
        @DisplayName("No duplicate IDs in result")
        void noDuplicateIds() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(square(15f)), 0f);
            long distinct = result.stream().distinct().count();
            assertEquals(distinct, result.size(),
                    "Result must not contain duplicate polygon IDs");
        }

        @Test
        @DisplayName("Tiny square far outside the grid returns no cells")
        void tinyFarSquareReturnsNoCells() {
            List<Integer> result = RoomRasterizer.rasterRoom(
                    INDEX, vol(tinyFarSquare()), 0f);
            assertTrue(result.isEmpty(),
                    "A room far outside the routing grid must not capture any cells");
        }

        @Test
        @DisplayName("Triangular footprint returns at least one cell")
        void triangleReturnsCells() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(triangle()), 0f);
            assertFalse(result.isEmpty(),
                    "A triangle covering the grid center must capture at least one cell");
        }
    }

    // -----------------------------------------------------------------------
    // Clearance radius behaviour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Clearance radius")
    class ClearanceRadius {

        @Test
        @DisplayName("Clearance > 0 returns at least as many cells as clearance == 0")
        void clearanceIncreasesOrEqualsCellCount() {
            RoomVolume2_5D v = vol(square(8f));
            int without = RoomRasterizer.rasterRoom(INDEX, v, 0f).size();
            int with    = RoomRasterizer.rasterRoom(INDEX, v, 3f).size();
            assertTrue(with >= without,
                    "Positive clearance must not reduce the cell count");
        }

        @Test
        @DisplayName("Large clearance captures more cells than zero clearance")
        void largeClearanceCapturesMore() {
            RoomVolume2_5D v = vol(square(4f));
            int without = RoomRasterizer.rasterRoom(INDEX, v,  0f).size();
            int withBig = RoomRasterizer.rasterRoom(INDEX, v, 10f).size();
            assertTrue(withBig > without,
                    "A large clearance should capture additional border cells");
        }

        @Test
        @DisplayName("No duplicate IDs with positive clearance")
        void noDuplicateIdsWithClearance() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(square(8f)), 2f);
            long distinct = result.stream().distinct().count();
            assertEquals(distinct, result.size(),
                    "Result with clearance must not contain duplicate IDs");
        }

        @Test
        @DisplayName("All IDs with clearance are valid polygon indices")
        void clearanceIdsAreValidIndices() {
            List<Integer> result = RoomRasterizer.rasterRoom(INDEX, vol(square(8f)), 2f);
            int polyCount = INDEX.polys.size();
            for (int id : result) {
                assertTrue(id >= 0 && id < polyCount,
                        "ID " + id + " with clearance is out of bounds [0, " + polyCount + ")");
            }
        }

        @Test
        @DisplayName("Negative clearance behaves the same as zero clearance")
        void negativeClearanceBehavesLikeZero() {
            RoomVolume2_5D v = vol(square(8f));
            int zeroCount = RoomRasterizer.rasterRoom(INDEX, v,  0f).size();
            int negCount  = RoomRasterizer.rasterRoom(INDEX, v, -1f).size();
            assertEquals(zeroCount, negCount,
                    "Negative clearance should produce the same result as zero clearance");
        }
    }

    // -----------------------------------------------------------------------
    // Monotonicity
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Monotonicity")
    class Monotonicity {

        @Test
        @DisplayName("Larger square captures at least as many cells as smaller square")
        void largerFootprintCapturesMoreOrEqual() {
            int small = RoomRasterizer.rasterRoom(INDEX, vol(square( 4f)), 0f).size();
            int large = RoomRasterizer.rasterRoom(INDEX, vol(square(12f)), 0f).size();
            assertTrue(large >= small,
                    "Larger footprint must capture at least as many cells as the smaller one");
        }
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("Same inputs produce identical results on repeated calls")
        void deterministicResults() {
            RoomVolume2_5D v = vol(square(10f));
            List<Integer> first  = RoomRasterizer.rasterRoom(INDEX, v, 1f);
            List<Integer> second = RoomRasterizer.rasterRoom(INDEX, v, 1f);
            assertEquals(first, second,
                    "rasterRoom must be deterministic for identical inputs");
        }
    }
}