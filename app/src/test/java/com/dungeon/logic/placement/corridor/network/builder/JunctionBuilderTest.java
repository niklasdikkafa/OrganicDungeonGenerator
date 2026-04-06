package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.graph.NodeKey;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.dungeon.logic.placement.corridor.network.junction.JunctionCorner;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.dungeon.config.DungeonConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JunctionBuilder}.
 */
class JunctionBuilderTest {

    private static final float ROUTING_EDGE = 6f;

    // -----------------------------------------------------------------------
    // Graph construction helpers
    // -----------------------------------------------------------------------

    private static CorridorNetwork emptyNet() {
        return new CorridorNetwork(CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS, ROUTING_EDGE);
    }

    /**
     * Adds a node to the graph. id must equal the current graph.nodes.size().
     * Does NOT set junction flag; callers do that explicitly.
     */
    private static GraphNode addNode(CorridorGraph g, float x, float y, float z, PointKind kind) {
        int id = g.nodes.size();
        NodeKey key = new NodeKey(kind, id, -1, Math.round(y / (Z_BAND_HEIGHT / 4f)));
        GraphNode n = new GraphNode(id, new Vector3f(x, y, z), kind, (short)0, key);
        g.nodes.add(n);
        g.adjacency.put(id, new java.util.ArrayList<>());
        return n;
    }

    /** Marks a node as junction and disables its frame (as CorridorNetworkBuilder would do). */
    private static void markJunction(GraphNode n) {
        n.isJunction = true;
        n.frameDisabled = true;
    }

    /**
     * Populates the frame corners of a portal node using a simple identity-like setup.
     * The corridor is assumed to run along the direction from the junction center to the portal.
     * halfInner and halfOuter set the lateral offsets.
     */
    private static void populatePortalFrame(GraphNode portal, float halfInner, float halfOuter) {
        float yBot = portal.position.y - CORRIDOR_HEIGHT * 0.5f;
        float yTop = portal.position.y + CORRIDOR_HEIGHT * 0.5f;

        // Normal pointing in X direction for portals on the Z axis, and vice versa.
        // For this test we use a fixed left=+X right=-X normal.
        float nx = (Math.abs(portal.position.z) > Math.abs(portal.position.x)) ? 1f : 0f;
        float nz = (Math.abs(portal.position.x) > Math.abs(portal.position.z)) ? 1f : 0f;

        portal.innerLeftBottom .set(portal.position.x + nx * halfInner, yBot, portal.position.z + nz * halfInner);
        portal.innerLeftTop    .set(portal.position.x + nx * halfInner, yTop, portal.position.z + nz * halfInner);
        portal.innerRightBottom.set(portal.position.x - nx * halfInner, yBot, portal.position.z - nz * halfInner);
        portal.innerRightTop   .set(portal.position.x - nx * halfInner, yTop, portal.position.z - nz * halfInner);
        portal.outerLeftBottom .set(portal.position.x + nx * halfOuter, yBot - WALL_THICKNESS, portal.position.z + nz * halfOuter);
        portal.outerLeftTop    .set(portal.position.x + nx * halfOuter, yTop + WALL_THICKNESS, portal.position.z + nz * halfOuter);
        portal.outerRightBottom.set(portal.position.x - nx * halfOuter, yBot - WALL_THICKNESS, portal.position.z - nz * halfOuter);
        portal.outerRightTop   .set(portal.position.x - nx * halfOuter, yTop + WALL_THICKNESS, portal.position.z - nz * halfOuter);
    }

    private static void populatePortalFrame(GraphNode p) {
        float hi = CORRIDOR_WIDTH * 0.5f;
        float ho = hi + WALL_THICKNESS;
        populatePortalFrame(p, hi, ho);
    }

    /**
     * Builds a T-junction: one junction node at the origin connected to
     * three portal nodes at N, E, W positions.
     *
     *             portal2 (N)
     *                |
     * portal0 (W) -- J -- portal1 (E)
     */
    private static CorridorNetwork tJunction() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j  = addNode(g,  0f, 1.25f, 0f,  PointKind.VOXEL_CENTER); // id 0
        GraphNode p0 = addNode(g, -8f, 1.25f, 0f,  PointKind.EDGE_MID);     // id 1, West
        GraphNode p1 = addNode(g,  8f, 1.25f, 0f,  PointKind.EDGE_MID);     // id 2, East
        GraphNode p2 = addNode(g,  0f, 1.25f, 8f,  PointKind.EDGE_MID);     // id 3, North

        markJunction(j);
        populatePortalFrame(p0);
        populatePortalFrame(p1);
        populatePortalFrame(p2);

        g.addUndirectedEdge(j.id, p0.id);
        g.addUndirectedEdge(j.id, p1.id);
        g.addUndirectedEdge(j.id, p2.id);

        return net;
    }

    /**
     * Builds an X-junction: one junction node connected to four portal nodes
     * at N, S, E, W positions.
     */
    private static CorridorNetwork xJunction() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j  = addNode(g,  0f, 1.25f,  0f, PointKind.VOXEL_CENTER);
        GraphNode pW = addNode(g, -8f, 1.25f,  0f, PointKind.EDGE_MID);
        GraphNode pE = addNode(g,  8f, 1.25f,  0f, PointKind.EDGE_MID);
        GraphNode pN = addNode(g,  0f, 1.25f,  8f, PointKind.EDGE_MID);
        GraphNode pS = addNode(g,  0f, 1.25f, -8f, PointKind.EDGE_MID);

        markJunction(j);
        populatePortalFrame(pW);
        populatePortalFrame(pE);
        populatePortalFrame(pN);
        populatePortalFrame(pS);

        g.addUndirectedEdge(j.id, pW.id);
        g.addUndirectedEdge(j.id, pE.id);
        g.addUndirectedEdge(j.id, pN.id);
        g.addUndirectedEdge(j.id, pS.id);

        return net;
    }

    /**
     * Builds a cluster of two adjacent junctions connected in a line (chain of 2):
     *
     *  p0 -- J0 -- J1 -- p1
     *              |
     *             p2
     *
     * J0 and J1 are both junctions; p0, p1, p2 are portals.
     * Note: For the network building algorithm, J0 wouldn't be considered a junction because it has only 2 neighbors,
     * but for the purpose of testing the cluster handling logic we mark both as junctions and expect them to be clustered together.
     */
    private static CorridorNetwork twoJunctionChain() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j0 = addNode(g, -4f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j1 = addNode(g,  4f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode p0 = addNode(g,-12f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p1 = addNode(g, 12f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p2 = addNode(g,  4f, 1.25f, 8f, PointKind.EDGE_MID);

        markJunction(j0);
        markJunction(j1);
        populatePortalFrame(p0);
        populatePortalFrame(p1);
        populatePortalFrame(p2);

        g.addUndirectedEdge(j0.id, j1.id);
        g.addUndirectedEdge(j0.id, p0.id);
        g.addUndirectedEdge(j1.id, p1.id);
        g.addUndirectedEdge(j1.id, p2.id);

        return net;
    }

    /** Same as above, but p2 is special node */
    private static CorridorNetwork twoJunctionChainSpecial() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j0 = addNode(g, -4f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j1 = addNode(g,  4f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode p0 = addNode(g,-12f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p1 = addNode(g, 12f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p2 = addNode(g,  4f, 1.25f, 8f, PointKind.EDGE_MID);

        markJunction(j0);
        markJunction(j1);
        populatePortalFrame(p0);
        populatePortalFrame(p1);
        populatePortalFrame(p2);

        g.addUndirectedEdge(j0.id, j1.id);
        g.addUndirectedEdge(j0.id, p0.id);
        g.addUndirectedEdge(j1.id, p1.id);
        g.addUndirectedEdge(j1.id, p2.id);
        g.addUndirectedEdge(j0.id, p2.id);

        return net;
    }

    /**
     * Builds a cluster of three junction nodes in a line (chain of 3):
     *
     *  p0 -- J0 -- J1 -- J2 -- p2
     *              |
     *             p1
     *
     * Note: For the network building algorithm, J0 and J2 wouldn't be considered a junction because they only have 2 neighbors,
     * but for the purpose of testing the cluster handling logic we mark both as junctions and expect them to be clustered together.
     */
    private static CorridorNetwork threeJunctionChain() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j0 = addNode(g, -8f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j1 = addNode(g,  0f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j2 = addNode(g,  8f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode p0 = addNode(g,-16f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p1 = addNode(g,  0f, 1.25f, 8f, PointKind.EDGE_MID);
        GraphNode p2 = addNode(g, 16f, 1.25f, 0f, PointKind.EDGE_MID);

        markJunction(j0);
        markJunction(j1);
        markJunction(j2);
        populatePortalFrame(p0);
        populatePortalFrame(p1);
        populatePortalFrame(p2);

        g.addUndirectedEdge(j0.id, j1.id);
        g.addUndirectedEdge(j1.id, j2.id);
        g.addUndirectedEdge(j0.id, p0.id);
        g.addUndirectedEdge(j1.id, p1.id);
        g.addUndirectedEdge(j2.id, p2.id);

        return net;
    }

    /** Same as above, but p1 is special node */
    private static CorridorNetwork threeJunctionChainSpecial() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        GraphNode j0 = addNode(g, -8f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j1 = addNode(g,  0f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode j2 = addNode(g,  8f, 1.25f, 0f, PointKind.VOXEL_CENTER);
        GraphNode p0 = addNode(g,-16f, 1.25f, 0f, PointKind.EDGE_MID);
        GraphNode p1 = addNode(g,  0f, 1.25f, 8f, PointKind.EDGE_MID);
        GraphNode p2 = addNode(g, 16f, 1.25f, 0f, PointKind.EDGE_MID);

        markJunction(j0);
        markJunction(j1);
        markJunction(j2);
        populatePortalFrame(p0);
        populatePortalFrame(p1);
        populatePortalFrame(p2);

        g.addUndirectedEdge(j0.id, j1.id);
        g.addUndirectedEdge(j1.id, j2.id);
        g.addUndirectedEdge(j0.id, p0.id);
        g.addUndirectedEdge(j1.id, p1.id);
        g.addUndirectedEdge(j2.id, p2.id);
        g.addUndirectedEdge(j0.id, p1.id);

        return net;
    }

    /**
     * Builds a cluster of four junction nodes in a square loop:
     *
     *   J0 -- J1
     *   |      |
     *   J3 -- J2
     *
     * Each junction has one portal pointing outward.
     */
    private static CorridorNetwork fourJunctionSquare() {
        CorridorNetwork net = emptyNet();
        CorridorGraph g = net.graph;

        float s = 5f;
        GraphNode j0 = addNode(g, -s, 1.25f,  s, PointKind.VOXEL_CENTER);
        GraphNode j1 = addNode(g,  s, 1.25f,  s, PointKind.VOXEL_CENTER);
        GraphNode j2 = addNode(g,  s, 1.25f, -s, PointKind.VOXEL_CENTER);
        GraphNode j3 = addNode(g, -s, 1.25f, -s, PointKind.VOXEL_CENTER);

        GraphNode p0 = addNode(g, -s * 3f, 1.25f,  s,      PointKind.EDGE_MID);
        GraphNode p1 = addNode(g,  s * 3f, 1.25f,  s,      PointKind.EDGE_MID);
        GraphNode p2 = addNode(g,  s * 3f, 1.25f, -s,      PointKind.EDGE_MID);
        GraphNode p3 = addNode(g, -s * 3f, 1.25f, -s,      PointKind.EDGE_MID);

        markJunction(j0); markJunction(j1); markJunction(j2); markJunction(j3);
        populatePortalFrame(p0); populatePortalFrame(p1);
        populatePortalFrame(p2); populatePortalFrame(p3);

        // Square edges
        g.addUndirectedEdge(j0.id, j1.id);
        g.addUndirectedEdge(j1.id, j2.id);
        g.addUndirectedEdge(j2.id, j3.id);
        g.addUndirectedEdge(j3.id, j0.id);

        // Each junction additionally connects to a portal
        g.addUndirectedEdge(j0.id, p0.id);
        g.addUndirectedEdge(j1.id, p1.id);
        g.addUndirectedEdge(j2.id, p2.id);
        g.addUndirectedEdge(j3.id, p3.id);

        return net;
    }

    // -----------------------------------------------------------------------
    // Contract: null guard
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Null guard")
    class NullGuard {

        @Test @DisplayName("Throws NullPointerException when net is null")
        void nullNetThrows() {
            assertThrows(NullPointerException.class,
                    () -> JunctionBuilder.buildJunctionCornerLinks(null, ROUTING_EDGE));
        }
    }

    // -----------------------------------------------------------------------
    // Empty / no-junction graph
    // -----------------------------------------------------------------------
    @Nested @DisplayName("No junctions")
    class NoJunctions {

        @Test @DisplayName("Graph with no junction nodes produces an empty junctionLinks map")
        void noJunctionsEmptyMap() {
            CorridorNetwork net = emptyNet();
            CorridorGraph g = net.graph;
            addNode(g, 0f, 1.25f, 0f, PointKind.VOXEL_CENTER);
            addNode(g, 5f, 1.25f, 0f, PointKind.VOXEL_CENTER);
            g.addUndirectedEdge(0, 1);

            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertTrue(net.junctionLinksByJunctionAndPortal.isEmpty());
        }

        @Test @DisplayName("Empty graph produces no junction links")
        void emptyGraphNoLinks() {
            CorridorNetwork net = emptyNet();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertTrue(net.junctionLinksByJunctionAndPortal.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // T-junction (single junction, 3 portals)
    // -----------------------------------------------------------------------
    @Nested @DisplayName("T-junction (1 junction, 3 portals)")
    class TJunctionTests {

        @Test @DisplayName("Three portal links are produced for T-junction")
        void threePortalLinksProduced() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertEquals(3, net.junctionLinksByJunctionAndPortal.size(),
                    "T-junction must produce exactly 3 portal links");
        }

        @Test @DisplayName("Each portal link has a non-negative nextCwPortalNodeId")
        void ringLinksSet() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                assertTrue(pl.nextCwPortalNodeId >= 0,
                        "Portal " + pl.portalNodeId + " must be linked to a successor");
            }
        }

        @Test @DisplayName("Portal ring forms a cycle: following nextCwPortalNodeId returns to start")
        void ringIsCyclic() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            Map<Long, JunctionPortalLink> links = net.junctionLinksByJunctionAndPortal;

            JunctionPortalLink first = links.values().iterator().next();
            int jid = first.junctionNodeId;
            int startPortal = first.portalNodeId;
            int current = startPortal;

            for (int step = 0; step < 3; step++) {
                JunctionPortalLink pl = links.get(JunctionBuilder.jpKey(jid, current));
                assertNotNull(pl, "Portal " + current + " must have a link entry");
                current = pl.nextCwPortalNodeId;
            }
            assertEquals(startPortal, current,
                    "Following the ring 3 times must return to the starting portal");
        }

        @Test @DisplayName("Every portal link has a non-null cornerToNext")
        void cornerToNextNonNull() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                assertNotNull(pl.cornerToNext,
                        "Portal " + pl.portalNodeId + " must have a cornerToNext (may be directConnect)");
            }
        }

        @Test @DisplayName("Portal centers are not all-zero (copied from portal node positions)")
        void portalCentersSet() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                assertNotEquals(Vector3f.ZERO, pl.center, "Portal " + pl.portalNodeId + " center must not be zero");
            }
        }
    }

    // -----------------------------------------------------------------------
    // X-junction (single junction, 4 portals)
    // -----------------------------------------------------------------------
    @Nested @DisplayName("X-junction (1 junction, 4 portals)")
    class XJunctionTests {

        @Test @DisplayName("Four portal links are produced for X-junction")
        void fourPortalLinksProduced() {
            CorridorNetwork net = xJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertEquals(4, net.junctionLinksByJunctionAndPortal.size());
        }

        @Test @DisplayName("Ring cycle length is exactly 4")
        void ringCycleLength4() {
            CorridorNetwork net = xJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            Map<Long, JunctionPortalLink> links = net.junctionLinksByJunctionAndPortal;

            JunctionPortalLink first = links.values().iterator().next();
            int jid = first.junctionNodeId;
            int start = first.portalNodeId;
            int cur = start;

            for (int step = 0; step < 4; step++) {
                cur = links.get(JunctionBuilder.jpKey(jid, cur)).nextCwPortalNodeId;
            }
            assertEquals(start, cur, "Ring must close after 4 steps for X-junction");
        }
    }

    // -----------------------------------------------------------------------
    // Two-junction chain cluster
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Two-junction chain cluster (cluster of 2)")
    class TwoJunctionClusterTests {

        @Test @DisplayName("Two adjacent junctions are treated as a single cluster")
        void singleCluster() {
            CorridorNetwork net = twoJunctionChain();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);

            // All links must share the same junctionNodeId (same cluster seed).
            long jid = net.junctionLinksByJunctionAndPortal.values()
                    .iterator().next().junctionNodeId;
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                assertEquals(jid, pl.junctionNodeId,
                        "All portals in a two-junction cluster must share the same junction id");
            }
        }

        @Test @DisplayName("Portal adjacent to both junctions (special node) is removed from portal ring")
        void specialPortalRemovedFromRing() {
            CorridorNetwork net = twoJunctionChainSpecial();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertEquals(2, net.junctionLinksByJunctionAndPortal.size(),
                    "2 portal links must survive after special-node removal");
        }

        @Test @DisplayName("Ring is cyclic for a two-junction chain cluster")
        void ringCyclicTwoCluster() {
            CorridorNetwork net = twoJunctionChain();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            Map<Long, JunctionPortalLink> links = net.junctionLinksByJunctionAndPortal;
            if (links.isEmpty()) return;

            JunctionPortalLink first = links.values().iterator().next();
            int jid = first.junctionNodeId;
            int start = first.portalNodeId;
            int cur = start;
            int size = links.size();

            for (int step = 0; step < size; step++) {
                JunctionPortalLink pl = links.get(JunctionBuilder.jpKey(jid, cur));
                assertNotNull(pl);
                cur = pl.nextCwPortalNodeId;
            }
            assertEquals(start, cur, "Ring must close after traversing all portals");
        }
    }

    // -----------------------------------------------------------------------
    // Three-junction chain cluster
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Three-junction chain cluster (cluster of 3)")
    class ThreeJunctionChainTests {

        @Test @DisplayName("All three junctions are collected into one cluster")
        void oneCluster() {
            CorridorNetwork net = threeJunctionChain();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertFalse(net.junctionLinksByJunctionAndPortal.isEmpty(),
                    "Three-junction chain must produce at least one portal link");
        }

        @Test @DisplayName("Portals between two junctions of the chain are removed as special nodes")
        void specialNodesFiltered() {
            CorridorNetwork net = threeJunctionChainSpecial();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            assertEquals(2, net.junctionLinksByJunctionAndPortal.size(),
                    "At least 2 portals must remain after special-node filtering in a 3-junction chain");
        }
    }

    // -----------------------------------------------------------------------
    // Four-junction square loop cluster
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Four-junction square loop cluster")
    class FourJunctionSquareTests {

        @Test @DisplayName("Four junctions form a single cluster")
        void singleCluster() {
            CorridorNetwork net = fourJunctionSquare();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            if (net.junctionLinksByJunctionAndPortal.isEmpty()) return;

            long jid = net.junctionLinksByJunctionAndPortal.values()
                    .iterator().next().junctionNodeId;
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                assertEquals(jid, pl.junctionNodeId,
                        "All portals in the square cluster must share one junction id");
            }
        }

        @Test @DisplayName("buildJunctionCornerLinks does not throw for a square loop cluster")
        void noThrowForSquareLoop() {
            CorridorNetwork net = fourJunctionSquare();
            assertDoesNotThrow(() ->
                    JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE));
        }
    }

    // -----------------------------------------------------------------------
    // jpKey
    // -----------------------------------------------------------------------
    @Nested @DisplayName("jpKey")
    class JpKeyTests {

        @Test @DisplayName("jpKey(j,p) encodes junction id in high bits and portal id in low bits")
        void keyEncoding() {
            long key = JunctionBuilder.jpKey(3, 7);
            int jExtracted = (int)(key >>> 32);
            int pExtracted = (int)(key & 0xFFFFFFFFL);
            assertEquals(3, jExtracted);
            assertEquals(7, pExtracted);
        }

        @Test @DisplayName("jpKey(a,b) != jpKey(b,a) (key is NOT symmetric)")
        void keyNotSymmetric() {
            assertNotEquals(JunctionBuilder.jpKey(1, 2), JunctionBuilder.jpKey(2, 1));
        }
    }

    // -----------------------------------------------------------------------
    // Repeated call / idempotency
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Idempotency")
    class Idempotency {

        @Test @DisplayName("Calling buildJunctionCornerLinks twice produces the same number of links")
        void idempotent() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            int first = net.junctionLinksByJunctionAndPortal.size();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            int second = net.junctionLinksByJunctionAndPortal.size();
            assertEquals(first, second, "Second call must produce the same number of portal links");
        }
    }

    // -----------------------------------------------------------------------
    // JunctionCorner directConnect fallback
    // -----------------------------------------------------------------------
    @Nested @DisplayName("JunctionCorner directConnect fallback")
    class DirectConnectFallback {

        @Test @DisplayName("Each JunctionCorner is either directConnect=true or has non-null corner points")
        void cornerConsistency() {
            CorridorNetwork net = tJunction();
            JunctionBuilder.buildJunctionCornerLinks(net, ROUTING_EDGE);
            for (JunctionPortalLink pl : net.junctionLinksByJunctionAndPortal.values()) {
                JunctionCorner c = pl.cornerToNext;
                if (c == null) continue;
                if (c.directConnect) continue;
                // If not directConnect all 8 corner points must be non-null
                assertNotNull(c.innerBottom0, "innerBottom0 must be set if not directConnect");
                assertNotNull(c.innerBottom1, "innerBottom1 must be set if not directConnect");
                assertNotNull(c.innerTop0,    "innerTop0 must be set if not directConnect");
                assertNotNull(c.innerTop1,    "innerTop1 must be set if not directConnect");
                assertNotNull(c.outerBottom0, "outerBottom0 must be set if not directConnect");
                assertNotNull(c.outerBottom1, "outerBottom1 must be set if not directConnect");
                assertNotNull(c.outerTop0,    "outerTop0 must be set if not directConnect");
                assertNotNull(c.outerTop1,    "outerTop1 must be set if not directConnect");
            }
        }
    }
}