package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dungeon.config.DungeonConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalGraphBuilder}.
 */
class GlobalGraphBuilderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static CorridorNetwork emptyNet() {
        return new CorridorNetwork(CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS, 6f);
    }

    /**
     * Creates a Room centered at (cx, cz) and places it in a roomsById map.
     */
    private static Room makeRoom(float cx, float cz, Map<Integer, Room> roomsById) {
        float h = 4f;
        List<Vector2f> inner = List.of(
                new Vector2f(cx - h, cz - h), new Vector2f(cx + h, cz - h),
                new Vector2f(cx + h, cz + h), new Vector2f(cx - h, cz + h));
        List<Vector2f> outer = List.of(
                new Vector2f(cx-h-0.3f, cz-h-0.3f), new Vector2f(cx+h+0.3f, cz-h-0.3f),
                new Vector2f(cx+h+0.3f, cz+h+0.3f), new Vector2f(cx-h-0.3f, cz+h+0.3f));
        Room r = new Room(inner, outer, new Vector2f(cx, cz), 0.3f, 0.3f, 0, 1);
        roomsById.put(r.getId(), r);
        return r;
    }

    /**
     * Creates a VOXEL_CENTER PathPoint3D at the given world position.
     * Doesn't use {@code C_REDUCER}.
     */
    private static PathPoint3D voxelPt(int polyId, float x, float z) {
        return new PathPoint3D(PointKind.VOXEL_CENTER,
                new Vector3f(x, CORRIDOR_HEIGHT * 0.5f, z), polyId, -1, (short) 0);
    }

    /**
     * Creates an EDGE_MID PathPoint3D between two poly ids.
     * Doesn't use {@code C_REDUCER}.
     */
    private static PathPoint3D edgePt(int a, int b, float x, float z) {
        return new PathPoint3D(PointKind.EDGE_MID,
                new Vector3f(x, CORRIDOR_HEIGHT * 0.5f, z), a, b, (short) 0);
    }

    /**
     * Builds a minimal two-point CorridorPath using the given room ids as
     * endpoint room ids. The first raw point is placed at (startX, 0) and the
     * last at (endX, 0). Both are VOXEL_CENTER kind so their positions lie well
     * inside the large rooms created by makeRoom.
     */
    private static CorridorPath twoPointPath(int fromRoomId, int toRoomId,
                                             float startX, float endX, int corridorIndex) {
        CorridorPath p = new CorridorPath(fromRoomId, toRoomId, corridorIndex,
                CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
        p.rawPoints.add(voxelPt(1000 + corridorIndex * 10,     startX, 0f));
        p.rawPoints.add(voxelPt(1000 + corridorIndex * 10 + 1, endX,   0f));
        return p;
    }

    // -----------------------------------------------------------------------
    // Empty paths list
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Empty paths list")
    class EmptyPaths {

        @Test @DisplayName("Empty paths list leaves graph empty")
        void emptyPathsEmptyGraph() {
            CorridorNetwork net = emptyNet();
            GlobalGraphBuilder.buildGlobalGraph(net, Map.of());
            assertTrue(net.graph.nodes.isEmpty());
        }

        @Test @DisplayName("Empty paths list does not throw")
        void emptyPathsNoThrow() {
            CorridorNetwork net = emptyNet();
            assertDoesNotThrow(() -> GlobalGraphBuilder.buildGlobalGraph(net, Map.of()));
        }
    }

    // -----------------------------------------------------------------------
    // Single path
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Single path")
    class SinglePath {

        @Test @DisplayName("Two raw points produce exactly two graph nodes")
        void twoPointsTwoNodes() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            net.paths.add(twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0));
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            assertEquals(2, net.graph.nodes.size(),
                    "Two distinct raw points must produce exactly two nodes");
        }

        @Test @DisplayName("nodeIds list size equals raw points size for non-duplicate path")
        void nodeIdsMatchRawPoints() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0);
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            assertEquals(path.rawPoints.size(), path.nodeIds.size(),
                    "nodeIds must have as many entries as raw points when all are distinct");
        }

        @Test @DisplayName("An undirected edge is added between the two nodes")
        void edgeBetweenTwoNodes() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            net.paths.add(twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0));
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            int a = net.graph.nodes.get(0).id;
            int b = net.graph.nodes.get(1).id;
            assertTrue(net.graph.adjacency.get(a).stream().anyMatch(e -> e.to == b),
                    "Edge a->b must exist after buildGlobalGraph");
            assertTrue(net.graph.adjacency.get(b).stream().anyMatch(e -> e.to == a),
                    "Edge b->a must exist after buildGlobalGraph");
        }

        @Test @DisplayName("First node is marked as room endpoint with correct room id")
        void firstNodeEndpoint() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0);
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            int firstNodeId = path.nodeIds.getFirst();
            GraphNode n = net.graph.nodes.get(firstNodeId);
            assertTrue(n.isEndpoint, "First node must be marked as endpoint");
            assertEquals(r0.getId(), n.roomId,
                    "First node must have roomId == fromRoom id");
        }

        @Test @DisplayName("Last node is marked as room endpoint with correct room id")
        void lastNodeEndpoint() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0);
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            int lastNodeId = path.nodeIds.getLast();
            GraphNode n = net.graph.nodes.get(lastNodeId);
            assertTrue(n.isEndpoint, "Last node must be marked as endpoint");
            assertEquals(r1.getId(), n.roomId,
                    "Last node must have roomId == toRoom id");
        }
    }

    // -----------------------------------------------------------------------
    // Consecutive duplicate removal
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Consecutive duplicate removal")
    class DuplicateRemoval {

        @Test @DisplayName("Identical consecutive raw points produce a single node and one nodeId entry")
        void consecutiveDuplicatesCollapsed() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = new CorridorPath(r0.getId(), r1.getId(), 0,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);

            // Same polyId and y -> same NodeKey -> same node id -> should be collapsed
            path.rawPoints.add(voxelPt(42, 0f, 0f));
            path.rawPoints.add(voxelPt(42, 0f, 0f)); // duplicate
            path.rawPoints.add(voxelPt(99, 50f, 0f));
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            // Two distinct nodes (42 and 99), nodeIds must skip the duplicate
            assertEquals(2, path.nodeIds.size(),
                    "nodeIds must not contain a consecutive duplicate entry");
        }
    }

    // -----------------------------------------------------------------------
    // Semantic node reuse across paths
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Semantic node reuse across paths")
    class NodeReuse {

        @Test @DisplayName("Two paths sharing a mid-point produce fewer nodes than total raw points")
        void sharedMidPointReused() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0  = makeRoom( 0f, 0f, rooms);
            Room rMid= makeRoom(25f, 0f, rooms);
            Room r1  = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();

            // Path 0: r0 -> rMid, passes through shared edge point at x=12.5
            CorridorPath p0 = new CorridorPath(r0.getId(), rMid.getId(), 0,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            p0.rawPoints.add(voxelPt(10, 0f,   0f));
            p0.rawPoints.add(edgePt(10, 20, 12.5f, 0f)); // shared
            p0.rawPoints.add(voxelPt(20, 25f,  0f));

            // Path 1: rMid -> r1, starts with the same shared edge point
            CorridorPath p1 = new CorridorPath(rMid.getId(), r1.getId(), 1,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            p1.rawPoints.add(edgePt(10, 20, 12.5f, 0f)); // same key as above
            p1.rawPoints.add(voxelPt(30, 37.5f, 0f));
            p1.rawPoints.add(voxelPt(40, 50f,   0f));

            net.paths.add(p0);
            net.paths.add(p1);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            int totalRaw = p0.rawPoints.size() + p1.rawPoints.size(); // 6
            int actual   = net.graph.nodes.size();
            assertTrue(actual < totalRaw,
                    "Shared edge point must be reused; got " + actual + " nodes for " + totalRaw + " raw points");
        }

        @Test @DisplayName("Shared node accumulates corridor indices from both paths")
        void sharedNodeHasBothCorridorIndices() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0  = makeRoom( 0f, 0f, rooms);
            Room rMid= makeRoom(25f, 0f, rooms);
            Room r1  = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();

            CorridorPath p0 = new CorridorPath(r0.getId(), rMid.getId(), 7,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            p0.rawPoints.add(voxelPt(10, 0f, 0f));
            p0.rawPoints.add(edgePt(10, 20, 12.5f, 0f));

            CorridorPath p1 = new CorridorPath(rMid.getId(), r1.getId(), 13,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            p1.rawPoints.add(edgePt(10, 20, 12.5f, 0f));
            p1.rawPoints.add(voxelPt(20, 50f, 0f));

            net.paths.add(p0);
            net.paths.add(p1);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            GraphNode shared = net.graph.nodes.stream()
                    .filter(n -> n.kind == PointKind.EDGE_MID)
                    .findFirst().orElse(null);

            assertNotNull(shared, "Shared EDGE_MID node must exist");
            assertTrue(shared.corridorIndices.contains(7),
                    "Shared node must reference corridor 7");
            assertTrue(shared.corridorIndices.contains(13),
                    "Shared node must reference corridor 13");
        }
    }

    // -----------------------------------------------------------------------
    // Endpoint position snap to interior point
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Endpoint position snap")
    class EndpointSnap {

        @Test @DisplayName("Endpoint outside room footprint is snapped to room interior point")
        void outsideEndpointSnapped() {
            Map<Integer, Room> rooms = new HashMap<>();
            // Room is at (0, 0) with half-size 4. Placing the raw point far away at (99, 0)
            // so it is definitely outside the room footprint.
            Room r0 = makeRoom(0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = new CorridorPath(r0.getId(), r1.getId(), 0,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            // First point far outside r0 footprint
            path.rawPoints.add(voxelPt(77, 99f, 99f));
            path.rawPoints.add(voxelPt(99, 50f,  0f));
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            GraphNode firstNode = net.graph.nodes.get(path.nodeIds.getFirst());
            // After snap the node position must equal the room interior point (0, 0) in XZ.
            assertEquals(r0.getInteriorPoint().x, firstNode.position.x, 1e-3f,
                    "Snapped endpoint X must equal room interior point X");
            assertEquals(r0.getInteriorPoint().y, firstNode.position.z, 1e-3f,
                    "Snapped endpoint Z must equal room interior point Y (jME Z = world Z)");
        }

        @Test @DisplayName("Endpoint inside room footprint keeps its original position")
        void insideEndpointKept() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom(0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork net = emptyNet();
            CorridorPath path = new CorridorPath(r0.getId(), r1.getId(), 0,
                    CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);
            // Place first point at (1, 0) which is inside r0's 4-unit-radius square footprint.
            path.rawPoints.add(voxelPt(10, 1f, 0f));
            path.rawPoints.add(voxelPt(99, 50f, 0f));
            net.paths.add(path);
            GlobalGraphBuilder.buildGlobalGraph(net, rooms);

            GraphNode firstNode = net.graph.nodes.get(path.nodeIds.getFirst());
            assertEquals(1f, firstNode.position.x, 1e-3f,
                    "Endpoint inside footprint must keep its original X position");
        }
    }

    // -----------------------------------------------------------------------
    // Determinism: same input on two independent networks produces same result
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Determinism")
    class Determinism {

        @Test @DisplayName("Same paths on two independent networks produce the same nodeIds count")
        void sameInputSameNodeIdCount() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork netA = emptyNet();
            CorridorPath pathA = twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0);
            netA.paths.add(pathA);
            GlobalGraphBuilder.buildGlobalGraph(netA, rooms);

            CorridorNetwork netB = emptyNet();
            CorridorPath pathB = twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0);
            netB.paths.add(pathB);
            GlobalGraphBuilder.buildGlobalGraph(netB, rooms);

            assertEquals(pathA.nodeIds.size(), pathB.nodeIds.size(),
                    "Same input on two independent networks must produce the same nodeIds count");
        }

        @Test @DisplayName("Same paths on two independent networks produce the same node count in graph")
        void sameInputSameGraphNodeCount() {
            Map<Integer, Room> rooms = new HashMap<>();
            Room r0 = makeRoom( 0f, 0f, rooms);
            Room r1 = makeRoom(50f, 0f, rooms);

            CorridorNetwork netA = emptyNet();
            netA.paths.add(twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0));
            GlobalGraphBuilder.buildGlobalGraph(netA, rooms);

            CorridorNetwork netB = emptyNet();
            netB.paths.add(twoPointPath(r0.getId(), r1.getId(), 0f, 50f, 0));
            GlobalGraphBuilder.buildGlobalGraph(netB, rooms);

            assertEquals(netA.graph.nodes.size(), netB.graph.nodes.size(),
                    "Same input on two independent networks must produce the same graph node count");
        }
    }
}