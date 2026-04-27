package com.dungeon.logic.mesh;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MeshAccumulator}.
 */
class MeshAccumulatorTest {

    // =========================================================================
    // toMesh - empty / null-safety
    // =========================================================================

    @Nested
    @DisplayName("toMesh - empty accumulator")
    class EmptyAccumulator {

        @Test
        @DisplayName("toMesh never returns null")
        void neverNull() {
            assertNotNull(new MeshAccumulator().toMesh());
        }

        @Test
        @DisplayName("toMesh on empty accumulator produces zero triangles")
        void zeroTriangles() {
            assertEquals(0, triangleCount(new MeshAccumulator().toMesh()));
        }

        @Test
        @DisplayName("toMesh on accumulator with vertices but no indices produces zero triangles")
        void verticesButNoIndices() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(0, 0, 0));
            acc.addVertex(new Vector3f(1, 0, 0));
            acc.addVertex(new Vector3f(0, 0, 1));
            assertEquals(0, triangleCount(acc.toMesh()));
        }
    }

    // =========================================================================
    // vertexCount
    // =========================================================================

    @Nested
    @DisplayName("vertexCount tests")
    class VertexCount {

        @Test
        @DisplayName("Initially zero")
        void initiallyZero() {
            assertEquals(0, new MeshAccumulator().vertexCount());
        }

        @Test
        @DisplayName("Increments by one per addVertex call")
        void incrementsByOne() {
            MeshAccumulator acc = new MeshAccumulator();
            for (int i = 1; i <= 5; i++) {
                acc.addVertex(new Vector3f(i, 0, 0));
                assertEquals(i, acc.vertexCount());
            }
        }

        @Test
        @DisplayName("addQuad increments vertex count by 4")
        void addQuadIncrementsBy4() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            assertEquals(4, acc.vertexCount());
        }
    }

    // =========================================================================
    // addVertex
    // =========================================================================

    @Nested
    @DisplayName("addVertex tests")
    class AddVertex {

        @Test
        @DisplayName("Returns consecutive indices starting from 0")
        void consecutiveIndices() {
            MeshAccumulator acc = new MeshAccumulator();
            assertEquals(0, acc.addVertex(new Vector3f(0,0,0)));
            assertEquals(1, acc.addVertex(new Vector3f(1,0,0)));
            assertEquals(2, acc.addVertex(new Vector3f(2,0,0)));
        }

        @Test
        @DisplayName("Defensively clones the position (mutating original does not affect stored vertex)")
        void defensiveClone() {
            MeshAccumulator acc = new MeshAccumulator();
            Vector3f original = new Vector3f(1, 2, 3);
            acc.addVertex(original);
            original.set(99, 99, 99); // mutate after add

            acc.addTri(0, 0, 0); // need at least one tri to trigger toMesh position buffer
            float[] verts = extractVertices(acc.toMesh());
            // First vertex should still be (1,2,3)
            assertEquals(1f, verts[0], 1e-5f);
            assertEquals(2f, verts[1], 1e-5f);
            assertEquals(3f, verts[2], 1e-5f);
        }
    }

    // =========================================================================
    // addTri
    // =========================================================================

    @Nested
    @DisplayName("addTri tests")
    class AddTri {

        @Test
        @DisplayName("Single addTri produces exactly one triangle")
        void singleTriProducesOneTri() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(0,0,0));
            acc.addVertex(new Vector3f(1,0,0));
            acc.addVertex(new Vector3f(0,0,1));
            acc.addTri(0, 1, 2);
            assertEquals(1, triangleCount(acc.toMesh()));
        }

        @Test
        @DisplayName("Indices are stored in the correct order (a, b, c)")
        void indicesInCorrectOrder() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(0,0,0));
            acc.addVertex(new Vector3f(1,0,0));
            acc.addVertex(new Vector3f(0,0,1));
            acc.addTri(2, 0, 1);

            int[] indices = extractIndices(acc.toMesh());
            assertArrayEquals(new int[]{2, 0, 1}, indices);
        }

        @Test
        @DisplayName("Multiple addTri calls accumulate all triangles")
        void multipleTriAccumulated() {
            MeshAccumulator acc = new MeshAccumulator();
            for (int i = 0; i < 4; i++) acc.addVertex(new Vector3f(i, 0, 0));
            acc.addTri(0, 1, 2);
            acc.addTri(0, 2, 3);
            assertEquals(2, triangleCount(acc.toMesh()));
        }
    }

    // =========================================================================
    // addQuad
    // =========================================================================

    @Nested
    @DisplayName("addQuad tests")
    class AddQuad {

        @Test
        @DisplayName("addQuad emits exactly 2 triangles")
        void twoTriangles() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            assertEquals(2, triangleCount(acc.toMesh()));
        }

        @Test
        @DisplayName("addQuad emits exactly 4 independent vertices")
        void fourIndependentVertices() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            assertEquals(4, extractVertices(acc.toMesh()).length / 3);
        }

        @Test
        @DisplayName("addQuad triangles use indices (0,1,2) and (0,2,3)")
        void quadTriangleIndices() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            int[] idx = extractIndices(acc.toMesh());
            assertArrayEquals(new int[]{0,1,2, 0,2,3}, idx);
        }

        @Test
        @DisplayName("Two addQuad calls accumulate 4 triangles and 8 vertices")
        void twoQuadsAccumulate() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            acc.addQuad(
                    new Vector3f(2,0,0), new Vector3f(2,1,0),
                    new Vector3f(3,1,0), new Vector3f(3,0,0));
            Mesh mesh = acc.toMesh();
            assertEquals(4, triangleCount(mesh));
            assertEquals(8, extractVertices(mesh).length / 3);
        }

        @Test
        @DisplayName("No NaN or Infinity in vertex positions after addQuad")
        void noNaNOrInfinity() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,2,0),
                    new Vector3f(3,2,0), new Vector3f(3,0,0));
            assertNoNaNOrInfinity(acc.toMesh());
        }
    }

    // =========================================================================
    // toMesh - position buffer
    // =========================================================================

    @Nested
    @DisplayName("toMesh - position buffer")
    class PositionBuffer {

        @Test
        @DisplayName("Position buffer contains exactly the added vertices")
        void positionBufferContainsAddedVertices() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(1, 2, 3));
            acc.addVertex(new Vector3f(4, 5, 6));
            acc.addVertex(new Vector3f(7, 8, 9));
            acc.addTri(0, 1, 2);

            float[] verts = extractVertices(acc.toMesh());
            assertArrayEquals(
                    new float[]{1,2,3, 4,5,6, 7,8,9},
                    verts, 1e-5f);
        }

        @Test
        @DisplayName("Position buffer size equals vertexCount * 3")
        void positionBufferSizeCorrect() {
            MeshAccumulator acc = new MeshAccumulator();
            for (int i = 0; i < 6; i++) acc.addVertex(new Vector3f(i, 0, 0));
            acc.addTri(0,1,2); acc.addTri(3,4,5);
            assertEquals(6 * 3, extractVertices(acc.toMesh()).length);
        }
    }

    // =========================================================================
    // toMesh - normal buffer (flat shading)
    // =========================================================================

    @Nested
    @DisplayName("toMesh - normal buffer (flat shading)")
    class NormalBuffer {

        @Test
        @DisplayName("Normal buffer is present after toMesh")
        void normalBufferPresent() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(0,0,0));
            acc.addVertex(new Vector3f(1,0,0));
            acc.addVertex(new Vector3f(0,0,1));
            acc.addTri(0, 1, 2);
            assertNotNull(acc.toMesh().getBuffer(VertexBuffer.Type.Normal));
        }

        @Test
        @DisplayName("Normal buffer has same element count as position buffer")
        void normalBufferSameCountAsPositions() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,1,0),
                    new Vector3f(1,1,0), new Vector3f(1,0,0));
            Mesh mesh = acc.toMesh();

            int posCount  = extractVertices(mesh).length;
            int normCount = extractNormals(mesh).length;
            assertEquals(posCount, normCount,
                    "Normal buffer must have same element count as position buffer");
        }

        @Test
        @DisplayName("All normals are unit-length (no zero-length normals for valid geometry)")
        void normalsUnitLength() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,0,1),
                    new Vector3f(1,0,1), new Vector3f(1,0,0));
            float[] normals = extractNormals(acc.toMesh());
            for (int i = 0; i < normals.length; i += 3) {
                float len = (float) Math.sqrt(
                        normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
                assertEquals(1f, len, 1e-4f,
                        "Normal at vertex " + (i/3) + " must be unit length, got length=" + len);
            }
        }

        @Test
        @DisplayName("Flat shading: all three vertices of a triangle share the same normal")
        void flatShadingAllThreeVerticesSameNormal() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(0,0,0));
            acc.addVertex(new Vector3f(1,0,0));
            acc.addVertex(new Vector3f(0,0,1));
            acc.addTri(0, 1, 2);

            float[] normals = extractNormals(acc.toMesh());
            // All three vertex normals must be identical
            assertEquals(normals[0], normals[3], 1e-5f, "nx of vertex 0 vs 1");
            assertEquals(normals[1], normals[4], 1e-5f, "ny of vertex 0 vs 1");
            assertEquals(normals[2], normals[5], 1e-5f, "nz of vertex 0 vs 1");
            assertEquals(normals[0], normals[6], 1e-5f, "nx of vertex 0 vs 2");
            assertEquals(normals[1], normals[7], 1e-5f, "ny of vertex 0 vs 2");
            assertEquals(normals[2], normals[8], 1e-5f, "nz of vertex 0 vs 2");
        }

        @Test
        @DisplayName("XZ-plane quad normal is perpendicular to XZ (Y component dominant)")
        void xzQuadNormalPerpendicular() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,0,1),
                    new Vector3f(1,0,1), new Vector3f(1,0,0));
            float[] normals = extractNormals(acc.toMesh());
            // Normal for XZ-plane quad must have dominant Y component
            float ny = normals[1];
            assertTrue(Math.abs(ny) > 0.9f,
                    "XZ-plane quad must produce a normal with dominant Y, got Y=" + ny);
        }

        @Test
        @DisplayName("No NaN or Infinity in normals for valid geometry")
        void noNaNOrInfinityNormals() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addQuad(
                    new Vector3f(0,0,0), new Vector3f(0,2,0),
                    new Vector3f(3,2,0), new Vector3f(3,0,0));
            float[] normals = extractNormals(acc.toMesh());
            for (int i = 0; i < normals.length; i++) {
                assertFalse(Float.isNaN(normals[i]),      "NaN in normal at index " + i);
                assertFalse(Float.isInfinite(normals[i]), "Infinity in normal at index " + i);
            }
        }

        @Test
        @DisplayName("Degenerate triangle (all vertices identical) does not produce NaN normals")
        void degenerateTriangleNoNaNNormals() {
            MeshAccumulator acc = new MeshAccumulator();
            acc.addVertex(new Vector3f(1, 1, 1));
            acc.addVertex(new Vector3f(1, 1, 1));
            acc.addVertex(new Vector3f(1, 1, 1));
            acc.addTri(0, 1, 2);

            float[] normals = extractNormals(acc.toMesh());
            for (float normal : normals) {
                assertFalse(Float.isNaN(normal), "Degenerate tri must not produce NaN normal");
                assertFalse(Float.isInfinite(normal), "Degenerate tri must not produce Infinity normal");
            }
        }

        @Test
        @DisplayName("Last triangle wins for shared vertex normal (flat shading override)")
        void lastTriangleWinsForSharedVertex() {
            // vertex 0 is shared by two triangles with opposite normals
            MeshAccumulator acc = new MeshAccumulator();
            // Triangle 1 (XZ plane, +Y normal): indices 0,1,2
            acc.addVertex(new Vector3f(0,0,0));  // 0 - shared
            acc.addVertex(new Vector3f(1,0,0));  // 1
            acc.addVertex(new Vector3f(0,0,1));  // 2
            // Triangle 2 (XY plane, +Z normal): indices 0,3,4
            acc.addVertex(new Vector3f(1,1,0));  // 3
            acc.addVertex(new Vector3f(0,1,0));  // 4
            acc.addTri(0, 1, 2);
            acc.addTri(0, 3, 4);

            float[] normals = extractNormals(acc.toMesh());
            // Vertex 0's normal must reflect the second triangle (+Z direction dominant)
            float nz0 = normals[2];  // z component of vertex 0 normal
            assertTrue(nz0 > 0.9f,
                    "Shared vertex 0 normal should be overwritten by last triangle (+Z), got nz=" + nz0);
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

    private static float[] extractNormals(Mesh mesh) {
        if (mesh == null) return new float[0];
        VertexBuffer nb = mesh.getBuffer(VertexBuffer.Type.Normal);
        if (nb == null) return new float[0];
        FloatBuffer fb = (FloatBuffer) nb.getData();
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
}