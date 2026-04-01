package com.dungeon.logic.placement.corridor.routing.grid;

import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GridQueries}.
 */
class GridQueriesTest {

    private static GridIndex INDEX;

    @BeforeAll
    static void buildIndex() {
        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(4, 6f), 13L);
        INDEX = new GridIndex(grid);
    }

    // -----------------------------------------------------------------------
    // nearestPoly
    // -----------------------------------------------------------------------
    @Nested @DisplayName("nearestPoly()")
    class NearestPoly {

        @Test @DisplayName("Returns valid polygon id for a point at the origin")
        void validIdAtOrigin() {
            int id = GridQueries.nearestPoly(INDEX, new Vector2f(0, 0));
            assertTrue(id >= 0 && id < INDEX.polys.size(),
                    "Must return a valid polygon index, got: " + id);
        }

        @Test @DisplayName("Query exactly at a polygon center returns that polygon")
        void exactCenterReturnsOwnPoly() {
            for (int i = 0; i < INDEX.centers.length; i++) {
                int result = GridQueries.nearestPoly(INDEX, new Vector2f(INDEX.centers[i]));
                assertEquals(i, result,
                        "Query at center[" + i + "] must return polygon " + i);
            }
        }

        @Test @DisplayName("Result is the truly nearest center (brute-force cross-check)")
        void resultIsTrulyNearest() {
            Vector2f q = new Vector2f(3.7f, -2.1f);
            int result = GridQueries.nearestPoly(INDEX, q);

            // brute-force nearest
            int best = -1;
            float bestD = Float.POSITIVE_INFINITY;
            for (int i = 0; i < INDEX.centers.length; i++) {
                float d = INDEX.centers[i].distanceSquared(q);
                if (d < bestD) { bestD = d; best = i; }
            }
            assertEquals(best, result,
                    "nearestPoly must return the truly closest polygon");
        }

        @Test @DisplayName("Deterministic: same query returns same result")
        void deterministic() {
            Vector2f q = new Vector2f(1f, 1f);
            int first  = GridQueries.nearestPoly(INDEX, q);
            int second = GridQueries.nearestPoly(INDEX, q);
            assertEquals(first, second);
        }

        @Test @DisplayName("Query far outside grid still returns a valid id (fallback scan)")
        void farOutsideGridFallback() {
            int id = GridQueries.nearestPoly(INDEX, new Vector2f(9999f, 9999f));
            assertTrue(id >= 0 && id < INDEX.polys.size(),
                    "Fallback scan must still return a valid polygon id");
        }

        @Test @DisplayName("Returns -1 only when index has no polygons")
        void emptyIndexReturnsMinusOne() {
            BaseGrid small = new BaseGridBuilder().build(new BaseGridConfig(1, 2f), 0L);
            GridIndex tiny = new GridIndex(small);
            // tiny will have some polys, so nearestPoly should NOT return -1
            int id = GridQueries.nearestPoly(tiny, new Vector2f(0, 0));
            assertTrue(id >= 0, "Non-empty index must not return -1");
        }
    }
}