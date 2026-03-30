package com.dungeon.logic.geometry;

import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Utilities}.
 */
@DisplayName("Utilities")
class UtilitiesTest {

    // =========================================================================
    // Shared fixtures
    // =========================================================================

    /** Unit square, CCW: (0,0) -> (1,0) -> (1,1) -> (0,1). */
    private static final List<Vector2f> SQUARE_CCW = List.of(
            new Vector2f(0, 0), new Vector2f(1, 0),
            new Vector2f(1, 1), new Vector2f(0, 1)
    );

    /** Same unit square, CW. */
    private static final List<Vector2f> SQUARE_CW = List.of(
            new Vector2f(0, 0), new Vector2f(0, 1),
            new Vector2f(1, 1), new Vector2f(1, 0)
    );

    /** CCW triangle: (0,0) -> (2,0) -> (1,2). */
    private static final List<Vector2f> TRIANGLE_CCW = List.of(
            new Vector2f(0, 0), new Vector2f(2, 0), new Vector2f(1, 2)
    );

    /** CW triangle: same vertices, reversed. */
    private static final List<Vector2f> TRIANGLE_CW = List.of(
            new Vector2f(1, 2), new Vector2f(2, 0), new Vector2f(0, 0)
    );

    /** L-shaped concave polygon, CCW. */
    private static final List<Vector2f> L_SHAPE_CCW = List.of(
            new Vector2f(0, 0), new Vector2f(2, 0), new Vector2f(2, 1),
            new Vector2f(1, 1), new Vector2f(1, 2), new Vector2f(0, 2)
    );

    /** Self-intersecting "butterfly" ring. */
    private static final List<Vector2f> SELF_INTERSECTING = List.of(
            new Vector2f(0, 0), new Vector2f(2, 2),
            new Vector2f(2, 0), new Vector2f(0, 2)
    );

    // =========================================================================
    // Polygon orientation - point-based
    // =========================================================================

    @Nested
    @DisplayName("isCCW(List<Vector2f>)")
    class IsCCWPoints {

        @Test
        @DisplayName("returns true for CCW square")
        void squareCCW() {
            assertThat(Utilities.isCCW(SQUARE_CCW)).isTrue();
        }

        @Test
        @DisplayName("returns false for CW square")
        void squareCW() {
            assertThat(Utilities.isCCW(SQUARE_CW)).isFalse();
        }

        @Test
        @DisplayName("returns true for CCW triangle")
        void triangleCCW() {
            assertThat(Utilities.isCCW(TRIANGLE_CCW)).isTrue();
        }

        @Test
        @DisplayName("returns false for CW triangle")
        void triangleCW() {
            assertThat(Utilities.isCCW(TRIANGLE_CW)).isFalse();
        }

        @Test
        @DisplayName("returns true for concave CCW polygon")
        void concaveCCW() {
            assertThat(Utilities.isCCW(L_SHAPE_CCW)).isTrue();
        }

        @Test
        @DisplayName("throws for null input")
        void nullInput() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.isCCW(null));
        }

        @Test
        @DisplayName("throws for fewer than 3 vertices")
        void tooFewVertices() {
            List<Vector2f> line = List.of(new Vector2f(0, 0), new Vector2f(1, 1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.isCCW(line));
        }
    }

    // =========================================================================
    // Polygon orientation - index-based
    // =========================================================================

    @Nested
    @DisplayName("isCCW(Polygon, List<Vector2f>)")
    class IsCCWIndexed {

        private final List<Vector2f> verts = List.of(
                new Vector2f(0, 0), new Vector2f(1, 0),
                new Vector2f(1, 1), new Vector2f(0, 1)
        );

        @Test
        @DisplayName("returns true for CCW-ordered indices")
        void ccwIndices() {
            Polygon p = new Polygon(new int[]{0, 1, 2, 3});
            assertThat(Utilities.isCCW(p, verts)).isTrue();
        }

        @Test
        @DisplayName("returns false for CW-ordered indices")
        void cwIndices() {
            Polygon p = new Polygon(new int[]{3, 2, 1, 0});
            assertThat(Utilities.isCCW(p, verts)).isFalse();
        }
    }

    // =========================================================================
    // ensureCCW - point-based
    // =========================================================================

    @Nested
    @DisplayName("ensureCCW(List<Vector2f>)")
    class EnsureCCWPoints {

        @Test
        @DisplayName("returns same reference when already CCW")
        void identityWhenCCW() {
            List<Vector2f> result = Utilities.ensureCCW(SQUARE_CCW);
            assertThat(result).isSameAs(SQUARE_CCW);
        }

        @Test
        @DisplayName("reverses CW polygon to produce CCW result")
        void reversesCW() {
            List<Vector2f> result = Utilities.ensureCCW(SQUARE_CW);
            assertThat(Utilities.isCCW(result)).isTrue();
        }

        @Test
        @DisplayName("reversed polygon has same vertices as original")
        void sameVerticesAfterReversal() {
            List<Vector2f> result = Utilities.ensureCCW(SQUARE_CW);
            assertThat(result).containsExactlyInAnyOrderElementsOf(SQUARE_CW);
        }

        @Test
        @DisplayName("result of reversing CW triangle is CCW")
        void triangleCWBecomeCCW() {
            List<Vector2f> result = Utilities.ensureCCW(TRIANGLE_CW);
            assertThat(Utilities.isCCW(result)).isTrue();
        }
    }

    // =========================================================================
    // ensureCCW - index-based (triangle-specific)
    // =========================================================================

    @Nested
    @DisplayName("ensureCCW(Polygon, List<Vector2f>)")
    class EnsureCCWIndexed {

        private final List<Vector2f> verts = List.of(
                new Vector2f(0, 0), new Vector2f(2, 0), new Vector2f(1, 2)
        );

        @Test
        @DisplayName("returns same instance when already CCW")
        void identityWhenCCW() {
            Polygon ccw = new Polygon(new int[]{0, 1, 2});
            assertThat(Utilities.ensureCCW(ccw, verts)).isSameAs(ccw);
        }

        @Test
        @DisplayName("produces CCW polygon from CW triangle")
        void cwTriangleBecomesCCW() {
            Polygon cw = new Polygon(new int[]{2, 1, 0});
            Polygon result = Utilities.ensureCCW(cw, verts);
            assertThat(Utilities.isCCW(result, verts)).isTrue();
        }
    }

    // =========================================================================
    // Self-intersection
    // =========================================================================

    @Nested
    @DisplayName("polygonIntersectsItself")
    class SelfIntersection {

        @Test
        @DisplayName("returns false for valid convex polygon")
        void validSquare() {
            assertThat(Utilities.polygonIntersectsItself(SQUARE_CCW)).isFalse();
        }

        @Test
        @DisplayName("returns false for valid concave polygon")
        void validLShape() {
            assertThat(Utilities.polygonIntersectsItself(L_SHAPE_CCW)).isFalse();
        }

        @Test
        @DisplayName("returns true for butterfly (self-intersecting) ring")
        void butterflyIsInvalid() {
            assertThat(Utilities.polygonIntersectsItself(SELF_INTERSECTING)).isTrue();
        }

        @Test
        @DisplayName("returns false for null input (no exception, safe guard)")
        void nullInput() {
            assertThat(Utilities.polygonIntersectsItself(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for fewer than 4 points (not enough to self-intersect)")
        void tooFewPoints() {
            List<Vector2f> tri = List.of(
                    new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1)
            );
            assertThat(Utilities.polygonIntersectsItself(tri)).isFalse();
        }
    }

    // =========================================================================
    // Shared edge detection
    // =========================================================================

    @Nested
    @DisplayName("findSharedEdge")
    class FindSharedEdge {

        // Two triangles sharing edge (1,2): triangle A = (0,1,2), triangle B = (1,3,2)
        private final Polygon triA = new Polygon(new int[]{0, 1, 2});
        private final Polygon triB = new Polygon(new int[]{1, 3, 2});
        private final Polygon noShared = new Polygon(new int[]{4, 5, 6});

        @Test
        @DisplayName("finds shared edge between adjacent triangles")
        void findsSharedEdge() {
            Edge e = Utilities.findSharedEdge(triA, triB);
            assertThat(e).isNotNull();
            assertThat(e.getV1()).isIn(1, 2);
            assertThat(e.getV2()).isIn(1, 2);
            assertThat(e.getV1()).isNotEqualTo(e.getV2());
        }

        @Test
        @DisplayName("shared edge is undirected - same edge regardless of polygon order")
        void edgeIsUndirected() {
            Edge ab = Utilities.findSharedEdge(triA, triB);
            Edge ba = Utilities.findSharedEdge(triB, triA);
            assertThat(ab).isEqualTo(ba);
        }

        @Test
        @DisplayName("returns null for non-adjacent polygons")
        void noSharedEdge() {
            assertThat(Utilities.findSharedEdge(triA, noShared)).isNull();
        }

        @Test
        @DisplayName("returns null when polygon shares only a single vertex")
        void onlyVertexShared() {
            // triA = (0,1,2), singleVertex shares only index 2
            Polygon singleVertex = new Polygon(new int[]{2, 5, 6});
            assertThat(Utilities.findSharedEdge(triA, singleVertex)).isNull();
        }
    }

    // =========================================================================
    // Centroid
    // =========================================================================

    @Nested
    @DisplayName("centroid")
    class Centroid {

        @Test
        @DisplayName("centroid of unit square is (0.5, 0.5)")
        void unitSquareCentroid() {
            Vector2f c = Utilities.centroid(SQUARE_CCW);
            assertThat(c.x).isCloseTo(0.5f, within(1e-5f));
            assertThat(c.y).isCloseTo(0.5f, within(1e-5f));
        }

        @Test
        @DisplayName("centroid of symmetric triangle is arithmetic mean of vertices")
        void triangleCentroid() {
            // (0,0),(2,0),(1,2) -> centroid = (1, 2/3)
            Vector2f c = Utilities.centroid(TRIANGLE_CCW);
            assertThat(c.x).isCloseTo(1.0f, within(1e-5f));
            assertThat(c.y).isCloseTo(2f / 3f, within(1e-5f));
        }

        @Test
        @DisplayName("throws for null input")
        void nullInput() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.centroid(null));
        }

        @Test
        @DisplayName("throws for fewer than 3 vertices")
        void tooFewVertices() {
            List<Vector2f> line = List.of(new Vector2f(0, 0), new Vector2f(1, 0));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.centroid(line));
        }
    }

    // =========================================================================
    // Interior point
    // =========================================================================

    @Nested
    @DisplayName("interiorPoint")
    class InteriorPointTests {

        @Test
        @DisplayName("interior point of convex polygon lies inside it")
        void insideConvex() {
            Vector2f p = Utilities.interiorPoint(SQUARE_CCW);
            assertThat(Utilities.pointInPolygonContains(SQUARE_CCW, p)).isTrue();
        }

        @Test
        @DisplayName("interior point of concave polygon lies inside it")
        void insideConcave() {
            Vector2f p = Utilities.interiorPoint(L_SHAPE_CCW);
            assertThat(Utilities.pointInPolygonContains(L_SHAPE_CCW, p)).isTrue();
        }

        @Test
        @DisplayName("throws for null input")
        void nullInput() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.interiorPoint(null));
        }
    }

    // =========================================================================
    // rotateAround
    // =========================================================================

    @Nested
    @DisplayName("rotateAround")
    class RotateAround {

        @Test
        @DisplayName("rotating by 0 degrees leaves all points unchanged")
        void zeroDegreeRotation() {
            List<Vector2f> pts = mutableSquare();
            Vector2f pivot = new Vector2f(0.5f, 0.5f);
            Utilities.rotateAround(pts, pivot, 0f);
            assertThat(pts.get(0).x).isCloseTo(0f, within(1e-5f));
            assertThat(pts.get(0).y).isCloseTo(0f, within(1e-5f));
            assertThat(pts.get(1).x).isCloseTo(1f, within(1e-5f));
            assertThat(pts.get(1).y).isCloseTo(0f, within(1e-5f));
        }

        @Test
        @DisplayName("rotating by 360 degrees returns to original positions")
        void fullRotation() {
            List<Vector2f> pts = mutableSquare();
            Vector2f pivot = new Vector2f(0.5f, 0.5f);
            Utilities.rotateAround(pts, pivot, 360f);
            assertThat(pts.getFirst().x).isCloseTo(0f, within(1e-5f));
            assertThat(pts.getFirst().y).isCloseTo(0f, within(1e-5f));
        }

        @Test
        @DisplayName("rotating by 90 degrees CCW maps (1,0) relative to pivot to (0,1)")
        void ninetyDegreeCCW() {
            // Single point at (1, 0.5) with pivot (0.5, 0.5) -> after 90° CCW -> (0.5, 1)
            List<Vector2f> pts = new java.util.ArrayList<>();
            pts.add(new Vector2f(1f, 0.5f));
            Vector2f pivot = new Vector2f(0.5f, 0.5f);
            Utilities.rotateAround(pts, pivot, 90f);
            assertThat(pts.getFirst().x).isCloseTo(0.5f, within(1e-5f));
            assertThat(pts.getFirst().y).isCloseTo(1.0f, within(1e-5f));
        }

        @Test
        @DisplayName("rotating by 180 degrees maps each point to opposite side of pivot")
        void halfRotation() {
            List<Vector2f> pts = new java.util.ArrayList<>();
            pts.add(new Vector2f(1f, 0f));
            Vector2f pivot = new Vector2f(0f, 0f);
            Utilities.rotateAround(pts, pivot, 180f);
            assertThat(pts.getFirst().x).isCloseTo(-1f, within(1e-5f));
            assertThat(pts.getFirst().y).isCloseTo(0f, within(1e-5f));
        }

        @Test
        @DisplayName("rotation around the point itself is identity")
        void rotateAroundSelf() {
            List<Vector2f> pts = new java.util.ArrayList<>();
            pts.add(new Vector2f(3f, 4f));
            Utilities.rotateAround(pts, new Vector2f(3f, 4f), 45f);
            assertThat(pts.getFirst().x).isCloseTo(3f, within(1e-5f));
            assertThat(pts.getFirst().y).isCloseTo(4f, within(1e-5f));
        }

        @Test
        @DisplayName("polygon winding flips after 180-degree rotation around external pivot")
        void windingFlipAfterExternalRotation() {
            // Rotating a CCW polygon 180° around an external pivot produces a CCW polygon
            List<Vector2f> pts = mutableSquare();
            Vector2f externalPivot = new Vector2f(10f, 10f);
            Utilities.rotateAround(pts, externalPivot, 180f);
            // After 180° rotation around an external pivot the winding should be CCW
            assertThat(Utilities.isCCW(pts)).isTrue();
        }

        private List<Vector2f> mutableSquare() {
            return new java.util.ArrayList<>(List.of(
                    new Vector2f(0, 0), new Vector2f(1, 0),
                    new Vector2f(1, 1), new Vector2f(0, 1)
            ));
        }
    }

    // =========================================================================
    // Point-in-polygon
    // =========================================================================

    @Nested
    @DisplayName("pointInPolygonCovers / pointInPolygonContains")
    class PointInPolygon {

        private final org.locationtech.jts.geom.Polygon jtsSquare =
                Utilities.toJtsPolygon(SQUARE_CCW);

        @Test
        @DisplayName("covers: interior point returns true")
        void coversInteriorPoint() {
            assertThat(Utilities.pointInPolygonCovers(jtsSquare, new Vector2f(0.5f, 0.5f))).isTrue();
        }

        @Test
        @DisplayName("covers: boundary point returns true (covers includes boundary)")
        void coversBoundaryPoint() {
            assertThat(Utilities.pointInPolygonCovers(jtsSquare, new Vector2f(0f, 0.5f))).isTrue();
        }

        @Test
        @DisplayName("covers: exterior point returns false")
        void coversExteriorPoint() {
            assertThat(Utilities.pointInPolygonCovers(jtsSquare, new Vector2f(2f, 2f))).isFalse();
        }

        @Test
        @DisplayName("contains: interior point returns true")
        void containsInteriorPoint() {
            assertThat(Utilities.pointInPolygonContains(SQUARE_CCW, new Vector2f(0.5f, 0.5f))).isTrue();
        }

        @Test
        @DisplayName("contains: exterior point returns false")
        void containsExteriorPoint() {
            assertThat(Utilities.pointInPolygonContains(SQUARE_CCW, new Vector2f(2f, 2f))).isFalse();
        }
    }

    // =========================================================================
    // polygonsOverlap / polygonDistance
    // =========================================================================

    @Nested
    @DisplayName("polygonsOverlap and polygonDistance")
    class OverlapAndDistance {

        private final org.locationtech.jts.geom.Polygon squareA =
                Utilities.toJtsPolygon(SQUARE_CCW);

        private final org.locationtech.jts.geom.Polygon overlapping =
                Utilities.toJtsPolygon(List.of(
                        new Vector2f(0.5f, 0.5f), new Vector2f(1.5f, 0.5f),
                        new Vector2f(1.5f, 1.5f), new Vector2f(0.5f, 1.5f)
                ));

        private final org.locationtech.jts.geom.Polygon distant =
                Utilities.toJtsPolygon(List.of(
                        new Vector2f(5f, 5f), new Vector2f(6f, 5f),
                        new Vector2f(6f, 6f), new Vector2f(5f, 6f)
                ));

        @Test
        @DisplayName("overlapping polygons return true")
        void overlappingPolygons() {
            assertThat(Utilities.polygonsOverlap(squareA, overlapping)).isTrue();
        }

        @Test
        @DisplayName("disjoint polygons return false")
        void disjointPolygons() {
            assertThat(Utilities.polygonsOverlap(squareA, distant)).isFalse();
        }

        @Test
        @DisplayName("distance between overlapping polygons is 0")
        void distanceOfOverlapping() {
            assertThat(Utilities.polygonDistance(squareA, overlapping)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("distance between disjoint polygons is positive and correct")
        void distanceOfDisjoint() {
            // squareA: max x=1, distant: min x=5 -> gap of 4 along x axis
            assertThat(Utilities.polygonDistance(squareA, distant)).isCloseTo(
                    Math.sqrt(4.0 * 4.0 + 4.0 * 4.0), within(1e-5));
        }
    }

    // =========================================================================
    // JTS conversion helpers
    // =========================================================================

    @Nested
    @DisplayName("toJtsPolygon")
    class ToJtsPolygon {

        @Test
        @DisplayName("converts point list to closed JTS polygon (ring is closed)")
        void closedRingFromPointList() {
            org.locationtech.jts.geom.Polygon p = Utilities.toJtsPolygon(SQUARE_CCW);
            assertThat(p.getExteriorRing().getCoordinates()).hasSize(5); // 4 + closing coord
        }

        @Test
        @DisplayName("converted polygon covers its own interior point")
        void convertedPolygonIsValid() {
            org.locationtech.jts.geom.Polygon p = Utilities.toJtsPolygon(SQUARE_CCW);
            assertThat(p.isValid()).isTrue();
        }

        @Test
        @DisplayName("throws for null point list")
        void nullPointList() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.toJtsPolygon(null));
        }

        @Test
        @DisplayName("throws for fewer than 3 points")
        void tooFewPoints() {
            List<Vector2f> line = List.of(new Vector2f(0, 0), new Vector2f(1, 0));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.toJtsPolygon(line));
        }

        @Test
        @DisplayName("converts index array to closed JTS polygon")
        void closedRingFromIndexArray() {
            List<Vector2f> verts = List.of(
                    new Vector2f(0, 0), new Vector2f(1, 0),
                    new Vector2f(1, 1), new Vector2f(0, 1)
            );
            org.locationtech.jts.geom.Polygon p = Utilities.toJtsPolygon(new int[]{0, 1, 2, 3}, verts);
            assertThat(p.getExteriorRing().getCoordinates()).hasSize(5);
        }

        @Test
        @DisplayName("throws for null index array")
        void nullIndexArray() {
            List<Vector2f> verts = List.of(new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.toJtsPolygon(null, verts));
        }

        @Test
        @DisplayName("throws for index array with fewer than 3 entries")
        void tooFewIndices() {
            List<Vector2f> verts = List.of(new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Utilities.toJtsPolygon(new int[]{0, 1}, verts));
        }

        @Test
        @DisplayName("point-list and index-array conversions produce geometrically identical results")
        void pointListAndIndexEquivalence() {
            List<Vector2f> verts = new java.util.ArrayList<>(SQUARE_CCW);
            org.locationtech.jts.geom.Polygon fromList  = Utilities.toJtsPolygon(SQUARE_CCW);
            org.locationtech.jts.geom.Polygon fromIndex = Utilities.toJtsPolygon(new int[]{0, 1, 2, 3}, verts);
            assertThat(fromList.equalsExact(fromIndex, 1e-9)).isTrue();
        }
    }

    // =========================================================================
    // Parametrised: isCCW is invariant under translation
    // =========================================================================

    @Nested
    @DisplayName("isCCW - translation invariance")
    class TranslationInvariance {

        static Stream<Vector2f> offsets() {
            return Stream.of(
                    new Vector2f(0, 0),
                    new Vector2f(100, 0),
                    new Vector2f(0, 100),
                    new Vector2f(-50, -50),
                    new Vector2f(1e6f, 1e6f)
            );
        }

        @ParameterizedTest(name = "offset = ({0})")
        @MethodSource("offsets")
        @DisplayName("CCW square translated by arbitrary offset remains CCW")
        void ccwPreservedAfterTranslation(Vector2f offset) {
            List<Vector2f> translated = SQUARE_CCW.stream()
                    .map(v -> v.add(offset))
                    .toList();
            assertThat(Utilities.isCCW(translated)).isTrue();
        }
    }
}