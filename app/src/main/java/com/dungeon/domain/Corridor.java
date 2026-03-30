package com.dungeon.domain;

import com.jme3.math.Vector2f;

import java.util.List;

/**
 * Immutable corridor representation used by the dungeon generator.
 * <p>
 * A {@code Corridor} connects two {@link Room}s (identified by their room IDs) and stores a 2D
 * centerline polyline that is later used to build a 3D corridor network and extruded corridor meshes.
 * The polyline is constructed in a "zigzag" fashion to preserve routing intent:
 * <pre>
 *   center(poly0) -> midpoint(sharedEdge01) -> center(poly1) -> ...
 * </pre>
 * For pure vertical transitions (e.g. ramps between z-bands) the polygon ID may remain unchanged,
 * in which case the next point is another center point (no edge-midpoint is inserted).
 * </p>
 *
 * <h3>Routing trace (grid-space)</h3>
 * <p>
 * In addition to geometry, a corridor stores the discrete routing trace per step:
 * </p>
 * <ul>
 *   <li>{@code polyIds}: the routing-grid polygon (cell) identifier for each polyline step</li>
 *   <li>{@code zBands}: the vertical band index for each polyline step</li>
 * </ul>
 * <p>
 * Both arrays are aligned by index and conceptually match the sampling resolution of {@link #polyline2D}.
 * They are primarily intended for debugging, reproducibility, and follow-up processing (e.g. network building).
 * </p>
 *
 * <h3>Dimensions</h3>
 * <ul>
 *   <li>{@code width}: interior corridor width</li>
 *   <li>{@code height}: interior corridor height</li>
 *   <li>{@code wallThickness}: thickness used for inner/outer shell generation</li>
 * </ul>
 */
public final class Corridor extends Structure {

    /** ID of the room where this corridor starts. */
    private final int fromRoomId;

    /** ID of the room where this corridor ends. */
    private final int toRoomId;

    /**
     * 2D centerline polyline in world/grid coordinates.
     * <p>
     * Typically alternates between cell centers and shared-edge midpoints to create a stable,
     * piecewise-linear routing path.
     * </p>
     * First and last vertex could be outside (smaller) rooms, but this case will get fixed
     * in {@link com.dungeon.logic.placement.corridor.network.builder.CorridorNetworkBuilder}
     */
    private final List<Vector2f> polyline2D;

    /**
     * Discrete routing-grid polygon ID per polyline step (aligned with {@link #zBands}).
     * <p>
     * Intended for debugging and tracing the corridor back to the routing grid.
     * </p>
     */
    private final int[] polyIds;

    /**
     * Discrete vertical band per polyline step (aligned with {@link #polyIds}).
     * <p>
     * Stored as {@code short} to keep memory footprint low.
     * </p>
     */
    private final short[] zBands;

    /** Interior corridor width. */
    private final float width;

    /** Interior corridor height. */
    private final float height;

    /** Wall thickness used for mesh generation (inner/outer shells). */
    private final float wallThickness;

    /**
     * Creates a new immutable {@code Corridor}.
     *
     * @param fromRoomId     start room ID
     * @param toRoomId       target room ID
     * @param polyline2D     2D corridor centerline points; will be defensively copied
     * @param polyIds        routing-grid polygon IDs per step (same logical length as {@code zBands});
     *                       copied into a primitive array
     * @param zBands         z-band indices per step (same logical length as {@code polyIds});
     *                       copied into a primitive array
     * @param width          interior corridor width
     * @param height         interior corridor height
     * @param wallThickness  wall thickness used for mesh generation
     * @throws NullPointerException if any list argument is {@code null}
     */
    public Corridor(int fromRoomId, int toRoomId,
                    List<Vector2f> polyline2D,
                    List<Integer> polyIds,
                    List<Integer> zBands,
                    float width,
                    float height,
                    float wallThickness) {

        this.fromRoomId = fromRoomId;
        this.toRoomId = toRoomId;

        this.polyline2D = List.copyOf(polyline2D);

        this.polyIds = new int[polyIds.size()];
        for (int i = 0; i < polyIds.size(); i++) this.polyIds[i] = polyIds.get(i);

        this.zBands = new short[zBands.size()];
        for (int i = 0; i < zBands.size(); i++) this.zBands[i] = (short) (int) zBands.get(i);

        this.width = width;
        this.height = height;
        this.wallThickness = wallThickness;
    }

    /** @return start room ID */
    public int getFromRoomId() { return fromRoomId; }

    /** @return target room ID */
    public int getToRoomId() { return toRoomId; }

    /** @return immutable 2D centerline polyline */
    public List<Vector2f> getPolyline2D() { return polyline2D; }

    /**
     * Returns the routing-grid polygon IDs per step.
     * <p>
     * Note: this returns the internal primitive array for performance. Treat it as read-only.
     * </p>
     */
    public int[] getPolyIds() { return polyIds; }

    /**
     * Returns the z-band indices per step.
     * <p>
     * Note: this returns the internal primitive array for performance. Treat it as read-only.
     * </p>
     */
    public short[] getZBands() { return zBands; }

    /** @return interior corridor width */
    public float getWidth() { return width; }

    /** @return interior corridor height */
    public float getHeight() { return height; }

    /** @return wall thickness used for mesh generation */
    public float getWallThickness() { return wallThickness; }
}