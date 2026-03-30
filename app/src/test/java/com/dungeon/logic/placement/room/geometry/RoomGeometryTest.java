package com.dungeon.logic.placement.room.geometry;

import com.jme3.math.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomGeometryTest {

    // ---------------- helpers ----------------

    private static List<Vector2f> squareCCW(float s) {
        return List.of(
                new Vector2f(0, 0),
                new Vector2f(s, 0),
                new Vector2f(s, s),
                new Vector2f(0, s)
        );
    }

    private static List<Vector2f> squareCW(float s) {
        return List.of(
                new Vector2f(0, 0),
                new Vector2f(0, s),
                new Vector2f(s, s),
                new Vector2f(s, 0)
        );
    }

    private static List<Vector2f> deepCopy(List<Vector2f> pts) {
        ArrayList<Vector2f> out = new ArrayList<>(pts.size());
        for (Vector2f p : pts) out.add(p.clone());
        return out;
    }

    private static void assertSameCoords(List<Vector2f> a, List<Vector2f> b, float eps) {
        assertEquals(a.size(), b.size(), "size mismatch");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x, eps, "x mismatch at " + i);
            assertEquals(a.get(i).y, b.get(i).y, eps, "y mismatch at " + i);
        }
    }

    // ---------------- preprocessInnerCorners ----------------

    @Test
    void preprocessInnerCorners_rotationMustNotMutateInputListOrPoints() {
        List<Vector2f> raw = deepCopy(squareCCW(10));
        List<Vector2f> rawBefore = deepCopy(raw);

        List<Vector2f> processed = RoomGeometry.preprocessInnerCorners(raw, 1.0f, 45f, 0.4f);

        assertNotNull(processed);
        assertTrue(processed.size() >= 4);

        assertSameCoords(rawBefore, raw, 1e-6f);
    }

    @Test
    void preprocessInnerCorners_acceptsCWInput_andReturnsSomePolygon() {
        List<Vector2f> raw = deepCopy(squareCW(10));
        List<Vector2f> processed = RoomGeometry.preprocessInnerCorners(raw, 1.0f, 0f, 0.4f);

        assertNotNull(processed);
        assertTrue(processed.size() >= 4);
    }

    // ---------------- roundCorners ----------------

    @Test
    void roundCorners_sharpCorner_increasesPointCount() {
        // Very "sharp" rectangle corner should be chamfered under minAngleDeg=130
        List<Vector2f> raw = deepCopy(squareCCW(10));

        List<Vector2f> chamfered = RoomGeometry.roundCorners(raw, 1.0f, 130f);

        // In a 4-corner square, each corner qualifies => each vertex becomes 2 points -> ~8 points
        assertTrue(chamfered.size() >= 6, "Expected more points after chamfer");
    }

    @Test
    void roundCorners_notSharpEnough_keepsVertices() {
        // If threshold is very small, no corner should qualify.
        List<Vector2f> raw = deepCopy(squareCCW(10));

        List<Vector2f> chamfered = RoomGeometry.roundCorners(raw, 1.0f, 10f);

        assertEquals(raw.size(), chamfered.size(), "No chamfer expected for small threshold");
    }

    // ---------------- simplifyNearlyCollinear ----------------

    @Test
    void simplifyNearlyCollinear_removesCollinearPoint() {
        // Rectangle with an extra point on bottom edge: (0,0)->(5,0)->(10,0)
        List<Vector2f> poly = List.of(
                new Vector2f(0, 0),
                new Vector2f(5, 0),   // redundant
                new Vector2f(10, 0),
                new Vector2f(10, 10),
                new Vector2f(0, 10)
        );

        List<Vector2f> simplified = RoomGeometry.simplifyNearlyCollinear(
                poly,
                0.001f,  // dist eps
                3.0f,           // angle eps
                0.1f            // min edge
        );

        assertTrue(simplified.size() < poly.size(), "Expected redundant point to be removed");
    }

    @Test
    void simplifyNearlyCollinear_collapsesTinyEdge() {
        // Introduce a tiny edge between two almost identical points
        List<Vector2f> poly = List.of(
                new Vector2f(0, 0),
                new Vector2f(0.00001f, 0.00001f), // tiny edge from (0,0)
                new Vector2f(10, 0),
                new Vector2f(10, 10),
                new Vector2f(0, 10)
        );

        List<Vector2f> simplified = RoomGeometry.simplifyNearlyCollinear(
                poly,
                0.01f,
                3.0f,
                0.1f // minEdgeLen -> should drop the tiny edge vertex
        );

        assertTrue(simplified.size() < poly.size(), "Expected tiny-edge vertex to be removed");
    }

    // ---------------- computeOuterCorners ----------------

    @Test
    void computeOuterCorners_nLessThan3_returnsEmpty() {
        assertTrue(RoomGeometry.computeOuterCorners(List.of(), 1f).isEmpty());
        assertTrue(RoomGeometry.computeOuterCorners(List.of(new Vector2f(0,0)), 1f).isEmpty());
        assertTrue(RoomGeometry.computeOuterCorners(List.of(new Vector2f(0,0), new Vector2f(1,0)), 1f).isEmpty());
    }

    @Test
    void computeOuterCorners_forSquare_offsetsOutwards() {
        List<Vector2f> inner = deepCopy(squareCCW(10));
        Vector2f center = RoomGeometry.computeCentroid(inner);

        List<Vector2f> outer = RoomGeometry.computeOuterCorners(inner, 1.0f);

        assertNotNull(outer);
        assertEquals(inner.size(), outer.size(), "Square should keep same vertex count (no self-intersections)");

        // For a convex polygon, outer vertices should generally be farther from center than inner ones
        for (int i = 0; i < inner.size(); i++) {
            float di = inner.get(i).distanceSquared(center);
            float do2 = outer.get(i).distanceSquared(center);
            assertTrue(do2 > di, "Outer point should be further from centroid (i=" + i + ")");
        }
    }

    @Test
    void computeOuterCorners_mustNotAliasInputPoints_evenOnDegenerateEdges() {
        List<Vector2f> inner = new ArrayList<>();
        inner.add(new Vector2f(0, 0));
        inner.add(new Vector2f(10, 0));
        inner.add(new Vector2f(10, 0)); // duplicate -> degenerate edge
        inner.add(new Vector2f(10, 10));
        inner.add(new Vector2f(0, 10));

        List<Vector2f> outer = RoomGeometry.computeOuterCorners(inner, 1.0f);

        assertNotNull(outer);
        assertEquals(inner.size(), outer.size());

        for (int i = 0; i < inner.size(); i++) {
            assertNotSame(inner.get(i), outer.get(i),
                    "Outer point must be a clone, not the same Vector2f reference (i=" + i + ")");
        }
    }

    // ---------------- fixSelfIntersections ----------------

    @Test
    void fixSelfIntersections_detectsConsecutivePairIntersection_andReplacesMiddleVertices() {
        // We want AB to intersect CD where edges are (0-1) and (2-3) -> consecutive-pair check hits.
        Vector2f A = new Vector2f(0, 0);
        Vector2f B = new Vector2f(2, 2);
        Vector2f C = new Vector2f(0, 2);
        Vector2f D = new Vector2f(2, 0);

        List<Vector2f> poly = List.of(A, B, C, D);

        List<Vector2f> fixed = RoomGeometry.fixSelfIntersections(poly);

        // intersection is at (1,1)
        Vector2f I = new Vector2f(1, 1);

        assertEquals(3, fixed.size(), "Algorithm changes size and replaces vertices");
        assertEquals(I.x, fixed.get(1).x, 1e-3f);
        assertEquals(I.y, fixed.get(1).y, 1e-3f);

    }
}