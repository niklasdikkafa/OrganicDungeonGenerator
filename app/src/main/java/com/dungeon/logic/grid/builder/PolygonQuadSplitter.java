package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;

import java.util.*;

/**
 * Splits input polygons (triangles/quads/any n-gons) into a set of quads by inserting
 * additional vertices:
 * <ul>
 *   <li>a centroid vertex per polygon</li>
 *   <li>one midpoint vertex per polygon edge (shared edges reuse the same midpoint)</li>
 * </ul>
 *
 * <p>The resulting quads follow a fan-like pattern around the inserted polygon centroid:
 * for each original vertex {@code vA}, one quad is created using
 * {@code (center, midpoint(prevEdge), vA, midpoint(nextEdge))}.</p>
 *
 * <p><b>Side effect:</b> The {@code vertices} list is modified in-place, because new
 * vertices (centers and midpoints) are appended and referenced by index in the output polygons.</p>
 */
public final class PolygonQuadSplitter {

    /**
     * Container holding the polygons produced by {@link #splitToQuads(List, List)}.
     */
    public static final class SplitResult {
        private final List<Polygon> polygons;

        SplitResult(List<Polygon> polygons) {
            this.polygons = polygons;
        }

        /**
         * @return the list of newly generated quad polygons (indices refer to the modified vertex list).
         */
        public List<Polygon> polygons() { return polygons; }
    }

    /**
     * Splits every polygon in {@code basePolys} into quads by:
     * <ol>
     *   <li>Computing and appending a centroid vertex for each polygon</li>
     *   <li>Computing and appending midpoints for all polygon edges (reused across polygons)</li>
     *   <li>Creating one quad per original polygon vertex</li>
     * </ol>
     *
     * <p>Midpoints are cached using {@link Edge} as a key, so that two polygons sharing the same
     * geometric edge also share the same midpoint index in {@code vertices}. This prevents cracks
     * in the mesh/topology and keeps connectivity consistent.</p>
     *
     * <p>Polygon centers are stored in an {@link IdentityHashMap} to ensure the mapping is bound
     * to the exact {@link Polygon} instance.</p>
     *
     * @param vertices list of vertex positions; will be modified by appending new vertices
     * @param basePolys polygons to split (each polygon must reference indices valid in {@code vertices})
     * @return a {@link SplitResult} containing the generated quad polygons
     */
    public static SplitResult splitToQuads(List<Vector2f> vertices, List<Polygon> basePolys) {

        // Maps an (undirected) edge to the vertex index of its midpoint
        Map<Edge, Integer> edgeMidpointIndex = new HashMap<>();

        // Maps a polygon instance to the vertex index of its appended centroid
        Map<Polygon, Integer> polygonCenterIndex = new IdentityHashMap<>();

        List<Polygon> newPolygons = new ArrayList<>();

        // 1) polygon centers: append one centroid vertex per polygon
        for (Polygon poly : basePolys) {
            int[] idx = poly.getVertexIndices();
            Vector2f center = new Vector2f(0, 0);
            for (int v : idx) center.addLocal(vertices.get(v));
            center.divideLocal(idx.length);

            int centerIdx = vertices.size();
            vertices.add(center);
            polygonCenterIndex.put(poly, centerIdx);
        }

        // 2) edge midpoints: append one midpoint per unique edge (shared edges reuse the same midpoint)
        for (Polygon poly : basePolys) {
            int[] idx = poly.getVertexIndices();
            int k = idx.length;
            for (int i = 0; i < k; i++) {
                int vA = idx[i];
                int vB = idx[(i + 1) % k];

                Edge key = new Edge(vA, vB);
                if (!edgeMidpointIndex.containsKey(key)) {
                    Vector2f mid = vertices.get(vA).add(vertices.get(vB)).mult(0.5f);
                    int midIdx = vertices.size();
                    vertices.add(mid);
                    edgeMidpointIndex.put(key, midIdx);
                }
            }
        }

        // 3) split each n-gon into n quads around the center
        for (Polygon poly : basePolys) {
            int[] idx = poly.getVertexIndices();
            int k = idx.length;
            int centerIdx = polygonCenterIndex.get(poly);

            for (int i = 0; i < k; i++) {
                int vA = idx[i];
                int vPrev = idx[(i - 1 + k) % k];
                int vB = idx[(i + 1) % k];

                // midpoint of edge (vPrev, vA) and (vA, vB)
                Integer midPrev = edgeMidpointIndex.get(new Edge(vPrev, vA));
                Integer midAB   = edgeMidpointIndex.get(new Edge(vA, vB));
                if (midPrev == null || midAB == null) continue;

                // Quad order: center -> prevEdgeMid -> vertex -> nextEdgeMid
                int[] quad = new int[]{ centerIdx, midPrev, vA, midAB };
                newPolygons.add(new Polygon(quad));
            }
        }

        return new SplitResult(newPolygons);
    }
}