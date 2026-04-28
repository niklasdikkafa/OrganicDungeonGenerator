package com.dungeon.logic.placement.room.boundary;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;
import com.jme3.math.Vector2f;

import java.util.*;

import static com.dungeon.logic.geometry.Utilities.*;

/**
 * Builds an ordered 2D outline (polygon chain) for a room based on the set of grid polygons
 * that belong to the room's vertex cluster.
 * <p>
 * Idea:
 * <ul>
 *   <li>The room is represented by a connected set of grid polygons (typically quads) that cover the cluster area.</li>
 *   <li>To derive a footprint, this class walks along "valid neighbor" relations between those polygons and emits
 *       alternating points: polygon center, shared-edge midpoint, polygon center, ...</li>
 * </ul>
 * The resulting vertex list is later used as a raw room footprint for further geometry processing
 * (chamfering, rotation, simplification, wall offset).
 * <p>
 * Note: This implementation is heuristic and assumes the chosen neighbor-walk approximates the boundary traversal.
 * It works best when {@code polygons} form a single boundary cycle without complex branching.
 */
public class RoomOutlineBuilder {

    private final BaseGrid grid;

    /**
     * Creates a builder that operates on a specific {@link BaseGrid}.
     *
     * @param grid the underlying grid providing polygon centers, vertices and topology
     * @throws NullPointerException if {@code grid} is {@code null}
     */
    public RoomOutlineBuilder(BaseGrid grid) {
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    /**
     * Computes an ordered list of 2D points approximating the room outline.
     * <p>
     * The returned list is a point sequence forming a polygonal chain in which points are appended in pairs:
     * <ol>
     *   <li>the center of the current polygon</li>
     *   <li>the midpoint of the shared edge to the next polygon</li>
     * </ol>
     * This produces a "zigzag" outline that follows the grid structure and is suitable as input for the
     * subsequent room-geometry pipeline.
     *
     * @param polygons polygons considered part of the room region (must belong to the same connected component)
     * @param cluster the originating vertex cluster; used to filter neighbor transitions
     * @return ordered list of outline points; may be empty if no walk could be established
     * @throws NullPointerException if any argument is {@code null}
     */
    public List<Vector2f> computeRoomPolygon(List<Polygon> polygons, VertexCluster cluster) {
        Objects.requireNonNull(polygons, "polygons");
        Objects.requireNonNull(cluster, "cluster");

        if (polygons.isEmpty()) return List.of();

        Map<Polygon, Vector2f> polygonCenters = grid.getPolygonCenters();
        Map<Polygon, Set<Polygon>> validNeighbors = getValidPolygonNeighbors(polygons, cluster);


        Set<Polygon> remaining = new HashSet<>(polygons);
        List<Vector2f> orderedCorners = new ArrayList<>();

        Polygon start = polygons.getFirst();
        Polygon current = start;

        // Walk until we cannot continue or we consumed the intended region.
        while (!remaining.isEmpty()) {

            Set<Polygon> neighbors = validNeighbors.getOrDefault(current, Set.of());
            Polygon next = null;

            // Case 1: "regular" boundary traversal: degree-2 node, pick the remaining neighbor.
            if (neighbors.size() == 2) {
                for (Polygon p : neighbors) {
                    if (remaining.contains(p) && p != current) {
                        next = p;
                        break;
                    }
                }
            }

            // Case 2 (fallback): if the local degree is > 2, try to choose a neighbor that looks like a boundary continuation.
            // This should never be the case because there should always be two valid neighbors for a quad grid.
            if (next == null && neighbors.size() > 2) {
                for (Polygon candidate : neighbors) {
                    if (!remaining.contains(candidate)) continue;
                    int neighborCount = validNeighbors.getOrDefault(candidate, Set.of()).size();
                    if (neighborCount == 2) {
                        next = candidate;
                        break;
                    }
                }
            }

            // If no continuation is found, stop the walk.
            if (next == null) {
                remaining.remove(current);
                break;
            }

            // Emit center and shared-edge midpoint.
            Edge sharedEdge = findSharedEdge(current, next);
            if (sharedEdge == null) {
                // Should not happen if neighbors are computed correctly, but keep the walk resilient.
                remaining.remove(current);
                current = next;
                continue;
            }

            Vector2f edgeMid = computeEdgeMidpoint(sharedEdge, grid);
            Vector2f centerA = polygonCenters.get(current).clone();

            // We store references as Vector2f; callers should treat the result as immutable geometry input.
            orderedCorners.add(centerA);
            orderedCorners.add(edgeMid);

            remaining.remove(current);
            current = next;
        }

        // Close the chain by adding the final center and the last edge midpoint back to the start (if possible).
        Edge lastEdge = findSharedEdge(current, start);
        if (lastEdge != null) {
            orderedCorners.add(grid.getPolygonCenters().get(current).clone());
            orderedCorners.add(computeEdgeMidpoint(lastEdge, grid));
        }

        return orderedCorners;
    }

    /**
     * Computes a filtered polygon-neighbor map that is suitable for boundary walking.
     * <p>
     * Two polygons {@code A} and {@code B} are considered neighbors if:
     * <ul>
     *   <li>they share an edge (topological adjacency),</li>
     *   <li>that shared edge is <em>not</em> part of the cluster's internal edge set (i.e. it lies on the boundary
     *       of the polygon region),</li>
     *   <li>and the shared edge touches the vertex cluster at least at one endpoint (acts as an additional filter).</li>
     * </ul>
     * The result is an undirected adjacency map for polygons in {@code polygons}.
     *
     * @param polygons the polygon region (room candidate) for which neighbors should be computed
     * @param cluster the vertex cluster that generated the region
     * @return map from polygon to the set of polygons that are valid neighbors under the above rules
     * @throws NullPointerException if any argument is {@code null}
     */
    public Map<Polygon, Set<Polygon>> getValidPolygonNeighbors(List<Polygon> polygons, VertexCluster cluster) {
        Objects.requireNonNull(polygons, "polygons");
        Objects.requireNonNull(cluster, "cluster");

        Map<Polygon, Set<Polygon>> neighborsMap = new HashMap<>();

        // VertexCluster currently exposes edges as a field; copy defensively for membership tests.
        Set<Edge> clusterEdges = new HashSet<>(cluster.getEdges());

        for (Polygon p : polygons) neighborsMap.put(p, new HashSet<>());

        int n = polygons.size();

        // neighbor detection by shared edge.
        for (int i = 0; i < n; i++) {
            Polygon polyA = polygons.get(i);

            for (int j = i + 1; j < n; j++) {
                Polygon polyB = polygons.get(j);

                Edge sharedEdge = findSharedEdge(polyA, polyB);
                if (sharedEdge == null) continue;

                // If the edge is inside the cluster, it is not part of the outline boundary.
                if (clusterEdges.contains(sharedEdge)) continue;

                // Additional filter: require the shared edge to touch at least one cluster vertex.
                // This reduces accidental connections when polygons are not strictly derived from the cluster boundary.
                int v1 = sharedEdge.getV1();
                int v2 = sharedEdge.getV2();
                boolean touchesCluster = cluster.contains(v1) || cluster.contains(v2);
                if (!touchesCluster) continue;

                neighborsMap.get(polyA).add(polyB);
                neighborsMap.get(polyB).add(polyA);
            }
        }

        return neighborsMap;
    }
}