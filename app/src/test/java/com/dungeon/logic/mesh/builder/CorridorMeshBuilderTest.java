package com.dungeon.logic.mesh.builder;

import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.graph.NodeKey;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.dungeon.logic.placement.corridor.network.junction.JunctionCorner;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathFrameSample;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.dungeon.logic.placement.corridor.network.builder.JunctionBuilder.jpKey;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CorridorMeshBuilder}.
 */
class CorridorMeshBuilderTest {

    // =========================================================================
    // Smoke tests
    // =========================================================================

    @Nested
    @DisplayName("Smoke tests")
    class Smoke {

        @Test
        @DisplayName("Never returns null for a valid straight corridor (outer)")
        void neverNullOuter() {
            assertNotNull(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), true));
        }

        @Test
        @DisplayName("Never returns null for a valid straight corridor (inner)")
        void neverNullInner() {
            assertNotNull(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), false));
        }

        @Test
        @DisplayName("Never returns null for an empty network")
        void neverNullEmptyNetwork() {
            CorridorNetwork net = TestNetworkFactory.empty();
            assertNotNull(CorridorMeshBuilder.buildCorridorMesh(net, true));
            assertNotNull(CorridorMeshBuilder.buildCorridorMesh(net, false));
        }

        @Test
        @DisplayName("Produces triangles for a valid straight corridor (outer)")
        void producesTrianglesOuter() {
            assertTrue(triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), true)) > 0);
        }

        @Test
        @DisplayName("Produces triangles for a valid straight corridor (inner)")
        void producesTrianglesInner() {
            assertTrue(triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), false)) > 0);
        }

        @Test
        @DisplayName("Throws NullPointerException when network is null")
        void throwsOnNullNetwork() {
            assertThrows(NullPointerException.class,
                    () -> CorridorMeshBuilder.buildCorridorMesh(null, true));
        }
    }

    // =========================================================================
    // Geometry tests
    // =========================================================================

    @Nested
    @DisplayName("Geometry tests")
    class Geometry {

        @Test
        @DisplayName("No NaN or Infinity in vertex positions (outer)")
        void noNaNOrInfinityOuter() {
            assertNoNaNOrInfinity(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), true));
        }

        @Test
        @DisplayName("No NaN or Infinity in vertex positions (inner)")
        void noNaNOrInfinityInner() {
            assertNoNaNOrInfinity(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), false));
        }

        @Test
        @DisplayName("No degenerate triangles (area ~ 0) in outer mesh")
        void noDegenerateTrisOuter() {
            assertEquals(0, countDegenerateTris(
                            CorridorMeshBuilder.buildCorridorMesh(
                                    TestNetworkFactory.straightCorridor(), true)),
                    "Outer mesh should contain no degenerate triangles");
        }

        @Test
        @DisplayName("No degenerate triangles (area ~ 0) in inner mesh")
        void noDegenerateTrisInner() {
            assertEquals(0, countDegenerateTris(
                            CorridorMeshBuilder.buildCorridorMesh(
                                    TestNetworkFactory.straightCorridor(), false)),
                    "Inner mesh should contain no degenerate triangles");
        }

        @Test
        @DisplayName("Output is deterministic: same input yields identical triangle count")
        void deterministicOutput() {
            CorridorNetwork net = TestNetworkFactory.straightCorridor();
            int first  = triangleCount(CorridorMeshBuilder.buildCorridorMesh(net, true));
            int second = triangleCount(CorridorMeshBuilder.buildCorridorMesh(net, true));
            assertEquals(first, second,
                    "Repeated calls with the same network must produce the same triangle count");
        }

        @Test
        @DisplayName("Inner mesh vertices lie within bounding box of outer mesh")
        void innerInsideOuterBounds() {
            CorridorNetwork net = TestNetworkFactory.straightCorridor();
            Mesh outer = CorridorMeshBuilder.buildCorridorMesh(net, true);
            Mesh inner = CorridorMeshBuilder.buildCorridorMesh(net, false);

            float[] outerBounds = computeAABB(outer);
            float[] innerVerts  = extractVertices(inner);

            float tolerance = 1e-3f;
            for (int i = 0; i < innerVerts.length; i += 3) {
                float x = innerVerts[i], y = innerVerts[i+1], z = innerVerts[i+2];
                assertTrue(x >= outerBounds[0] - tolerance && x <= outerBounds[3] + tolerance,
                        "Inner vertex X=" + x + " outside outer bounds ["
                                + outerBounds[0] + ", " + outerBounds[3] + "]");
                assertTrue(y >= outerBounds[1] - tolerance && y <= outerBounds[4] + tolerance,
                        "Inner vertex Y=" + y + " outside outer bounds");
                assertTrue(z >= outerBounds[2] - tolerance && z <= outerBounds[5] + tolerance,
                        "Inner vertex Z=" + z + " outside outer bounds");
            }
        }
    }

    // =========================================================================
    // Deduplication tests
    // =========================================================================

    @Nested
    @DisplayName("Deduplication tests")
    class Deduplication {

        @Test
        @DisplayName("Shared graph edge appearing in two paths is only striped once")
        void sharedEdgeNotDoubled() {
            int triSingle  = triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), true));
            int triDoubled = triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridorEdgeTwice(), true));
            assertEquals(triSingle, triDoubled,
                    "Duplicating the same graph edge across two paths must not double the triangle count");
        }

        @Test
        @DisplayName("End-cap for a shared endpoint node is emitted only once")
        void endCapNotDoubled() {
            int triSingle = triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.straightCorridor(), true));
            int triShared = triangleCount(CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.twoPathsShareEndpoint(), true));
            assertEquals(triSingle, triShared,
                    "Shared endpoint referenced by two paths must produce only one end-cap");
        }
    }

    // =========================================================================
    // Edge-case / robustness tests
    // =========================================================================

    @Nested
    @DisplayName("Edge case tests")
    class EdgeCases {

        @Test
        @DisplayName("Path with only one sample does not throw")
        void pathWithOneSampleDoesNotThrow() {
            assertDoesNotThrow(() -> CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.pathWithOneSample(), true));
        }

        @Test
        @DisplayName("Path with only one sample produces a non-null valid mesh")
        void pathWithOneSampleProducesValidMesh() {
            Mesh mesh = CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.pathWithOneSample(), true);
            assertNotNull(mesh);
            assertNoNaNOrInfinity(mesh);
        }

        @Test
        @DisplayName("Node with frameDisabled=true does not throw")
        void frameDisabledNodeDoesNotThrow() {
            assertDoesNotThrow(() -> CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.corridorWithFrameDisabledEndpoint(), true));
        }

        @Test
        @DisplayName("Node with zero-length tangent does not throw")
        void zeroTangentDoesNotThrow() {
            assertDoesNotThrow(() -> CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.corridorWithZeroTangentEndpoint(), true));
        }

        @Test
        @DisplayName("Junction ring with fewer than 3 portals does not throw")
        void junctionRingTooSmallDoesNotThrow() {
            assertDoesNotThrow(() -> CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.junctionWithTwoPortals(), true));
        }

        @Test
        @DisplayName("Network with only junction nodes and no regular strips does not throw")
        void onlyJunctionNodesDoesNotThrow() {
            assertDoesNotThrow(() -> CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.tJunction(), true));
        }

        @Test
        @DisplayName("Empty path list produces empty but non-null mesh")
        void emptyPathListProducesEmptyMesh() {
            Mesh mesh = CorridorMeshBuilder.buildCorridorMesh(
                    TestNetworkFactory.empty(), true);
            assertNotNull(mesh);
            assertEquals(0, triangleCount(mesh));
        }
    }

    // =========================================================================
    // Assertion helpers
    // =========================================================================

    private static void assertNoNaNOrInfinity(Mesh mesh) {
        float[] verts = extractVertices(mesh);
        for (int i = 0; i < verts.length; i++) {
            assertFalse(Float.isNaN(verts[i]),      "NaN at vertex buffer index " + i);
            assertFalse(Float.isInfinite(verts[i]), "Infinity at vertex buffer index " + i);
        }
    }

    private static float[] extractVertices(Mesh mesh) {
        if (mesh == null) return new float[0];
        VertexBuffer pb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (pb == null) return new float[0];
        FloatBuffer fb = (FloatBuffer) pb.getData();
        fb.rewind();
        float[] out = new float[fb.limit()];
        fb.get(out);
        return out;
    }

    private static int[] extractIndices(Mesh mesh) {
        if (mesh == null) return new int[0];
        VertexBuffer ib = mesh.getBuffer(VertexBuffer.Type.Index);
        if (ib == null) return new int[0];
        Buffer raw = ib.getData();
        raw.rewind();
        if (raw instanceof IntBuffer buf) {
            int[] out = new int[buf.limit()]; buf.get(out); return out;
        } else if (raw instanceof ShortBuffer buf) {
            int[] out = new int[buf.limit()];
            for (int i = 0; i < out.length; i++) out[i] = buf.get() & 0xFFFF;
            return out;
        }
        return new int[0];
    }

    private static int triangleCount(Mesh mesh) {
        return extractIndices(mesh).length / 3;
    }

    private static int countDegenerateTris(Mesh mesh) {
        float[] verts   = extractVertices(mesh);
        int[]   indices = extractIndices(mesh);
        int count = 0;
        for (int i = 0; i < indices.length; i += 3) {
            float[] a = v(verts, indices[i]);
            float[] b = v(verts, indices[i + 1]);
            float[] c = v(verts, indices[i + 2]);
            float[] cross = cross(sub(b, a), sub(c, a));
            double area = Math.sqrt(
                    cross[0]*cross[0] + cross[1]*cross[1] + cross[2]*cross[2]);
            if (area < 1e-6) count++;
        }
        return count;
    }

    private static float[] computeAABB(Mesh mesh) {
        float[] v = extractVertices(mesh);
        if (v.length == 0) return new float[]{0,0,0,0,0,0};
        float minX=v[0], minY=v[1], minZ=v[2];
        float maxX=v[0], maxY=v[1], maxZ=v[2];
        for (int i = 0; i < v.length; i += 3) {
            minX=Math.min(minX,v[i]); minY=Math.min(minY,v[i+1]); minZ=Math.min(minZ,v[i+2]);
            maxX=Math.max(maxX,v[i]); maxY=Math.max(maxY,v[i+1]); maxZ=Math.max(maxZ,v[i+2]);
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static float[] v(float[] buf, int idx) {
        return new float[]{buf[idx*3], buf[idx*3+1], buf[idx*3+2]};
    }
    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]};
    }
    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1]*b[2]-a[2]*b[1],
                a[2]*b[0]-a[0]*b[2],
                a[0]*b[1]-a[1]*b[0]
        };
    }

    // =========================================================================
    // Test network factory
    // =========================================================================

    /**
     * Builds synthetic {@link CorridorNetwork} instances for testing without
     * invoking any real generator code.
     *
     * <p>All networks use {@code CORRIDOR_WIDTH=2, CORRIDOR_HEIGHT=2.5, WALL_THICKNESS=0.3}
     * and a routing cell size of {@code 1.0}.</p>
     */
    static final class TestNetworkFactory {

        private static final float W  = 2.0f;
        private static final float H  = 2.5f;
        private static final float WT = 0.3f;
        private static final float CS = 1.0f;

        /**
         * Minimal straight corridor: two nodes, one path, no junctions.
         * Node 0 at origin, node 1 ten units along +Z.
         */
        static CorridorNetwork straightCorridor() {
            CorridorNetwork net = emptyNet();
            GraphNode a = addNode(net, 0, pos(0,  1.25f, 0),  tangent(0, 0, 1));
            GraphNode b = addNode(net, 1, pos(0,  1.25f, 10), tangent(0, 0, 1));
            net.graph.addUndirectedEdge(a.id, b.id);
            net.paths.add(makePath(0, 1, 0, sample(a), sample(b)));
            return net;
        }

        /**
         * Same two-node corridor but the graph edge (0-1) appears in two separate paths.
         * Verifies that the strip deduplication key prevents the edge from being emitted twice.
         */
        static CorridorNetwork straightCorridorEdgeTwice() {
            CorridorNetwork net = emptyNet();
            GraphNode a = addNode(net, 0, pos(0, 1.25f, 0),  tangent(0, 0, 1));
            GraphNode b = addNode(net, 1, pos(0, 1.25f, 10), tangent(0, 0, 1));
            net.graph.addUndirectedEdge(a.id, b.id);
            net.paths.add(makePath(0, 1, 0, sample(a), sample(b)));
            net.paths.add(makePath(0, 1, 1, sample(a), sample(b)));
            return net;
        }

        /**
         * Two paths that both start at node 0 (degree 1).
         * Verifies the end-cap deduplication for shared endpoint nodes.
         */
        static CorridorNetwork twoPathsShareEndpoint() {
            CorridorNetwork net = emptyNet();
            GraphNode a = addNode(net, 0, pos(0, 1.25f,  0), tangent(0, 0,  1));
            GraphNode b = addNode(net, 1, pos(0, 1.25f, 10), tangent(0, 0,  1));
            net.graph.addUndirectedEdge(a.id, b.id);
            net.paths.add(makePath(0, 1, 0, sample(a), sample(b)));
            net.paths.add(makePath(0, 1, 1, sample(a), sample(b)));
            return net;
        }

        /** Path with only a single sample (fewer than the required 2 for a strip). */
        static CorridorNetwork pathWithOneSample() {
            CorridorNetwork net = emptyNet();
            GraphNode a = addNode(net, 0, pos(0, 1.25f, 0), tangent(0, 0, 1));
            net.paths.add(makePath(0, 0, 0, sample(a)));
            return net;
        }

        /** Corridor whose last node has {@code frameDisabled = true}. */
        static CorridorNetwork corridorWithFrameDisabledEndpoint() {
            CorridorNetwork net = emptyNet();
            GraphNode a = addNode(net, 0, pos(0, 1.25f, 0),  tangent(0, 0, 1));
            GraphNode b = addNode(net, 1, pos(0, 1.25f, 10), tangent(0, 0, 1));
            b.frameDisabled = true;
            net.graph.addUndirectedEdge(a.id, b.id);

            // Build samples manually so that frameDisabled is reflected
            PathFrameSample sa = sample(a);
            PathFrameSample sb = sample(b);
            sb.frameDisabled = true;
            net.paths.add(makePath(0, 1, 0, sa, sb));
            return net;
        }

        /** Corridor whose first node has a zero-length tangent. */
        static CorridorNetwork corridorWithZeroTangentEndpoint() {
            CorridorNetwork net = emptyNet();
            // Node a has zero tangent -> all frame points collapse to the position
            GraphNode a = addNode(net, 0, pos(0, 1.25f, 0),  new Vector3f(0, 0, 0));
            GraphNode b = addNode(net, 1, pos(0, 1.25f, 10), tangent(0, 0, 1));
            net.graph.addUndirectedEdge(a.id, b.id);
            net.paths.add(makePath(0, 1, 0, sample(a), sample(b)));
            return net;
        }

        /**
         * Junction ring with only two portals.
         * The builder requires at least 3 portals; this tests the silent skip guard.
         */
        static CorridorNetwork junctionWithTwoPortals() {
            CorridorNetwork net = emptyNet();
            GraphNode j  = addJunctionNode(net, 0, pos(0, 1.25f, 0));
            GraphNode p1 = addNode(net, 1, pos( 5, 1.25f, 0), tangent( 1, 0, 0));
            GraphNode p2 = addNode(net, 2, pos(-5, 1.25f, 0), tangent(-1, 0, 0));
            net.graph.addUndirectedEdge(j.id, p1.id);
            net.graph.addUndirectedEdge(j.id, p2.id);

            JunctionPortalLink lnk1 = makePortalLink(0, 1, 2, p1);
            JunctionPortalLink lnk2 = makePortalLink(0, 2, 1, p2);
            net.junctionLinksByJunctionAndPortal.put(jpKey(0, 1), lnk1);
            net.junctionLinksByJunctionAndPortal.put(jpKey(0, 2), lnk2);
            return net;
        }

        /**
         * Minimal T-junction with three portal nodes and one junction node.
         * Only junction patches are produced (no regular strips because all neighbors
         * of the junction have their strips suppressed by the {@code isJunction} flag).
         */
        static CorridorNetwork tJunction() {
            CorridorNetwork net = emptyNet();
            GraphNode j  = addJunctionNode(net, 0, pos(  0, 1.25f,  0));
            GraphNode p1 = addNode(net, 1, pos( 10, 1.25f,  0), tangent( 1, 0, 0));
            GraphNode p2 = addNode(net, 2, pos(  0, 1.25f, 10), tangent( 0, 0, 1));
            GraphNode p3 = addNode(net, 3, pos(-10, 1.25f,  0), tangent(-1, 0, 0));
            net.graph.addUndirectedEdge(j.id, p1.id);
            net.graph.addUndirectedEdge(j.id, p2.id);
            net.graph.addUndirectedEdge(j.id, p3.id);

            JunctionPortalLink lnk1 = makePortalLink(0, 1, 2, p1);
            JunctionPortalLink lnk2 = makePortalLink(0, 2, 3, p2);
            JunctionPortalLink lnk3 = makePortalLink(0, 3, 1, p3);
            lnk1.cornerToNext = makeCorner(lnk1, lnk2);
            lnk2.cornerToNext = makeCorner(lnk2, lnk3);
            lnk3.cornerToNext = makeCorner(lnk3, lnk1);
            net.junctionLinksByJunctionAndPortal.put(jpKey(0, 1), lnk1);
            net.junctionLinksByJunctionAndPortal.put(jpKey(0, 2), lnk2);
            net.junctionLinksByJunctionAndPortal.put(jpKey(0, 3), lnk3);
            return net;
        }

        /** Empty network with no paths and no nodes. */
        static CorridorNetwork empty() {
            return emptyNet();
        }

        // ------------------------------------------------------------------
        // Assembly helpers
        // ------------------------------------------------------------------

        private static CorridorNetwork emptyNet() {
            return new CorridorNetwork(W, H, WT, CS);
        }

        /**
         * Adds a regular (non-junction) node to the network graph and populates its frame points.
         * Frame points are computed as a rectangular cross-section centred at {@code pos}.
         */
        private static GraphNode addNode(CorridorNetwork net,
                                         int id,
                                         Vector3f pos,
                                         Vector3f tangent) {
            NodeKey key = new NodeKey(PointKind.VOXEL_CENTER, id, -1, 0);
            GraphNode n = new GraphNode(id, pos, PointKind.VOXEL_CENTER, (short) 0, key);
            n.tangent.set(tangent);

            // Compute a simple axis-aligned normal from the tangent (XZ only)
            float tx = tangent.x, tz = tangent.z;
            float len = (float) Math.sqrt(tx*tx + tz*tz);
            if (len < 1e-6f) { tx = 0; tz = 1; } else { tx /= len; tz /= len; }
            float nx = -tz, nz = tx;   // rotate 90° in XZ

            float hi = W * 0.5f;
            float ho = hi + WT;
            float yBot = pos.y - H * 0.5f;
            float yTop = pos.y + H * 0.5f;

            n.innerLeftBottom .set(pos.x + nx*hi, yBot,       pos.z + nz*hi);
            n.innerLeftTop    .set(pos.x + nx*hi, yTop,       pos.z + nz*hi);
            n.innerRightBottom.set(pos.x - nx*hi, yBot,       pos.z - nz*hi);
            n.innerRightTop   .set(pos.x - nx*hi, yTop,       pos.z - nz*hi);
            n.outerLeftBottom .set(pos.x + nx*ho, yBot - WT,  pos.z + nz*ho);
            n.outerLeftTop    .set(pos.x + nx*ho, yTop + WT,  pos.z + nz*ho);
            n.outerRightBottom.set(pos.x - nx*ho, yBot - WT,  pos.z - nz*ho);
            n.outerRightTop   .set(pos.x - nx*ho, yTop + WT,  pos.z - nz*ho);

            // CorridorGraph.nodes is a final ArrayList -> add via index
            ensureSize(net.graph.nodes, id + 1);
            net.graph.nodes.set(id, n);
            net.graph.adjacency.put(id, new ArrayList<>());
            return n;
        }

        /** Adds a junction node (frameDisabled = true, isJunction = true). */
        private static GraphNode addJunctionNode(CorridorNetwork net, int id, Vector3f pos) {
            GraphNode n = addNode(net, id, pos, tangent(0, 0, 1));
            n.isJunction    = true;
            n.frameDisabled = true;
            return n;
        }

        /** Ensures the list has at least {@code size} elements (filled with null). */
        private static void ensureSize(List<GraphNode> list, int size) {
            while (list.size() < size) list.add(null);
        }

        /** Builds a {@link PathFrameSample} that mirrors the given node's frame data. */
        private static PathFrameSample sample(GraphNode n) {
            PathFrameSample s = new PathFrameSample(n.id, n.position.clone());
            s.frameDisabled       = n.frameDisabled;
            s.isJunctionSample    = n.isJunction;
            s.isRoomEndpointSample = n.isEndpoint;
            s.tangent.set(n.tangent);
            s.normal.set(n.normal);
            s.binormal.set(n.binormal);
            s.innerLeftBottom .set(n.innerLeftBottom);
            s.innerLeftTop    .set(n.innerLeftTop);
            s.innerRightBottom.set(n.innerRightBottom);
            s.innerRightTop   .set(n.innerRightTop);
            s.outerLeftBottom .set(n.outerLeftBottom);
            s.outerLeftTop    .set(n.outerLeftTop);
            s.outerRightBottom.set(n.outerRightBottom);
            s.outerRightTop   .set(n.outerRightTop);
            return s;
        }

        private static CorridorPath makePath(int fromRoom, int toRoom, int idx,
                                             PathFrameSample... samples) {
            CorridorPath p = new CorridorPath(fromRoom, toRoom, idx, W, H, WT);
            for (PathFrameSample s : samples) {
                p.nodeIds.add(s.graphNodeId);
                p.samples.add(s);
            }
            return p;
        }

        private static JunctionPortalLink makePortalLink(int junctionId,
                                                         int portalId,
                                                         int nextCwPortalId,
                                                         GraphNode portalNode) {
            JunctionPortalLink lnk = new JunctionPortalLink(junctionId, portalId,
                    portalNode.position.clone());
            lnk.nextCwPortalNodeId = nextCwPortalId;
            lnk.innerLeftBottom  = portalNode.innerLeftBottom.clone();
            lnk.innerLeftTop     = portalNode.innerLeftTop.clone();
            lnk.innerRightBottom = portalNode.innerRightBottom.clone();
            lnk.innerRightTop    = portalNode.innerRightTop.clone();
            lnk.outerLeftBottom  = portalNode.outerLeftBottom.clone();
            lnk.outerLeftTop     = portalNode.outerLeftTop.clone();
            lnk.outerRightBottom = portalNode.outerRightBottom.clone();
            lnk.outerRightTop    = portalNode.outerRightTop.clone();
            lnk.cornerToNext     = directCorner(); // safe fallback for 2-portal rings
            return lnk;
        }

        /** A corner that signals direct portal-to-portal connection (no chamfer needed). */
        private static JunctionCorner directCorner() {
            JunctionCorner c = new JunctionCorner();
            c.directConnect = true;
            return c;
        }

        /**
         * A corner with valid chamfer geometry derived from two consecutive portal links.
         * The corner points are placed at the midpoint between the relevant portal frame points.
         */
        private static JunctionCorner makeCorner(JunctionPortalLink cur, JunctionPortalLink nxt) {
            JunctionCorner c = new JunctionCorner();
            c.directConnect = false;
            c.innerBottom0 = mid(cur.innerLeftBottom,  nxt.innerRightBottom);
            c.innerBottom1 = mid(nxt.innerRightBottom, cur.innerLeftBottom);
            c.innerTop0    = mid(cur.innerLeftTop,     nxt.innerRightTop);
            c.innerTop1    = mid(nxt.innerRightTop,    cur.innerLeftTop);
            c.outerBottom0 = mid(cur.outerLeftBottom,  nxt.outerRightBottom);
            c.outerBottom1 = mid(nxt.outerRightBottom, cur.outerLeftBottom);
            c.outerTop0    = mid(cur.outerLeftTop,     nxt.outerRightTop);
            c.outerTop1    = mid(nxt.outerRightTop,    cur.outerLeftTop);
            return c;
        }

        private static Vector3f mid(Vector3f a, Vector3f b) {
            return a.add(b).multLocal(0.5f);
        }

        private static Vector3f pos(float x, float y, float z) {
            return new Vector3f(x, y, z);
        }

        private static Vector3f tangent(float x, float y, float z) {
            Vector3f t = new Vector3f(x, y, z);
            if (t.lengthSquared() > 1e-8f) t.normalizeLocal();
            return t;
        }
    }
}