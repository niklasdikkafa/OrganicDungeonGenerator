package com.dungeon.logic.placement.corridor.routing.grid;

import com.jme3.math.Vector2f;

import java.util.List;

/**
 * Small query helpers for {@link GridIndex}.
 * <p>
 * This class provides fast lookups on the routing grid using the spatial index stored in
 * {@link GridIndex#spatial}. The methods are used during corridor routing to map continuous
 * world coordinates to the nearest routing cell (polygon).
 * </p>
 */
public final class GridQueries {

    /**
     * Returns the polygon id whose precomputed center is closest to the given point {@code p}.
     * <p>
     * The method first queries the {@link GridIndex#spatial} hash to get a small set of candidate
     * polygons near {@code p}. If that candidate set is empty (rare), it falls back to a full
     * scan over all polygon centers.
     * </p>
     *
     * @param index routing grid index containing polygon centers and a spatial hash
     * @param p query point in world/grid coordinates (2D)
     * @return polygon id in {@link GridIndex#polys}, or {@code -1} if the index has no polygons
     * @throws NullPointerException if {@code index} or {@code p} is {@code null}
     */
    public static int nearestPoly(GridIndex index, Vector2f p) {
        List<Integer> cands = index.spatial.query(p);
        int best = -1;
        float bestD = Float.POSITIVE_INFINITY;

        for (int id : cands) {
            float d = index.centers[id].distanceSquared(p);
            if (d < bestD) { bestD = d; best = id; }
        }
        if (best >= 0) return best;

        // fallback
        for (int i = 0; i < index.centers.length; i++) {
            float d = index.centers[i].distanceSquared(p);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
}