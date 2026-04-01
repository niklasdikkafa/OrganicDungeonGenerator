package com.dungeon.logic.placement.corridor.network.junction;

import com.jme3.math.Vector3f;

/**
 * Geometry description for connecting two consecutive junction portals in the junction ring.
 * <p>
 * A {@code JunctionCorner} represents the corner (chamfer-like) that joins the end of the
 * current portal frame to the start of the next portal frame. It is computed in the junction
 * builder after portals have been ordered clockwise around a junction (or junction cluster).
 * </p>
 *
 * <h2>Direct connection mode</h2>
 * <p>
 * If {@link #directConnect} is {@code true}, the builder could not produce a reliable corner
 * (e.g. missing/ambiguous junction ownership, degenerate geometry, or a fallback rule).
 * In that case, consumers should connect portal frame endpoints directly without inserting intermediate corner points.
 * </p>
 *
 * <h2>Corner point layout</h2>
 * <p>
 * Each “lane” (inner/outer) and height (bottom/top) can be represented by two points:
 * </p>
 * <ul>
 *   <li>{@code *0}: point near the current portal</li>
 *   <li>{@code *1}: point near the next portal</li>
 * </ul>
 * <p>
 * Using two points allows a chamfer-like corner (segment from portal to *0, then *0 to *1,
 * then *1 to the next portal).
 * </p>
 */
public final class JunctionCorner {

    /**
     * If {@code true}, no valid corner was computed and consumers should fall back to a direct
     * connection between portal endpoints instead of using the corner points.
     */
    public boolean directConnect = false;

    /** Inner wall bottom corner points (current-side and next-side). */
    public Vector3f innerBottom0, innerBottom1;

    /** Inner wall top corner points (current-side and next-side). */
    public Vector3f innerTop0, innerTop1;

    /** Outer wall bottom corner points (current-side and next-side). */
    public Vector3f outerBottom0, outerBottom1;

    /** Outer wall top corner points (current-side and next-side). */
    public Vector3f outerTop0, outerTop1;
}