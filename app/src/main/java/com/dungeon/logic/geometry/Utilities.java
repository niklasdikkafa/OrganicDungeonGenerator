package com.dungeon.logic.geometry;

import com.dungeon.logic.grid.BaseGrid;
import com.jme3.math.Vector2f;
import org.locationtech.jts.algorithm.InteriorPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collection of small, reusable geometry helper functions used throughout the generator.
 *
 * <p>The methods in this class are intentionally stateless and operate either on index-based
 * polygons ({@link Polygon}) or on explicit vertex lists ({@link Vector2f}).</p>
 *
 * <p>Covered functionality:</p>
 * <ul>
 *   <li>Orientation checks / normalization (CW vs. CCW)</li>
 *   <li>Self-intersection detection for polygon rings</li>
 *   <li>Shared-edge detection between index-polygons</li>
 *   <li>Edge midpoint computation (via {@link BaseGrid})</li>
 *   <li>Centroid and interior-point (visual center) computation</li>
 *   <li>In-place rotation of point sets around a pivot</li>
 *   <li>JTS conversion helpers and common spatial predicates used by rasterization</li>
 * </ul>
 */
public final class Utilities {

    private Utilities() {}

    /** Shared JTS geometry factory used for polygon conversion and point predicates. */
    private static final GeometryFactory GF = new GeometryFactory();

    // ================================= POLYGON ORIENTATION ================================= //

    /**
     * Determines whether an index-based polygon is oriented counter-clockwise (CCW).
     *
     * <p>Uses the signed area (shoelace) test via determinants. A positive signed area
     * indicates CCW orientation in a standard Cartesian coordinate system (Y-up).</p>
     *
     * @param poly polygon described by vertex indices
     * @param verts vertex list referenced by {@code poly}
     * @return {@code true} if CCW, {@code false} if clockwise (CW)
     * @throws IllegalArgumentException  if {@code poly} has fewer than 3 vertices
     * @throws IndexOutOfBoundsException if {@code poly} references invalid indices in {@code verts}
     */
    public static boolean isCCW(Polygon poly, List<Vector2f> verts) {
        int[] v = poly.getVertexIndices();
        if (v.length < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices.");
        }

        double area2 = 0.0;
        int n = v.length;

        for (int i = 0; i < n; i++) {
            Vector2f a = verts.get(v[i]);
            Vector2f b = verts.get(v[(i + 1) % n]);

            area2 += (double) a.x * b.y - (double) b.x * a.y;
        }

        // CCW -> area2 > 0
        return area2 > 0.0;
    }

    /**
     * Determines whether a point-based polygon ring is oriented counter-clockwise (CCW).
     *
     * <p>Uses the signed area (shoelace) test. Works for open rings (implicitly closed)
     * and for both convex and concave simple polygons.</p>
     *
     * @param vertices polygon corners in order (implicitly closed)
     * @return {@code true} if CCW, {@code false} if CW
     * @throws IllegalArgumentException if {@code vertices} is {@code null} or has fewer than 3 points
     */
    public static boolean isCCW(List<Vector2f> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices.");
        }

        double area2 = 0.0; // 2 * signed area
        int n = vertices.size();

        for (int i = 0; i < n; i++) {
            Vector2f a = vertices.get(i);
            Vector2f b = vertices.get((i + 1) % n);
            area2 += (double) a.x * b.y - (double) b.x * a.y;
        }

        // CCW -> positive signed area
        return area2 > 0.0;
    }

    /**
     * Ensures that an index-based polygon is oriented counter-clockwise (CCW) by swapping two indices if necessary.
     *
     * <p><b>Important:</b> As written, this method is triangle-specific because it swaps indices {@code 1} and {@code 2}.
     * For polygons with more than 3 vertices, the usual approach is reversing the entire order.</p>
     *
     * @param poly polygon to normalize (expected to be a triangle)
     * @param verts vertex list referenced by {@code poly}
     * @return {@code poly} if already CCW, otherwise a new polygon instance with swapped indices
     * @throws IllegalArgumentException if {@code poly} has fewer than 3 vertices
     */
    public static Polygon ensureCCW(Polygon poly, List<Vector2f> verts) {
        if (isCCW(poly, verts)) return poly;

        int[] v = poly.getVertexIndices().clone();
        int tmp = v[1];
        v[1] = v[2];
        v[2] = tmp;
        return new Polygon(v);
    }

    /**
     * Ensures that a point-based polygon ring is oriented counter-clockwise (CCW).
     *
     * <p>If the input is clockwise, the method returns a reversed copy. If the input is already CCW,
     * it returns the original list reference unchanged.</p>
     *
     * @param vertices polygon corners in order (implicitly closed)
     * @return {@code vertices} if already CCW, otherwise a reversed copy
     * @throws IllegalArgumentException if {@code vertices} is {@code null} or has fewer than 3 points
     */
    public static List<Vector2f> ensureCCW(List<Vector2f> vertices) {
        if (isCCW(vertices)) return vertices;

        List<Vector2f> ccw = new ArrayList<>(vertices);
        Collections.reverse(ccw);
        return ccw;
    }

    // ================================ POLYGON INTERSECTION ================================ //

    /**
     * Checks whether the polygon ring is invalid, which typically indicates self-intersections
     * or other topological issues.
     *
     * <p>This method converts {@code corners} to a JTS polygon and uses {@link IsValidOp}
     * for validation. Note that JTS validity includes more than strict self-intersection
     * (e.g., repeated points or degenerate rings may also be considered invalid).</p>
     *
     * @param corners polygon corners in order (implicitly closed)
     * @return {@code true} if the polygon is invalid (e.g., self-intersecting), otherwise {@code false}
     */
    public static boolean polygonIntersectsItself(List<Vector2f> corners) {
        if (corners == null || corners.size() < 4) return false;

        org.locationtech.jts.geom.Polygon poly = toJtsPolygon(corners);

        IsValidOp op = new IsValidOp(poly);
        return !op.isValid();
    }

    // ================================ SHARED EDGE MIDPOINT ================================ //

    /**
     * Finds a shared undirected edge between two index-based polygons, if one exists.
     *
     * <p>The edge is detected by comparing consecutive index pairs from both polygons.
     * Orientation does not matter (AB equals BA).</p>
     *
     * @param a polygon A
     * @param b polygon B
     * @return the shared edge as an {@link Edge}, or {@code null} if the polygons do not share an edge
     */
    public static Edge findSharedEdge(Polygon a, Polygon b) {
        int[] A = a.getVertexIndices();
        int[] B = b.getVertexIndices();

        for (int i = 0; i < A.length; i++) {
            int a1 = A[i];
            int a2 = A[(i + 1) % A.length];

            for (int j = 0; j < B.length; j++) {
                int b1 = B[j];
                int b2 = B[(j + 1) % B.length];

                if ((a1 == b1 && a2 == b2) || (a1 == b2 && a2 == b1)) {
                    return new Edge(a1, a2);
                }
            }
        }
        return null;
    }

    /**
     * Computes the midpoint of an {@link Edge} using vertex positions from a {@link BaseGrid}.
     *
     * @param edge edge described by two vertex indices
     * @param grid grid providing the vertex list
     * @return midpoint in world/grid coordinates
     * @throws IndexOutOfBoundsException if the edge indices are not valid for {@code grid.getVertices()}
     */
    public static Vector2f computeEdgeMidpoint(Edge edge, BaseGrid grid) {
        Vector2f v1 = grid.getVertices().get(edge.getV1());
        Vector2f v2 = grid.getVertices().get(edge.getV2());
        return v1.add(v2).mult(0.5f);
    }

    // ================================ POLYGON CENTROID ================================ //

    /**
     * Computes the centroid of a polygon ring using JTS.
     *
     * <p>This method delegates to {@link org.locationtech.jts.geom.Polygon#getCentroid()}.
     * The centroid is not guaranteed to be inside the polygon for concave shapes.</p>
     *
     * @param poly polygon corners in order (implicitly closed)
     * @return centroid position in the same coordinate system as {@code poly}
     * @throws IllegalArgumentException if {@code poly} is {@code null} or has fewer than 3 points
     */
    public static Vector2f centroid(List<Vector2f> poly) {
        if (poly == null || poly.size() < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices.");
        }
        org.locationtech.jts.geom.Polygon polygon = toJtsPolygon(poly);

        Coordinate centroid = polygon.getCentroid().getCoordinate();
        return new Vector2f((float) centroid.x, (float) centroid.y);
    }

    /**
     * Computes an interior point of a polygon ring.
     *
     * <p>The returned point is guaranteed to lie inside the polygon (for valid polygons).
     * This is often more stable for placing labels, “visual centers”, or room endpoints
     * than the geometric centroid.</p>
     *
     * @param poly polygon corners in order (implicitly closed)
     * @return a point guaranteed to be inside the polygon
     * @throws IllegalArgumentException if {@code poly} is {@code null} or has fewer than 3 points
     */
    public static Vector2f interiorPoint(List<Vector2f> poly) {
        if (poly == null || poly.size() < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices.");
        }

        org.locationtech.jts.geom.Polygon polygon = toJtsPolygon(poly);

        Coordinate visualCenter = InteriorPoint.getInteriorPoint(polygon);
        return new Vector2f((float) visualCenter.x, (float) visualCenter.y);
    }

    // ================================ POLYGON ROTATION ================================ //

    /**
     * Rotates a set of points around {@code centroid} by {@code rotationDeg} degrees (in-place).
     *
     * <p>Uses the standard 2D rotation matrix. With a conventional coordinate system
     * (x right, y up), a positive angle produces a counter-clockwise rotation.</p>
     *
     * @param points points to rotate (modified in place)
     * @param centroid rotation pivot
     * @param rotationDeg rotation angle in degrees
     * @throws NullPointerException if {@code points} or {@code centroid} is {@code null}
     */
    public static void rotateAround(List<Vector2f> points, Vector2f centroid, float rotationDeg) {
        float rad = (float) Math.toRadians(rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        for (Vector2f v : points) {
            float rx = v.x - centroid.x;
            float ry = v.y - centroid.y;

            float xNew = rx * cos - ry * sin;
            float yNew = rx * sin + ry * cos;

            v.set(centroid.x + xNew, centroid.y + yNew);
        }
    }

    // ================================ ROOM RASTERIZATION HELPERS ================================ //

    /**
     * Returns {@code true} if the polygon {@code poly} covers the point {@code p} (boundary inclusive).
     *
     * <p>This is the preferred predicate for rasterization use-cases where points exactly on the boundary
     * should be considered "inside".</p>
     *
     * @param poly JTS polygon
     * @param p point in polygon coordinates
     * @return {@code true} if {@code p} is inside or on the boundary of {@code poly}
     */
    public static boolean pointInPolygonCovers(org.locationtech.jts.geom.Polygon poly, Vector2f p) {
        return poly.covers(GF.createPoint(new Coordinate(p.x, p.y)));
    }

    /**
     * Returns {@code true} if the polygon (given as vertices) strictly contains the point {@code p}.
     *
     * <p>Unlike {@link #pointInPolygonCovers(org.locationtech.jts.geom.Polygon, Vector2f)}, this uses
     * {@code contains}, which typically excludes the boundary.</p>
     *
     * @param vertices polygon vertices in order (implicitly closed)
     * @param p query point
     * @return {@code true} if the polygon strictly contains {@code p}
     * @throws IllegalArgumentException if {@code vertices} is {@code null} or has fewer than 3 points
     */
    public static boolean pointInPolygonContains(List<Vector2f> vertices, Vector2f p) {
        org.locationtech.jts.geom.Polygon poly = toJtsPolygon(vertices);
        return poly.contains(GF.createPoint(new Coordinate(p.x, p.y)));
    }

    /**
     * Returns {@code true} if polygons {@code a} and {@code b} overlap or touch.
     *
     * @param a polygon A
     * @param b polygon B
     * @return {@code true} if {@code a} intersects {@code b}
     */
    public static boolean polygonsOverlap(org.locationtech.jts.geom.Polygon a, org.locationtech.jts.geom.Polygon b) {
        return a.intersects(b);
    }

    /**
     * Computes the minimum distance between two polygons using JTS.
     *
     * @param a polygon A
     * @param b polygon B
     * @return the minimum distance between {@code a} and {@code b}
     */
    public static double polygonDistance(org.locationtech.jts.geom.Polygon a, org.locationtech.jts.geom.Polygon b) {
        return a.distance(b);
    }

    // ================================ JTS HELPERS ================================ //

    /**
     * Converts a list of {@link Vector2f} points representing a polygon ring into a JTS polygon.
     *
     * <p>The ring is closed implicitly by repeating the first coordinate as the last coordinate.</p>
     *
     * @param ring polygon vertices in order (implicitly closed)
     * @return JTS polygon representing the same geometry as the input ring
     * @throws IllegalArgumentException if {@code ring} is {@code null} or has fewer than 3 points
     */
    public static org.locationtech.jts.geom.Polygon toJtsPolygon(List<Vector2f> ring) {
        if (ring == null || ring.size() < 3) throw new IllegalArgumentException("Need >= 3 points");
        Coordinate[] coords = new Coordinate[ring.size() + 1];
        for (int i = 0; i < ring.size(); i++) coords[i] = new Coordinate(ring.get(i).x, ring.get(i).y);
        coords[ring.size()] = coords[0];
        return GF.createPolygon(coords);
    }

    /**
     * Converts an index-based polygon (vertex indices into {@code verts}) into a JTS polygon.
     *
     * <p>The ring is closed implicitly by repeating the first coordinate as the last coordinate.</p>
     *
     * @param polyIdx vertex indices describing the polygon ring
     * @param verts vertex positions referenced by {@code polyIdx}
     * @return JTS polygon representing the same geometry as the index polygon
     * @throws IllegalArgumentException  if {@code polyIdx} is {@code null} or has fewer than 3 indices
     * @throws IndexOutOfBoundsException if {@code polyIdx} references invalid indices in {@code verts}
     */
    public static org.locationtech.jts.geom.Polygon toJtsPolygon(int[] polyIdx, List<Vector2f> verts) {
        if (polyIdx == null || polyIdx.length < 3) throw new IllegalArgumentException("Need >= 3 points");
        Coordinate[] coords = new Coordinate[polyIdx.length + 1];
        for (int i = 0; i < polyIdx.length; i++) {
            Vector2f v = verts.get(polyIdx[i]);
            coords[i] = new Coordinate(v.x, v.y);
        }
        coords[polyIdx.length] = coords[0];
        return GF.createPolygon(coords);
    }
}