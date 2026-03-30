package com.dungeon.logic.placement.room;

import com.dungeon.domain.Room;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.room.debug.DebugRoomData;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RoomPlacer")
class RoomPlacerTest {

    /** Deterministic grid used across all tests. */
    private BaseGrid grid;

    @BeforeEach
    void buildGrid() {
        grid = new BaseGridBuilder().build(new BaseGridConfig(4, 1.0f, 0.3f, 2), 0L);
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("throws for null grid")
        void nullGrid() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoomPlacer(null, 3, 8, 0L))
                    .withMessageContaining("grid");
        }

        @Test
        @DisplayName("throws for minRoomSize < 1")
        void minRoomSizeTooSmall() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoomPlacer(grid, 0, 8, 0L))
                    .withMessageContaining("minRoomSize");
        }

        @Test
        @DisplayName("throws when maxRoomSize < minRoomSize")
        void maxSmallerThanMin() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoomPlacer(grid, 5, 3, 0L))
                    .withMessageContaining("maxRoomSize");
        }

        @Test
        @DisplayName("minRoomSize == maxRoomSize is accepted")
        void equalMinMax() {
            assertThatNoException().isThrownBy(() -> new RoomPlacer(grid, 4, 4, 0L));
        }

        @Test
        @DisplayName("minRoomSize = 1 is accepted")
        void minRoomSizeOne() {
            assertThatNoException().isThrownBy(() -> new RoomPlacer(grid, 1, 5, 0L));
        }
    }

    // =========================================================================
    // generateRooms - return value
    // =========================================================================

    @Nested
    @DisplayName("generateRooms - return value")
    class GenerateRoomsResult {

        @Test
        @DisplayName("returns empty list for numRooms = 0")
        void zeroRooms() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 6, 0L);
            assertThat(placer.generateRooms(0)).isEmpty();
        }

        @Test
        @DisplayName("returns at most numRooms rooms")
        void atMostRequestedCount() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 42L);
            List<Room> rooms = placer.generateRooms(5);
            assertThat(rooms.size()).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("returns at least one room for a sufficiently large grid")
        void atLeastOneRoom() {
            RoomPlacer placer = new RoomPlacer(grid, 2, 5, 1L);
            List<Room> rooms = placer.generateRooms(3);
            assertThat(rooms).isNotEmpty();
        }

        @Test
        @DisplayName("each returned room is non-null")
        void noNullRooms() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 6, 7L);
            List<Room> rooms = placer.generateRooms(4);
            assertThat(rooms).doesNotContainNull();
        }
    }

    // =========================================================================
    // generateRooms - room properties
    // =========================================================================

    @Nested
    @DisplayName("generateRooms - room geometry")
    class RoomGeometryChecks {

        @Test
        @DisplayName("every room has at least 3 inner corners")
        void atLeastThreeInnerCorners() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 2L);
            for (Room r : placer.generateRooms(4)) {
                assertThat(r.getInnerCorners().size())
                        .as("room %d inner corners", r.getId())
                        .isGreaterThanOrEqualTo(3);
            }
        }

        @Test
        @DisplayName("every room has at least 3 outer corners")
        void atLeastThreeOuterCorners() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 3L);
            for (Room r : placer.generateRooms(4)) {
                assertThat(r.getOuterCorners().size())
                        .as("room %d outer corners", r.getId())
                        .isGreaterThanOrEqualTo(3);
            }
        }

        @Test
        @DisplayName("room height is positive")
        void heightPositive() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 5L);
            for (Room r : placer.generateRooms(4)) {
                assertThat(r.getHeight()).as("room %d height", r.getId()).isPositive();
            }
        }

        @Test
        @DisplayName("room zLevel is non-negative")
        void zLevelNonNegative() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 8L);
            for (Room r : placer.generateRooms(4)) {
                assertThat(r.getZLevel()).as("room %d zLevel", r.getId()).isGreaterThanOrEqualTo(0.0F);
            }
        }

        @Test
        @DisplayName("every room has a non-null interior point")
        void visualCenterNotNull() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 9L);
            for (Room r : placer.generateRooms(4)) {
                assertThat(r.getInteriorPoint()).as("room %d interior point", r.getId()).isNotNull();
            }
        }
    }

    // =========================================================================
    // generateRooms - collision / validity
    // =========================================================================

    @Nested
    @DisplayName("generateRooms - no overlapping rooms")
    class CollisionChecks {

        @RepeatedTest(3)
        @DisplayName("no two returned rooms have overlapping AABBs in 2D AND the same Z interval")
        void noOverlappingRooms() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 8, System.nanoTime());
            List<Room> rooms = placer.generateRooms(6);

            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    Room a = rooms.get(i);
                    Room b = rooms.get(j);
                    // Use the same conservative check as RoomValidator
                    assertThat(possiblyCollides(a, b))
                            .as("rooms %d and %d should not collide", a.getId(), b.getId())
                            .isFalse();
                }
            }
        }
    }

    // =========================================================================
    // generateRooms - determinism
    // =========================================================================

    @Nested
    @DisplayName("generateRooms - determinism")
    class Determinism {

        @Test
        @DisplayName("same seed produces the same number of rooms")
        void sameSeedSameCount() {
            RoomPlacer p1 = new RoomPlacer(grid, 3, 7, 111L);
            RoomPlacer p2 = new RoomPlacer(grid, 3, 7, 111L);
            assertThat(p1.generateRooms(5).size())
                    .isEqualTo(p2.generateRooms(5).size());
        }

        @Test
        @DisplayName("same seed produces rooms with the same IDs and zLevels")
        void sameSeedSameRooms() {
            RoomPlacer p1 = new RoomPlacer(grid, 3, 7, 222L);
            RoomPlacer p2 = new RoomPlacer(grid, 3, 7, 222L);
            List<Room> r1 = p1.generateRooms(4);
            List<Room> r2 = p2.generateRooms(4);
            assertThat(r1).hasSameSizeAs(r2);
            for (int i = 0; i < r1.size(); i++) {
                assertThat(r1.get(i).getZLevel())
                        .isEqualTo(r2.get(i).getZLevel());
            }
        }
    }

    // =========================================================================
    // BaseGrid immutability - central requirement
    // =========================================================================

    @Nested
    @DisplayName("BaseGrid immutability after room placement")
    class GridImmutabilityAfterPlacement {

        /**
         * Captures a deep snapshot of the grid's geometry and topology maps.
         * We store:
         * <ul>
         *     <li>vertex positions (by value, not reference)</li>
         *     <li>polygon vertex index arrays (by value)</li>
         *     <li>polygon center positions (by value)</li>
         *     <li>sizes of all topology maps</li>
         * </ul>
         */
        private GridSnapshot snapshot(BaseGrid g) {
            // Vertex positions
            List<float[]> verts = new ArrayList<>();
            for (Vector2f v : g.getVertices()) verts.add(new float[]{v.x, v.y});

            // All-polygon index arrays
            List<int[]> polyIndices = new ArrayList<>();
            for (Polygon p : g.getAllPolygons()) polyIndices.add(p.getVertexIndices().clone());

            // Polygon center positions
            Map<Integer, float[]> centers = new LinkedHashMap<>();
            List<Polygon> polys = g.getAllPolygons();
            for (int i = 0; i < polys.size(); i++) {
                Vector2f c = g.getPolygonCenters().get(polys.get(i));
                if (c != null) centers.put(i, new float[]{c.x, c.y});
            }

            // Topology sizes
            int nbSize    = g.getVertexNeighbors().size();
            int validNbSz = g.getValidVertexNeighbors().size();
            int v2pSize   = g.getVertexToPolygons().size();
            int pnSize    = g.getPolygonNeighbors().size();

            return new GridSnapshot(verts, polyIndices, centers,
                    nbSize, validNbSz, v2pSize, pnSize,
                    g.getSideCount(), g.getEdgeLength(), g.getOriginalVertexCount());
        }

        @Test
        @DisplayName("vertex count does not change after room placement")
        void vertexCountUnchanged() {
            int before = grid.getVertices().size();
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            assertThat(grid.getVertices().size()).isEqualTo(before);
        }

        @Test
        @DisplayName("polygon count does not change after room placement")
        void polygonCountUnchanged() {
            int before = grid.getAllPolygons().size();
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            assertThat(grid.getAllPolygons().size()).isEqualTo(before);
        }

        @Test
        @DisplayName("vertex positions are unchanged after room placement")
        void vertexPositionsUnchanged() {
            GridSnapshot before = snapshot(grid);
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            GridSnapshot after = snapshot(grid);

            assertThat(after.vertices).hasSameSizeAs(before.vertices);
            for (int i = 0; i < before.vertices.size(); i++) {
                assertThat(after.vertices.get(i)[0])
                        .as("vertex %d x", i)
                        .isEqualTo(before.vertices.get(i)[0]);
                assertThat(after.vertices.get(i)[1])
                        .as("vertex %d y", i)
                        .isEqualTo(before.vertices.get(i)[1]);
            }
        }

        @Test
        @DisplayName("polygon vertex indices are unchanged after room placement")
        void polygonIndicesUnchanged() {
            GridSnapshot before = snapshot(grid);
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            GridSnapshot after = snapshot(grid);

            assertThat(after.polyIndices).hasSameSizeAs(before.polyIndices);
            for (int i = 0; i < before.polyIndices.size(); i++) {
                assertThat(after.polyIndices.get(i))
                        .as("polygon %d indices", i)
                        .isEqualTo(before.polyIndices.get(i));
            }
        }

        @Test
        @DisplayName("polygon centers are unchanged after room placement")
        void polygonCentersUnchanged() {
            GridSnapshot before = snapshot(grid);
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            GridSnapshot after = snapshot(grid);

            for (Map.Entry<Integer, float[]> e : before.centers.entrySet()) {
                float[] a = after.centers.get(e.getKey());
                assertThat(a).as("center for polygon %d", e.getKey()).isNotNull();
                assertThat(a[0]).as("cx[%d]", e.getKey()).isEqualTo(e.getValue()[0]);
                assertThat(a[1]).as("cy[%d]", e.getKey()).isEqualTo(e.getValue()[1]);
            }
        }

        @Test
        @DisplayName("topology map sizes are unchanged after room placement")
        void topologySizesUnchanged() {
            GridSnapshot before = snapshot(grid);
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            GridSnapshot after = snapshot(grid);

            assertThat(after.nbSize)    .isEqualTo(before.nbSize);
            assertThat(after.validNbSz) .isEqualTo(before.validNbSz);
            assertThat(after.v2pSize)   .isEqualTo(before.v2pSize);
            assertThat(after.pnSize)    .isEqualTo(before.pnSize);
        }

        @Test
        @DisplayName("grid parameters (sideCount, edgeLength, originalVertexCount) are unchanged")
        void gridParametersUnchanged() {
            GridSnapshot before = snapshot(grid);
            new RoomPlacer(grid, 3, 7, 0L).generateRooms(8);
            GridSnapshot after = snapshot(grid);

            assertThat(after.sideCount)            .isEqualTo(before.sideCount);
            assertThat(after.edgeLength)            .isEqualTo(before.edgeLength);
            assertThat(after.originalVertexCount)   .isEqualTo(before.originalVertexCount);
        }

        @Test
        @DisplayName("multiple independent RoomPlacer runs on the same grid leave it unchanged")
        void multipleRunsLeaveGridUnchanged() {
            GridSnapshot original = snapshot(grid);
            for (int run = 0; run < 3; run++) {
                new RoomPlacer(grid, 3, 7, run).generateRooms(5);
            }
            GridSnapshot after = snapshot(grid);

            assertThat(after.vertices).hasSameSizeAs(original.vertices);
            assertThat(after.polyIndices).hasSameSizeAs(original.polyIndices);
            assertThat(after.nbSize).isEqualTo(original.nbSize);
        }

        // Snapshot value type
        private record GridSnapshot(
                List<float[]> vertices,
                List<int[]>   polyIndices,
                Map<Integer, float[]> centers,
                int nbSize, int validNbSz, int v2pSize, int pnSize,
                int sideCount, float edgeLength, int originalVertexCount) {}
    }

    // =========================================================================
    // generateSingleDebugRoom
    // =========================================================================

    @Nested
    @DisplayName("generateSingleDebugRoom")
    class DebugRoom {

        @Test
        @DisplayName("returns a non-null DebugRoomData for a reasonable room size")
        void returnsNonNull() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 0L);
            DebugRoomData debug = placer.generateSingleDebugRoom(5);
            assertThat(debug).isNotNull();
        }

        @Test
        @DisplayName("debug room has a non-null Room, cluster and boundaryPolygons")
        void debugRoomHasAllFields() {
            RoomPlacer placer = new RoomPlacer(grid, 3, 7, 1L);
            DebugRoomData debug = placer.generateSingleDebugRoom(4);
            if (debug == null) return;
            assertThat(debug.room).isNotNull();
            assertThat(debug.cluster).isNotNull();
            assertThat(debug.boundaryPolygons).isNotNull().isNotEmpty();
        }
    }

    // =========================================================================
    // Helper: conservative 2.5D collision test (mirrors RoomValidator)
    // =========================================================================

    private static boolean possiblyCollides(Room a, Room b) {
        // 2D AABB
        float aMinX = Float.MAX_VALUE, aMaxX = -Float.MAX_VALUE;
        float aMinY = Float.MAX_VALUE, aMaxY = -Float.MAX_VALUE;
        for (Vector2f v : a.getInnerCorners()) {
            aMinX = Math.min(aMinX, v.x - a.getWallThickness());
            aMaxX = Math.max(aMaxX, v.x + a.getWallThickness());
            aMinY = Math.min(aMinY, v.y - a.getWallThickness());
            aMaxY = Math.max(aMaxY, v.y + a.getWallThickness());
        }
        float bMinX = Float.MAX_VALUE, bMaxX = -Float.MAX_VALUE;
        float bMinY = Float.MAX_VALUE, bMaxY = -Float.MAX_VALUE;
        for (Vector2f v : b.getInnerCorners()) {
            bMinX = Math.min(bMinX, v.x - b.getWallThickness());
            bMaxX = Math.max(bMaxX, v.x + b.getWallThickness());
            bMinY = Math.min(bMinY, v.y - b.getWallThickness());
            bMaxY = Math.max(bMaxY, v.y + b.getWallThickness());
        }
        boolean aabb = aMinX <= bMaxX && bMinX <= aMaxX && aMinY <= bMaxY && bMinY <= aMaxY;
        if (!aabb) return false;

        // Z overlap
        double aZMin = a.getZLevel() - a.getFloorThickness();
        double aZMax = a.getZLevel() + a.getHeight() + a.getFloorThickness();
        double bZMin = b.getZLevel() - b.getFloorThickness();
        double bZMax = b.getZLevel() + b.getHeight() + b.getFloorThickness();
        return aZMin <= bZMax && bZMin <= aZMax;
    }
}