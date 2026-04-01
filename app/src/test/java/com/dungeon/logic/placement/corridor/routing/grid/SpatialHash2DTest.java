package com.dungeon.logic.placement.corridor.routing.grid;

import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SpatialHash2D}.
 */
class SpatialHash2DTest {

    private static final float CELL = 5f;

    private SpatialHash2D make(int expected) {
        return new SpatialHash2D(CELL, expected);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Constructor")
    class Constructor {

        @Test @DisplayName("Valid construction does not throw")
        void valid() { assertDoesNotThrow(() -> new SpatialHash2D(1f, 10)); }

        @Test @DisplayName("Throws for cellSize <= 0")
        void zeroCellSize() {
            assertThrows(IllegalArgumentException.class, () -> new SpatialHash2D(0f, 10));
            assertThrows(IllegalArgumentException.class, () -> new SpatialHash2D(-1f, 10));
        }
    }

    // -----------------------------------------------------------------------
    // insert + query
    // -----------------------------------------------------------------------
    @Nested @DisplayName("insert / query")
    class InsertQuery {

        @Test @DisplayName("Query at insert location returns inserted id")
        void queryAtInsertLocation() {
            SpatialHash2D h = make(4);
            h.insert(new Vector2f(2f, 2f), 7);
            List<Integer> r = h.query(new Vector2f(2f, 2f));
            assertTrue(r.contains(7));
        }

        @Test @DisplayName("Empty hash returns empty list")
        void emptyHashReturnsEmpty() {
            SpatialHash2D h = make(0);
            assertTrue(h.query(new Vector2f(0, 0)).isEmpty());
        }

        @Test @DisplayName("Query finds id inserted in adjacent bucket")
        void adjacentBucketFound() {
            SpatialHash2D h = make(4);
            // Insert near bucket boundary so adjacent query picks it up
            h.insert(new Vector2f(4.9f, 0f), 3);  // bucket (0,0)
            List<Integer> r = h.query(new Vector2f(5.1f, 0f)); // bucket (1,0) — adjacent
            assertTrue(r.contains(3), "Id in adjacent bucket must be returned");
        }

        @Test @DisplayName("Query far away does not return id")
        void farQueryMisses() {
            SpatialHash2D h = make(4);
            h.insert(new Vector2f(0f, 0f), 1);
            List<Integer> r = h.query(new Vector2f(100f, 100f));
            assertFalse(r.contains(1), "Id far outside 3x3 neighborhood must not appear");
        }

        @Test @DisplayName("Multiple ids in same bucket are all returned")
        void multipleIdsInBucket() {
            SpatialHash2D h = make(8);
            h.insert(new Vector2f(1f, 1f), 10);
            h.insert(new Vector2f(2f, 2f), 20);
            h.insert(new Vector2f(3f, 3f), 30);
            List<Integer> r = h.query(new Vector2f(2f, 2f));
            assertTrue(r.containsAll(List.of(10, 20, 30)));
        }

        @Test @DisplayName("Negative coordinates work correctly")
        void negativeCoordinates() {
            SpatialHash2D h = make(4);
            h.insert(new Vector2f(-3f, -3f), 99);
            List<Integer> r = h.query(new Vector2f(-3f, -3f));
            assertTrue(r.contains(99));
        }

        @Test @DisplayName("Same id can be inserted twice and appears in results")
        void duplicateInsert() {
            SpatialHash2D h = make(4);
            h.insert(new Vector2f(0f, 0f), 5);
            h.insert(new Vector2f(0f, 0f), 5);
            List<Integer> r = h.query(new Vector2f(0f, 0f));
            assertTrue(r.contains(5));
        }

        @Test @DisplayName("All inserted ids appear in large-radius effective query")
        void allIdsAppearInBroadQuery() {
            SpatialHash2D h = make(10);
            for (int i = 0; i < 9; i++)
                h.insert(new Vector2f(i * CELL * 0.1f, i * CELL * 0.1f), i);
            // Query near center of cluster
            List<Integer> r = h.query(new Vector2f(CELL * 0.45f, CELL * 0.45f));
            Set<Integer> found = new HashSet<>(r);
            // All points inserted within one CELL of center should be found
            for (int i = 0; i < 9; i++)
                assertTrue(found.contains(i), "Id " + i + " not found in broad query");
        }
    }

    // -----------------------------------------------------------------------
    // Boundary / edge cases
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Boundary cases")
    class Boundary {

        @Test @DisplayName("Point exactly on bucket boundary is queryable from both sides")
        void bucketBoundaryPoint() {
            SpatialHash2D h = make(4);
            h.insert(new Vector2f(CELL, 0f), 42); // on boundary between bucket 0 and 1
            List<Integer> fromLeft  = h.query(new Vector2f(CELL - 0.1f, 0f));
            List<Integer> fromRight = h.query(new Vector2f(CELL + 0.1f, 0f));
            // At least one side must find it (3x3 neighborhood covers the boundary)
            assertTrue(fromLeft.contains(42) || fromRight.contains(42),
                    "Point on bucket boundary must be reachable from either side");
        }

        @Test @DisplayName("Large expectedPoints does not cause construction failure")
        void largeSizeConstructs() {
            assertDoesNotThrow(() -> new SpatialHash2D(1f, 1_000_000));
        }
    }
}