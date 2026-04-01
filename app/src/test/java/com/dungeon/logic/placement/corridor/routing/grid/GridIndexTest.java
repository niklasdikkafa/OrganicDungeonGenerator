package com.dungeon.logic.placement.corridor.routing.grid;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GridIndex}.
 */
class GridIndexTest {

    private static GridIndex INDEX;
    private static BaseGrid  GRID;

    @BeforeAll
    static void buildIndex() {
        GRID  = new BaseGridBuilder().build(new BaseGridConfig(4, 6f), 7L);
        INDEX = new GridIndex(GRID);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Throws NullPointerException for null grid")
        void nullGridThrows() {
            assertThrows(NullPointerException.class, () -> new GridIndex(null));
        }

        @Test
        @DisplayName("grid field is the same object as the input grid")
        void gridFieldSet() {
            assertSame(GRID, INDEX.grid);
        }

        @Test
        @DisplayName("polys list is non-null and non-empty")
        void polysNonEmpty() {
            assertNotNull(INDEX.polys);
            assertFalse(INDEX.polys.isEmpty());
        }

        @Test
        @DisplayName("polys matches grid.getAllPolygons()")
        void polysMatchesGrid() {
            assertEquals(GRID.getAllPolygons(), INDEX.polys);
        }

        @Test
        @DisplayName("centers array length matches polys size")
        void centersLengthMatchesPolys() {
            assertEquals(INDEX.polys.size(), INDEX.centers.length);
        }

        @Test
        @DisplayName("neighbors array length matches polys size")
        void neighborsLengthMatchesPolys() {
            assertEquals(INDEX.polys.size(), INDEX.neighbors.length);
        }

        @Test
        @DisplayName("spatial index is non-null")
        void spatialNonNull() {
            assertNotNull(INDEX.spatial);
        }
    }

    // -----------------------------------------------------------------------
    // Centers
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Centers array")
    class CentersTests {

        @Test
        @DisplayName("No center is null")
        void noCenterIsNull() {
            for (int i = 0; i < INDEX.centers.length; i++)
                assertNotNull(INDEX.centers[i], "Center at index " + i + " must not be null");
        }

        @Test
        @DisplayName("No center has NaN or Infinity coordinates")
        void noCenterHasNaNOrInfinity() {
            for (int i = 0; i < INDEX.centers.length; i++) {
                Vector2f c = INDEX.centers[i];
                assertFalse(Float.isNaN(c.x) || Float.isNaN(c.y),      "Center " + i + " has NaN");
                assertFalse(Float.isInfinite(c.x) || Float.isInfinite(c.y), "Center " + i + " has Infinity");
            }
        }

        @Test
        @DisplayName("Centers are defensive copies - mutating a slot does not affect a fresh index")
        void centersAreDefensiveCopies() {
            float originalX = INDEX.centers[0].x;
            INDEX.centers[0].x += 9999f;

            GridIndex fresh = new GridIndex(GRID);
            assertEquals(originalX, fresh.centers[0].x, 1e-4f,
                    "Mutating centers[] must not affect a newly built index");

            INDEX.centers[0].x = originalX;
        }
    }

    // -----------------------------------------------------------------------
    // Neighbors
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Neighbors array")
    class NeighborsTests {

        @Test
        @DisplayName("No neighbor entry is null")
        void noNeighborEntryIsNull() {
            for (int i = 0; i < INDEX.neighbors.length; i++)
                assertNotNull(INDEX.neighbors[i], "Neighbor array at index " + i + " must not be null");
        }

        @Test
        @DisplayName("All neighbor IDs are valid polygon indices")
        void neighborIdsAreValidIndices() {
            int n = INDEX.polys.size();
            for (int i = 0; i < INDEX.neighbors.length; i++)
                for (int nb : INDEX.neighbors[i])
                    assertTrue(nb >= 0 && nb < n,
                            "Neighbor ID " + nb + " of poly " + i + " is out of bounds [0," + n + ")");
        }

        @Test
        @DisplayName("No polygon lists itself as a neighbor (no self-loops)")
        void noSelfLoops() {
            for (int i = 0; i < INDEX.neighbors.length; i++)
                for (int nb : INDEX.neighbors[i])
                    assertNotEquals(i, nb, "Polygon " + i + " must not list itself as a neighbor");
        }

        @Test
        @DisplayName("Neighbor relation is symmetric: if i->j then j->i")
        void neighborSymmetry() {
            for (int i = 0; i < INDEX.neighbors.length; i++) {
                for (int nb : INDEX.neighbors[i]) {
                    boolean found = false;
                    for (int back : INDEX.neighbors[nb]) {
                        if (back == i) { found = true; break; }
                    }
                    assertTrue(found, "Polygon " + nb + " must list " + i + " as a neighbor (symmetry)");
                }
            }
        }

        @Test
        @DisplayName("No duplicate neighbor IDs per polygon")
        void noDuplicateNeighbors() {
            for (int i = 0; i < INDEX.neighbors.length; i++) {
                Set<Integer> seen = new HashSet<>();
                for (int nb : INDEX.neighbors[i])
                    assertTrue(seen.add(nb), "Polygon " + i + " has duplicate neighbor " + nb);
            }
        }

        @Test
        @DisplayName("At least one polygon has neighbors in a non-trivial grid")
        void atLeastOnePolyHasNeighbors() {
            boolean any = false;
            for (int[] nbs : INDEX.neighbors) if (nbs.length > 0) { any = true; break; }
            assertTrue(any, "At least one polygon must have neighbors in a non-trivial grid");
        }
    }

    // -----------------------------------------------------------------------
    // findSharedEdgeById
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findSharedEdgeById")
    class FindSharedEdgeTests {

        /** Returns the first adjacent pair (i, j) found in the index, or null. */
        private int[] firstAdjacentPair() {
            for (int i = 0; i < INDEX.neighbors.length; i++)
                if (INDEX.neighbors[i].length > 0)
                    return new int[]{i, INDEX.neighbors[i][0]};
            return null;
        }

        @Test
        @DisplayName("Returns null for two non-adjacent polygons (index 0 and last, if non-adjacent)")
        void nonAdjacentReturnsNull() {
            int a = 0, b = INDEX.polys.size() - 1;
            boolean adjacent = false;
            for (int nb : INDEX.neighbors[a]) if (nb == b) { adjacent = true; break; }
            if (!adjacent)
                assertNull(INDEX.findSharedEdgeById(a, b), "Non-adjacent polygons must return null");
        }

        @Test
        @DisplayName("Returns non-null edge for an adjacent polygon pair")
        void adjacentPairReturnsEdge() {
            int[] pair = firstAdjacentPair();
            assertNotNull(pair, "No adjacent pair found");
            assertNotNull(INDEX.findSharedEdgeById(pair[0], pair[1]),
                    "Adjacent polygons must share a non-null edge");
        }

        @Test
        @DisplayName("findSharedEdgeById is symmetric: edge(a,b).equals(edge(b,a))")
        void symmetricResult() {
            int[] pair = firstAdjacentPair();
            assertNotNull(pair, "No adjacent pair found");
            Edge ab = INDEX.findSharedEdgeById(pair[0], pair[1]);
            Edge ba = INDEX.findSharedEdgeById(pair[1], pair[0]);
            assertNotNull(ab);
            assertNotNull(ba);
            assertEquals(ab, ba,
                    "findSharedEdgeById(a,b) must equal findSharedEdgeById(b,a)");
        }

        @Test
        @DisplayName("Repeated calls return the same Edge instance (cache hit)")
        void cacheHitReturnsSameInstance() {
            int[] pair = firstAdjacentPair();
            assertNotNull(pair, "No adjacent pair found");
            Edge first  = INDEX.findSharedEdgeById(pair[0], pair[1]);
            Edge second = INDEX.findSharedEdgeById(pair[0], pair[1]);
            assertSame(first, second, "Repeated calls must return the cached Edge instance");
        }

        @Test
        @DisplayName("Shared edge vertex indices appear in both polygon vertex index arrays")
        void sharedEdgeVerticesBelongToBothPolygons() {
            int[] pair = firstAdjacentPair();
            assertNotNull(pair, "No adjacent pair found");

            Edge edge = INDEX.findSharedEdgeById(pair[0], pair[1]);
            assertNotNull(edge);

            // Edge stores vertex indices into GRID.getVertices()
            int v1 = edge.getV1();
            int v2 = edge.getV2();

            Set<Integer> idxI = polyIndexSet(INDEX.polys.get(pair[0]));
            Set<Integer> idxJ = polyIndexSet(INDEX.polys.get(pair[1]));

            assertTrue(idxI.contains(v1) && idxI.contains(v2),
                    "Both edge vertex indices must appear in polygon " + pair[0]);
            assertTrue(idxJ.contains(v1) && idxJ.contains(v2),
                    "Both edge vertex indices must appear in polygon " + pair[1]);
        }

        @Test
        @DisplayName("All adjacent pairs (up to 50) have a non-null shared edge")
        void allAdjacentPairsHaveSharedEdge() {
            int checked = 0;
            outer:
            for (int i = 0; i < INDEX.neighbors.length; i++) {
                for (int nb : INDEX.neighbors[i]) {
                    assertNotNull(INDEX.findSharedEdgeById(i, nb),
                            "Adjacent polygons " + i + " and " + nb + " must share a non-null edge");
                    if (++checked >= 50) break outer;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns the set of vertex indices stored in the given polygon. */
    private static Set<Integer> polyIndexSet(Polygon p) {
        Set<Integer> s = new HashSet<>();
        for (int vi : p.getVertexIndices()) s.add(vi);
        return s;
    }
}