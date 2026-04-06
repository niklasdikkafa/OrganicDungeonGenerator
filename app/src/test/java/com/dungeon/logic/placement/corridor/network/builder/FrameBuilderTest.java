package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.graph.NodeKey;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.dungeon.config.DungeonConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FrameBuilder}.
 */
class FrameBuilderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final float EDGE_LEN = 6f;

    /** Creates a minimal CorridorNetwork with sensible geometric parameters. */
    private static CorridorNetwork emptyNet() {
        return new CorridorNetwork(CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS, EDGE_LEN);
    }

    /**
     * Adds a GraphNode to a graph with the given id, position, kind and zBand.
     * The node is NOT a junction and NOT a room endpoint by default.
     */
    private static GraphNode addNode(CorridorGraph g, int id, float x, float z, PointKind kind) {
        NodeKey key = new NodeKey(kind, id, -1, 0);
        GraphNode n = new GraphNode(id, new Vector3f(x, CORRIDOR_HEIGHT * 0.5f, z),
                kind, (short) 0, key);
        g.nodes.add(n);
        g.adjacency.put(id, new java.util.ArrayList<>());
        return n;
    }

    /**
     * Builds a simple straight chain: n0 -- n1 -- n2 aligned along the X axis.
     * n0 is the room endpoint (degree 1), n1 is the middle node (degree 2),
     * n2 is the other endpoint (degree 1).
     */
    private static CorridorNetwork straightChain() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode n0 = addNode(g, 0, 0f,  0f, PointKind.ROOM_CENTER);
        GraphNode n1 = addNode(g, 1, 5f,  0f, PointKind.VOXEL_CENTER);
        GraphNode n2 = addNode(g, 2, 10f, 0f, PointKind.ROOM_CENTER);

        n0.isEndpoint = true;  n0.roomId = 100;
        n2.isEndpoint = true;  n2.roomId = 101;

        g.addUndirectedEdge(0, 1);
        g.addUndirectedEdge(1, 2);

        return net;
    }

    // -----------------------------------------------------------------------
    // Basic frame population
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Basic frame population")
    class BasicFrames {

        @Test @DisplayName("Tangent is non-zero for degree-2 node after buildFrames")
        void tangentNonZeroForDeg2() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            assertFalse(n1.tangent.lengthSquared() < 1e-8f,
                    "Degree-2 node must have a non-zero tangent after buildFrames");
        }

        @Test @DisplayName("Tangent is unit length for all non-disabled nodes")
        void tangentUnitLength() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            for (GraphNode n : net.graph.nodes) {
                if (n.frameDisabled) continue;
                float len = n.tangent.length();
                assertEquals(1f, len, 1e-4f,
                        "Node " + n.id + " tangent must be unit length, got " + len);
            }
        }

        @Test @DisplayName("Normal is perpendicular to tangent in XZ (dot product near 0)")
        void normalPerpendicularToTangent() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            for (GraphNode n : net.graph.nodes) {
                if (n.frameDisabled) continue;
                Vector3f t = new Vector3f(n.tangent.x, 0f, n.tangent.z);
                Vector3f nm = new Vector3f(n.normal.x, 0f, n.normal.z);
                if (t.lengthSquared() < 1e-8f || nm.lengthSquared() < 1e-8f) continue;
                float dot = t.dot(nm);
                assertEquals(0f, dot, 1e-4f,
                        "Node " + n.id + " normal must be perpendicular to tangent");
            }
        }

        @Test @DisplayName("Inner frame corners are set (not all-zero) for frame-enabled nodes")
        void innerCornersSet() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            for (GraphNode n : net.graph.nodes) {
                if (n.frameDisabled) continue;
                assertFalse(
                        n.innerLeftBottom.equals(Vector3f.ZERO) &&
                                n.innerRightBottom.equals(Vector3f.ZERO),
                        "Node " + n.id + " must have non-zero inner frame corners after buildFrames");
            }
        }

        @Test @DisplayName("Outer corners are farther from node center than inner corners")
        void outerFartherThanInner() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            for (GraphNode n : net.graph.nodes) {
                if (n.frameDisabled) continue;
                float innerDist = n.innerLeftBottom.distance(n.position);
                float outerDist = n.outerLeftBottom.distance(n.position);
                assertTrue(outerDist >= innerDist - 1e-4f,
                        "Node " + n.id + " outer corner must be at least as far as inner corner");
            }
        }

        @Test @DisplayName("Frame-disabled nodes (junctions) keep zero tangent")
        void junctionFrameNotSet() {
            CorridorNetwork net = emptyNet();
            CorridorGraph g = net.graph;

            GraphNode j = addNode(g, 0, 0f, 0f, PointKind.VOXEL_CENTER);
            j.isJunction = true;
            j.frameDisabled = true;

            GraphNode p1 = addNode(g, 1, 5f,  0f, PointKind.EDGE_MID);
            GraphNode p2 = addNode(g, 2, -5f, 0f, PointKind.EDGE_MID);
            GraphNode p3 = addNode(g, 3, 0f,  5f, PointKind.EDGE_MID);

            g.addUndirectedEdge(0, 1);
            g.addUndirectedEdge(0, 2);
            g.addUndirectedEdge(0, 3);

            FrameBuilder.buildFrames(net, Map.of());

            assertEquals(0f, j.tangent.length(), 1e-4f,
                    "Junction node with frameDisabled must not receive a tangent");
        }
    }

    // -----------------------------------------------------------------------
    // Tangent direction
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Tangent direction")
    class TangentDirection {

        @Test @DisplayName("Degree-2 middle node on X axis has tangent pointing along X")
        void middleNodeTangentAlongX() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            assertEquals(0f, n1.tangent.y, 1e-4f, "Tangent Y must be 0 (XZ plane only)");
            assertEquals(0f, n1.tangent.z, 1e-4f, "Tangent Z must be 0 for X-axis chain");
            assertEquals(1f, Math.abs(n1.tangent.x), 1e-4f,
                    "Tangent X must be +/-1 for an X-axis chain");
        }

        @Test @DisplayName("Degree-1 endpoint tangent points away from or towards neighbor")
        void endpointTangentAwayOrTowards() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n0 = net.graph.nodes.getFirst(); // at x=0
            GraphNode n1 = net.graph.nodes.getLast();
            assertFalse(Math.abs(n0.tangent.x) < 1e-4f,
                    "Endpoint tangent must have a non-zero X component for X-axis chain");
            assertFalse(Math.abs(n1.tangent.x) < 1e-4f,
                    "Endpoint tangent must have a non-zero X component for X-axis chain");
            float dot = n0.tangent.dot(n1.tangent);
            assertEquals(1, dot,
                    "For straight chain the tangent on both endpoints must point in the same direction");
        }
    }

    // -----------------------------------------------------------------------
    // Profile geometry
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Profile geometry")
    class ProfileGeometry {

        @Test @DisplayName("Inner left-right bottom distance equals CORRIDOR_WIDTH")
        void innerWidthCorrect() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            float dist = n1.innerLeftBottom.distance(n1.innerRightBottom);
            assertEquals(CORRIDOR_WIDTH, dist, 1e-3f,
                    "Inner left-right distance must equal CORRIDOR_WIDTH");
        }

        @Test @DisplayName("Inner top-bottom height equals CORRIDOR_HEIGHT")
        void innerHeightCorrect() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            float height = n1.innerLeftTop.y - n1.innerLeftBottom.y;
            assertEquals(CORRIDOR_HEIGHT, height, 1e-3f,
                    "Inner top minus bottom must equal CORRIDOR_HEIGHT");
        }

        @Test @DisplayName("Outer width equals CORRIDOR_WIDTH + 2 x WALL_THICKNESS")
        void outerWidthCorrect() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            float dist = n1.outerLeftBottom.distance(n1.outerRightBottom);
            float expected = CORRIDOR_WIDTH + 2f * WALL_THICKNESS;
            assertEquals(expected, dist, 1e-3f,
                    "Outer left-right distance must equal CORRIDOR_WIDTH + 2 x WALL_THICKNESS");
        }

        @Test @DisplayName("Outer height equals CORRIDOR_HEIGHT + 2 x WALL_THICKNESS")
        void outerHeightCorrect() {
            CorridorNetwork net = straightChain();
            FrameBuilder.buildFrames(net, Map.of());
            GraphNode n1 = net.graph.nodes.get(1);
            float height = n1.outerLeftTop.y - n1.outerLeftBottom.y;
            float expected = CORRIDOR_HEIGHT + 2f * WALL_THICKNESS;
            assertEquals(expected, height, 1e-3f,
                    "Outer height must equal CORRIDOR_HEIGHT + 2 x WALL_THICKNESS");
        }
    }

    // -----------------------------------------------------------------------
    // Isolated node
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Isolated node")
    class IsolatedNode {

        @Test @DisplayName("buildFrames does not throw for an isolated (degree-0) node")
        void isolatedNodeNoThrow() {
            CorridorNetwork net = emptyNet();
            addNode(net.graph, 0, 0f, 0f, PointKind.VOXEL_CENTER);
            assertDoesNotThrow(() -> FrameBuilder.buildFrames(net, Map.of()));
        }
    }
}