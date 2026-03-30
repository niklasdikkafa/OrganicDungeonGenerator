package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TriangleConnector")
class TriangleConnectorTest {

    /** Generates a standard vertex list for the given sideCount. */
    private static List<Vector2f> verts(int sideCount) {
        return HexPointGenerator.generate(sideCount, 1.0f);
    }

    // -------------------------------------------------------------------------
    // Triangle count
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Triangle count")
    class TriangleCount {

        @Test
        @DisplayName("sideCount=0 produces no triangles")
        void sideCountZero() {
            assertThat(TriangleConnector.connect(verts(0), 0)).isEmpty();
        }

        @Test
        @DisplayName("sideCount=1 produces exactly 6 triangles (center fan)")
        void sideCountOne() {
            assertThat(TriangleConnector.connect(verts(1), 1)).hasSize(6);
        }

        @Test
        @DisplayName("sideCount=2 produces 6 + 12 = 18 triangles")
        void sideCountTwo() {
            assertThat(TriangleConnector.connect(verts(2), 2)).hasSize(24);
        }

        @Test
        @DisplayName("sideCount=3 produces 6 + 12 + 18 = 36 triangles")
        void sideCountThree() {
            assertThat(TriangleConnector.connect(verts(3), 3)).hasSize(54);
        }

        @ParameterizedTest(name = "sideCount = {0}")
        @ValueSource(ints = {1, 2, 3, 4})
        @DisplayName("triangle count equals 6 * sideCount * sideCount")
        void triangleCountFormula(int r) {
            int expected = 6 * r * r;
            List<Polygon> result = TriangleConnector.connect(verts(r), r);
            assertThat(result).hasSize(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Triangle shape
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Triangle shape")
    class TriangleShape {

        @Test
        @DisplayName("every polygon has exactly 3 vertices")
        void allTriangles() {
            List<Polygon> tris = TriangleConnector.connect(verts(3), 3);
            for (Polygon p : tris) {
                assertThat(p.getVertexIndices()).hasSize(3);
            }
        }

        @Test
        @DisplayName("all vertex indices are valid (within vertex list bounds)")
        void allIndicesValid() {
            int sideCount = 3;
            List<Vector2f> v = verts(sideCount);
            List<Polygon> tris = TriangleConnector.connect(v, sideCount);
            for (Polygon p : tris) {
                for (int idx : p.getVertexIndices()) {
                    assertThat(idx).isBetween(0, v.size() - 1);
                }
            }
        }

        @Test
        @DisplayName("no triangle has duplicate vertex indices")
        void noDegenerateTriangles() {
            List<Polygon> tris = TriangleConnector.connect(verts(3), 3);
            for (Polygon p : tris) {
                int[] v = p.getVertexIndices();
                assertThat(v[0]).isNotEqualTo(v[1]);
                assertThat(v[0]).isNotEqualTo(v[2]);
                assertThat(v[1]).isNotEqualTo(v[2]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // CCW orientation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CCW orientation")
    class CcwOrientation {

        @Test
        @DisplayName("all triangles in ring-1 fan are CCW")
        void ring1FanIsCCW() {
            List<Vector2f> v = verts(1);
            List<Polygon> tris = TriangleConnector.connect(v, 1);
            for (Polygon p : tris) {
                assertThat(isCCW(p, v)).as("triangle %s", Arrays.toString(p.getVertexIndices())).isTrue();
            }
        }

        @Test
        @DisplayName("all triangles in sideCount=3 grid are CCW")
        void allCCW() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = TriangleConnector.connect(v, 3);
            for (Polygon p : tris) {
                assertThat(isCCW(p, v)).as("triangle %s", Arrays.toString(p.getVertexIndices())).isTrue();
            }
        }

        /** Signed-area CCW test (same logic as Utilities.isCCW for index polygons). */
        private boolean isCCW(Polygon poly, List<Vector2f> verts) {
            int[] idx = poly.getVertexIndices();
            float sum = 0f;
            for (int i = 0; i < idx.length; i++) {
                Vector2f a = verts.get(idx[i]);
                Vector2f b = verts.get(idx[(i + 1) % idx.length]);
                sum += (b.x - a.x) * (b.y + a.y);
            }
            return sum < 0f;
        }
    }

    // -------------------------------------------------------------------------
    // Coverage - every vertex is used
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Vertex coverage")
    class VertexCoverage {

        @Test
        @DisplayName("every vertex index appears in at least one triangle")
        void allVerticesCovered() {
            int sideCount = 3;
            List<Vector2f> v = verts(sideCount);
            List<Polygon> tris = TriangleConnector.connect(v, sideCount);

            Set<Integer> used = new HashSet<>();
            for (Polygon p : tris) {
                for (int idx : p.getVertexIndices()) used.add(idx);
            }

            for (int i = 0; i < v.size(); i++) {
                assertThat(used).as("vertex %d should be covered", i).contains(i);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("two calls with the same input produce identical triangle lists")
        void deterministicOutput() {
            List<Vector2f> v = verts(2);
            List<Polygon> a = TriangleConnector.connect(v, 2);
            List<Polygon> b = TriangleConnector.connect(v, 2);
            assertThat(a).hasSameSizeAs(b);
            for (int i = 0; i < a.size(); i++) {
                assertThat(a.get(i).getVertexIndices())
                        .isEqualTo(b.get(i).getVertexIndices());
            }
        }
    }
}