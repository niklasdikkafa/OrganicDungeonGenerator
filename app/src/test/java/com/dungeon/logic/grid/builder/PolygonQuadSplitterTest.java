package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PolygonQuadSplitter")
class PolygonQuadSplitterTest {

    private static final float EPSILON = 1e-5f;

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Unit square as a 4-vertex polygon (indices 0-3). */
    private static List<Object> unitSquare() {
        List<Vector2f> v = new ArrayList<>();
        v.add(new Vector2f(0, 0)); // 0
        v.add(new Vector2f(1, 0)); // 1
        v.add(new Vector2f(1, 1)); // 2
        v.add(new Vector2f(0, 1)); // 3
        Polygon poly = new Polygon(new int[]{0, 1, 2, 3});
        return List.of(v, poly);
    }

    /** Single triangle with vertices at (0,0), (2,0), (1,2). */
    private static List<Object> singleTriangle() {
        List<Vector2f> v = new ArrayList<>();
        v.add(new Vector2f(0, 0));
        v.add(new Vector2f(2, 0));
        v.add(new Vector2f(1, 2));
        Polygon tri = new Polygon(new int[]{0, 1, 2});
        return List.of(v, tri);
    }

    // -------------------------------------------------------------------------
    // Quad count
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Output quad count")
    class QuadCount {

        @Test
        @DisplayName("splitting a triangle produces 3 quads")
        void triangleProducesThreeQuads() {
            List<Object> in = singleTriangle();
            List<Vector2f> v = cast(in.get(0));
            Polygon tri = (Polygon) in.get(1);
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of(tri));
            assertThat(result.polygons()).hasSize(3);
        }

        @Test
        @DisplayName("splitting a quad produces 4 quads")
        void quadProducesFourQuads() {
            List<Object> in = unitSquare();
            List<Vector2f> v = cast(in.get(0));
            Polygon sq = (Polygon) in.get(1);
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of(sq));
            assertThat(result.polygons()).hasSize(4);
        }

        @Test
        @DisplayName("splitting n polygons with k_i vertices produces sum(k_i) quads")
        void sumFormula() {
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0, 0)); v.add(new Vector2f(1, 0));
            v.add(new Vector2f(1, 1)); v.add(new Vector2f(0, 1)); // square (0-3)
            v.add(new Vector2f(2, 0)); v.add(new Vector2f(2, 1)); // extra quad right (1,4,5,2)
            Polygon sq1 = new Polygon(new int[]{0, 1, 2, 3});
            Polygon sq2 = new Polygon(new int[]{1, 4, 5, 2});
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of(sq1, sq2));
            // 4 + 4 = 8 quads
            assertThat(result.polygons()).hasSize(8);
        }

        @Test
        @DisplayName("empty input produces no quads")
        void emptyInput() {
            List<Vector2f> v = new ArrayList<>();
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of());
            assertThat(result.polygons()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // All output polygons have 4 vertices
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("All outputs are quads")
    class AllOutputsAreQuads {

        @Test
        @DisplayName("all polygons produced from a triangle have exactly 4 vertices")
        void triangleSplitProducesQuads() {
            List<Object> in = singleTriangle();
            List<Vector2f> v = cast(in.get(0));
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of((Polygon) in.get(1)));
            for (Polygon p : result.polygons()) {
                assertThat(p.getVertexIndices()).hasSize(4);
            }
        }

        @Test
        @DisplayName("all polygons from a full hex grid split have exactly 4 vertices")
        void fullGridSplitProducesQuads() {
            List<Vector2f> v = HexPointGenerator.generate(2, 1.0f);
            List<Polygon> tris = TriangleConnector.connect(v, 2);
            TrianglePairer.PairingResult pairing = TrianglePairer.pair(v, tris, new Random(0));
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, pairing.basePolygons());
            for (Polygon p : result.polygons()) {
                assertThat(p.getVertexIndices()).hasSize(4);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Vertex list is extended
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Vertex list modification")
    class VertexListModification {

        @Test
        @DisplayName("splitting a triangle appends exactly 4 new vertices (1 center + 3 midpoints)")
        void triangleAddsCorrectVertexCount() {
            List<Object> in = singleTriangle();
            List<Vector2f> v = cast(in.get(0));
            int before = v.size(); // 3
            PolygonQuadSplitter.splitToQuads(v, List.of((Polygon) in.get(1)));
            assertThat(v.size()).isEqualTo(before + 4); // 3 edges + 1 center
        }

        @Test
        @DisplayName("splitting a quad appends exactly 5 new vertices (1 center + 4 midpoints)")
        void quadAddsCorrectVertexCount() {
            List<Object> in = unitSquare();
            List<Vector2f> v = cast(in.get(0));
            int before = v.size(); // 4
            PolygonQuadSplitter.splitToQuads(v, List.of((Polygon) in.get(1)));
            assertThat(v.size()).isEqualTo(before + 5); // 4 edges + 1 center
        }

        @Test
        @DisplayName("shared edge midpoints are NOT duplicated for two adjacent quads")
        void sharedEdgeMidpointNotDuplicated() {
            // Two squares sharing edge (1,2)
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0, 0)); v.add(new Vector2f(1, 0));
            v.add(new Vector2f(1, 1)); v.add(new Vector2f(0, 1));
            v.add(new Vector2f(2, 0)); v.add(new Vector2f(2, 1));
            Polygon sq1 = new Polygon(new int[]{0, 1, 2, 3});
            Polygon sq2 = new Polygon(new int[]{1, 4, 5, 2});

            int before = v.size(); // 6
            PolygonQuadSplitter.splitToQuads(v, List.of(sq1, sq2));

            // sq1: 4 edges + 1 center = 5 new verts
            // sq2: 3 new edges (edge 1-2 is shared) + 1 center = 4 new verts
            // Total new: 9; total = 15
            assertThat(v.size()).isEqualTo(before + 9);
        }
    }

    // -------------------------------------------------------------------------
    // Centroid position
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Inserted centroid position")
    class CentroidPosition {

        @Test
        @DisplayName("center vertex appended for unit square is at (0.5, 0.5)")
        void squareCentroidIsCenter() {
            List<Object> in = unitSquare();
            List<Vector2f> v = cast(in.get(0));
            PolygonQuadSplitter.splitToQuads(v, List.of((Polygon) in.get(1)));
            // center is the first appended vertex (index 4)
            assertThat(v.get(4).x).isCloseTo(0.5f, within(EPSILON));
            assertThat(v.get(4).y).isCloseTo(0.5f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Index validity
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Index validity")
    class IndexValidity {

        @Test
        @DisplayName("all quad indices reference valid positions in the extended vertex list")
        void allIndicesValid() {
            List<Vector2f> v = HexPointGenerator.generate(2, 1.0f);
            List<Polygon> tris = TriangleConnector.connect(v, 2);
            TrianglePairer.PairingResult pairing = TrianglePairer.pair(v, tris, new Random(42));
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, pairing.basePolygons());

            for (Polygon p : result.polygons()) {
                for (int idx : p.getVertexIndices()) {
                    assertThat(idx).isBetween(0, v.size() - 1);
                }
            }
        }

        @Test
        @DisplayName("no quad has duplicate vertex indices")
        void noQuadHasDuplicateIndices() {
            List<Object> in = unitSquare();
            List<Vector2f> v = cast(in.get(0));
            PolygonQuadSplitter.SplitResult result = PolygonQuadSplitter.splitToQuads(v, List.of((Polygon) in.get(1)));
            for (Polygon p : result.polygons()) {
                int[] idx = p.getVertexIndices();
                Set<Integer> unique = new HashSet<>();
                for (int i : idx) unique.add(i);
                assertThat(unique).hasSize(4);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Vector2f> cast(Object o) {
        return (List<Vector2f>) o;
    }
}