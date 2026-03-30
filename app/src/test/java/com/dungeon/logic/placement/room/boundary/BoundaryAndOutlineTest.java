package com.dungeon.logic.placement.room.boundary;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.room.cluster.ClusterSampler;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BoundaryPolygonCollector} and {@link RoomOutlineBuilder}.
 */
@DisplayName("BoundaryPolygonCollector and RoomOutlineBuilder")
class BoundaryAndOutlineTest {

    private BaseGrid grid;
    private ClusterSampler clusterSampler;

    @BeforeEach
    void buildGrid() {
        // Small deterministic grid
        grid = new BaseGridBuilder().build(new BaseGridConfig(3, 1.0f, 0.3f, 2), 42L);
        clusterSampler = new ClusterSampler(grid, new Random(42));
    }

    // =========================================================================
    // BoundaryPolygonCollector
    // =========================================================================

    @Nested
    @DisplayName("BoundaryPolygonCollector")
    class BoundaryPolygonCollectorTests {

        private BoundaryPolygonCollector collector;

        @BeforeEach
        void setup() {
            collector = new BoundaryPolygonCollector(grid);
        }

        // --- Constructor ---

        @Test
        @DisplayName("throws for null grid")
        void nullGrid() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new BoundaryPolygonCollector(null));
        }

        // --- getValidPolygonsForCluster ---

        @Test
        @DisplayName("throws for null cluster")
        void nullCluster() {
            assertThatNullPointerException()
                    .isThrownBy(() -> collector.getValidPolygonsForCluster(null));
        }

        @Test
        @DisplayName("result is non-empty for a valid cluster from a real grid")
        void nonEmptyForRealCluster() {
            VertexCluster cluster = buildCluster(5);
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);
            assertThat(polys).isNotEmpty();
        }

        @Test
        @DisplayName("every returned polygon touches at least one cluster vertex")
        void everyPolyTouchesCluster() {
            VertexCluster cluster = buildCluster(5);
            Set<Integer> clusterVerts = new HashSet<>(cluster.getVertices());
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);

            for (Polygon p : polys) {
                boolean touches = false;
                for (int v : p.getVertexIndices()) {
                    if (clusterVerts.contains(v)) { touches = true; break; }
                }
                assertThat(touches).as("polygon %s should touch cluster", Arrays.toString(p.getVertexIndices())).isTrue();
            }
        }

        @Test
        @DisplayName("no returned polygon is fully contained inside the cluster (inner polygon excluded)")
        void noInnerPolygon() {
            VertexCluster cluster = buildCluster(8);
            Set<Integer> clusterVerts = new HashSet<>(cluster.getVertices());
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);

            for (Polygon p : polys) {
                boolean allInside = true;
                for (int v : p.getVertexIndices()) {
                    if (!clusterVerts.contains(v)) { allInside = false; break; }
                }
                assertThat(allInside)
                        .as("polygon %s should NOT be fully inside cluster", Arrays.toString(p.getVertexIndices()))
                        .isFalse();
            }
        }

        @Test
        @DisplayName("result is sorted deterministically (same cluster → same list twice)")
        void deterministicOrder() {
            VertexCluster cluster = buildCluster(5);
            List<Polygon> first  = collector.getValidPolygonsForCluster(cluster);
            List<Polygon> second = collector.getValidPolygonsForCluster(cluster);

            assertThat(first).hasSameSizeAs(second);
            for (int i = 0; i < first.size(); i++) {
                assertThat(first.get(i).getVertexIndices())
                        .isEqualTo(second.get(i).getVertexIndices());
            }
        }

        @Test
        @DisplayName("single-vertex cluster returns adjacent boundary polygons")
        void singleVertexCluster() {
            // Build a trivial single-vertex cluster
            int start = clusterSampler.pickRandomStartVertex(Set.of());
            assumeNonNegative(start);
            VertexCluster cluster = clusterSampler.getConnectedCluster(start, 1);
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);
            assertThat(polys).isNotEmpty();
        }
    }

    // =========================================================================
    // RoomOutlineBuilder
    // =========================================================================

    @Nested
    @DisplayName("RoomOutlineBuilder")
    class RoomOutlineBuilderTests {

        private RoomOutlineBuilder builder;
        private BoundaryPolygonCollector collector;

        @BeforeEach
        void setup() {
            builder   = new RoomOutlineBuilder(grid);
            collector = new BoundaryPolygonCollector(grid);
        }

        // --- Constructor ---

        @Test
        @DisplayName("throws for null grid")
        void nullGrid() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new RoomOutlineBuilder(null));
        }

        // --- computeRoomPolygon ---

        @Test
        @DisplayName("throws for null polygons")
        void nullPolygons() {
            VertexCluster c = buildCluster(3);
            assertThatNullPointerException()
                    .isThrownBy(() -> builder.computeRoomPolygon(null, c));
        }

        @Test
        @DisplayName("throws for null cluster")
        void nullCluster() {
            assertThatNullPointerException()
                    .isThrownBy(() -> builder.computeRoomPolygon(List.of(), null));
        }

        @Test
        @DisplayName("returns empty list for empty polygon list")
        void emptyInput() {
            VertexCluster cluster = buildCluster(3);
            assertThat(builder.computeRoomPolygon(List.of(), cluster)).isEmpty();
        }

        @Test
        @DisplayName("result has at least 3 points for a valid real cluster")
        void atLeastThreePoints() {
            VertexCluster cluster = buildClusterOrSkip(5);
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);
            assumeNotEmpty(polys);
            List<Vector2f> corners = builder.computeRoomPolygon(polys, cluster);
            assertThat(corners.size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("result points are within the bounding box of the polygon centers")
        void pointsInReasonableBounds() {
            VertexCluster cluster = buildClusterOrSkip(6);
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);
            assumeNotEmpty(polys);
            List<Vector2f> corners = builder.computeRoomPolygon(polys, cluster);
            if (corners.isEmpty()) return;

            // All polygon centers
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Polygon p : polys) {
                Vector2f c = grid.getPolygonCenters().get(p);
                if (c == null) continue;
                minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
                minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
            }
            // Corners should lie within the bounding box (with small tolerance for midpoints)
            float tol = grid.getEdgeLength() * 1.5f;
            for (Vector2f pt : corners) {
                assertThat(pt.x).isBetween(minX - tol, maxX + tol);
                assertThat(pt.y).isBetween(minY - tol, maxY + tol);
            }
        }

        // --- getValidPolygonNeighbors ---

        @Test
        @DisplayName("neighbor relation returned by getValidPolygonNeighbors is symmetric")
        void neighborRelationSymmetric() {
            VertexCluster cluster = buildClusterOrSkip(6);
            List<Polygon> polys = collector.getValidPolygonsForCluster(cluster);
            assumeNotEmpty(polys);

            Map<Polygon, Set<Polygon>> neighbors = builder.getValidPolygonNeighbors(polys, cluster);
            for (Map.Entry<Polygon, Set<Polygon>> entry : neighbors.entrySet()) {
                for (Polygon nbr : entry.getValue()) {
                    assertThat(neighbors.getOrDefault(nbr, Set.of()))
                            .as("neighbor relation should be symmetric")
                            .contains(entry.getKey());
                }
            }
        }

        @Test
        @DisplayName("getValidPolygonNeighbors throws for null polygons")
        void nullPolygonsNeighbors() {
            VertexCluster c = buildCluster(3);
            assertThatNullPointerException()
                    .isThrownBy(() -> builder.getValidPolygonNeighbors(null, c));
        }

        @Test
        @DisplayName("getValidPolygonNeighbors throws for null cluster")
        void nullClusterNeighbors() {
            assertThatNullPointerException()
                    .isThrownBy(() -> builder.getValidPolygonNeighbors(List.of(), null));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private VertexCluster buildCluster(int size) {
        int start = clusterSampler.pickRandomStartVertex(Set.of());
        if (start < 0) throw new AssumptionViolatedException("no valid start vertex");
        return clusterSampler.getConnectedCluster(start, size);
    }

    private VertexCluster buildClusterOrSkip(int size) {
        int start = clusterSampler.pickRandomStartVertex(Set.of());
        if (start < 0) throw new AssumptionViolatedException("no valid start vertex");
        return clusterSampler.getConnectedCluster(start, size);
    }

    private void assumeNonNegative(int v) {
        if (v < 0) throw new AssumptionViolatedException("no valid start vertex");
    }

    private void assumeNotEmpty(List<?> list) {
        if (list.isEmpty()) throw new AssumptionViolatedException("empty polygon list - cluster too small");
    }

    // Simple assumption exception reuse
    static class AssumptionViolatedException extends RuntimeException {
        AssumptionViolatedException(String msg) { super(msg); }
    }
}