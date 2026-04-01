package com.dungeon.logic.placement.corridor.network.graph;

import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;

import static com.dungeon.config.DungeonConfig.CORRIDOR_HEIGHT;
import static com.dungeon.config.DungeonConfig.Z_BAND_HEIGHT;

/**
 * Stable, semantic identity key for a {@link com.dungeon.logic.placement.corridor.network.graph.GraphNode}.
 * <p>
 * {@code NodeKey} is used during global corridor graph construction to ensure that logically identical
 * path points are represented by a single shared node (node reuse), even if they appear in multiple
 * {@link com.dungeon.logic.placement.corridor.network.path.CorridorPath}s.
 * </p>
 *
 * <h2>Key components</h2>
 * <ul>
 *   <li>{@link #kind} - the semantic type of the point (voxel center, edge midpoint, room center).</li>
 *   <li>{@link #a} / {@link #b} - identifiers that define the point in the routing grid.</li>
 *   <li>{@link #yKey} - quantized vertical key derived from {@code y} to make matching robust for
 *       stair interpolation points.</li>
 * </ul>
 *
 * <h2>Canonicalization rules</h2>
 * <ul>
 *   <li><b>{@code VOXEL_CENTER}:</b> {@code (kind, polyIdA, -1, yKey)}</li>
 *   <li><b>{@code ROOM_CENTER}:</b>  {@code (kind, polyIdA, -1, yKey)}</li>
 *   <li><b>{@code EDGE_MID}:</b>     {@code (kind, min(polyIdA, polyIdB), max(polyIdA, polyIdB), yKey)}</li>
 * </ul>
 *
 * <p>
 * {@code yKey} is quantized in quarter z-band steps because stair points are partially generated at fractions
 * of a full corridor height. The {@code CORRIDOR_HEIGHT * 0.5} offset matches the convention used for
 * voxel center placement in the corridor system.
 * </p>
 */
public final class NodeKey {

    /** Semantic type of the point. */
    public final PointKind kind;

    /** Primary routing-grid identifier (e.g. polygon id). */
    public final int a;

    /**
     * Secondary routing-grid identifier.
     * Used for {@link PointKind#EDGE_MID} (the neighboring polygon id), otherwise {@code -1}.
     */
    public final int b;

    /**
     * Quantized vertical key derived from {@code PathPoint3D.position.y}.
     * <p>
     * Quantization improves stability for points placed on intermediate stair steps.
     * </p>
     */
    public final int yKey;

    public NodeKey(PointKind kind, int a, int b, int yKey) {
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.yKey = yKey;
    }

    /**
     * Quantizes a world-space {@code y} coordinate into a discrete key.
     * <p>
     * Uses {@code Z_BAND_HEIGHT / 4} granularity to support quarter-step stair points.
     * The offset {@code CORRIDOR_HEIGHT * 0.5} aligns the quantization origin with the
     * corridor voxel-center convention.
     * </p>
     *
     * @param y world-space y coordinate
     * @return quantized vertical key
     */
    private static int quantizeYKey(float y) {
        float unit = Z_BAND_HEIGHT / 4f;         // stair points can be at quarter steps of a voxel
        float base = CORRIDOR_HEIGHT * 0.5f;     // same offset as voxelCenterY
        return Math.round((y - base) / unit);
    }

    /**
     * Creates a canonical {@code NodeKey} from a routed path point.
     *
     * @param p the routed path point
     * @return canonical key representing the semantic identity of {@code p}
     */
    public static NodeKey from(PathPoint3D p) {
        int yKey = quantizeYKey(p.position.y);

        if (p.kind == PointKind.VOXEL_CENTER) {
            return new NodeKey(PointKind.VOXEL_CENTER, p.polyIdA, -1, yKey);
        }
        if (p.kind == PointKind.ROOM_CENTER) {
            return new NodeKey(PointKind.ROOM_CENTER, p.polyIdA, -1, yKey);
        }

        int lo = Math.min(p.polyIdA, p.polyIdB);
        int hi = Math.max(p.polyIdA, p.polyIdB);
        return new NodeKey(PointKind.EDGE_MID, lo, hi, yKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeKey k)) return false;
        return a == k.a && b == k.b && yKey == k.yKey && kind == k.kind;
    }

    @Override
    public int hashCode() {
        int h = kind.hashCode();
        h = 31 * h + a;
        h = 31 * h + b;
        h = 31 * h + yKey;
        return h;
    }
}