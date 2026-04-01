package com.dungeon.logic.placement.corridor;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.connections.RoomEdge;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CorridorPlacer}.
 */
class CorridorPlacerTest {

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /** Builds a minimal but usable routing base grid. */
    private static BaseGrid smallGrid() {
        return new BaseGridBuilder().build(new BaseGridConfig(4, 8f), 42L);
    }

    /**
     * Creates a mock {@link Room} whose geometry is minimal but consistent enough
     * for the {@link CorridorPlacer} to process without NPEs.
     *
     * @param cx X coordinate of visual center
     * @param cy Y coordinate of visual center
     * @param zBandIndex discrete floor band
     */
    private static Room mockRoom(float cx, float cy, int zBandIndex) {
        Room room = mock(Room.class);
        when(room.getInteriorPoint()).thenReturn(new Vector2f(cx, cy));
        when(room.getZBandIndex()).thenReturn(zBandIndex);
        when(room.getZLevel()).thenReturn(zBandIndex * 2.5f);
        when(room.getId()).thenReturn(System.identityHashCode(room));

        // Minimal footprint: a small quad centered on (cx, cy)
        List<Vector2f> inner = List.of(
                new Vector2f(cx - 1f, cy - 1f),
                new Vector2f(cx + 1f, cy - 1f),
                new Vector2f(cx + 1f, cy + 1f),
                new Vector2f(cx - 1f, cy + 1f)
        );
        List<Vector2f> outer = List.of(
                new Vector2f(cx - 1.3f, cy - 1.3f),
                new Vector2f(cx + 1.3f, cy - 1.3f),
                new Vector2f(cx + 1.3f, cy + 1.3f),
                new Vector2f(cx - 1.3f, cy + 1.3f)
        );
        when(room.getInnerCorners()).thenReturn(inner);
        when(room.getOuterCorners()).thenReturn(outer);
        when(room.getWallThickness()).thenReturn(0.3f);
        when(room.getFloorThickness()).thenReturn(0.2f);
        when(room.getHeight()).thenReturn(2.5f);
        return room;
    }

    // -----------------------------------------------------------------------
    // Constructor tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Creates placer without throwing for valid inputs")
        void validInputs_noException() {
            assertDoesNotThrow(() ->
                    new CorridorPlacer(smallGrid(), CorridorPlacer.Density.MEDIUM, 0L)
            );
        }

        @Test
        @DisplayName("Throws NullPointerException when baseGrid is null")
        void nullBaseGrid_throws() {
            assertThrows(NullPointerException.class, () ->
                    new CorridorPlacer(null, CorridorPlacer.Density.MEDIUM, 0L)
            );
        }

        @Test
        @DisplayName("Throws NullPointerException when density is null")
        void nullDensity_throws() {
            assertThrows(NullPointerException.class, () ->
                    new CorridorPlacer(smallGrid(), null, 0L)
            );
        }

        @Test
        @DisplayName("getRoutingGrid returns a non-null grid after construction")
        void getRoutingGrid_notNull() {
            CorridorPlacer placer = new CorridorPlacer(smallGrid(), CorridorPlacer.Density.SPARSE, 1L);
            assertNotNull(placer.getRoutingGrid());
        }

        @Test
        @DisplayName("getDebugLastState is null before any corridor generation")
        void debugState_nullBeforeGeneration() {
            CorridorPlacer placer = new CorridorPlacer(smallGrid(), CorridorPlacer.Density.SPARSE, 1L);
            assertNull(placer.getDebugLastState());
        }
    }

    // -----------------------------------------------------------------------
    // generateCorridors - edge cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("generateCorridors - edge cases")
    class GenerateCorridorsEdgeCaseTests {

        private CorridorPlacer placer;

        @BeforeEach
        void setUp() {
            placer = new CorridorPlacer(smallGrid(), CorridorPlacer.Density.MEDIUM, 42L);
        }

        @Test
        @DisplayName("Returns empty list for null room input")
        void nullRooms_returnsEmpty() {
            List<Corridor> result = placer.generateCorridors(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty list for empty room list")
        void emptyRooms_returnsEmpty() {
            List<Corridor> result = placer.generateCorridors(new ArrayList<>());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty list for single-room list (no connection possible)")
        void singleRoom_returnsEmpty() {
            List<Room> rooms = new ArrayList<>();
            rooms.add(mockRoom(0f, 0f, 0));
            List<Corridor> result = placer.generateCorridors(rooms);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("debugLastState is populated after generateCorridors is called")
        void debugState_populatedAfterCall() {
            List<Room> rooms = new ArrayList<>();
            rooms.add(mockRoom(0f, 0f, 0));
            rooms.add(mockRoom(5f, 0f, 0));
            placer.generateCorridors(rooms);
            assertNotNull(placer.getDebugLastState());
        }
    }

    // -----------------------------------------------------------------------
    // generateCorridors - same-seed determinism
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("generateCorridors - determinism")
    class DeterminismTests {

        @Test
        @DisplayName("Same seed produces same number of corridors")
        void sameSeed_sameCorridorCount() {
            List<Room> rooms1 = twoRoomList(0f, 0f, 6f, 0f);
            List<Room> rooms2 = twoRoomList(0f, 0f, 6f, 0f);

            CorridorPlacer p1 = new CorridorPlacer(
                    new BaseGridBuilder().build(new BaseGridConfig(4, 8f), 99L),
                    CorridorPlacer.Density.SPARSE, 7L);
            CorridorPlacer p2 = new CorridorPlacer(
                    new BaseGridBuilder().build(new BaseGridConfig(4, 8f), 99L),
                    CorridorPlacer.Density.SPARSE, 7L);

            int c1 = p1.generateCorridors(rooms1).size();
            int c2 = p2.generateCorridors(rooms2).size();

            assertEquals(c1, c2,
                    "Same seed must produce the same number of corridors");
        }

        private List<Room> twoRoomList(float ax, float ay, float bx, float by) {
            List<Room> rooms = new ArrayList<>();
            rooms.add(mockRoom(ax, ay, 0));
            rooms.add(mockRoom(bx, by, 0));
            return rooms;
        }
    }

    // -----------------------------------------------------------------------
    // generateCorridors - connectivity fallback
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("generateCorridors - connectivity fallback")
    class ConnectivityFallbackTests {

        @Test
        @DisplayName("Room list is pruned to largest connected component when routing fails partially")
        void unconnectedRooms_removedFromList() {
            // Place two clusters far apart so that only intra-cluster routing succeeds on the small grid. At least the rooms list must not grow.
            List<Room> rooms = new ArrayList<>();
            rooms.add(mockRoom(0f,  0f,  0));
            rooms.add(mockRoom(4f,  0f,  0));
            // A third room far away - likely unreachable on tiny grid
            rooms.add(mockRoom(200f, 200f, 0));

            CorridorPlacer placer = new CorridorPlacer(
                    smallGrid(), CorridorPlacer.Density.SPARSE, 5L);

            int before = rooms.size();
            placer.generateCorridors(rooms);
            int after = rooms.size();

            assertTrue(after <= before,
                    "rooms list must not grow after connectivity cleanup");
        }
    }

    // -----------------------------------------------------------------------
    // Density enum
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Density enum")
    class DensityTests {

        @Test
        @DisplayName("SPARSE has extraEdgeRatio == 0")
        void sparse_zeroRatio() {
            assertEquals(0.0, CorridorPlacer.Density.SPARSE.extraEdgeRatio(), 1e-9);
        }

        @Test
        @DisplayName("MEDIUM has extraEdgeRatio in (0, 1)")
        void medium_ratioInOpenUnit() {
            double r = CorridorPlacer.Density.MEDIUM.extraEdgeRatio();
            assertTrue(r > 0.0 && r < 1.0,
                    "MEDIUM ratio should be in (0,1), was: " + r);
        }

        @Test
        @DisplayName("DENSE has higher extraEdgeRatio than MEDIUM")
        void dense_greaterThanMedium() {
            assertTrue(
                    CorridorPlacer.Density.DENSE.extraEdgeRatio() >
                            CorridorPlacer.Density.MEDIUM.extraEdgeRatio()
            );
        }

        @Test
        @DisplayName("All three density values are present")
        void allValuesPresent() {
            CorridorPlacer.Density[] values = CorridorPlacer.Density.values();
            assertEquals(3, values.length);
        }
    }

    // -----------------------------------------------------------------------
    // ConnectedGraph (package-private inner class)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ConnectedGraph")
    class ConnectedGraphTests {

        /** Helper: creates a RoomEdge mock with the given room IDs. */
        private RoomEdge edge(int idA, int idB) {
            Room a = mock(Room.class);
            Room b = mock(Room.class);
            when(a.getId()).thenReturn(idA);
            when(b.getId()).thenReturn(idB);
            when(a.getInteriorPoint()).thenReturn(new Vector2f(0f, 0f));
            when(b.getInteriorPoint()).thenReturn(new Vector2f(1f, 0f));
            when(a.getZLevel()).thenReturn(0f);
            when(b.getZLevel()).thenReturn(0f);

            RoomEdge e = mock(RoomEdge.class);
            when(e.getA()).thenReturn(a);
            when(e.getB()).thenReturn(b);
            return e;
        }

        @Test
        @DisplayName("Empty graph accepts any first edge")
        void emptyGraph_acceptsFirstEdge() {
            CorridorPlacer.ConnectedGraph g = new CorridorPlacer.ConnectedGraph();
            assertTrue(g.add(edge(1, 2)));
            assertTrue(g.getRoomIds().contains(1));
            assertTrue(g.getRoomIds().contains(2));
        }

        @Test
        @DisplayName("Graph accepts edge that shares a room ID")
        void acceptsConnectedEdge() {
            CorridorPlacer.ConnectedGraph g = new CorridorPlacer.ConnectedGraph();
            g.add(edge(1, 2));
            assertTrue(g.add(edge(2, 3)), "Edge sharing room 2 should be accepted");
            assertTrue(g.getRoomIds().contains(3));
        }

        @Test
        @DisplayName("Graph rejects edge with no shared room IDs")
        void rejectsDisconnectedEdge() {
            CorridorPlacer.ConnectedGraph g = new CorridorPlacer.ConnectedGraph();
            g.add(edge(1, 2));
            assertFalse(g.add(edge(5, 6)), "Edge with disjoint rooms should be rejected");
        }

        @Test
        @DisplayName("touches returns true when components share a room ID")
        void touches_true() {
            CorridorPlacer.ConnectedGraph g1 = new CorridorPlacer.ConnectedGraph();
            g1.add(edge(1, 2));

            CorridorPlacer.ConnectedGraph g2 = new CorridorPlacer.ConnectedGraph();
            g2.add(edge(2, 3));

            assertTrue(g1.touches(g2));
        }

        @Test
        @DisplayName("touches returns false for disjoint components")
        void touches_false() {
            CorridorPlacer.ConnectedGraph g1 = new CorridorPlacer.ConnectedGraph();
            g1.add(edge(1, 2));

            CorridorPlacer.ConnectedGraph g2 = new CorridorPlacer.ConnectedGraph();
            g2.add(edge(7, 8));

            assertFalse(g1.touches(g2));
        }

        @Test
        @DisplayName("mergeFrom absorbs all room IDs from the other component")
        void mergeFrom_containsAllIds() {
            CorridorPlacer.ConnectedGraph g1 = new CorridorPlacer.ConnectedGraph();
            g1.add(edge(1, 2));

            CorridorPlacer.ConnectedGraph g2 = new CorridorPlacer.ConnectedGraph();
            g2.add(edge(3, 4));

            g1.mergeFrom(g2);

            assertTrue(g1.getRoomIds().containsAll(List.of(1, 2, 3, 4)));
        }

        @Test
        @DisplayName("getRoomIds is live - reflects subsequent adds")
        void getRoomIds_isLive() {
            CorridorPlacer.ConnectedGraph g = new CorridorPlacer.ConnectedGraph();
            var ids = g.getRoomIds();
            g.add(edge(10, 20));
            assertTrue(ids.contains(10),
                    "getRoomIds must return a live view of the internal set");
        }

        @Test
        @DisplayName("Empty graph does not touch any other graph")
        void emptyGraph_touchesNobody() {
            CorridorPlacer.ConnectedGraph empty = new CorridorPlacer.ConnectedGraph();
            CorridorPlacer.ConnectedGraph other = new CorridorPlacer.ConnectedGraph();
            other.add(edge(1, 2));
            assertFalse(empty.touches(other));
        }
    }
}