package com.dungeon.logic.mesh.adapter;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import eu.mihosoft.vvecmath.Vector3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JCSGAdapter}.
 */
class JCSGAdapterTest {

    // =========================================================================
    // meshToCSG
    // =========================================================================

    @Nested
    @DisplayName("meshToCSG tests")
    class MeshToCSG {

        @Test
        @DisplayName("Returns non-null CSG for null mesh input")
        void nullMeshReturnsNonNullCSG() {
            assertNotNull(JCSGAdapter.meshToCSG(null));
        }

        @Test
        @DisplayName("Returns empty CSG for null mesh (no polygons)")
        void nullMeshReturnsEmptyCSG() {
            assertEquals(0, JCSGAdapter.meshToCSG(null).getPolygons().size());
        }

        @Test
        @DisplayName("Returns empty CSG for mesh with no position buffer")
        void noPositionBufferReturnsEmptyCSG() {
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Index, 3,
                    BufferUtils.createIntBuffer(0, 1, 2));
            assertEquals(0, JCSGAdapter.meshToCSG(mesh).getPolygons().size());
        }

        @Test
        @DisplayName("Returns empty CSG for mesh with no index buffer")
        void noIndexBufferReturnsEmptyCSG() {
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3,
                    BufferUtils.createFloatBuffer(
                            new Vector3f(0,0,0),
                            new Vector3f(1,0,0),
                            new Vector3f(0,0,1)));
            assertEquals(0, JCSGAdapter.meshToCSG(mesh).getPolygons().size());
        }

        @Test
        @DisplayName("Returns empty CSG for empty mesh")
        void emptyMeshReturnsEmptyCSG() {
            assertEquals(0, JCSGAdapter.meshToCSG(new Mesh()).getPolygons().size());
        }

        @Test
        @DisplayName("Single triangle mesh produces exactly one CSG polygon")
        void singleTriangleProducesOnePolygon() {
            assertEquals(1, JCSGAdapter.meshToCSG(singleTriangleMesh()).getPolygons().size());
        }

        @Test
        @DisplayName("Each CSG polygon from a triangle mesh has exactly 3 vertices")
        void polygonHasThreeVertices() {
            assertEquals(3,
                    JCSGAdapter.meshToCSG(singleTriangleMesh())
                            .getPolygons().getFirst().vertices.size());
        }

        @Test
        @DisplayName("n-triangle mesh produces exactly n CSG polygons")
        void nTrianglesProducesNPolygons() {
            assertEquals(2, JCSGAdapter.meshToCSG(quadMesh()).getPolygons().size());
        }

        @Test
        @DisplayName("Vertex positions are preserved correctly in CSG polygon")
        void vertexPositionsPreserved() {
            Vector3f a = new Vector3f(0, 0, 0);
            Vector3f b = new Vector3f(3, 0, 0);
            Vector3f c = new Vector3f(0, 4, 0);
            CSG csg = JCSGAdapter.meshToCSG(triangleMesh(a, b, c));

            assertFalse(csg.getPolygons().isEmpty());
            Polygon poly = csg.getPolygons().getFirst();
            assertVector3dEquals(a, poly.vertices.get(0).pos, 1e-4);
            assertVector3dEquals(b, poly.vertices.get(1).pos, 1e-4);
            assertVector3dEquals(c, poly.vertices.get(2).pos, 1e-4);
        }

        @Test
        @DisplayName("All three vertices of a CSG polygon share the same face normal")
        void allVerticesShareFaceNormal() {
            List<Vertex> verts =
                    JCSGAdapter.meshToCSG(singleTriangleMesh()).getPolygons().getFirst().vertices;
            Vector3d n0 = verts.get(0).normal;
            Vector3d n1 = verts.get(1).normal;
            Vector3d n2 = verts.get(2).normal;
            assertEquals(n0.x(), n1.x(), 1e-5);
            assertEquals(n0.y(), n1.y(), 1e-5);
            assertEquals(n0.z(), n1.z(), 1e-5);
            assertEquals(n0.x(), n2.x(), 1e-5);
            assertEquals(n0.y(), n2.y(), 1e-5);
            assertEquals(n0.z(), n2.z(), 1e-5);
        }

        @Test
        @DisplayName("Face normal is unit-length")
        void faceNormalUnitLength() {
            Vector3d n = JCSGAdapter.meshToCSG(singleTriangleMesh())
                    .getPolygons().getFirst().vertices.getFirst().normal;
            double len = Math.sqrt(n.x()*n.x() + n.y()*n.y() + n.z()*n.z());
            assertEquals(1.0, len, 1e-5);
        }

        @Test
        @DisplayName("XZ-plane triangle face normal has dominant Y component")
        void xzPlaneNormalDominantY() {
            Mesh mesh = triangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
            Vector3d n = JCSGAdapter.meshToCSG(mesh)
                    .getPolygons().getFirst().vertices.getFirst().normal;
            assertTrue(Math.abs(n.y()) > 0.9,
                    "XZ-plane normal must have dominant Y, got Y=" + n.y());
        }

        @Test
        @DisplayName("Degenerate triangle (zero area) is skipped")
        void degenerateTriangleSkipped() {
            Mesh mesh = triangleMesh(
                    new Vector3f(1,1,1), new Vector3f(1,1,1), new Vector3f(1,1,1));
            assertEquals(0, JCSGAdapter.meshToCSG(mesh).getPolygons().size());
        }

        @Test
        @DisplayName("Collinear triangle (zero area) is skipped")
        void collinearTriangleSkipped() {
            Mesh mesh = triangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(2,0,0));
            assertEquals(0, JCSGAdapter.meshToCSG(mesh).getPolygons().size());
        }

        @Test
        @DisplayName("Mixed mesh: valid triangles counted, degenerate skipped")
        void mixedMeshDegenerateSkipped() {
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3,
                    BufferUtils.createFloatBuffer(
                            new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1),
                            new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,1,0),
                            new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(2,0,0)
                    ));
            mesh.setBuffer(VertexBuffer.Type.Index, 3,
                    BufferUtils.createIntBuffer(0,1,2, 3,4,5, 6,7,8));
            mesh.updateBound(); mesh.updateCounts();
            assertEquals(2, JCSGAdapter.meshToCSG(mesh).getPolygons().size());
        }
    }

    // =========================================================================
    // csgToJmeMesh
    // =========================================================================

    @Nested
    @DisplayName("csgToJmeMesh tests")
    class CsgToJmeMesh {

        @Test
        @DisplayName("Returns non-null mesh for null CSG input")
        void nullCSGReturnsNonNullMesh() {
            assertNotNull(JCSGAdapter.csgToJmeMesh(null));
        }

        @Test
        @DisplayName("Returns empty mesh for null CSG input")
        void nullCSGReturnsEmptyMesh() {
            assertEquals(0, triangleCount(JCSGAdapter.csgToJmeMesh(null)));
        }

        @Test
        @DisplayName("Returns empty mesh for CSG with no polygons")
        void emptyCSGReturnsEmptyMesh() {
            assertEquals(0, triangleCount(JCSGAdapter.csgToJmeMesh(CSG.fromPolygons(List.of()))));
        }

        @Test
        @DisplayName("Single triangle CSG polygon produces exactly one triangle")
        void singlePolygonProducesOneTri() {
            assertEquals(1, triangleCount(JCSGAdapter.csgToJmeMesh(
                    csgFromTriangle(new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1)))));
        }

        @Test
        @DisplayName("Quad CSG polygon (4 vertices) produces exactly 2 triangles via fan")
        void quadPolygonProducesTwoTris() {
            assertEquals(2, triangleCount(JCSGAdapter.csgToJmeMesh(csgFromQuad())));
        }

        @Test
        @DisplayName("n-gon CSG polygon produces n-2 triangles via fan")
        void nGonPolygonProducesNMinus2Tris() {
            for (int n = 3; n <= 7; n++) {
                assertEquals(n - 2,
                        triangleCount(JCSGAdapter.csgToJmeMesh(csgFromNGon(n))),
                        n + "-gon must produce " + (n-2) + " triangles");
            }
        }

        @Test
        @DisplayName("No NaN or Infinity in output vertex positions")
        void noNaNOrInfinity() {
            assertNoNaNOrInfinity(JCSGAdapter.csgToJmeMesh(
                    csgFromTriangle(new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1))));
        }

        @Test
        @DisplayName("Vertex positions are preserved correctly in output mesh")
        void vertexPositionsPreservedRoundTrip() {
            Vector3f a = new Vector3f(2, 3, 4);
            Vector3f b = new Vector3f(5, 6, 7);
            Vector3f c = new Vector3f(8, 0, 1);
            float[] verts = extractVertices(JCSGAdapter.csgToJmeMesh(csgFromTriangle(a, b, c)));
            assertEquals(9, verts.length);
            assertVector3fEquals(a, verts, 0, 1e-4f);
            assertVector3fEquals(b, verts, 3, 1e-4f);
            assertVector3fEquals(c, verts, 6, 1e-4f);
        }

        @Test
        @DisplayName("Output mesh has a normal buffer")
        void outputMeshHasNormalBuffer() {
            Mesh mesh = JCSGAdapter.csgToJmeMesh(
                    csgFromTriangle(new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1)));
            assertNotNull(mesh.getBuffer(VertexBuffer.Type.Normal));
        }
    }

    // =========================================================================
    // union
    // =========================================================================

    @Nested
    @DisplayName("union tests")
    class UnionTests {

        @Test
        @DisplayName("union of corridor with empty room list returns non-null mesh")
        void emptyRoomListReturnsNonNull() {
            Mesh corridor = singleTriangleMesh();
            assertNotNull(JCSGAdapter.union(corridor, List.of(), 1e-3));
        }

        @Test
        @DisplayName("union of empty corridor mesh with one room returns non-null mesh")
        void emptyCorridorWithOneRoom() {
            Mesh room = singleTriangleMesh();
            assertDoesNotThrow(() -> JCSGAdapter.union(new Mesh(), List.of(room), 1e-3));
        }

        @Test
        @DisplayName("union of corridor with one room at the same position has at least as many "
                + "triangles as the corridor alone")
        void unionHasAtLeastCorridorTriangles() {
            Mesh corridor = quadMesh();
            Mesh room     = quadMesh();
            Mesh result   = JCSGAdapter.union(corridor, List.of(room), 1e-3);
            assertTrue(triangleCount(result) >= triangleCount(corridor),
                    "Union must not lose geometry relative to the corridor alone");
        }

        @Test
        @DisplayName("union output has no NaN or Infinity in vertex positions")
        void unionNoNaN() {
            Mesh corridor = quadMesh();
            Mesh room     = singleTriangleMesh();
            assertNoNaNOrInfinity(JCSGAdapter.union(corridor, List.of(room), 1e-3));
        }

        @Test
        @DisplayName("union of spatially disjoint meshes still returns non-null result")
        void disjointMeshesNoThrow() {
            Mesh corridor = quadMesh(); // near origin
            Mesh farRoom  = farTriangle(); // far away
            assertDoesNotThrow(() -> JCSGAdapter.union(corridor, List.of(farRoom), 1e-3));
            assertNotNull(JCSGAdapter.union(corridor, List.of(farRoom), 1e-3));
        }
    }

    // =========================================================================
    // unionBoundsFiltered
    // =========================================================================

    @Nested
    @DisplayName("unionBoundsFiltered tests")
    class UnionBoundsFilteredTests {

        @Test
        @DisplayName("Throws NullPointerException for null first argument")
        void nullAThrows() {
            assertThrows(NullPointerException.class,
                    () -> JCSGAdapter.unionBoundsFiltered(null,
                            JCSGAdapter.meshToCSG(singleTriangleMesh()), 1e-3));
        }

        @Test
        @DisplayName("Throws NullPointerException for null second argument")
        void nullBThrows() {
            assertThrows(NullPointerException.class,
                    () -> JCSGAdapter.unionBoundsFiltered(
                            JCSGAdapter.meshToCSG(singleTriangleMesh()), null, 1e-3));
        }

        @Test
        @DisplayName("Overlapping solids produce a non-null result")
        void overlappingNonNull() {
            CSG a = JCSGAdapter.meshToCSG(quadMesh());
            CSG b = JCSGAdapter.meshToCSG(quadMesh());
            assertNotNull(JCSGAdapter.unionBoundsFiltered(a, b, 1e-3));
        }

        @Test
        @DisplayName("Disjoint solids produce a non-null result (falls back to dumbUnion)")
        void disjointNonNull() {
            CSG a = JCSGAdapter.meshToCSG(quadMesh());
            CSG b = JCSGAdapter.meshToCSG(farTriangle());
            assertNotNull(JCSGAdapter.unionBoundsFiltered(a, b, 1e-3));
        }

        @Test
        @DisplayName("Result contains at least as many polygons as each individual input")
        void resultContainsAllGeometry() {
            CSG a = JCSGAdapter.meshToCSG(quadMesh());
            CSG b = JCSGAdapter.meshToCSG(singleTriangleMesh());
            CSG result = JCSGAdapter.unionBoundsFiltered(a, b, 0.01);
            int minExpected = Math.max(a.getPolygons().size(), b.getPolygons().size());
            assertTrue(result.getPolygons().size() >= minExpected,
                    "Union must contain at least as many polygons as the larger input");
        }

        @Test
        @DisplayName("Empty first CSG returns second CSG (identity for empty a)")
        void emptyAReturnsB() {
            CSG empty  = CSG.fromPolygons(List.of());
            CSG b      = JCSGAdapter.meshToCSG(singleTriangleMesh());
            CSG result = JCSGAdapter.unionBoundsFiltered(empty, b, 1e-3);
            assertEquals(b.getPolygons().size(), result.getPolygons().size(),
                    "Union of empty CSG with b must equal b");
        }

        @Test
        @DisplayName("Empty second CSG returns first CSG (identity for empty b)")
        void emptyBReturnsA() {
            CSG a     = JCSGAdapter.meshToCSG(singleTriangleMesh());
            CSG empty = CSG.fromPolygons(List.of());
            CSG result = JCSGAdapter.unionBoundsFiltered(a, empty, 1e-3);
            assertEquals(a.getPolygons().size(), result.getPolygons().size(),
                    "Union of a with empty CSG must equal a");
        }
    }

    // =========================================================================
    // Round-trip tests (meshToCSG -> csgToJmeMesh)
    // =========================================================================

    @Nested
    @DisplayName("Round-trip conversion tests")
    class RoundTrip {

        @Test
        @DisplayName("Triangle count is preserved after round-trip (1 triangle)")
        void triangleCountPreservedSingleTri() {
            assertEquals(1, triangleCount(
                    JCSGAdapter.csgToJmeMesh(JCSGAdapter.meshToCSG(singleTriangleMesh()))));
        }

        @Test
        @DisplayName("Triangle count is preserved after round-trip (quad = 2 triangles)")
        void triangleCountPreservedQuad() {
            assertEquals(2, triangleCount(
                    JCSGAdapter.csgToJmeMesh(JCSGAdapter.meshToCSG(quadMesh()))));
        }

        @Test
        @DisplayName("No NaN or Infinity in vertex positions after round-trip")
        void noNaNAfterRoundTrip() {
            assertNoNaNOrInfinity(
                    JCSGAdapter.csgToJmeMesh(JCSGAdapter.meshToCSG(singleTriangleMesh())));
        }

        @Test
        @DisplayName("Vertex positions are approximately preserved after round-trip")
        void vertexPositionsApproximatelyPreserved() {
            Vector3f a = new Vector3f(1, 0, 0);
            Vector3f b = new Vector3f(0, 0, 1);
            Vector3f c = new Vector3f(0, 1, 0);
            float[] verts = extractVertices(
                    JCSGAdapter.csgToJmeMesh(JCSGAdapter.meshToCSG(triangleMesh(a, b, c))));
            assertEquals(9, verts.length);
            assertVector3fEquals(a, verts, 0, 1e-4f);
            assertVector3fEquals(b, verts, 3, 1e-4f);
            assertVector3fEquals(c, verts, 6, 1e-4f);
        }

        @Test
        @DisplayName("No degenerate triangles after round-trip for valid input")
        void noDegenerateTrisAfterRoundTrip() {
            assertEquals(0, countDegenerateTris(
                    JCSGAdapter.csgToJmeMesh(JCSGAdapter.meshToCSG(quadMesh()))));
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

    private static void assertVector3dEquals(Vector3f expected, Vector3d actual, double eps) {
        assertEquals(expected.x, actual.x(), eps, "x mismatch");
        assertEquals(expected.y, actual.y(), eps, "y mismatch");
        assertEquals(expected.z, actual.z(), eps, "z mismatch");
    }

    private static void assertVector3fEquals(Vector3f expected, float[] buf, int offset, float eps) {
        assertEquals(expected.x, buf[offset],   eps, "x mismatch");
        assertEquals(expected.y, buf[offset+1], eps, "y mismatch");
        assertEquals(expected.z, buf[offset+2], eps, "z mismatch");
    }

    // =========================================================================
    // Buffer extraction helpers
    // =========================================================================

    private static float[] extractVertices(Mesh mesh) {
        if (mesh == null) return new float[0];
        var pb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (pb == null) return new float[0];
        FloatBuffer fb = (FloatBuffer) pb.getData();
        fb.rewind();
        float[] out = new float[fb.limit()];
        fb.get(out);
        return out;
    }

    private static int[] extractIndices(Mesh mesh) {
        if (mesh == null) return new int[0];
        var ib = mesh.getBuffer(VertexBuffer.Type.Index);
        if (ib == null) return new int[0];
        var raw = ib.getData();
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
            float[] b = v(verts, indices[i+1]);
            float[] c = v(verts, indices[i+2]);
            float[] cr = cross(sub(b, a), sub(c, a));
            if (Math.sqrt(cr[0]*cr[0] + cr[1]*cr[1] + cr[2]*cr[2]) < 1e-6) count++;
        }
        return count;
    }

    // =========================================================================
    // Mesh / CSG factory helpers
    // =========================================================================

    private static Mesh singleTriangleMesh() {
        return triangleMesh(
                new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
    }

    private static Mesh triangleMesh(Vector3f a, Vector3f b, Vector3f c) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(a, b, c));
        m.setBuffer(VertexBuffer.Type.Index,    3, BufferUtils.createIntBuffer(0, 1, 2));
        m.updateBound(); m.updateCounts();
        return m;
    }

    private static Mesh quadMesh() {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3,
                BufferUtils.createFloatBuffer(
                        new Vector3f(0,0,0), new Vector3f(1,0,0),
                        new Vector3f(1,0,1), new Vector3f(0,0,1)));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0,1,2, 0,2,3));
        m.updateBound(); m.updateCounts();
        return m;
    }

    /** Triangle far from the origin. */
    private static Mesh farTriangle() {
        return triangleMesh(
                new Vector3f(500,0,500), new Vector3f(501,0,500), new Vector3f(500,0,501));
    }

    private static CSG csgFromTriangle(Vector3f a, Vector3f b, Vector3f c) {
        Vector3d n = Vector3d.xyz(0, 1, 0);
        return CSG.fromPolygons(List.of(new Polygon(Arrays.asList(
                new Vertex(Vector3d.xyz(a.x, a.y, a.z), n),
                new Vertex(Vector3d.xyz(b.x, b.y, b.z), n),
                new Vertex(Vector3d.xyz(c.x, c.y, c.z), n)))));
    }

    private static CSG csgFromQuad() {
        Vector3d n = Vector3d.xyz(0, 1, 0);
        return CSG.fromPolygons(List.of(new Polygon(Arrays.asList(
                new Vertex(Vector3d.xyz(0,0,0), n), new Vertex(Vector3d.xyz(1,0,0), n),
                new Vertex(Vector3d.xyz(1,0,1), n), new Vertex(Vector3d.xyz(0,0,1), n)))));
    }

    private static CSG csgFromNGon(int n) {
        Vector3d normal = Vector3d.xyz(0, 1, 0);
        List<Vertex> verts = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            verts.add(new Vertex(Vector3d.xyz(Math.cos(angle), 0, Math.sin(angle)), normal));
        }
        return CSG.fromPolygons(List.of(new Polygon(verts)));
    }

    // =========================================================================
    // Math helpers
    // =========================================================================

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
                a[0]*b[1]-a[1]*b[0]};
    }
}