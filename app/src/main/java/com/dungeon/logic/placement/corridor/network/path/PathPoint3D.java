package com.dungeon.logic.placement.corridor.network.path;

import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.jme3.math.Vector3f;

/**
 * Immutable 3D point used as the raw input representation of a corridor path.
 * <p>
 * A {@code PathPoint3D} is produced during corridor routing and later consumed
 * by the global graph builder to create/reuse semantic graph nodes. Besides its world-space
 * {@link #position}, a point also carries lightweight semantic identifiers that make it possible to
 * define a stable node identity across different corridor paths.
 * </p>
 *
 * <h2>Semantic identity</h2>
 * <ul>
 *   <li>{@link #kind} describes what the point represents (e.g. room center, voxel center, edge midpoint).</li>
 *   <li>{@link #polyIdA} and {@link #polyIdB} reference routing-grid polygons:
 *     <ul>
 *       <li>For cell/room-center points, {@code polyIdB} is typically {@code -1}.</li>
 *       <li>For {@code EDGE_MID} points, {@code polyIdA} and {@code polyIdB} identify the two adjacent cells.</li>
 *     </ul>
 *   </li>
 *   <li>{@link #zBand} stores the discrete vertical band index used in 2.5D/3D routing.</li>
 * </ul>
 */
public final class PathPoint3D {

    /** Semantic type of this path point (room center, voxel center, edge midpoint, ...). */
    public final PointKind kind;

    /** World-space position of the point (X/Z are horizontal, Y is vertical in jME). */
    public final Vector3f position;

    /** Primary routing-grid polygon id associated with this point. */
    public final int polyIdA;

    /**
     * Secondary polygon id for {@code EDGE_MID} points.
     * <p>
     * For non-edge-mid points this is typically {@code -1}.
     * </p>
     */
    public final int polyIdB;

    /** Discrete vertical band index for this point. */
    public final short zBand;

    /**
     * Creates a new immutable raw path point.
     *
     * @param kind     semantic point kind
     * @param position world-space position
     * @param polyIdA  primary polygon id
     * @param polyIdB  secondary polygon id (only meaningful for {@code EDGE_MID}; otherwise {@code -1})
     * @param zBand    discrete z-band index
     */
    public PathPoint3D(PointKind kind, Vector3f position, int polyIdA, int polyIdB, short zBand) {
        this.kind = kind;
        this.position = position;
        this.polyIdA = polyIdA;
        this.polyIdB = polyIdB;
        this.zBand = zBand;
    }
}