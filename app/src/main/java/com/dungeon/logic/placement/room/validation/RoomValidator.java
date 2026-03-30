package com.dungeon.logic.placement.room.validation;

import com.dungeon.domain.Room;
import com.jme3.math.Vector2f;
import org.locationtech.jts.geom.Envelope;

import java.util.List;

/**
 * Validates room placement by performing a conservative 2.5D collision check.
 * <p>
 * The validator is intentionally fast and conservative: it uses an axis-aligned bounding box (AABB)
 * overlap test in the horizontal plane (2D) and a simple interval overlap test in the vertical
 * dimension (Z). If both overlap, the rooms are considered to (possibly) collide.
 * </p>
 *
 * <h2>Collision model</h2>
 * <ul>
 *   <li><b>2D:</b> AABB of the room's inner polygon expanded by its wall thickness.</li>
 *   <li><b>Z:</b> Interval from {@code zLevel - floorThickness} to
 *       {@code zLevel + height + floorThickness}.</li>
 * </ul>
 *
 * <p>
 * Note: This does <em>not</em> perform precise polygon intersection. Therefore, it may reject some
 * placements that would be valid with an exact test (false positives), but it should not allow
 * overlapping rooms if the AABB/Z model correctly bounds the geometry.
 * </p>
 */
public final class RoomValidator {

    /**
     * Checks whether a candidate room can be added without colliding with already placed rooms.
     * <p>
     * The test is conservative and based on {@link #possiblyCollides(Room, Room)}.
     * </p>
     *
     * @param candidate the room to validate
     * @param existingRooms already accepted rooms to test against
     * @return {@code true} if no collision is detected; {@code false} if the candidate overlaps any existing room
     */
    public static boolean isValid(Room candidate, List<Room> existingRooms) {
        for (Room other : existingRooms) {
            if (possiblyCollides(candidate, other)) return false;
        }
        return true;
    }

    /**
     * Performs a conservative 2.5D overlap test between two rooms.
     * <p>
     * Returns {@code true} if the rooms overlap in the horizontal AABB projection and their
     * vertical intervals overlap. Because the 2D test is AABB-based, this is a "possible collision"
     * test (may contain false positives).
     * </p>
     *
     * @param a first room
     * @param b second room
     * @return {@code true} if they possibly collide; {@code false} if they are guaranteed separated in 2D or Z
     */
    public static boolean possiblyCollides(Room a, Room b) {
        return aabbOverlap2D(a, b) && zOverlap(a, b);
    }

    /**
     * Checks whether two rooms overlap in the horizontal plane using expanded AABBs.
     * <p>
     * Each room is approximated by the axis-aligned bounding box (AABB) of its inner footprint,
     * expanded by its wall thickness. This is a conservative broad-phase test:
     * it may report overlap even if the actual polygons do not intersect (false positives),
     * but it is fast and allocation-light.
     * </p>
     *
     * @param a first room
     * @param b second room
     * @return {@code true} if the expanded AABBs intersect; {@code false} if they are separated in 2D
     */
    private static boolean aabbOverlap2D(Room a, Room b) {
        Envelope A = envelopeOf(a.getInnerCorners(), a.getWallThickness());
        Envelope B = envelopeOf(b.getInnerCorners(), b.getWallThickness());
        return A.intersects(B);
    }

    /**
     * Computes the axis-aligned bounding box (AABB) of a 2D polygon and expands it by a margin.
     * <p>
     * The input polygon is given as a list of points in the X/Y plane. The returned {@link Envelope}
     * bounds all points and is then expanded uniformly by {@code expand} in both axes.
     * This expansion is used to conservatively account for wall thickness around the inner footprint.
     * </p>
     *
     * @param poly polygon vertices in the X/Y plane (typically {@code Room#getInnerCorners()})
     * @param expand symmetric expansion margin applied to the envelope in X and Y
     * @return expanded AABB envelope of the polygon
     */
    private static Envelope envelopeOf(List<Vector2f> poly, float expand) {
        Envelope env = new Envelope();
        for (Vector2f v : poly) {
            env.expandToInclude(v.x, v.y);
        }
        env.expandBy(expand);
        return env;
    }

    /**
     * Checks whether the vertical extents of two rooms overlap.
     * <p>
     * The vertical extent includes floor thickness on both bottom and top to conservatively
     * account for slab thickness when stacking rooms.
     * </p>
     *
     * @return {@code true} if the Z-intervals overlap; {@code false} otherwise
     */
    private static boolean zOverlap(Room a, Room b) {
        double aMin = a.getZLevel() - a.getFloorThickness();
        double aMax = a.getZLevel() + a.getHeight() + a.getFloorThickness();

        double bMin = b.getZLevel() - b.getFloorThickness();
        double bMax = b.getZLevel() + b.getHeight() + b.getFloorThickness();

        return aMin <= bMax && bMin <= aMax;
    }

}