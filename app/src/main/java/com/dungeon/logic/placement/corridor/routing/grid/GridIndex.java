package com.dungeon.logic.placement.corridor.routing.grid;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.jme3.math.Vector2f;

import java.util.*;

import static com.dungeon.logic.geometry.Utilities.findSharedEdge;

/**
 * Precomputed index over a {@link BaseGrid} optimized for corridor routing queries.
 * <p>
 * {@code GridIndex} provides fast access to:
 * </p>
 * <ul>
 *   <li>All grid polygons ({@link #polys}) and their 2D centers ({@link #centers}).</li>
 *   <li>Polygon adjacency as compact integer neighbor lists ({@link #neighbors}).</li>
 *   <li>A spatial hash ({@link #spatial}) for quick “nearest / nearby cell” lookups using polygon centers.</li>
 *   <li>A small open-addressing cache to accelerate repeated shared-edge queries between polygon pairs
 *       ({@link #findSharedEdgeById(int, int)}).</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>Polygon identity is handled via {@link IdentityHashMap} because {@link Polygon} is index-based and
 *       stable within a {@link BaseGrid}, but not necessarily value-equal.</li>
 *   <li>The shared-edge cache is intentionally small and fixed-size (power-of-two capacity). It is designed
 *       for hot-path lookups during path-to-polyline conversion and routing logic.</li>
 *   <li>This class is immutable after construction (all arrays are built once).</li>
 * </ul>
 */
public final class GridIndex {

    /** Source grid that defines vertices, polygons, and topology. */
    public final BaseGrid grid;

    /** All polygons of the routing grid (index order matches {@link #centers} and {@link #neighbors}). */
    public final List<Polygon> polys;

    /** 2D centers for each polygon in {@link #polys}, in the same index order. */
    public final Vector2f[] centers;

    /**
     * Adjacency list representation of polygon neighbors.
     * <p>
     * {@code neighbors[i]} contains the polygon indices of all adjacent polygons of {@code polys.get(i)}.
     * </p>
     */
    public final int[][] neighbors;

    /**
     * Spatial hash index over {@link #centers} for proximity queries.
     * <p>
     * Uses {@code cellSize = grid.getEdgeLength()} (clamped to a small minimum) to map centers into buckets.
     * </p>
     */
    public final SpatialHash2D spatial;

    // -----------------------------------------------------------------------------------------
    // Shared-edge cache (open addressing): (minId,maxId) -> Edge
    // -----------------------------------------------------------------------------------------

    /** Cached pair keys, {@code Long.MIN_VALUE} denotes an empty slot. */
    private final long[] edgeKeys;

    /** Cached shared edges corresponding to {@link #edgeKeys}. */
    private final Edge[] edgeVals;

    /**
     * Builds an index for a given routing grid.
     *
     * @param grid base grid used for routing (must not be {@code null})
     * @throws NullPointerException if {@code grid} is {@code null}
     */
    public GridIndex(BaseGrid grid) {
        this.grid = Objects.requireNonNull(grid);
        this.polys = grid.getAllPolygons();

        // --- polygon centers ---
        Map<Polygon, Vector2f> centerMap = grid.getPolygonCenters();
        this.centers = new Vector2f[polys.size()];
        for (int i = 0; i < polys.size(); i++) {
            Vector2f c = centerMap.get(polys.get(i));
            centers[i] = new Vector2f(c);
        }

        // --- polygon neighbors (index-based) ---
        Map<Polygon, Set<Polygon>> nbMap = grid.getPolygonNeighbors();
        IdentityHashMap<Polygon, Integer> id = new IdentityHashMap<>(nbMap.size());
        for (int i = 0; i < polys.size(); i++) id.put(polys.get(i), i);

        this.neighbors = new int[polys.size()][];
        for (int i = 0; i < polys.size(); i++) {
            Set<Polygon> nbs = nbMap.getOrDefault(polys.get(i), Set.of());
            int[] arr = new int[nbs.size()];
            int k = 0;
            for (Polygon nb : nbs) {
                Integer nbId = id.get(nb);
                if (nbId != null) arr[k++] = nbId;
            }
            if (k != arr.length) arr = Arrays.copyOf(arr, k);
            neighbors[i] = arr;
        }

        // --- spatial center index ---
        float cellSize = Math.max(1e-3f, grid.getEdgeLength());
        this.spatial = new SpatialHash2D(cellSize, polys.size());
        for (int i = 0; i < centers.length; i++) spatial.insert(centers[i], i);

        // --- small open-addressing cache for shared edges ---
        int cap = 1 << 16; // fixed-size cache (must be power of two)
        this.edgeKeys = new long[cap];
        this.edgeVals = new Edge[cap];
        Arrays.fill(edgeKeys, Long.MIN_VALUE);
    }

    /**
     * Returns the shared (undirected) edge between polygons {@code a} and {@code b}, if any.
     * <p>
     * The result is cached using a small open-addressing table keyed by {@code (min(a,b), max(a,b))}.
     * </p>
     *
     * @param a polygon id (index into {@link #polys})
     * @param b polygon id (index into {@link #polys})
     * @return the shared edge, or {@code null} if polygons do not share an edge
     * @throws IndexOutOfBoundsException if {@code a} or {@code b} are out of range
     */
    public Edge findSharedEdgeById(int a, int b) {
        long key = pairKey(a, b);
        int slot = (mix64(key) & (edgeKeys.length - 1));
        while (true) {
            long k = edgeKeys[slot];
            if (k == Long.MIN_VALUE) break;
            if (k == key) return edgeVals[slot];
            slot = (slot + 1) & (edgeKeys.length - 1);
        }

        Edge e = findSharedEdge(polys.get(a), polys.get(b));
        if (e == null) return null;

        // insert (linear probing)
        int s = (mix64(key) & (edgeKeys.length - 1));
        while (edgeKeys[s] != Long.MIN_VALUE && edgeKeys[s] != key) {
            s = (s + 1) & (edgeKeys.length - 1);
        }
        edgeKeys[s] = key;
        edgeVals[s] = e;
        return e;
    }

    /**
     * Packs two polygon ids into a stable undirected key {@code (min, max)}.
     */
    private static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    /**
     * 64-bit mix function used to scatter keys for open-addressing.
     * <p>
     * Returns a 32-bit hash value derived from the input.
     * </p>
     */
    private static int mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }
}