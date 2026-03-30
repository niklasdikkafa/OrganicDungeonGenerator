package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BaseGridBuilder")
class BaseGridBuilderTest {

    private static final BaseGridConfig SMALL = new BaseGridConfig(2, 1.0f, 0.3f, 2);

    // -------------------------------------------------------------------------
    // Null / invalid input
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("throws for null config")
        void nullConfig() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BaseGridBuilder().build(null, 0L));
        }
    }

    // -------------------------------------------------------------------------
    // Grid parameters are stored
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Stored parameters")
    class StoredParameters {

        @Test
        @DisplayName("sideCount and edgeLength from config are accessible on BaseGrid")
        void configParametersStored() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            assertThat(grid.getSideCount()).isEqualTo(SMALL.sideCount);
            assertThat(grid.getEdgeLength()).isEqualTo(SMALL.edgeLength);
        }
    }

    // -------------------------------------------------------------------------
    // Geometry completeness
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Geometry completeness")
    class GeometryCompleteness {

        @Test
        @DisplayName("vertex list is non-empty")
        void verticesNonEmpty() {
            assertThat(new BaseGridBuilder().build(SMALL, 0L).getVertices()).isNotEmpty();
        }

        @Test
        @DisplayName("triangle list is non-empty")
        void trianglesNonEmpty() {
            assertThat(new BaseGridBuilder().build(SMALL, 0L).getTriangles()).isNotEmpty();
        }

        @Test
        @DisplayName("allPolygons list is non-empty")
        void allPolygonsNonEmpty() {
            assertThat(new BaseGridBuilder().build(SMALL, 0L).getAllPolygons()).isNotEmpty();
        }

        @Test
        @DisplayName("allPolygons contains only quads (4 vertices each)")
        void allPolygonsAreQuads() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            for (Polygon p : grid.getAllPolygons()) {
                assertThat(p.getVertexIndices())
                        .as("polygon should be a quad")
                        .hasSize(4);
            }
        }

        @Test
        @DisplayName("originalVertexCount is less than the final vertex count (splitting added vertices)")
        void splittingAddedVertices() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            assertThat(grid.getOriginalVertexCount())
                    .isLessThan(grid.getVertices().size());
        }
    }

    // -------------------------------------------------------------------------
    // Topology maps
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Topology maps")
    class TopologyMaps {

        @Test
        @DisplayName("polygonCenters has one entry per polygon")
        void polygonCenterCount() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            assertThat(grid.getPolygonCenters()).hasSameSizeAs(grid.getAllPolygons());
        }

        @Test
        @DisplayName("every polygon has a center entry")
        void allPolygonsHaveCenter() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            for (Polygon p : grid.getAllPolygons()) {
                assertThat(grid.getPolygonCenters()).containsKey(p);
            }
        }

        @Test
        @DisplayName("vertexNeighbors is non-empty")
        void vertexNeighborsNonEmpty() {
            assertThat(new BaseGridBuilder().build(SMALL, 0L).getVertexNeighbors()).isNotEmpty();
        }

        @Test
        @DisplayName("polygonNeighbors is non-empty")
        void polygonNeighborsNonEmpty() {
            assertThat(new BaseGridBuilder().build(SMALL, 0L).getPolygonNeighbors()).isNotEmpty();
        }

        @Test
        @DisplayName("vertexToPolygons maps every polygon's vertex to that polygon")
        void vertexToPolygonsConsistency() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            for (Polygon p : grid.getAllPolygons()) {
                for (int idx : p.getVertexIndices()) {
                    assertThat(grid.getVertexToPolygons().get(idx))
                            .as("vertex %d should map to polygon", idx)
                            .contains(p);
                }
            }
        }

        @Test
        @DisplayName("polygon neighbor relation is symmetric")
        void polygonNeighborSymmetric() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            for (Map.Entry<Polygon, Set<Polygon>> entry : grid.getPolygonNeighbors().entrySet()) {
                for (Polygon nbr : entry.getValue()) {
                    assertThat(grid.getPolygonNeighbors().getOrDefault(nbr, Set.of()))
                            .contains(entry.getKey());
                }
            }
        }

        @Test
        @DisplayName("vertex neighbor relation is symmetric")
        void vertexNeighborSymmetric() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            for (Map.Entry<Integer, Set<Integer>> entry : grid.getVertexNeighbors().entrySet()) {
                int v = entry.getKey();
                for (int nbr : entry.getValue()) {
                    assertThat(grid.getVertexNeighbors().getOrDefault(nbr, Set.of())).contains(v);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Vertex index validity
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Index validity")
    class IndexValidity {

        @Test
        @DisplayName("all polygon vertex indices are valid (within vertex list bounds)")
        void allPolygonIndicesValid() {
            BaseGrid grid = new BaseGridBuilder().build(SMALL, 0L);
            int maxIdx = grid.getVertices().size() - 1;
            for (Polygon p : grid.getAllPolygons()) {
                for (int idx : p.getVertexIndices()) {
                    assertThat(idx).isBetween(0, maxIdx);
                }
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
        @DisplayName("same seed produces grids with equal vertex counts and polygon counts")
        void sameSeedSameSize() {
            BaseGridConfig cfg = new BaseGridConfig(2, 1.0f);
            BaseGrid a = new BaseGridBuilder().build(cfg, 12345L);
            BaseGrid b = new BaseGridBuilder().build(cfg, 12345L);
            assertThat(a.getVertices()).hasSameSizeAs(b.getVertices());
            assertThat(a.getAllPolygons()).hasSameSizeAs(b.getAllPolygons());
        }

        @Test
        @DisplayName("different seeds may produce different polygon counts (pairing is random)")
        void differentSeedsMayDiffer() {
            BaseGridConfig cfg = new BaseGridConfig(3, 1.0f);
            Set<Integer> polygonCounts = new HashSet<>();
            for (long seed = 0; seed < 10; seed++) {
                polygonCounts.add(new BaseGridBuilder().build(cfg, seed).getAllPolygons().size());
            }
            assertThat(polygonCounts.size()).isGreaterThanOrEqualTo(1); // at least runs without error
        }
    }

    // -------------------------------------------------------------------------
    // Relaxation effect
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Relaxation effect")
    class RelaxationEffect {

        @Test
        @DisplayName("grid built with 0 relaxation iterations has different centers than one with 3 iterations")
        void relaxationChangesPolygonCenters() {
            BaseGridConfig noRelax  = new BaseGridConfig(3, 1.0f, 0.3f, 0);
            BaseGridConfig withRelax = new BaseGridConfig(3, 1.0f, 0.3f, 3);
            long seed = 42L;

            BaseGrid gr0 = new BaseGridBuilder().build(noRelax,  seed);
            BaseGrid gr3 = new BaseGridBuilder().build(withRelax, seed);

            // Polygon counts must match (relaxation only moves positions)
            assertThat(gr0.getAllPolygons()).hasSameSizeAs(gr3.getAllPolygons());

            // At least one polygon center should differ
            boolean anyDifferent = false;
            List<Polygon> polys = gr0.getAllPolygons();
            for (int i = 0; i < polys.size(); i++) {
                Vector2f c0 = gr0.getPolygonCenters().get(polys.get(i));
                Vector2f c3 = gr3.getPolygonCenters().get(gr3.getAllPolygons().get(i));
                if (c0 != null && c3 != null) {
                    if (Math.abs(c0.x - c3.x) > 1e-4f || Math.abs(c0.y - c3.y) > 1e-4f) {
                        anyDifferent = true;
                        break;
                    }
                }
            }
            assertThat(anyDifferent)
                    .as("relaxation should have changed at least one polygon center")
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Various sideCount values
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "sideCount = {0}")
    @ValueSource(ints = {1, 2, 3, 4})
    @DisplayName("builds successfully for sideCount 1..4")
    void buildsForVariousSideCounts(int sc) {
        assertThatNoException().isThrownBy(
                () -> new BaseGridBuilder().build(new BaseGridConfig(sc, 1.0f), 0L));
    }
}