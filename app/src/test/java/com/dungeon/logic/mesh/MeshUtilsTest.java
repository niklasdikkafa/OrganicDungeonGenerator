package com.dungeon.logic.mesh;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MeshUtils}.
 */
class MeshUtilsTest {

    // =========================================================================
    // addCap3DProjectedXZ
    // =========================================================================

    @Nested
    @DisplayName("addCap3DProjectedXZ tests")
    class AddCap3DProjectedXZ {

        @Test
        @DisplayName("Does not throw for a valid CCW triangle (upward normal)")
        void validTriangleDoesNotThrow() {
            MeshAccumulator acc = new MeshAccumulator();
            assertDoesNotThrow(() ->
                    MeshUtils.addCap3DProjectedXZ(acc, ccwTriangle(), Vector3f.UNIT_Y));
        }

        @Test
        @DisplayName("Produces exactly one triangle for a 3-vertex polygon")
        void trianglePolygonProducesOneTri() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, ccwTriangle(), Vector3f.UNIT_Y);
            assertEquals(1, triangleCount(acc.toMesh()),
                    "A 3-vertex polygon must produce exactly one triangle");
        }

        @Test
        @DisplayName("Produces n-2 triangles for an n-vertex convex polygon")
        void convexPolygonProducesNMinus2Tris() {
            for (int n = 3; n <= 8; n++) {
                MeshAccumulator acc = new MeshAccumulator();
                MeshUtils.addCap3DProjectedXZ(acc, regularPolygon(n, 5f), Vector3f.UNIT_Y);
                assertEquals(n - 2, triangleCount(acc.toMesh()),
                        "A " + n + "-vertex convex polygon must produce " + (n-2) + " triangles");
            }
        }

        @Test
        @DisplayName("Does nothing for null polygon")
        void nullPolygonProducesNoVertices() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, null, Vector3f.UNIT_Y);
            assertEquals(0, triangleCount(acc.toMesh()));
        }

        @Test
        @DisplayName("Does nothing for a polygon with fewer than 3 vertices")
        void tooFewVerticesProducesNothing() {
            MeshAccumulator acc = new MeshAccumulator();
            List<Vector3f> two = List.of(new Vector3f(0,0,0), new Vector3f(1,0,0));
            MeshUtils.addCap3DProjectedXZ(acc, two, Vector3f.UNIT_Y);
            assertEquals(0, triangleCount(acc.toMesh()));
        }

        @Test
        @DisplayName("Does nothing for a degenerate desired normal (zero vector)")
        void zeroDesiredNormalProducesNothing() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, ccwTriangle(), new Vector3f(0,0,0));
            assertEquals(0, triangleCount(acc.toMesh()));
        }

        @Test
        @DisplayName("Output contains no NaN or Infinity vertex coordinates")
        void noNaNOrInfinityInOutput() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, ccwTriangle(), Vector3f.UNIT_Y);
            assertNoNaNOrInfinity(acc.toMesh());
        }

        @Test
        @DisplayName("No NaN or Infinity for a hexagonal polygon")
        void hexagonNoNaN() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, regularPolygon(6, 8f), Vector3f.UNIT_Y);
            assertNoNaNOrInfinity(acc.toMesh());
        }

        @Test
        @DisplayName("No degenerate triangles for a convex hexagon")
        void hexagonNoDegenerateTris() {
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, regularPolygon(6, 8f), Vector3f.UNIT_Y);
            assertEquals(0, countDegenerateTris(acc.toMesh()));
        }

        @Test
        @DisplayName("CW polygon input is handled: same triangle count as CCW input")
        void cwPolygonSameCountAsCCW() {
            List<Vector3f> ccw = ccwTriangle();
            List<Vector3f> cw  = new ArrayList<>(ccwTriangle());
            java.util.Collections.reverse(cw);

            MeshAccumulator accCCW = new MeshAccumulator();
            MeshAccumulator accCW  = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(accCCW, ccw, Vector3f.UNIT_Y);
            MeshUtils.addCap3DProjectedXZ(accCW,  cw,  Vector3f.UNIT_Y);

            assertEquals(triangleCount(accCCW.toMesh()), triangleCount(accCW.toMesh()),
                    "CW and CCW inputs must produce the same triangle count after normalization");
        }

        @Test
        @DisplayName("All output triangles have face normals aligned with desiredNormal (+Y)")
        void faceNormalsAlignedWithDesiredUp() {
            MeshAccumulator acc = new MeshAccumulator();
            List<Vector3f> poly = regularPolygon(6, 5f);
            MeshUtils.addCap3DProjectedXZ(acc, poly, Vector3f.UNIT_Y);
            assertFaceNormalsAligned(acc.toMesh(), Vector3f.UNIT_Y);
        }

        @Test
        @DisplayName("All output triangles have face normals aligned with desiredNormal (−Y)")
        void faceNormalsAlignedWithDesiredDown() {
            MeshAccumulator acc = new MeshAccumulator();
            List<Vector3f> poly = regularPolygon(6, 5f);
            MeshUtils.addCap3DProjectedXZ(acc, poly, Vector3f.UNIT_Y.negate());
            assertFaceNormalsAligned(acc.toMesh(), Vector3f.UNIT_Y.negate());
        }

        @Test
        @DisplayName("Non-planar polygon (varying Y) does not throw and produces valid output")
        void nonPlanarPolygonValid() {
            List<Vector3f> nonPlanar = List.of(
                    new Vector3f( 0, 0.0f,  0),
                    new Vector3f( 5, 0.5f,  0),
                    new Vector3f( 5, 1.0f,  5),
                    new Vector3f( 0, 0.5f,  5)
            );
            MeshAccumulator acc = new MeshAccumulator();
            assertDoesNotThrow(() ->
                    MeshUtils.addCap3DProjectedXZ(acc, nonPlanar, Vector3f.UNIT_Y));
            assertNoNaNOrInfinity(acc.toMesh());
        }

        @Test
        @DisplayName("Simple concave polygon produces a valid, non-empty triangulation")
        void concavePolygonValid() {
            // L-shape (concave)
            List<Vector3f> lShape = List.of(
                    new Vector3f(0,0,0),
                    new Vector3f(4,0,0),
                    new Vector3f(4,0,2),
                    new Vector3f(2,0,2),
                    new Vector3f(2,0,4),
                    new Vector3f(0,0,4)
            );
            MeshAccumulator acc = new MeshAccumulator();
            MeshUtils.addCap3DProjectedXZ(acc, lShape, Vector3f.UNIT_Y);
            Mesh mesh = acc.toMesh();
            assertNotNull(mesh);
            assertTrue(triangleCount(mesh) > 0,
                    "Concave L-shape must produce at least one triangle");
            assertNoNaNOrInfinity(mesh);
        }
    }

    // =========================================================================
    // getFloatBuffer
    // =========================================================================

    @Nested
    @DisplayName("getFloatBuffer tests")
    class GetFloatBuffer {

        @Test
        @DisplayName("Returns null for a mesh with no position buffer")
        void nullForEmptyMesh() {
            assertNull(MeshUtils.getFloatBuffer(new Mesh()));
        }

        @Test
        @DisplayName("Returns non-null FloatBuffer for a mesh with positions")
        void nonNullForMeshWithPositions() {
            Mesh mesh = meshWithPositions(
                    new Vector3f(0,0,0),
                    new Vector3f(1,0,0),
                    new Vector3f(0,0,1)
            );
            assertNotNull(MeshUtils.getFloatBuffer(mesh));
        }

        @Test
        @DisplayName("Returned buffer has correct limit (3 floats per vertex)")
        void bufferLimitCorrect() {
            Vector3f[] verts = { new Vector3f(1,2,3), new Vector3f(4,5,6) };
            Mesh mesh = meshWithPositions(verts);
            FloatBuffer fb = MeshUtils.getFloatBuffer(mesh);
            assertNotNull(fb);
            assertEquals(verts.length * 3, fb.limit(),
                    "Buffer limit must equal vertexCount * 3");
        }

        @Test
        @DisplayName("Returned buffer contains correct vertex coordinates")
        void bufferContainsCorrectCoordinates() {
            Vector3f v = new Vector3f(7f, 8f, 9f);
            Mesh mesh = meshWithPositions(v, new Vector3f(0,0,0), new Vector3f(1,0,0));
            FloatBuffer fb = MeshUtils.getFloatBuffer(mesh);
            assertNotNull(fb);
            fb.rewind();
            assertEquals(7f, fb.get(), 1e-5f);
            assertEquals(8f, fb.get(), 1e-5f);
            assertEquals(9f, fb.get(), 1e-5f);
        }
    }

    // =========================================================================
    // getIndexArray
    // =========================================================================

    @Nested
    @DisplayName("getIndexArray tests")
    class GetIndexArray {

        @Test
        @DisplayName("Returns empty array for a mesh with no index buffer")
        void emptyArrayForNoIndexBuffer() {
            assertArrayEquals(new int[0], MeshUtils.getIndexArray(new Mesh()));
        }

        @Test
        @DisplayName("Returns correct indices for an IntBuffer index mesh")
        void correctIndicesIntBuffer() {
            Mesh mesh = meshWithIntIndices(0, 1, 2);
            assertArrayEquals(new int[]{0, 1, 2}, MeshUtils.getIndexArray(mesh));
        }

        @Test
        @DisplayName("Returns correct indices for a ShortBuffer index mesh")
        void correctIndicesShortBuffer() {
            Mesh mesh = meshWithShortIndices((short)0, (short)1, (short)2);
            assertArrayEquals(new int[]{0, 1, 2}, MeshUtils.getIndexArray(mesh));
        }

        @Test
        @DisplayName("Returned array length matches index count")
        void arrayLengthMatchesIndexCount() {
            Mesh mesh = meshWithIntIndices(0, 1, 2, 0, 2, 3);
            assertEquals(6, MeshUtils.getIndexArray(mesh).length);
        }
    }

    // =========================================================================
    // readPositions
    // =========================================================================

    @Nested
    @DisplayName("readPositions tests")
    class ReadPositions {

        @Test
        @DisplayName("Returns empty array for an empty FloatBuffer")
        void emptyBufferReturnsEmptyArray() {
            FloatBuffer fb = FloatBuffer.allocate(0);
            assertEquals(0, MeshUtils.readPositions(fb).length);
        }

        @Test
        @DisplayName("Returns correct Vector3f values from buffer")
        void correctVector3fValues() {
            FloatBuffer fb = FloatBuffer.wrap(new float[]{1f,2f,3f, 4f,5f,6f});
            Vector3f[] result = MeshUtils.readPositions(fb);

            assertEquals(2, result.length);
            assertEquals(new Vector3f(1,2,3), result[0]);
            assertEquals(new Vector3f(4,5,6), result[1]);
        }

        @Test
        @DisplayName("Rewinds buffer before reading (does not depend on initial position)")
        void rewindsBufferBeforeReading() {
            FloatBuffer fb = FloatBuffer.wrap(new float[]{1f,0f,0f, 0f,1f,0f});
            fb.position(3); // advance past first vertex

            Vector3f[] result = MeshUtils.readPositions(fb);

            assertEquals(2, result.length, "Should read from rewound start, not current position");
            assertEquals(new Vector3f(1,0,0), result[0]);
        }

        @Test
        @DisplayName("Returns array with length = buffer.limit() / 3")
        void arrayLengthEqualsBufferLimitDivThree() {
            int vertexCount = 7;
            FloatBuffer fb = FloatBuffer.allocate(vertexCount * 3);
            assertEquals(vertexCount, MeshUtils.readPositions(fb).length);
        }

        @Test
        @DisplayName("No NaN or Infinity in returned positions when buffer contains valid data")
        void noNaNOrInfinityForValidData() {
            FloatBuffer fb = FloatBuffer.wrap(new float[]{
                    0f,0f,0f, 1f,2f,3f, -5f,0.5f,100f
            });
            Vector3f[] result = MeshUtils.readPositions(fb);
            for (Vector3f v : result) {
                assertFalse(Float.isNaN(v.x) || Float.isNaN(v.y) || Float.isNaN(v.z));
                assertFalse(Float.isInfinite(v.x) || Float.isInfinite(v.y) || Float.isInfinite(v.z));
            }
        }
    }

    // =========================================================================
    // Geometry assertion helpers
    // =========================================================================

    private static void assertNoNaNOrInfinity(Mesh mesh) {
        float[] verts = extractVertices(mesh);
        for (int i = 0; i < verts.length; i++) {
            assertFalse(Float.isNaN(verts[i]),      "NaN at vertex buffer index " + i);
            assertFalse(Float.isInfinite(verts[i]), "Infinity at vertex buffer index " + i);
        }
    }

    /**
     * Verifies that every triangle's face normal has a positive dot product with
     * {@code expected} (i.e. points in the same general direction).
     */
    private static void assertFaceNormalsAligned(Mesh mesh, Vector3f expected) {
        float[] verts   = extractVertices(mesh);
        int[]   indices = extractIndices(mesh);
        Vector3f en = expected.normalize();

        for (int i = 0; i < indices.length; i += 3) {
            float[] a = v(verts, indices[i]);
            float[] b = v(verts, indices[i+1]);
            float[] c = v(verts, indices[i+2]);
            float[] fn = cross(sub(b,a), sub(c,a));
            float dot = fn[0]*en.x + fn[1]*en.y + fn[2]*en.z;
            assertTrue(dot >= 0f,
                    "Triangle " + (i/3) + " face normal opposes desired direction (dot=" + dot + ")");
        }
    }

    private static int countDegenerateTris(Mesh mesh) {
        float[] verts   = extractVertices(mesh);
        int[]   indices = extractIndices(mesh);
        int count = 0;
        for (int i = 0; i < indices.length; i += 3) {
            float[] a = v(verts, indices[i]);
            float[] b = v(verts, indices[i+1]);
            float[] c = v(verts, indices[i+2]);
            float[] cross = cross(sub(b,a), sub(c,a));
            double area = Math.sqrt(cross[0]*cross[0]+cross[1]*cross[1]+cross[2]*cross[2]);
            if (area < 1e-6) count++;
        }
        return count;
    }

    // =========================================================================
    // Mesh / buffer construction helpers
    // =========================================================================

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

    private static Mesh meshWithPositions(Vector3f... verts) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(verts));
        m.updateBound(); m.updateCounts();
        return m;
    }

    private static Mesh meshWithIntIndices(int... indices) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        m.updateCounts();
        return m;
    }

    private static Mesh meshWithShortIndices(short... indices) {
        Mesh m = new Mesh();
        ShortBuffer sb = ShortBuffer.wrap(indices);
        m.setBuffer(VertexBuffer.Type.Index, 3, sb);
        m.updateCounts();
        return m;
    }

    // =========================================================================
    // Polygon factory helpers
    // =========================================================================

    /** CCW triangle in the XZ plane at Y=0. */
    private static List<Vector3f> ccwTriangle() {
        return new ArrayList<>(List.of(
                new Vector3f(0, 0, 0),
                new Vector3f(5, 0, 0),
                new Vector3f(0, 0, 5)
        ));
    }

    /** Regular n-gon in the XZ plane at Y=0, CCW, with given radius. */
    private static List<Vector3f> regularPolygon(int n, float radius) {
        List<Vector3f> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            pts.add(new Vector3f(
                    (float)(radius * Math.cos(a)),
                    0f,
                    (float)(radius * Math.sin(a))
            ));
        }
        return pts;
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
                a[0]*b[1]-a[1]*b[0]
        };
    }
}