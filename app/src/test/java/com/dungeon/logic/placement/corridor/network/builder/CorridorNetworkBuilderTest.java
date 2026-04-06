package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathFrameSample;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid;
import com.dungeon.logic.placement.corridor.routing.path.NodeState;
import com.dungeon.logic.placement.corridor.routing.path.PathFinder3D;
import com.dungeon.logic.placement.corridor.routing.path.RoutingParams;
import com.dungeon.logic.factory.CorridorFactory;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.config.DungeonConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link CorridorNetworkBuilder}.
 */
class CorridorNetworkBuilderTest {

    private static GridIndex INDEX;
    private static final int Z_BANDS = 10;

    @BeforeAll
    static void buildIndex() {
        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(5, 5f), 42L);
        INDEX = new GridIndex(grid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal Room centered at (cx, cz) with the given zBandIndex.
     */
    private static Room makeRoom(float cx, float cz, int zBandIndex) {
        float h = 4f;
        List<Vector2f> inner = List.of(
                new Vector2f(cx - h, cz - h), new Vector2f(cx + h, cz - h),
                new Vector2f(cx + h, cz + h), new Vector2f(cx - h, cz + h));
        List<Vector2f> outer = List.of(
                new Vector2f(cx-h-0.3f, cz-h-0.3f), new Vector2f(cx+h+0.3f, cz-h-0.3f),
                new Vector2f(cx+h+0.3f, cz+h+0.3f), new Vector2f(cx-h-0.3f, cz+h+0.3f));
        return new Room(inner, outer, new Vector2f(cx, cz), 0.3f, 0.3f, zBandIndex, 1);
    }

    /**
     * Routes a path between two polygons on the given grid and wraps it in a
     * Corridor. fromRoomId and toRoomId must be real Room.getId() values.
     */
    private static Corridor routeCorridor(int fromPoly, int fromZ,
                                          int toPoly,   int toZ,
                                          VoxelStateGrid grid,
                                          int fromRoomId, int toRoomId) {
        RoutingParams rp = new RoutingParams();
        rp.noiseWeight = 0f;
        List<NodeState> path = PathFinder3D.findPath(
                INDEX, fromPoly, fromZ, toPoly, toZ, grid, rp);
        if (path == null || path.size() < 2) return null;
        return CorridorFactory.createFromPath(
                fromRoomId, toRoomId, INDEX, path,
                CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
    }

    /** BFS to find the polygon farthest from index 0. */
    private static int[] distantPair() {
        int src = 0;
        int[] dist = new int[INDEX.polys.size()];
        java.util.Arrays.fill(dist, -1);
        dist[src] = 0;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(src);
        int best = src;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : INDEX.neighbors[cur]) {
                if (dist[nb] == -1) {
                    dist[nb] = dist[cur] + 1;
                    q.add(nb);
                    if (dist[nb] > dist[best]) best = nb;
                }
            }
        }
        return new int[]{src, best};
    }

    /**
     * Builds a CorridorNetwork from a single routed corridor between the
     * two endpoints of distantPair(). Returns null if routing fails.
     */
    private static CorridorNetwork buildSingleCorridorNet() {
        int[] pair = distantPair();
        VoxelStateGrid grid = new VoxelStateGrid(INDEX.polys.size(), Z_BANDS);

        // Create rooms FIRST
        Room r0 = makeRoom(INDEX.centers[pair[0]].x, INDEX.centers[pair[0]].y, 0);
        Room r1 = makeRoom(INDEX.centers[pair[1]].x, INDEX.centers[pair[1]].y, 0);

        Corridor c = routeCorridor(pair[0], 0, pair[1], 0, grid, r0.getId(), r1.getId());
        if (c == null) return null;

        return CorridorNetworkBuilder.build(
                List.of(c), List.of(r0, r1), INDEX.grid.getEdgeLength());
    }

    // -----------------------------------------------------------------------
    // Null guard
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Null guard")
    class NullGuard {

        @Test @DisplayName("Throws NullPointerException when corridors is null")
        void nullCorridorsThrows() {
            assertThrows(NullPointerException.class,
                    () -> CorridorNetworkBuilder.build(null, List.of(), 5f));
        }
    }

    // -----------------------------------------------------------------------
    // Empty input
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Empty input")
    class EmptyInput {

        @Test @DisplayName("Empty corridor list returns non-null CorridorNetwork")
        void emptyCorridorsNonNull() {
            assertNotNull(CorridorNetworkBuilder.build(List.of(), List.of(), 5f));
        }

        @Test @DisplayName("Empty corridor list produces empty graph")
        void emptyCorridorsEmptyGraph() {
            assertTrue(CorridorNetworkBuilder.build(List.of(), List.of(), 5f)
                    .graph.nodes.isEmpty());
        }

        @Test @DisplayName("Empty corridor list produces empty paths list")
        void emptyCorridorsNoPaths() {
            assertTrue(CorridorNetworkBuilder.build(List.of(), List.of(), 5f)
                    .paths.isEmpty());
        }

        @Test @DisplayName("Null entry in corridor list is skipped without throwing")
        void nullCorridorSkipped() {
            List<Corridor> list = new ArrayList<>();
            list.add(null);
            assertDoesNotThrow(() -> CorridorNetworkBuilder.build(list, List.of(), 5f));
        }
    }

    // -----------------------------------------------------------------------
    // Geometric parameters
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Geometric parameters")
    class GeometricParams {

        @Test @DisplayName("CorridorNetwork carries CORRIDOR_WIDTH from DungeonConfig")
        void corridorWidthSet() {
            assertEquals(CORRIDOR_WIDTH,
                    CorridorNetworkBuilder.build(List.of(), List.of(), 5f).corridorWidth, 1e-4f);
        }

        @Test @DisplayName("CorridorNetwork carries CORRIDOR_HEIGHT from DungeonConfig")
        void corridorHeightSet() {
            assertEquals(CORRIDOR_HEIGHT,
                    CorridorNetworkBuilder.build(List.of(), List.of(), 5f).corridorHeight, 1e-4f);
        }

        @Test @DisplayName("CorridorNetwork carries WALL_THICKNESS from DungeonConfig")
        void wallThicknessSet() {
            assertEquals(WALL_THICKNESS,
                    CorridorNetworkBuilder.build(List.of(), List.of(), 5f).wallThickness, 1e-4f);
        }

        @Test @DisplayName("CorridorNetwork carries the provided routingGridEdgeLength")
        void routingCellSizeSet() {
            assertEquals(7.5f,
                    CorridorNetworkBuilder.build(List.of(), List.of(), 7.5f).routingCellSize, 1e-4f);
        }
    }

    // -----------------------------------------------------------------------
    // Single-corridor pipeline
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Single corridor pipeline")
    class SingleCorridor {

        @Test @DisplayName("Single corridor produces at least one CorridorPath")
        void atLeastOnePath() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            assertFalse(net.paths.isEmpty(),
                    "Single routed corridor must produce at least one path");
        }

        @Test @DisplayName("Graph has at least 2 nodes for a single corridor")
        void atLeastTwoNodes() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            assertTrue(net.graph.nodes.size() >= 2);
        }

        @Test @DisplayName("Each path has exactly as many samples as nodeIds")
        void samplesMatchNodeIds() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            for (CorridorPath p : net.paths) {
                assertEquals(p.nodeIds.size(), p.samples.size(),
                        "samples list must have the same size as nodeIds");
            }
        }

        @Test @DisplayName("Every sample references a valid graph node id")
        void sampleNodeIdsValid() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            int nodeCount = net.graph.nodes.size();
            for (CorridorPath p : net.paths) {
                for (PathFrameSample s : p.samples) {
                    assertTrue(s.graphNodeId >= 0 && s.graphNodeId < nodeCount,
                            "Sample graphNodeId " + s.graphNodeId + " is out of range [0," + nodeCount + ")");
                }
            }
        }

        @Test @DisplayName("Non-junction frame-enabled nodes with degree >= 1 have non-zero tangents")
        void frameEnabledNodeHasTangent() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            for (GraphNode n : net.graph.nodes) {
                if (n.frameDisabled) continue;
                if (net.graph.degree(n.id) == 0) continue;
                assertFalse(n.tangent.lengthSquared() < 1e-8f,
                        "Node " + n.id + " must have non-zero tangent after buildFrames, but tangent is " + n.tangent.lengthSquared());
            }
        }

        @Test @DisplayName("First and last samples in each path are marked as room endpoints")
        void endpointsMarked() {
            CorridorNetwork net = buildSingleCorridorNet();
            if (net == null) return;
            for (CorridorPath p : net.paths) {
                if (p.samples.isEmpty()) continue;
                assertTrue(p.samples.getFirst().isRoomEndpointSample,
                        "First sample must be a room endpoint");
                assertTrue(p.samples.getLast().isRoomEndpointSample,
                        "Last sample must be a room endpoint");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Semantic node reuse across corridors
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Semantic node reuse")
    class NodeReuse {

        @Test @DisplayName("Two corridors sharing a waypoint produce fewer nodes than total raw points")
        void nodeReuseReducesCount() {
            int[] pair = distantPair();
            int mid = INDEX.neighbors[pair[0]].length > 0
                    ? INDEX.neighbors[pair[0]][0] : pair[0];
            VoxelStateGrid grid = new VoxelStateGrid(INDEX.polys.size(), Z_BANDS);

            // Rooms are created first so their ids are known.
            Room rA = makeRoom(INDEX.centers[pair[0]].x, INDEX.centers[pair[0]].y, 0);
            Room rMid = makeRoom(INDEX.centers[mid].x,   INDEX.centers[mid].y,     0);
            Room rB = makeRoom(INDEX.centers[pair[1]].x, INDEX.centers[pair[1]].y, 0);

            Corridor c0 = routeCorridor(pair[0], 0, mid,     0, grid, rA.getId(),   rMid.getId());
            Corridor c1 = routeCorridor(mid,     0, pair[1], 0, grid, rMid.getId(), rB.getId());
            if (c0 == null || c1 == null) return;

            CorridorNetwork net = CorridorNetworkBuilder.build(
                    List.of(c0, c1), List.of(rA, rMid, rB), INDEX.grid.getEdgeLength());

            int totalRaw = net.paths.stream().mapToInt(p -> p.rawPoints.size()).sum();
            int graphNodes = net.graph.nodes.size();
            assertTrue(graphNodes <= totalRaw,
                    "Graph nodes (" + graphNodes + ") must be <= sum of raw points (" + totalRaw +
                            ") due to semantic node reuse");
        }
    }

    // -----------------------------------------------------------------------
    // Junction detection
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Junction detection after build")
    class JunctionDetection {

        @Test @DisplayName("Hub polygon shared by 3 corridors produces at least one junction node")
        void sharedHubIsJunction() {
            int hub = 0;
            int[] nbs = INDEX.neighbors[hub];
            if (nbs.length < 3) return;

            VoxelStateGrid grid = new VoxelStateGrid(INDEX.polys.size(), Z_BANDS);
            List<Corridor> corridors = new ArrayList<>();
            List<Room> rooms = new ArrayList<>();

            Room hubRoom = makeRoom(INDEX.centers[hub].x, INDEX.centers[hub].y, 0);
            rooms.add(hubRoom);

            for (int i = 0; i < 3; i++) {
                Room leafRoom = makeRoom(INDEX.centers[nbs[i]].x, INDEX.centers[nbs[i]].y, 0);
                rooms.add(leafRoom);
                Corridor c = routeCorridor(hub, 0, nbs[i], 0, grid,
                        hubRoom.getId(), leafRoom.getId());
                if (c == null) return;
                corridors.add(c);
            }

            CorridorNetwork net = CorridorNetworkBuilder.build(
                    corridors, rooms, INDEX.grid.getEdgeLength());

            boolean anyJunction = net.graph.nodes.stream().anyMatch(n -> n.isJunction);
            assertTrue(anyJunction,
                    "A hub reached by 3 corridors must produce at least one junction node");
        }
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Determinism")
    class Determinism {

        @Test @DisplayName("Building the same corridors twice produces the same node count")
        void deterministicNodeCount() {
            int[] pair = distantPair();
            VoxelStateGrid grid = new VoxelStateGrid(INDEX.polys.size(), Z_BANDS);

            Room r0 = makeRoom(INDEX.centers[pair[0]].x, INDEX.centers[pair[0]].y, 0);
            Room r1 = makeRoom(INDEX.centers[pair[1]].x, INDEX.centers[pair[1]].y, 0);
            Corridor c = routeCorridor(pair[0], 0, pair[1], 0, grid, r0.getId(), r1.getId());
            if (c == null) return;

            CorridorNetwork n1 = CorridorNetworkBuilder.build(
                    List.of(c), List.of(r0, r1), INDEX.grid.getEdgeLength());
            CorridorNetwork n2 = CorridorNetworkBuilder.build(
                    List.of(c), List.of(r0, r1), INDEX.grid.getEdgeLength());

            assertEquals(n1.graph.nodes.size(), n2.graph.nodes.size(),
                    "Node count must be deterministic for the same input");
        }
    }
}