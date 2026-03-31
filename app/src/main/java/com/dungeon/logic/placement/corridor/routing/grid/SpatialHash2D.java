package com.dungeon.logic.placement.corridor.routing.grid;

import com.jme3.math.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight 2D spatial hash for fast neighborhood queries on polygon/cell centers.
 * <p>
 * Points are inserted into square hash buckets of size {@code cellSize}. A query returns the
 * ids from the 3x3 neighborhood of buckets around the query point, which is typically enough
 * to find the closest cell center without scanning the entire grid.
 * </p>
 *
 * <p><b>Notes:</b>
 * <ul>
 *   <li>This structure stores only integer ids, not the point positions themselves.</li>
 *   <li>It is designed for many reads (queries) and relatively few writes (build-time inserts).</li>
 * </ul>
 * </p>
 */
public final class SpatialHash2D {

    /** Inverse bucket size (1 / cellSize) for fast coordinate-to-cell mapping. */
    private final float inv;

    /** Maps packed (cellX, cellY) -> list of point ids stored in that bucket. */
    private final Map<Long, IntList> buckets;

    /**
     * Creates a new spatial hash.
     *
     * @param cellSize size of a hash bucket in world/grid units; must be > 0
     * @param expectedPoints expected number of inserted points (used to size the internal map)
     * @throws IllegalArgumentException if {@code cellSize <= 0}
     */
    SpatialHash2D(float cellSize, int expectedPoints) {
        if (cellSize <= 0f) throw new IllegalArgumentException("cellSize must be > 0");
        this.inv = 1f / cellSize;
        this.buckets = new HashMap<>(Math.max(16, expectedPoints / 4));
    }

    /**
     * Inserts a point id into the bucket that contains {@code p}.
     *
     * @param p point position in world/grid coordinates
     * @param id payload id to store for this point (e.g. polygon id)
     * @throws NullPointerException if {@code p} is {@code null}
     */
    void insert(Vector2f p, int id) {
        long k = key(cell(p.x), cell(p.y));
        buckets.computeIfAbsent(k, _ -> new IntList()).add(id);
    }

    /**
     * Returns candidate ids near {@code p} by collecting ids from the 3x3 neighborhood of buckets
     * around the point.
     * <p>
     * This is a candidate query: it is not guaranteed that the true nearest point is returned if
     * buckets are too large or if data is sparse, but it works well for grid-like distributions
     * (such as polygon centers of a routing grid).
     * </p>
     *
     * @param p query point in world/grid coordinates
     * @return list of ids from neighboring buckets (may be empty)
     * @throws NullPointerException if {@code p} is {@code null}
     */
    List<Integer> query(Vector2f p) {
        int cx = cell(p.x);
        int cy = cell(p.y);

        ArrayList<Integer> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                IntList list = buckets.get(key(cx + dx, cy + dy));
                if (list != null) list.copyTo(out);
            }
        }
        return out;
    }

    /** Maps a coordinate to its bucket index using floor(x / cellSize). */
    private int cell(float x) {
        return (int) Math.floor(x * inv);
    }

    /**
     * Packs two 32-bit cell coordinates into a single 64-bit key.
     * <p>
     * The x coordinate is stored in the high 32 bits, and y in the low 32 bits (masked to unsigned).
     * </p>
     */
    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /**
     * Small growable int array used as a bucket payload to avoid per-id boxing overhead.
     */
    private static final class IntList {
        private int[] a = new int[8];
        private int n = 0;

        void add(int v) {
            if (n == a.length) {
                int[] b = new int[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = v;
        }

        void copyTo(List<Integer> out) {
            for (int i = 0; i < n; i++) out.add(a[i]);
        }
    }
}