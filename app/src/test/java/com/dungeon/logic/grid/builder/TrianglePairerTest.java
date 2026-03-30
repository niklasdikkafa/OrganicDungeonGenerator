package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TrianglePairer")
class TrianglePairerTest {

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Builds a standard hex grid and triangulates it. */
    private static List<Polygon> triangles(int sideCount) {
        List<Vector2f> v = HexPointGenerator.generate(sideCount, 1.0f);
        return TriangleConnector.connect(v, sideCount);
    }

    private static List<Vector2f> verts(int sideCount) {
        return HexPointGenerator.generate(sideCount, 1.0f);
    }

    // -------------------------------------------------------------------------
    // Conservation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Triangle conservation")
    class Conservation {

        @Test
        @DisplayName("every input triangle ends up in either remainingTriangles or a quad")
        void allTrianglesAccountedFor() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(42));

            int paired = result.triangleToQuadMap().size() * 2;
            int remaining = result.remainingTriangles().size();
            assertThat(paired + remaining).isEqualTo(tris.size());
        }

        @Test
        @DisplayName("basePolygons() contains remaining triangles plus all quads")
        void basePolygonsIsUnion() {
            List<Vector2f> v = verts(2);
            List<Polygon> tris = triangles(2);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(0));

            int expected = result.remainingTriangles().size() + result.triangleToQuadMap().size();
            assertThat(result.basePolygons()).hasSize(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Quad shape
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Quad shape")
    class QuadShape {

        @Test
        @DisplayName("every generated quad has exactly 4 vertices")
        void quadsHaveFourVertices() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(1));

            for (Polygon quad : result.triangleToQuadMap().values()) {
                assertThat(quad.getVertexIndices()).hasSize(4);
            }
        }

        @Test
        @DisplayName("every quad has 4 distinct vertex indices")
        void quadsHaveDistinctVertices() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(99));

            for (Polygon quad : result.triangleToQuadMap().values()) {
                int[] idx = quad.getVertexIndices();
                Set<Integer> unique = new HashSet<>();
                for (int i : idx) unique.add(i);
                assertThat(unique).hasSize(4);
            }
        }

        @Test
        @DisplayName("quad vertex indices are valid positions in the vertex list")
        void quadIndicesAreValid() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(7));

            for (Polygon quad : result.triangleToQuadMap().values()) {
                for (int idx : quad.getVertexIndices()) {
                    assertThat(idx).isBetween(0, v.size() - 1);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // No reuse
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("No triangle reuse")
    class NoReuse {

        @Test
        @DisplayName("no triangle index appears in more than one quad")
        void noTriangleReused() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(13));

            Set<Integer> usedInPairs = new HashSet<>();
            for (TrianglePairer.TrianglePair pair : result.triangleToQuadMap().keySet()) {
                assertThat(usedInPairs).doesNotContain(pair.t1, pair.t2);
                usedInPairs.add(pair.t1);
                usedInPairs.add(pair.t2);
            }
        }

        @Test
        @DisplayName("remaining triangles do not overlap with paired triangles")
        void remainingAndPairedAreDisjoint() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(55));

            Set<Integer> pairedIdx = new HashSet<>();
            for (TrianglePairer.TrianglePair pair : result.triangleToQuadMap().keySet()) {
                pairedIdx.add(pair.t1);
                pairedIdx.add(pair.t2);
            }

            for (Polygon rem : result.remainingTriangles()) {
                assertThat(pairedIdx).doesNotContain(tris.indexOf(rem));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Empty / trivial input
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("empty triangle list produces empty result")
        void emptyTriangles() {
            List<Vector2f> v = List.of(new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1));
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, List.of(), new Random(0));
            assertThat(result.basePolygons()).isEmpty();
            assertThat(result.remainingTriangles()).isEmpty();
            assertThat(result.triangleToQuadMap()).isEmpty();
        }

        @Test
        @DisplayName("single triangle produces no quads and one remaining triangle")
        void singleTriangle() {
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0, 0));
            v.add(new Vector2f(1, 0));
            v.add(new Vector2f(0, 1));
            Polygon tri = new Polygon(new int[]{0, 1, 2});

            TrianglePairer.PairingResult result = TrianglePairer.pair(v, List.of(tri), new Random(0));
            assertThat(result.triangleToQuadMap()).isEmpty();
            assertThat(result.remainingTriangles()).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // Seed reproducibility
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Seed reproducibility")
    class SeedReproducibility {

        @Test
        @DisplayName("same seed produces the same number of quads")
        void sameSeedSameQuadCount() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            int count1 = TrianglePairer.pair(v, tris, new Random(42)).triangleToQuadMap().size();
            int count2 = TrianglePairer.pair(v, tris, new Random(42)).triangleToQuadMap().size();
            assertThat(count1).isEqualTo(count2);
        }

        @RepeatedTest(5)
        @DisplayName("different seeds may produce different pairing counts (non-determinism across seeds)")
        void differentSeedsMayDiffer() {
            List<Vector2f> v = verts(3);
            List<Polygon> tris = triangles(3);
            long seed = System.nanoTime();
            TrianglePairer.PairingResult result = TrianglePairer.pair(v, tris, new Random(seed));
            assertThat(result.basePolygons().size()).isPositive();
        }
    }
}