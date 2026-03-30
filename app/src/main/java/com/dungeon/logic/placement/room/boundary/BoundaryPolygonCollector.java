package com.dungeon.logic.placement.room.boundary;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;

import java.util.*;

/**
 * Collects candidate boundary polygons for a {@link VertexCluster} on a {@link BaseGrid}.
 * <p>
 * During room placement, a room footprint is derived from polygons (quads) that lie on or around the sampled
 * vertex cluster. This helper gathers all polygons that touch at least one cluster vertex, but filters out
 * polygons that are completely inside the cluster ("inner polygons"), because they do not contribute to the
 * room boundary outline.
 * <p>
 * Definition used here:
 * <ul>
 *   <li><b>Adjacent polygon</b>: a polygon that contains at least one vertex from the cluster.</li>
 *   <li><b>Inner polygon</b>: a polygon whose <em>all</em> vertices are contained in the cluster.</li>
 * </ul>
 */
public class BoundaryPolygonCollector {

    private final BaseGrid grid;

    /**
     * Creates a new collector bound to a specific base grid.
     *
     * @param grid base grid providing vertex-to-polygon adjacency information
     * @throws NullPointerException if {@code grid} is {@code null}
     */
    public BoundaryPolygonCollector(BaseGrid grid) {
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    /**
     * Returns all polygons that are adjacent to the given cluster and not fully contained in it.
     * <p>
     * Implementation outline:
     * <ol>
     *   <li>Compute the set of cluster vertex indices.</li>
     *   <li>Union all polygons incident to any cluster vertex using {@code grid.getVertexToPolygons()}.</li>
     *   <li>Remove inner polygons whose every vertex is contained in the cluster.</li>
     * </ol>
     *
     * @param cluster vertex cluster defining the room seed region
     * @return list of unique boundary-adjacent polygons (order is unspecified)
     * @throws NullPointerException if {@code cluster} is {@code null}
     */
    public List<Polygon> getValidPolygonsForCluster(VertexCluster cluster) {
        Objects.requireNonNull(cluster, "cluster");

        // 1) Collect all vertex indices that belong to the cluster.
        Set<Integer> clusterVertices = new HashSet<>(cluster.getVertices());

        // 2) Union all polygons adjacent to at least one cluster vertex.
        Set<Polygon> candidatePolys = new HashSet<>();
        for (int v : clusterVertices) {
            List<Polygon> adjacent = grid.getVertexToPolygons().get(v);
            if (adjacent != null) {
                candidatePolys.addAll(adjacent);
            }
        }

        // 3) Filter out polygons completely inside the cluster (inner polygons).
        candidatePolys.removeIf(poly -> isInnerPolygon(poly, clusterVertices));

        List<Polygon> out = new ArrayList<>(candidatePolys);
        out.sort(Comparator.comparing(p -> Arrays.toString(p.getVertexIndices())));

        return out;
    }

    /**
     * Checks whether a polygon is fully contained within the cluster vertex set.
     *
     * @param poly polygon to test
     * @param clusterVertices vertex indices that belong to the cluster
     * @return {@code true} if all polygon vertices are in {@code clusterVertices}; otherwise {@code false}
     */
    private static boolean isInnerPolygon(Polygon poly, Set<Integer> clusterVertices) {
        for (int v : poly.getVertexIndices()) {
            if (!clusterVertices.contains(v)) {
                return false;
            }
        }
        return true;
    }
}