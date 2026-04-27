package com.dungeon.logic.mesh.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.mesh.DungeonMesh;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.jme3.math.Vector2f;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DungeonMeshBuilder}.
 */
class DungeonMeshBuilderTest {

    // =========================================================================
    // invertNormalsInPlace
    // =========================================================================

    @Nested
    @DisplayName("invertNormalsInPlace tests")
    class InvertNormals {

        @Test
        @DisplayName("Does not throw when mesh is null")
        void nullMeshDoesNotThrow() {
            assertDoesNotThrow(() -> DungeonMeshBuilder.invertNormalsInPlace(null));
        }

        @Test
        @DisplayName("Does not throw on an empty mesh")
        void emptyMeshDoesNotThrow() {
            assertDoesNotThrow(() -> DungeonMeshBuilder.invertNormalsInPlace(new Mesh()));
        }

        @Test
        @DisplayName("Flips triangle winding: index order [a,b,c] becomes [a,c,b]")
        void windingFlipped() {
            Mesh mesh = singleTriangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
            int[] before = extractIndices(mesh);
            DungeonMeshBuilder.invertNormalsInPlace(mesh);
            int[] after = extractIndices(mesh);
            assertEquals(before[0], after[0], "Index 0 must stay the same");
            assertEquals(before[1], after[2], "Index 1 must swap with index 2");
            assertEquals(before[2], after[1], "Index 2 must swap with index 1");
        }

        @Test
        @DisplayName("Normal buffer is populated after inversion (no NaN, no Infinity)")
        void normalBufferPopulatedAfterInversion() {
            Mesh mesh = singleTriangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
            DungeonMeshBuilder.invertNormalsInPlace(mesh);

            VertexBuffer nb = mesh.getBuffer(VertexBuffer.Type.Normal);
            assertNotNull(nb, "Normal buffer must exist after invertNormalsInPlace");

            FloatBuffer fb = (FloatBuffer) nb.getData();
            fb.rewind();
            float[] normals = new float[fb.limit()];
            fb.get(normals);

            for (int i = 0; i < normals.length; i++) {
                assertFalse(Float.isNaN(normals[i]),      "NaN in normal at index " + i);
                assertFalse(Float.isInfinite(normals[i]), "Infinity in normal at index " + i);
            }
            for (int i = 0; i < normals.length; i += 3) {
                float len = (float) Math.sqrt(
                        normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
                assertEquals(1f, len, 1e-4f, "Normal at vertex " + (i/3) + " must be unit length");
            }
        }

        @Test
        @DisplayName("Calling invertNormalsInPlace twice restores original winding")
        void doubleInversionRestoresWinding() {
            Mesh mesh = singleTriangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
            int[] original = extractIndices(mesh).clone();
            DungeonMeshBuilder.invertNormalsInPlace(mesh);
            DungeonMeshBuilder.invertNormalsInPlace(mesh);
            assertArrayEquals(original, extractIndices(mesh),
                    "Two inversions must restore the original index order");
        }

        @Test
        @DisplayName("Flipped normal for XZ-plane triangle points opposite to original Y")
        void flippedNormalDirectionCorrect() {
            Mesh mesh = singleTriangleMesh(
                    new Vector3f(0,0,0), new Vector3f(1,0,0), new Vector3f(0,0,1));
            float originalY = new Vector3f(1,0,0).subtract(new Vector3f(0,0,0))
                    .cross(new Vector3f(0,0,1).subtract(new Vector3f(0,0,0))).normalizeLocal().y;
            DungeonMeshBuilder.invertNormalsInPlace(mesh);
            FloatBuffer fb = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Normal).getData();
            fb.rewind(); fb.get(); float ny = fb.get();
            if (originalY > 0f) assertTrue(ny < 0f, "Flipped normal must point downward");
            else assertTrue(ny > 0f, "Flipped normal must point upward");
        }

        @Test
        @DisplayName("Degenerate triangle does not produce NaN or Infinity normals")
        void degenerateTriangleNoNaN() {
            Mesh mesh = singleTriangleMesh(
                    new Vector3f(1,0,1), new Vector3f(1,0,1), new Vector3f(1,0,1));
            assertDoesNotThrow(() -> DungeonMeshBuilder.invertNormalsInPlace(mesh));
            FloatBuffer fb = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Normal).getData();
            fb.rewind();
            while (fb.hasRemaining()) {
                float v = fb.get();
                assertFalse(Float.isNaN(v),      "NaN in degenerate normal");
                assertFalse(Float.isInfinite(v), "Infinity in degenerate normal");
            }
        }

        @Test
        @DisplayName("Multi-triangle mesh: all normals are unit-length after inversion")
        void multiTriangleMeshNormalsUnitLength() {
            Mesh mesh = quadMesh();
            DungeonMeshBuilder.invertNormalsInPlace(mesh);
            FloatBuffer fb = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Normal).getData();
            fb.rewind();
            float[] normals = new float[fb.limit()];
            fb.get(normals);
            for (int i = 0; i < normals.length; i += 3) {
                float len = (float) Math.sqrt(
                        normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
                assertEquals(1f, len, 1e-4f, "Normal at vertex " + (i/3) + " must be unit length");
            }
        }
    }

    // =========================================================================
    // buildDungeonMesh - null / contract tests
    // =========================================================================

    @Nested
    @DisplayName("buildDungeonMesh contract tests")
    class BuildDungeonMeshContract {

        @Test
        @DisplayName("Throws NullPointerException when net is null")
        void throwsOnNullNet() {
            assertThrows(NullPointerException.class,
                    () -> DungeonMeshBuilder.buildDungeonMesh(null, List.of(), true));
        }

        @Test
        @DisplayName("Throws NullPointerException when rooms list is null")
        void throwsOnNullRooms() {
            assertThrows(NullPointerException.class,
                    () -> DungeonMeshBuilder.buildDungeonMesh(emptyNet(), null, true));
        }
    }

    // =========================================================================
    // buildDungeonMesh
    // =========================================================================

    @Nested
    @DisplayName("buildDungeonMesh integration tests")
    class BuildDungeonMeshIntegration {

        @Test
        @DisplayName("Returns non-null DungeonMesh for empty room list")
        void nonNullForEmptyRooms() {
            assertNotNull(DungeonMeshBuilder.buildDungeonMesh(emptyNet(), List.of(), true));
        }

        @Test
        @DisplayName("DungeonMesh.dungeon is never null (outer shell)")
        void combinedMeshNeverNullOuter() {
            assertNotNull(
                    DungeonMeshBuilder.buildDungeonMesh(emptyNet(), List.of(), true).getDungeon());
        }

        @Test
        @DisplayName("DungeonMesh.dungeon is never null (inner shell)")
        void combinedMeshNeverNullInner() {
            assertNotNull(
                    DungeonMeshBuilder.buildDungeonMesh(emptyNet(), List.of(), false).getDungeon());
        }

        @Test
        @DisplayName("Null entries in room list are silently skipped")
        void nullRoomSkipped() {
            List<Room> rooms = new ArrayList<>();
            rooms.add(null);
            assertDoesNotThrow(() ->
                    DungeonMeshBuilder.buildDungeonMesh(emptyNet(), rooms, true));
        }

        @Test
        @DisplayName("Combined mesh for one square room has no NaN vertex positions (outer)")
        void oneRoomNoNaNOuter() {
            assertNoNaNOrInfinity(DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom()), true).getDungeon());
        }

        @Test
        @DisplayName("Combined mesh for one square room has no NaN vertex positions (inner)")
        void oneRoomNoNaNInner() {
            assertNoNaNOrInfinity(DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom()), false).getDungeon());
        }

        @Test
        @DisplayName("Combined mesh for one square room has triangles (outer)")
        void oneRoomHasTrianglesOuter() {
            assertTrue(triangleCount(DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom()), true).getDungeon()) > 0);
        }

        @Test
        @DisplayName("DungeonMesh.network is non-null")
        void networkMeshNonNull() {
            assertNotNull(DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(), true).getNetwork());
        }

        @Test
        @DisplayName("DungeonMesh.rooms list is non-null")
        void roomMeshesListNonNull() {
            assertNotNull(DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom()), true).getRooms());
        }

        @Test
        @DisplayName("DungeonMesh.rooms has one entry per non-null room")
        void roomMeshesCountMatchesRooms() {
            DungeonMesh dm = DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom(), squareRoom()), true);
            assertEquals(2, dm.getRooms().size());
        }

        @Test
        @DisplayName("Inner shell normals are all unit-length")
        void innerShellNormalsUnitLength() {
            Mesh mesh = DungeonMeshBuilder.buildDungeonMesh(
                    emptyNet(), List.of(squareRoom()), false).getDungeon();
            VertexBuffer nb = mesh.getBuffer(VertexBuffer.Type.Normal);
            if (nb == null) return;
            FloatBuffer fb = (FloatBuffer) nb.getData();
            fb.rewind();
            float[] normals = new float[fb.limit()];
            fb.get(normals);
            for (int i = 0; i < normals.length; i += 3) {
                float len = (float) Math.sqrt(
                        normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
                assertEquals(1f, len, 1e-3f, "Inner shell normal at vertex " + (i/3));
            }
        }
    }

    // =========================================================================
    // Helpers
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
        float[] out = new float[fb.limit()]; fb.get(out); return out;
    }

    private static int[] extractIndices(Mesh mesh) {
        if (mesh == null) return new int[0];
        VertexBuffer ib = mesh.getBuffer(VertexBuffer.Type.Index);
        if (ib == null) return new int[0];
        Buffer raw = ib.getData(); raw.rewind();
        if (raw instanceof IntBuffer buf) {
            int[] out = new int[buf.limit()]; buf.get(out); return out;
        } else if (raw instanceof ShortBuffer buf) {
            int[] out = new int[buf.limit()];
            for (int i = 0; i < out.length; i++) out[i] = buf.get() & 0xFFFF;
            return out;
        }
        return new int[0];
    }

    private static int triangleCount(Mesh mesh) { return extractIndices(mesh).length / 3; }

    private static Mesh singleTriangleMesh(Vector3f a, Vector3f b, Vector3f c) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, com.jme3.util.BufferUtils.createFloatBuffer(a, b, c));
        m.setBuffer(VertexBuffer.Type.Index,    3, com.jme3.util.BufferUtils.createIntBuffer(0, 1, 2));
        m.updateBound(); m.updateCounts(); return m;
    }

    private static Mesh quadMesh() {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3,
                com.jme3.util.BufferUtils.createFloatBuffer(
                        new Vector3f(0,0,0), new Vector3f(1,0,0),
                        new Vector3f(1,0,1), new Vector3f(0,0,1)));
        m.setBuffer(VertexBuffer.Type.Index, 3, com.jme3.util.BufferUtils.createIntBuffer(0,1,2, 0,2,3));
        m.updateBound(); m.updateCounts(); return m;
    }

    private static CorridorNetwork emptyNet() {
        return new CorridorNetwork(2.0f, 2.5f, 0.3f, 1.0f);
    }

    private static Room squareRoom() {
        float h = 5f, ho = h + 0.3f;
        return new Room(
                List.of(new Vector2f(-h,-h), new Vector2f(h,-h),
                        new Vector2f(h,h),   new Vector2f(-h,h)),
                List.of(new Vector2f(-ho,-ho), new Vector2f(ho,-ho),
                        new Vector2f(ho,ho),   new Vector2f(-ho,ho)),
                new Vector2f(0f, 0f), 0.3f, 0.3f, 0, 1);
    }
}