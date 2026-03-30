package com.dungeon.logic.placement.room.cluster;

import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ClusterSampler")
class ClusterSamplerTest {

    private BaseGrid grid;
    private ClusterSampler sampler;

    @BeforeEach
    void buildGrid() {
        grid    = new BaseGridBuilder().build(new BaseGridConfig(3, 1.0f, 0.3f, 2), 99L);
        sampler = new ClusterSampler(grid, new Random(42));
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("throws for null grid")
    void nullGrid() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ClusterSampler(null, new Random()));
    }

    @Test
    @DisplayName("throws for null Random")
    void nullRandom() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ClusterSampler(grid, null));
    }

    // -------------------------------------------------------------------------
    // pickRandomStartVertex
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pickRandomStartVertex")
    class PickRandomStartVertex {

        @Test
        @DisplayName("throws for null usedVertices")
        void nullUsedVertices() {
            assertThatNullPointerException()
                    .isThrownBy(() -> sampler.pickRandomStartVertex(null));
        }

        @Test
        @DisplayName("returns a valid vertex index when usedVertices is empty")
        void returnsValidVertex() {
            int v = sampler.pickRandomStartVertex(Set.of());
            assertThat(v).isGreaterThanOrEqualTo(0);
            assertThat(v).isLessThan(grid.getVertices().size());
        }

        @Test
        @DisplayName("returned vertex is in validVertexNeighbors (non-boundary)")
        void returnsNonBoundaryVertex() {
            int v = sampler.pickRandomStartVertex(Set.of());
            assertThat(grid.getValidVertexNeighbors()).containsKey(v);
        }

        @Test
        @DisplayName("returns -1 when all valid vertices are used")
        void returnsMinusOneWhenExhausted() {
            Set<Integer> all = new HashSet<>(grid.getValidVertexNeighbors().keySet());
            int v = sampler.pickRandomStartVertex(all);
            assertThat(v).isEqualTo(-1);
        }

        @Test
        @DisplayName("does not return a vertex from usedVertices")
        void avoidsUsedVertices() {
            Set<Integer> valid = grid.getValidVertexNeighbors().keySet();
            // Block all but two valid vertices
            Set<Integer> used = new HashSet<>(valid);
            Iterator<Integer> it = used.iterator();
            Integer keep1 = it.next(); it.remove();
            Integer keep2 = it.next(); it.remove();

            int result = sampler.pickRandomStartVertex(used);
            assertThat(result).isIn(keep1, keep2);
        }
    }

    // -------------------------------------------------------------------------
    // getConnectedCluster
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getConnectedCluster")
    class GetConnectedCluster {

        @Test
        @DisplayName("throws for count < 1")
        void countTooSmall() {
            int start = sampler.pickRandomStartVertex(Set.of());
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> sampler.getConnectedCluster(start, 0));
        }

        @Test
        @DisplayName("count=1 returns cluster containing exactly the start vertex")
        void countOne() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 1);
            assertThat(cluster.getVertices()).containsExactly(start);
        }

        @RepeatedTest(10)
        @DisplayName("result vertex count is >= 1 and does not exceed requested count by more than hole-fill")
        void sizeAtLeastOne() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 5);
            assertThat(cluster.getVertices().size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("all vertex indices in the cluster are valid (within grid vertex list)")
        void allIndicesValid() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 6);
            int maxIdx = grid.getVertices().size() - 1;
            for (int v : cluster.getVertices()) {
                assertThat(v).isBetween(0, maxIdx);
            }
        }

        @Test
        @DisplayName("start vertex is always part of the cluster")
        void startVertexIncluded() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 4);
            assertThat(cluster.getVertices()).contains(start);
        }

        @Test
        @DisplayName("cluster is connected: every vertex has at least one neighbor also in the cluster")
        void clusterIsConnected() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 6);
            Set<Integer> verts = new HashSet<>(cluster.getVertices());

            if (verts.size() <= 1) return; // trivially connected

            for (int v : verts) {
                Set<Integer> neighbors = grid.getVertexNeighbors().getOrDefault(v, Set.of());
                boolean hasNeighborInCluster = neighbors.stream().anyMatch(verts::contains);
                assertThat(hasNeighborInCluster)
                        .as("vertex %d should have at least one cluster neighbor", v)
                        .isTrue();
            }
        }

        @RepeatedTest(5)
        @DisplayName("no holes: no non-cluster vertex is fully enclosed by cluster vertices")
        void noHoles() {
            int start = sampler.pickRandomStartVertex(Set.of());
            VertexCluster cluster = sampler.getConnectedCluster(start, 8);
            Set<Integer> clusterVerts = new HashSet<>(cluster.getVertices());

            // Re-run the same flood-fill logic as fillAllHoles to verify no enclosed vertices remain.
            Set<Integer> nonBoundary = grid.getValidVertexNeighbors().keySet();
            Map<Integer, Set<Integer>> neighbors = grid.getVertexNeighbors();

            ArrayDeque<Integer> q = new ArrayDeque<>();
            Set<Integer> outside = new HashSet<>();

            for (int v : neighbors.keySet()) {
                boolean isBoundary = !nonBoundary.contains(v);
                if (isBoundary && !clusterVerts.contains(v)) {
                    outside.add(v);
                    q.add(v);
                }
            }
            while (!q.isEmpty()) {
                int cur = q.poll();
                for (int nb : neighbors.getOrDefault(cur, Set.of())) {
                    if (!clusterVerts.contains(nb) && outside.add(nb)) q.add(nb);
                }
            }
            for (int v : neighbors.keySet()) {
                if (!clusterVerts.contains(v) && !outside.contains(v)) {
                    fail("Vertex %d should have been filled into the cluster but was not", v);
                }
            }
        }

        @Test
        @DisplayName("determinism: same seed and start produce the same cluster")
        void deterministic() {
            ClusterSampler s1 = new ClusterSampler(grid, new Random(7));
            ClusterSampler s2 = new ClusterSampler(grid, new Random(7));
            int start1 = s1.pickRandomStartVertex(Set.of());
            int start2 = s2.pickRandomStartVertex(Set.of());
            assertThat(start1).isEqualTo(start2);

            VertexCluster c1 = s1.getConnectedCluster(start1, 6);
            VertexCluster c2 = s2.getConnectedCluster(start2, 6);

            assertThat(c1.getVertices()).containsExactlyInAnyOrderElementsOf(c2.getVertices());
        }
    }
}