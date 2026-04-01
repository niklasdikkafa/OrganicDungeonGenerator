package com.dungeon.logic.placement.corridor.routing.occupancy;

import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.routing.block.RoomVolume2_5D;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dungeon.config.DungeonConfig.NUMBER_OF_VOXELS_PER_POLYGON;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VoxelStateBuilder}.
 */
class VoxelStateBuilderTest {

    private static GridIndex INDEX;

    @BeforeAll
    static void buildIndex() {
        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(4, 6f), 42L);
        INDEX = new GridIndex(grid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static RoomVolume2_5D vol(List<Vector2f> outer, int zBandIndex, int zBandHeightBands) {
        List<Vector2f> inner = shrink(outer, 0.9f);
        Vector2f center = centroid(outer);
        Room room = new Room(inner, outer, center, 0.3f, 0.3f, zBandIndex, zBandHeightBands);
        return new RoomVolume2_5D(room);
    }

    private static List<Vector2f> square(float h) {
        return List.of(
                new Vector2f(-h, -h), new Vector2f(h, -h),
                new Vector2f(h,  h),  new Vector2f(-h, h));
    }

    private static List<Vector2f> shrink(List<Vector2f> poly, float f) {
        Vector2f c = centroid(poly);
        return poly.stream()
                .map(p -> new Vector2f(c.x + (p.x-c.x)*f, c.y + (p.y-c.y)*f))
                .toList();
    }

    private static Vector2f centroid(List<Vector2f> p) {
        float cx = 0, cy = 0;
        for (Vector2f v : p) { cx += v.x; cy += v.y; }
        return new Vector2f(cx/p.size(), cy/p.size());
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Constructor")
    class Constructor {

        @Test @DisplayName("Constructs without throwing for valid index")
        void constructsOk() {
            assertDoesNotThrow(() -> new VoxelStateBuilder(INDEX));
        }
    }

    // -----------------------------------------------------------------------
    // buildInitialState - return value
    // -----------------------------------------------------------------------
    @Nested @DisplayName("buildInitialState - return value")
    class ReturnValue {

        @Test @DisplayName("Returns non-null grid for empty volume list")
        void nonNullForEmpty() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            assertNotNull(b.buildInitialState(List.of(), 0f));
        }

        @Test @DisplayName("Grid dimensions match index poly count and DungeonConfig zBands")
        void gridDimensions() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            VoxelStateGrid g = b.buildInitialState(List.of(), 0f);
            assertEquals(INDEX.polys.size(), g.polyCount());
            assertEquals(NUMBER_OF_VOXELS_PER_POLYGON, g.zBands());
        }

        @Test @DisplayName("All cells FREE_SPACE when no volumes provided")
        void allFreeForEmpty() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            VoxelStateGrid g = b.buildInitialState(List.of(), 0f);
            for (int p = 0; p < g.polyCount(); p++)
                for (int z = 0; z < g.zBands(); z++)
                    assertEquals(VoxelState.FREE_SPACE, g.getState(p, z),
                            "Cell (" + p + "," + z + ") must be FREE when no rooms provided");
        }
    }

    // -----------------------------------------------------------------------
    // buildInitialState - room blocking
    // -----------------------------------------------------------------------
    @Nested @DisplayName("buildInitialState - room blocking")
    class RoomBlocking {

        @Test @DisplayName("At least one ROOM cell after adding a large room at zBand 0")
        void atLeastOneRoomCell() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            RoomVolume2_5D v = vol(square(12f), 0, 1);
            VoxelStateGrid g = b.buildInitialState(List.of(v), 0f);

            boolean found = false;
            for (int p = 0; p < g.polyCount() && !found; p++)
                if (g.isRoom(p, 0)) found = true;
            assertTrue(found, "At least one polygon must be marked ROOM");
        }

        @Test @DisplayName("No ROOM cell above zBandMax for a single-band room")
        void noRoomAboveMaxBand() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            int zBand = 2;
            RoomVolume2_5D v = vol(square(12f), zBand, 1); // occupies only band 2
            VoxelStateGrid g = b.buildInitialState(List.of(v), 0f);

            for (int p = 0; p < g.polyCount(); p++)
                for (int z = 0; z < g.zBands(); z++)
                    if (z != zBand)
                        assertFalse(g.isRoom(p, z),
                                "ROOM must only appear at zBand=" + zBand + ", found at (" + p + "," + z + ")");
        }

        @Test @DisplayName("Multi-band room: bottom band is ROOM, upper bands are BORDER")
        void multiBandRoomBlocking() {
            int bandStart = 1;
            int bandHeight = 3; // occupies bands 1, 2, 3
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            RoomVolume2_5D v = vol(square(12f), bandStart, bandHeight);
            VoxelStateGrid g = b.buildInitialState(List.of(v), 0f);

            boolean foundRoom = false, foundBorderAbove = false;
            for (int p = 0; p < g.polyCount(); p++) {
                if (g.isRoom(p, bandStart)) foundRoom = true;
                for (int z = bandStart + 1; z < bandStart + bandHeight; z++)
                    if (g.isRoomBorder(p, z)) foundBorderAbove = true;
            }
            assertTrue(foundRoom, "Bottom band must contain at least one ROOM cell");
            assertTrue(foundBorderAbove, "Bands above bottom must contain at least one BORDER cell");
        }

        @Test @DisplayName("Vertical clearance: band below zBandMin is BORDER (if in range)")
        void verticalClearanceBelowRoom() {
            int zBand = 2;
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            RoomVolume2_5D v = vol(square(12f), zBand, 1);
            VoxelStateGrid g = b.buildInitialState(List.of(v), 0f);

            // band zBand-1 = 1 should have BORDER cells for the rasterized polygons
            boolean foundBorder = false;
            for (int p = 0; p < g.polyCount(); p++)
                if (g.isRoomBorder(p, zBand - 1)) { foundBorder = true; break; }
            assertTrue(foundBorder, "Band below room must contain BORDER clearance cells");
        }

        @Test @DisplayName("Vertical clearance: band above zBandMax is BORDER (if in range)")
        void verticalClearanceAboveRoom() {
            int zBand = 1;
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            RoomVolume2_5D v = vol(square(12f), zBand, 1);
            VoxelStateGrid g = b.buildInitialState(List.of(v), 0f);

            boolean foundBorder = false;
            for (int p = 0; p < g.polyCount(); p++)
                if (g.isRoomBorder(p, zBand + 1)) { foundBorder = true; break; }
            assertTrue(foundBorder, "Band above room must contain BORDER clearance cells");
        }

        @Test @DisplayName("Positive clearance radius blocks more cells than zero radius")
        void clearanceRadiusBlocksMore() {
            VoxelStateBuilder b = new VoxelStateBuilder(INDEX);
            RoomVolume2_5D v = vol(square(6f), 0, 1);

            VoxelStateGrid g0 = b.buildInitialState(List.of(v), 0f);
            VoxelStateGrid gc = b.buildInitialState(List.of(v), 4f);

            int blocked0 = countNonFree(g0);
            int blockedC = countNonFree(gc);
            assertTrue(blockedC >= blocked0,
                    "Positive clearance must block at least as many cells as zero clearance");
        }

        private int countNonFree(VoxelStateGrid g) {
            int n = 0;
            for (int p = 0; p < g.polyCount(); p++)
                for (int z = 0; z < g.zBands(); z++)
                    if (g.getState(p, z) != VoxelState.FREE_SPACE) n++;
            return n;
        }
    }
}