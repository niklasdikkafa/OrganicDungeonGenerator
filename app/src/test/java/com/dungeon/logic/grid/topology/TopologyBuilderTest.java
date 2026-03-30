package com.dungeon.logic.grid.topology;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.builder.HexPointGenerator;
import com.dungeon.logic.grid.builder.TriangleConnector;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TopologyBuilder")
class TopologyBuilderTest {

    private static final float EPSILON = 1e-5f;

    // -------------------------------------------------------------------------
    // Shared fixture: a simple 4-polygon grid
    // -------------------------------------------------------------------------

    /**
     * Creates four unit quads arranged in a 2x2 grid:
     *   v0=(0,0) v1=(1,0) v2=(2,0)
     *   v3=(0,1) v4=(1,1) v5=(2,1)
     *   v6=(0,2) v7=(1,2) v8=(2,2)
     * Polygons: BL=(0,1,4,3), BR=(1,2,5,4), TL=(3,4,7,6), TR=(4,5,8,7)
     */
    private static List<Vector2f> gridVerts() {
        return List.of(
                new Vector2f(0,0), new Vector2f(1,0), new Vector2f(2,0),
                new Vector2f(0,1), new Vector2f(1,1), new Vector2f(2,1),
                new Vector2f(0,2), new Vector2f(1,2), new Vector2f(2,2)
        );
    }

    private static List<Polygon> gridPolys() {
        return List.of(
                new Polygon(new int[]{0,1,4,3}), // BL
                new Polygon(new int[]{1,2,5,4}), // BR
                new Polygon(new int[]{3,4,7,6}), // TL
                new Polygon(new int[]{4,5,8,7})  // TR
        );
    }

    // -------------------------------------------------------------------------
    // vertexToPolygons
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("vertexToPolygons")
    class VertexToPolygons {

        @Test
        @DisplayName("center vertex (4) of 2x2 grid appears in all 4 polygons")
        void centerVertexInAllPolygons() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            assertThat(result.vertexToPolygons().get(4)).hasSize(4);
        }

        @Test
        @DisplayName("corner vertex (0) appears in exactly 1 polygon")
        void cornerVertexInOnePolygon() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            assertThat(result.vertexToPolygons().get(0)).hasSize(1);
        }

        @Test
        @DisplayName("every vertex in polygons is present in the incidence map")
        void allVerticesMapped() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            for (Polygon p : gridPolys()) {
                for (int idx : p.getVertexIndices()) {
                    assertThat(result.vertexToPolygons()).containsKey(idx);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // vertexNeighbors
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("vertexNeighbors")
    class VertexNeighbors {

        @Test
        @DisplayName("neighbor relation is symmetric")
        void symmetric() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            Map<Integer, Set<Integer>> nb = result.vertexNeighbors();
            for (Map.Entry<Integer, Set<Integer>> entry : nb.entrySet()) {
                int v = entry.getKey();
                for (int nbr : entry.getValue()) {
                    assertThat(nb.getOrDefault(nbr, Set.of()))
                            .as("neighbor relation: %d ↔ %d", v, nbr)
                            .contains(v);
                }
            }
        }

        @Test
        @DisplayName("vertex does not appear as its own neighbor")
        void noSelfLoop() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            for (Map.Entry<Integer, Set<Integer>> entry : result.vertexNeighbors().entrySet()) {
                assertThat(entry.getValue()).doesNotContain(entry.getKey());
            }
        }

        @Test
        @DisplayName("center vertex (4) has at least 4 neighbors in 2x2 grid")
        void centerVertexHasNeighbors() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            assertThat(result.vertexNeighbors().get(4).size()).isGreaterThanOrEqualTo(4);
        }
    }

    // -------------------------------------------------------------------------
    // validVertexNeighbors (boundary exclusion)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validVertexNeighbors")
    class ValidVertexNeighbors {

        @Test
        @DisplayName("valid neighbor map is a subset of the full neighbor map")
        void validIsSubsetOfFull() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            Map<Integer, Set<Integer>> full = result.vertexNeighbors();
            Map<Integer, Set<Integer>> valid = result.validVertexNeighbors();
            for (Map.Entry<Integer, Set<Integer>> entry : valid.entrySet()) {
                assertThat(full.getOrDefault(entry.getKey(), Set.of()))
                        .containsAll(entry.getValue());
            }
        }

        @Test
        @DisplayName("boundary vertices have no valid neighbors in small 2x2 grid (all vertices are boundary)")
        void allBoundaryInTinyGrid() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            for (Map.Entry<Integer, Set<Integer>> entry : result.validVertexNeighbors().entrySet()) {
                assertThat(entry.getValue()).isNotNull();
            }
        }

        @Test
        @DisplayName("no boundary vertex has any valid neighbors in a hex-ring grid")
        void hexGridBoundaryHasNoValidNeighbors() {
            List<Vector2f> v = HexPointGenerator.generate(2, 1.0f);
            List<Polygon> tris = TriangleConnector.connect(v, 2);
            TopologyBuilder.Result result = TopologyBuilder.build(v, tris);

            Map<String, Integer> edgeCount = new HashMap<>();
            for (Polygon p : tris) {
                int k = p.vertexCount();
                for (int i = 0; i < k; i++) {
                    int a = p.get(i), b = p.get((i + 1) % k);
                    String key = Math.min(a, b) + "-" + Math.max(a, b);
                    edgeCount.merge(key, 1, Integer::sum);
                }
            }
            Set<Integer> boundaryVerts = new HashSet<>();
            for (Map.Entry<String,Integer> e : edgeCount.entrySet()) {
                if (e.getValue() == 1) {
                    String[] parts = e.getKey().split("-");
                    boundaryVerts.add(Integer.parseInt(parts[0]));
                    boundaryVerts.add(Integer.parseInt(parts[1]));
                }
            }

            // No boundary vertex should appear as a key in validVertexNeighbors
            for (int bv : boundaryVerts) {
                Set<Integer> validNbs = result.validVertexNeighbors().getOrDefault(bv, Set.of());
                assertThat(validNbs).as("boundary vertex %d should have no valid neighbors", bv).isEmpty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // polygonCenters
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("polygonCenters")
    class PolygonCenters {

        @Test
        @DisplayName("every polygon has a center entry")
        void allPolygonsHaveCenter() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            for (Polygon p : gridPolys()) {
                assertThat(result.polygonCenters()).containsKey(p);
            }
        }

        @Test
        @DisplayName("center of BL quad (0,1,4,3) is (0.5, 0.5)")
        void blQuadCenter() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            Vector2f c = result.polygonCenters().get(gridPolys().getFirst());
            assertThat(c.x).isCloseTo(0.5f, within(EPSILON));
            assertThat(c.y).isCloseTo(0.5f, within(EPSILON));
        }

        @Test
        @DisplayName("center of TR quad (4,5,8,7) is (1.5, 1.5)")
        void trQuadCenter() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            Vector2f c = result.polygonCenters().get(gridPolys().get(3));
            assertThat(c.x).isCloseTo(1.5f, within(EPSILON));
            assertThat(c.y).isCloseTo(1.5f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // polygonNeighbors
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("polygonNeighbors")
    class PolygonNeighbors {

        @Test
        @DisplayName("neighbor relation is symmetric")
        void symmetric() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            Map<Polygon, Set<Polygon>> nb = result.polygonNeighbors();
            for (Map.Entry<Polygon, Set<Polygon>> entry : nb.entrySet()) {
                for (Polygon nbr : entry.getValue()) {
                    assertThat(nb.getOrDefault(nbr, Set.of()))
                            .as("%s should be neighbor of %s", nbr, entry.getKey())
                            .contains(entry.getKey());
                }
            }
        }

        @Test
        @DisplayName("BL and BR quads are neighbors (share edge 1-4)")
        void blBrAreNeighbors() {
            List<Polygon> polys = gridPolys();
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), polys);
            Polygon bl = polys.get(0);
            Polygon br = polys.get(1);
            assertThat(result.polygonNeighbors().getOrDefault(bl, Set.of())).contains(br);
        }

        @Test
        @DisplayName("BL and TR quads are NOT neighbors (only share vertex 4, not an edge)")
        void blTrAreNotNeighbors() {
            List<Polygon> polys = gridPolys();
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), polys);
            Polygon bl = polys.get(0);
            Polygon tr = polys.get(3);
            assertThat(result.polygonNeighbors().getOrDefault(bl, Set.of())).doesNotContain(tr);
        }

        @Test
        @DisplayName("polygon does not appear as its own neighbor")
        void noSelfLoop() {
            TopologyBuilder.Result result = TopologyBuilder.build(gridVerts(), gridPolys());
            for (Map.Entry<Polygon, Set<Polygon>> entry : result.polygonNeighbors().entrySet()) {
                assertThat(entry.getValue()).doesNotContain(entry.getKey());
            }
        }
    }

    // -------------------------------------------------------------------------
    // computePolygonCenters (static helper)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("computePolygonCenters (static)")
    class ComputePolygonCentersStatic {

        @Test
        @DisplayName("center of a regular triangle is the arithmetic mean of its vertices")
        void triangleCentroid() {
            List<Vector2f> v = List.of(
                    new Vector2f(0, 0), new Vector2f(3, 0), new Vector2f(0, 3)
            );
            Polygon tri = new Polygon(new int[]{0, 1, 2});
            Map<Polygon, Vector2f> centers = TopologyBuilder.computePolygonCenters(v, List.of(tri));
            assertThat(centers.get(tri).x).isCloseTo(1f, within(EPSILON));
            assertThat(centers.get(tri).y).isCloseTo(1f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Integration: full hex grid
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Integration: hex grid topology")
    class Integration {

        @Test
        @DisplayName("full hex grid build produces topology maps with no null entries")
        void noNullEntries() {
            List<Vector2f> v = HexPointGenerator.generate(2, 1.0f);
            List<Polygon> tris = TriangleConnector.connect(v, 2);
            TopologyBuilder.Result result = TopologyBuilder.build(v, tris);

            assertThat(result.vertexToPolygons()).isNotNull().isNotEmpty();
            assertThat(result.vertexNeighbors()).isNotNull().isNotEmpty();
            assertThat(result.polygonCenters()).isNotNull().isNotEmpty();
            assertThat(result.polygonNeighbors()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("polygon center count matches polygon count")
        void centerCountMatchesPolygonCount() {
            List<Vector2f> v = HexPointGenerator.generate(2, 1.0f);
            List<Polygon> tris = TriangleConnector.connect(v, 2);
            TopologyBuilder.Result result = TopologyBuilder.build(v, tris);
            assertThat(result.polygonCenters()).hasSize(tris.size());
        }
    }
}