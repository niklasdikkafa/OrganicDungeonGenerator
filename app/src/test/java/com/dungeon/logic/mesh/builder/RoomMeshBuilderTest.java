package com.dungeon.logic.mesh.builder;

import com.dungeon.domain.Room;
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
 * Unit tests for {@link RoomMeshBuilder}.
 */
class RoomMeshBuilderTest {

    // =========================================================================
    // Smoke tests
    // =========================================================================

    @Nested
    @DisplayName("Smoke tests")
    class Smoke {

        @Test
        @DisplayName("Never returns null for a valid square room (outer)")
        void neverNullOuter() {
            assertNotNull(RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), true));
        }

        @Test
        @DisplayName("Never returns null for a valid square room (inner)")
        void neverNullInner() {
            assertNotNull(RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), false));
        }

        @Test
        @DisplayName("Throws NullPointerException when room is null")
        void throwsOnNullRoom() {
            assertThrows(NullPointerException.class,
                    () -> RoomMeshBuilder.buildRoomMesh(null, true));
        }

        @Test
        @DisplayName("Produces triangles for a valid square room (outer)")
        void producesTrianglesOuter() {
            assertTrue(triangleCount(
                    RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), true)) > 0);
        }

        @Test
        @DisplayName("Produces triangles for a valid square room (inner)")
        void producesTrianglesInner() {
            assertTrue(triangleCount(
                    RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), false)) > 0);
        }

        @Test
        @DisplayName("Returns empty mesh for room with fewer than 3 corners")
        void emptyMeshForDegenerateRoom() {
            Mesh mesh = RoomMeshBuilder.buildRoomMesh(TestRoomFactory.twoCornerRoom(), true);
            assertNotNull(mesh);
            assertEquals(0, triangleCount(mesh));
        }

        @Test
        @DisplayName("Returns empty mesh for room with empty corner lists")
        void emptyMeshForEmptyCorners() {
            Mesh mesh = RoomMeshBuilder.buildRoomMesh(TestRoomFactory.emptyCornersRoom(), true);
            assertNotNull(mesh);
            assertEquals(0, triangleCount(mesh));
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
            assertNoNaNOrInfinity(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.squareRoom(), true));
        }

        @Test
        @DisplayName("No NaN or Infinity in vertex positions (inner)")
        void noNaNOrInfinityInner() {
            assertNoNaNOrInfinity(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.squareRoom(), false));
        }

        @Test
        @DisplayName("No degenerate triangles (area ~ 0) in outer mesh")
        void noDegenerateTrianglesOuter() {
            assertEquals(0, countDegenerateTris(
                            RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), true)),
                    "Outer mesh must not contain degenerate triangles");
        }

        @Test
        @DisplayName("No degenerate triangles (area ~ 0) in inner mesh")
        void noDegenerateTrianglesInner() {
            assertEquals(0, countDegenerateTris(
                            RoomMeshBuilder.buildRoomMesh(TestRoomFactory.squareRoom(), false)),
                    "Inner mesh must not contain degenerate triangles");
        }

        @Test
        @DisplayName("Output is deterministic: same room yields identical triangle count")
        void deterministicOutput() {
            Room room = TestRoomFactory.squareRoom();
            int first  = triangleCount(RoomMeshBuilder.buildRoomMesh(room, true));
            int second = triangleCount(RoomMeshBuilder.buildRoomMesh(room, true));
            assertEquals(first, second,
                    "Repeated calls with the same room must produce the same triangle count");
        }

        @Test
        @DisplayName("Inner mesh vertices lie within bounding box of outer mesh")
        void innerInsideOuterBounds() {
            Room room  = TestRoomFactory.squareRoom();
            Mesh outer = RoomMeshBuilder.buildRoomMesh(room, true);
            Mesh inner = RoomMeshBuilder.buildRoomMesh(room, false);

            float[] ob = computeAABB(outer);
            float[] iv = extractVertices(inner);

            float tol = 1e-3f;
            for (int i = 0; i < iv.length; i += 3) {
                float x = iv[i], y = iv[i + 1], z = iv[i + 2];
                assertTrue(x >= ob[0] - tol && x <= ob[3] + tol,
                        "Inner X=" + x + " outside outer [" + ob[0] + ", " + ob[3] + "]");
                assertTrue(y >= ob[1] - tol && y <= ob[4] + tol,
                        "Inner Y=" + y + " outside outer [" + ob[1] + ", " + ob[4] + "]");
                assertTrue(z >= ob[2] - tol && z <= ob[5] + tol,
                        "Inner Z=" + z + " outside outer [" + ob[2] + ", " + ob[5] + "]");
            }
        }

        @Test
        @DisplayName("Outer mesh Y extent is larger than inner mesh Y extent (floor thickness)")
        void outerTallerThanInner() {
            Room room  = TestRoomFactory.squareRoom();
            float[] ob = computeAABB(RoomMeshBuilder.buildRoomMesh(room, true));
            float[] ib = computeAABB(RoomMeshBuilder.buildRoomMesh(room, false));

            float outerHeight = ob[4] - ob[1];
            float innerHeight = ib[4] - ib[1];
            assertTrue(outerHeight > innerHeight,
                    "Outer mesh should be taller than inner mesh by 2 * floorThickness");
        }

        @Test
        @DisplayName("Outer Y extent matches expected zLevel +/- (height + floorThickness)")
        void outerYExtentMatchesExpected() {
            Room room = TestRoomFactory.squareRoom();
            float[] ob = computeAABB(RoomMeshBuilder.buildRoomMesh(room, true));

            float expectedYMin = room.getZLevel() - room.getFloorThickness();
            float expectedYMax = room.getZLevel() + room.getHeight() + room.getFloorThickness();

            assertEquals(expectedYMin, ob[1], 1e-3f,
                    "Outer mesh Y min should be zLevel - floorThickness");
            assertEquals(expectedYMax, ob[4], 1e-3f,
                    "Outer mesh Y max should be zLevel + height + floorThickness");
        }

        @Test
        @DisplayName("Inner Y extent matches expected zLevel to zLevel + height")
        void innerYExtentMatchesExpected() {
            Room room = TestRoomFactory.squareRoom();
            float[] ib = computeAABB(RoomMeshBuilder.buildRoomMesh(room, false));

            assertEquals(room.getZLevel(),                    ib[1], 1e-3f,
                    "Inner mesh Y min should equal zLevel");
            assertEquals(room.getZLevel() + room.getHeight(), ib[4], 1e-3f,
                    "Inner mesh Y max should equal zLevel + height");
        }

        @Test
        @DisplayName("No NaN or Infinity for a rotated (non-axis-aligned) room")
        void noNaNForRotatedRoom() {
            assertNoNaNOrInfinity(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.rotatedRoom(), true));
            assertNoNaNOrInfinity(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.rotatedRoom(), false));
        }

        @Test
        @DisplayName("No degenerate triangles for a rotated room")
        void noDegenerateTrianglesRotatedRoom() {
            assertEquals(0, countDegenerateTris(
                    RoomMeshBuilder.buildRoomMesh(TestRoomFactory.rotatedRoom(), true)));
            assertEquals(0, countDegenerateTris(
                    RoomMeshBuilder.buildRoomMesh(TestRoomFactory.rotatedRoom(), false)));
        }
    }

    // =========================================================================
    // Polygon sanitization tests
    // =========================================================================

    @Nested
    @DisplayName("Polygon sanitization tests")
    class Sanitization {

        @Test
        @DisplayName("Duplicate consecutive vertices do not produce degenerate triangles")
        void duplicateVerticesHandled() {
            assertEquals(0, countDegenerateTris(
                    RoomMeshBuilder.buildRoomMesh(
                            TestRoomFactory.roomWithDuplicateVertices(), true)));
        }

        @Test
        @DisplayName("Closing vertex equal to first vertex is handled without crash")
        void closingVertexHandled() {
            assertDoesNotThrow(() -> RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.roomWithClosingVertex(), true));
        }

        @Test
        @DisplayName("CW-wound polygon produces same triangle count as CCW polygon")
        void cwAndCcwProduceSameTriangleCount() {
            int ccw = triangleCount(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.squareRoom(), true));
            int cw = triangleCount(RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.cwSquareRoom(), true));
            assertEquals(ccw, cw,
                    "CCW and CW input polygons should produce the same triangle count after normalization");
        }
    }

    // =========================================================================
    // Edge-case / robustness tests
    // =========================================================================

    @Nested
    @DisplayName("Edge case tests")
    class EdgeCases {

        @Test
        @DisplayName("Room with zero height does not throw")
        void zeroHeightDoesNotThrow() {
            assertDoesNotThrow(() ->
                    RoomMeshBuilder.buildRoomMesh(TestRoomFactory.zeroHeightRoom(), true));
        }

        @Test
        @DisplayName("Room at negative Z level does not throw and produces valid mesh")
        void negativeZLevelDoesNotThrow() {
            Mesh mesh = RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.negativeZLevelRoom(), true);
            assertNotNull(mesh);
            assertNoNaNOrInfinity(mesh);
        }

        @Test
        @DisplayName("Triangular room (3 corners) produces valid mesh")
        void triangularRoomValid() {
            Mesh mesh = RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.triangularRoom(), true);
            assertNotNull(mesh);
            assertTrue(triangleCount(mesh) > 0);
            assertNoNaNOrInfinity(mesh);
            assertEquals(0, countDegenerateTris(mesh));
        }

        @Test
        @DisplayName("Hexagonal room produces valid mesh without degenerate triangles")
        void hexagonalRoomValid() {
            Mesh mesh = RoomMeshBuilder.buildRoomMesh(
                    TestRoomFactory.hexagonalRoom(), true);
            assertNotNull(mesh);
            assertTrue(triangleCount(mesh) > 0);
            assertEquals(0, countDegenerateTris(mesh));
        }
    }

    // =========================================================================
    // toPoly3 unit tests
    // =========================================================================

    @Nested
    @DisplayName("toPoly3 helper tests")
    class ToPoly3 {

        @Test
        @DisplayName("toPoly3 maps x/z correctly and sets constant Y")
        void mapsCoordinatesCorrectly() {
            List<Vector2f> poly = List.of(
                    new Vector2f(1f, 2f),
                    new Vector2f(3f, 4f),
                    new Vector2f(5f, 6f)
            );
            List<Vector3f> result = RoomMeshBuilder.toPoly3(poly, 7f);

            assertEquals(3, result.size());
            assertEquals(new Vector3f(1f, 7f, 2f), result.get(0));
            assertEquals(new Vector3f(3f, 7f, 4f), result.get(1));
            assertEquals(new Vector3f(5f, 7f, 6f), result.get(2));
        }

        @Test
        @DisplayName("toPoly3 with empty input returns empty list")
        void emptyInputReturnsEmptyList() {
            assertTrue(RoomMeshBuilder.toPoly3(List.of(), 0f).isEmpty());
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
            int[] out = new int[buf.limit()];
            buf.get(out);
            return out;
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
                    cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2]);
            if (area < 1e-6) count++;
        }
        return count;
    }

    private static float[] computeAABB(Mesh mesh) {
        float[] v = extractVertices(mesh);
        if (v.length == 0) return new float[]{0, 0, 0, 0, 0, 0};
        float minX = v[0], minY = v[1], minZ = v[2];
        float maxX = v[0], maxY = v[1], maxZ = v[2];
        for (int i = 0; i < v.length; i += 3) {
            minX = Math.min(minX, v[i]);     minY = Math.min(minY, v[i + 1]); minZ = Math.min(minZ, v[i + 2]);
            maxX = Math.max(maxX, v[i]);     maxY = Math.max(maxY, v[i + 1]); maxZ = Math.max(maxZ, v[i + 2]);
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static float[] v(float[] buf, int idx) {
        return new float[]{buf[idx * 3], buf[idx * 3 + 1], buf[idx * 3 + 2]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    // =========================================================================
    // Test room factory
    // =========================================================================

    /**
     * Builds real {@link Room} instances for testing.
     *
     * <p>{@link Room} is {@code final} and cannot be subclassed. All geometry
     * (inner/outer corners) is passed directly to the constructor. Vertical
     * parameters are back-calculated from the desired world values:
     * <pre>
     *   zBandIndex = Math.round(desiredZLevel / Z_BAND_HEIGHT)
     *   zBandHeightBands = Math.max(1, Math.round(desiredHeight / Z_BAND_HEIGHT))
     * </pre>
     * Note: {@code zBandHeightBands = 0} is avoided because the Room constructor
     * would produce {@code height = 0} — tests that need zero height use
     * {@code zBandHeightBands = 0} intentionally via {@link #zeroHeightRoom()}.
     * </p>
     */
    static final class TestRoomFactory {

        /**
         * A simple axis-aligned 10x10 square room.
         * zLevel = 0, height = Z_BAND_HEIGHT (one band), floorThickness = 0.3.
         */
        static Room squareRoom() {
            return room(ccwSquare(10f), outerExpand(ccwSquare(10f), 0.3f),
                    0, 1, 0.3f, 0.3f);
        }

        /** Same square but vertices wound clockwise (tests sanitizePolygon normalization). */
        static Room cwSquareRoom() {
            List<Vector2f> cw = new ArrayList<>(ccwSquare(10f));
            java.util.Collections.reverse(cw);
            return room(cw, outerExpand(cw, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /** A 45°-rotated square room (diamond shape). */
        static Room rotatedRoom() {
            float r = 8f;
            List<Vector2f> inner = List.of(
                    new Vector2f(r,0),
                    new Vector2f(0, r),
                    new Vector2f(-r,0),
                    new Vector2f(0, -r)
            );
            return room(inner, outerExpand(inner, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /** A triangular room (3 corners -> minimum valid polygon). */
        static Room triangularRoom() {
            List<Vector2f> inner = List.of(
                    new Vector2f( 0,  0),
                    new Vector2f(10,  0),
                    new Vector2f( 5, 10)
            );
            return room(inner, outerExpand(inner, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /** A regular hexagonal room. */
        static Room hexagonalRoom() {
            List<Vector2f> pts = new ArrayList<>();
            float r = 8f;
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3.0 * i;
                pts.add(new Vector2f(
                        (float) (r * Math.cos(angle)),
                        (float) (r * Math.sin(angle))));
            }
            return room(pts, outerExpand(pts, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /** Room where the inner corner list has only 2 vertices (degenerate). */
        static Room twoCornerRoom() {
            return room(
                    List.of(new Vector2f(0, 0), new Vector2f(5, 0)),
                    List.of(new Vector2f(-0.3f, -0.3f), new Vector2f(5.3f, -0.3f)),
                    0, 1, 0.3f, 0.3f);
        }

        /** Room whose corner lists are empty. */
        static Room emptyCornersRoom() {
            return room(List.of(), List.of(), 0, 1, 0.3f, 0.3f);
        }

        /** Room with a duplicate consecutive vertex in the polygon. */
        static Room roomWithDuplicateVertices() {
            List<Vector2f> inner = new ArrayList<>(ccwSquare(10f));
            inner.add(1, inner.get(1).clone());
            return room(inner, outerExpand(inner, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /** Room whose polygon ends with a closing vertex identical to the first. */
        static Room roomWithClosingVertex() {
            List<Vector2f> inner = new ArrayList<>(ccwSquare(10f));
            inner.add(inner.getFirst().clone());
            return room(inner, outerExpand(inner, 0.3f), 0, 1, 0.3f, 0.3f);
        }

        /**
         * Room with zero height.
         * {@code zBandHeightBands = 0} -> {@code height = 0 * Z_BAND_HEIGHT = 0}.
         */
        static Room zeroHeightRoom() {
            return room(ccwSquare(10f), outerExpand(ccwSquare(10f), 0.3f),
                    0, 0, 0.3f, 0.3f);
        }

        /**
         * Room at a negative Z level.
         * {@code zBandIndex = -2} -> {@code zLevel = -2 * 2.5 = -5.0}.
         */
        static Room negativeZLevelRoom() {
            return room(ccwSquare(10f), outerExpand(ccwSquare(10f), 0.3f),
                    -2, 1, 0.3f, 0.3f);
        }

        // ------------------------------------------------------------------
        // Assembly helpers
        // ------------------------------------------------------------------

        /**
         * Creates a real {@link Room} from the given parameters.
         *
         * @param inner            inner footprint corners
         * @param outer            outer footprint corners
         * @param zBandIndex       discrete floor band (zLevel = zBandIndex * Z_BAND_HEIGHT)
         * @param zBandHeightBands room height in bands (height = bands * Z_BAND_HEIGHT)
         * @param floorThickness   slab thickness
         * @param wallThickness    wall offset thickness
         */
        static Room room(List<Vector2f> inner,
                         List<Vector2f> outer,
                         int zBandIndex,
                         int zBandHeightBands,
                         float floorThickness,
                         float wallThickness) {
            Vector2f center = computeCenter(inner);
            return new Room(inner, outer, center,
                    floorThickness, wallThickness,
                    zBandIndex, zBandHeightBands);
        }

        /** Generates a CCW axis-aligned square with half-size {@code s}. */
        static List<Vector2f> ccwSquare(float s) {
            float h = s * 0.5f;
            return List.of(
                    new Vector2f(-h, -h),
                    new Vector2f( h, -h),
                    new Vector2f( h,  h),
                    new Vector2f(-h,  h)
            );
        }

        /**
         * Expands each vertex outward from the centroid by {@code margin}.
         * Used to generate a plausible outer footprint from an inner one.
         */
        static List<Vector2f> outerExpand(List<Vector2f> poly, float margin) {
            if (poly == null || poly.isEmpty()) return List.of();
            float cx = 0, cy = 0;
            for (Vector2f p : poly) { cx += p.x; cy += p.y; }
            cx /= poly.size();
            cy /= poly.size();

            List<Vector2f> out = new ArrayList<>();
            for (Vector2f p : poly) {
                float dx = p.x - cx, dy = p.y - cy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-6f) { out.add(p.clone()); continue; }
                out.add(new Vector2f(p.x + dx / len * margin, p.y + dy / len * margin));
            }
            return out;
        }

        /** Computes centroid; returns origin for null/empty input. */
        private static Vector2f computeCenter(List<Vector2f> poly) {
            if (poly == null || poly.isEmpty()) return new Vector2f(0f, 0f);
            float cx = 0, cy = 0;
            for (Vector2f p : poly) { cx += p.x; cy += p.y; }
            return new Vector2f(cx / poly.size(), cy / poly.size());
        }
    }
}