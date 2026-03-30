package com.dungeon.logic.placement.room.geometry;

import com.jme3.math.Vector2f;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.logic.geometry.Utilities.*;

/**
 * Utility methods for constructing robust room geometry from an organic 2D polygon.
 * <p>
 * This class provides:
 * <ul>
 *   <li>preprocessing of raw inner room corners (orientation, chamfering, rotation, simplification)</li>
 *   <li>derivation of an outer contour from the processed inner contour (constant wall thickness offset)</li>
 *   <li>robust post-processing utilities such as self-intersection cleanup</li>
 * </ul>
 * All methods are stateless and operate purely on the provided corner lists.
 */
public final class RoomGeometry {

    /**
     * Preprocesses raw inner corners to obtain a stable polygon suitable for wall generation.
     * <p>
     * Pipeline:
     * <ol>
     *   <li>Ensure counter-clockwise (CCW) orientation</li>
     *   <li>Chamfer sharp corners (linear corner cuts)</li>
     *   <li>Compute polygon centroid</li>
     *   <li>Optionally rotate around centroid by {@code rotationDeg}</li>
     *   <li>Simplify nearly collinear points and collapse tiny edges</li>
     * </ol>
     *
     * @param rawInnerCorners raw polygon corners
     * @param wallThickness wall thickness used to derive simplification thresholds
     * @param rotationDeg optional rotation angle in degrees; {@code 0} means no rotation
     * @param gridEdgeLength the side length of the base grid used for room placement
     * @return processed inner corners (CCW) ready for outer contour generation and meshing
     */
    public static List<Vector2f> preprocessInnerCorners(List<Vector2f> rawInnerCorners,
                                                        float wallThickness,
                                                        float rotationDeg,
                                                        float gridEdgeLength) {

        List<Vector2f> inner = new ArrayList<>();
        for (Vector2f p : ensureCCW(rawInnerCorners)) inner.add(p.clone());

        // Chamfer: replace very sharp corners by two points along the adjacent edges
        float chamferRadius = gridEdgeLength * 0.1f;
        float minAngleDeg = 130f;
        inner = roundCorners(inner, chamferRadius, minAngleDeg);

        Vector2f center = centroid(inner);

        // Rotate around centroid (in-place)
        if (rotationDeg != 0f) rotateAround(inner, center, rotationDeg);

        // Simplify
        float distEps   = wallThickness * 0.03f;
        float angleEps  = 3.0f;
        float minEdge   = wallThickness * 0.40f;
        inner = simplifyNearlyCollinear(inner, distEps, angleEps, minEdge);

        return inner;
    }

    /**
     * Computes the centroid of a polygon.
     * <p>
     * This delegates to {@link com.dungeon.logic.geometry.Utilities#centroid(List)}.
     *
     * @param innerCorners polygon corners (expected simple polygon, CCW or CW)
     * @return centroid of the polygon
     */
    public static Vector2f computeCentroid(List<Vector2f> innerCorners) {
        return centroid(innerCorners);
    }

    /**
     * Computes an interior point of a polygon, which may differ from the centroid.
     * <p>
     * The interior point is a point that is more visually representative of the polygon's interior,
     * especially for non-convex shapes. This will be used for corridor routing.
     *
     * @param innerCorners polygon corners (expected simple polygon, CCW or CW)
     * @return interior point of the polygon
     */
    public static Vector2f computeInteriorPoint(List<Vector2f> innerCorners) {
        return interiorPoint(innerCorners);
    }

    /**
     * Computes the outer contour for a room given its inner contour and a desired wall thickness.
     * <p>
     * The method approximates a constant-distance offset polygon by shifting each vertex along
     * the angle-bisector direction derived from the adjacent edge normals.
     * <p>
     * Robustness handling:
     * <ul>
     *   <li>Degenerate edges (very small) are skipped by keeping the original vertex</li>
     *   <li>Nearly straight corners (bisector undefined / unstable) fall back to a single normal</li>
     *   <li>Extreme offsets at sharp corners are clamped by a minimum cosine value</li>
     *   <li>Potential self-intersections are corrected by {@link #fixSelfIntersections(List)}</li>
     * </ul>
     *
     * @param innerCorners processed inner polygon corners (should be CCW for outward normals as used here)
     * @param wallThickness target wall thickness
     * @return outer polygon corners (post-processed to reduce self intersections)
     */
    public static List<Vector2f> computeOuterCorners(List<Vector2f> innerCorners, float wallThickness) {
        int n = innerCorners.size();
        List<Vector2f> outer = new ArrayList<>(n);
        if (n < 3) return outer;

        // EPS: threshold for "degenerate edge" decisions in offset computation (practical tolerance)
        final float EPS = 1e-5f;

        // MIN_COS: clamp for cosine(half-angle) to avoid exploding offsets at very sharp corners
        final float MIN_COS = 0.2f;

        for (int i = 0; i < n; i++) {
            Vector2f prev = innerCorners.get((i - 1 + n) % n);
            Vector2f curr = innerCorners.get(i);
            Vector2f next = innerCorners.get((i + 1) % n);

            // Edge directions along polygon boundary
            Vector2f e0 = curr.subtract(prev);
            Vector2f e1 = next.subtract(curr);

            // Skip if edges are too small (unstable normals)
            if (e0.lengthSquared() < EPS || e1.lengthSquared() < EPS) {
                outer.add(curr.clone());
                continue;
            }

            e0.normalizeLocal();
            e1.normalizeLocal();

            // Outward normals for CCW polygon (rotate clockwise by 90 degrees)
            Vector2f n0 = new Vector2f(e0.y, -e0.x);
            Vector2f n1 = new Vector2f(e1.y, -e1.x);

            // Average normal approximates bisector direction
            Vector2f nAvg = n0.add(n1);

            // If normals cancel out (near 180°), fall back to shifting by one normal
            if (nAvg.lengthSquared() < EPS) {
                outer.add(curr.add(n0.mult(wallThickness)));
                continue;
            }

            nAvg.normalizeLocal();

            // cosHalf = cos(half-angle) between nAvg and n0
            float cosHalf = nAvg.dot(n0);

            // Clamp very small cosHalf to avoid huge offsets
            if (Math.abs(cosHalf) < MIN_COS) cosHalf = Math.copySign(MIN_COS, cosHalf);

            // offset = hypotenuse = adjacent side / alpha = wallThickness / cosHalf
            float offset = wallThickness / cosHalf;
            outer.add(curr.add(nAvg.mult(offset)));
        }

        return fixSelfIntersections(outer);
    }

    // ------------------- helpers -------------------

    /**
     * Chamfers sharp corners by replacing each qualifying corner point with two points on its adjacent edges.
     * <p>
     * For each vertex {@code curr}, two points are created:
     * <ul>
     *   <li>{@code p1} on the edge from {@code curr} toward {@code prev}</li>
     *   <li>{@code p2} on the edge from {@code curr} toward {@code next}</li>
     * </ul>
     * The distance {@code d} from {@code curr} is limited by:
     * <ul>
     *   <li>{@code maxRadius}</li>
     *   <li>40% of each adjacent edge length (to prevent cutting too far on short edges)</li>
     * </ul>
     * <p>
     * Angle criterion:
     * {@code angle = acos(dot(dirPrev, dirNext))} where {@code dirPrev} and {@code dirNext} point
     * from {@code curr} to {@code prev/next}. A corner is chamfered if {@code angle <= minAngleDeg}.
     *
     * @param input polygon corners (ideally CCW and simple)
     * @param maxRadius maximum chamfer radius (upper bound on distance along each edge)
     * @param minAngleDeg chamfer threshold in degrees (corners with angle <= threshold are chamfered)
     * @return new list of corners; may contain more points than the input
     */
    public static List<Vector2f> roundCorners(List<Vector2f> input, float maxRadius, float minAngleDeg) {
        int n = input.size();
        List<Vector2f> result = new ArrayList<>();
        if (n < 3) return new ArrayList<>(input);

        float minAngleRad = (float) Math.toRadians(minAngleDeg);

        for (int i = 0; i < n; i++) {
            Vector2f prev = input.get((i - 1 + n) % n);
            Vector2f curr = input.get(i);
            Vector2f next = input.get((i + 1) % n);

            Vector2f vPrev = prev.subtract(curr);
            Vector2f vNext = next.subtract(curr);

            float lenPrev = vPrev.length();
            float lenNext = vNext.length();

            // Degenerate: cannot define a stable corner
            if (lenPrev < 1e-6f || lenNext < 1e-6f) {
                result.add(curr.clone());
                continue;
            }

            Vector2f dirPrev = vPrev.normalize();
            Vector2f dirNext = vNext.normalize();

            // Angle between the two rays originating at curr
            float dot = dirPrev.dot(dirNext);
            dot = Math.max(-1f, Math.min(1f, dot));
            float angle = (float) Math.acos(dot);

            // Not sharp enough: keep original vertex
            if (angle > minAngleRad) {
                result.add(curr.clone());
                continue;
            }

            // Choose chamfer distance d (clamped)
            float maxAlongPrev = 0.4f * lenPrev;
            float maxAlongNext = 0.4f * lenNext;
            float d = Math.min(maxRadius, Math.min(maxAlongPrev, maxAlongNext));

            // Too small: avoid creating nearly identical points
            if (d < 1e-4f) {
                result.add(curr.clone());
                continue;
            }

            // Two chamfer points placed along each adjacent direction
            Vector2f p1 = curr.add(dirPrev.mult(d));
            Vector2f p2 = curr.add(dirNext.mult(d));

            result.add(p1);
            result.add(p2);
        }
        return result;
    }

    /**
     * Attempts to remove local self-intersections from a polygon by repeatedly checking
     * for intersections between consecutive edge pairs and replacing the two middle vertices
     * by the computed intersection point.
     * <p>
     * This is a local, heuristic cleanup pass (not a full polygon boolean operation).
     * It is primarily used to mitigate artifacts introduced by corner offsets.
     *
     * @param poly polygon corner list
     * @return corrected polygon (may have fewer points); returns input if polygon is too small
     */
    public static List<Vector2f> fixSelfIntersections(List<Vector2f> poly) {
        if (poly == null || poly.size() < 4) return poly;

        List<Vector2f> cur = new ArrayList<>(poly);
        boolean changed = true;

        while (changed) {
            changed = false;
            int m = cur.size();
            if (m < 4) break;

            for (int i = 0; i < m; i++) {
                int a = i;
                int b = (i + 1) % m;
                int c = (i + 2) % m;
                int d = (i + 3) % m;

                Vector2f A = cur.get(a);
                Vector2f B = cur.get(b);
                Vector2f C = cur.get(c);
                Vector2f D = cur.get(d);

                Vector2f I = intersectSegmentsStrict(A, B, C, D);
                if (I != null) {
                    // Replace vertices b and c by a SINGLE intersection point (list shrinks by 1)
                    List<Vector2f> next = new ArrayList<>(m - 1);

                    for (int j = 0; j < m; j++) {
                        if (j == b) {
                            next.add(I.clone());   // insert I once at position of b
                        } else if (j == c) {
                            // skip c (already replaced by I)
                        } else {
                            next.add(cur.get(j));
                        }
                    }

                    cur = next;
                    changed = true;
                    break;
                }
            }
        }

        return cur;
    }


    /**
     * Computes a strict segment intersection point between segments (p1,p2) and (p3,p4).
     * <p>
     * "Strict" means endpoint-only intersections are ignored (to avoid false positives when
     * edges merely touch due to chamfering/offsetting). The method uses a small epsilon margin.
     *
     * @param p1 first segment start
     * @param p2 first segment end
     * @param p3 second segment start
     * @param p4 second segment end
     * @return intersection point if the segments properly intersect (excluding endpoints), otherwise {@code null}
     */
    private static Vector2f intersectSegmentsStrict(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {
        Coordinate A1 = new Coordinate(p1.x, p1.y);
        Coordinate A2 = new Coordinate(p2.x, p2.y);
        Coordinate B1 = new Coordinate(p3.x, p3.y);
        Coordinate B2 = new Coordinate(p4.x, p4.y);

        LineSegment s1 = new LineSegment(A1, A2);
        LineSegment s2 = new LineSegment(B1, B2);

        Coordinate ip = s1.intersection(s2);
        if (ip == null) return null; // no single intersection

        // STRICT: intersection must be strictly inside both segments (exclude endpoints)
        double t = s1.projectionFactor(ip); // parameter on s1, ~[0..1]
        double u = s2.projectionFactor(ip); // parameter on s2, ~[0..1]

        final double eps = 1e-4;

        if (t > eps && t < 1.0 - eps && u > eps && u < 1.0 - eps) {
            return new Vector2f((float) ip.x, (float) ip.y);
        }
        return null;
    }

    /**
     * Simplifies a polygon by removing vertices that are nearly collinear (redundant support points)
     * and by collapsing very short edges.
     * <p>
     * A point {@code c} is removed if:
     * <ol>
     *   <li>Either adjacent edge is shorter than {@code minEdgeLen}</li>
     *   <li>The direction change is below {@code angleEpsDeg} (nearly straight)</li>
     *   <li>{@code c} lies close to the line segment from {@code prev} to {@code next}
     *       (within {@code distEps})</li>
     * </ol>
     * The algorithm repeats until no vertex was removed in a full pass over the polygon or
     * the polygon has shrunk to fewer than 4 vertices.
     *
     * @param poly        polygon corners (expected CCW and simple)
     * @param distEps     maximum allowed perpendicular deviation from the prev-next segment
     * @param angleEpsDeg angular tolerance in degrees for the collinearity test
     * @param minEdgeLen  minimum allowed adjacent edge length; shorter edges are collapsed
     *                    by removing the vertex
     * @return simplified polygon (may have fewer points); returns input if polygon is too small
     */
    public static List<Vector2f> simplifyNearlyCollinear(
            List<Vector2f> poly,
            float distEps,
            float angleEpsDeg,
            float minEdgeLen
    ) {
        if (poly == null || poly.size() < 4) return poly;

        float distEps2 = distEps * distEps;
        float minEdge2 = minEdgeLen * minEdgeLen;
        float cosTol   = (float) Math.cos(Math.toRadians(angleEpsDeg));

        ArrayList<Vector2f> cur = new ArrayList<>(poly.size());
        for (Vector2f p : poly) cur.add(p.clone());

        boolean removed;
        do {
            removed = false;

            for (int i = 0; i < cur.size(); i++) {
                int ip = (i - 1 + cur.size()) % cur.size();
                int in = (i + 1) % cur.size();

                Vector2f prev = cur.get(ip);
                Vector2f c    = cur.get(i);
                Vector2f next = cur.get(in);

                Vector2f v0 = c.subtract(prev);
                Vector2f v1 = next.subtract(c);

                float l0 = v0.lengthSquared();
                float l1 = v1.lengthSquared();

                // Collapse tiny edges
                if (l0 < minEdge2 || l1 < minEdge2) {
                    cur.remove(i);
                    removed = true;
                    break;
                }

                // Angle check: near-straight if directions almost equal
                Vector2f d0 = v0.normalize();
                Vector2f d1 = v1.normalize();
                float dot = d0.dot(d1);

                if (dot < cosTol) continue;

                // Distance check: point must lie close to the segment prev-next
                if (pointSegmentDistanceSquared(c, prev, next) > distEps2) continue;

                cur.remove(i);
                removed = true;
                break;
            }
        } while (removed && cur.size() >= 4);

        return cur;
    }

    /**
     * Computes squared distance from a point {@code p} to the segment {@code [a,b]}.
     * <p>
     * Squared distance is used to avoid unnecessary square roots in comparisons.
     *
     * @param p query point
     * @param a segment start
     * @param b segment end
     * @return squared distance from {@code p} to the segment
     */
    private static float pointSegmentDistanceSquared(Vector2f p, Vector2f a, Vector2f b) {
        Vector2f ab = b.subtract(a);
        float ab2 = ab.lengthSquared();
        if (ab2 < 1e-12f) return p.distanceSquared(a);

        float t = (p.subtract(a)).dot(ab) / ab2;
        t = Math.max(0f, Math.min(1f, t));

        Vector2f q = a.add(ab.mult(t));
        return p.distanceSquared(q);
    }
}