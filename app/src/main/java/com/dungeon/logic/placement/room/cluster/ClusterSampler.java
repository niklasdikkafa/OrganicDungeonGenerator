package com.dungeon.logic.placement.room.cluster;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;

import java.util.*;

/**
 * Samples connected vertex clusters on a {@link BaseGrid} for room placement.
 * <p>
 * The room placement in this project starts from a randomly chosen non-boundary vertex and then grows a
 * connected set of vertices until a desired size is reached. The growth is stochastic:
 * each iteration picks a random vertex from the current frontier and expands to a random not-yet-selected
 * neighbor (if possible).
 * <p>
 * After the initial growth, the cluster is post-processed by {@link #fillAllHoles(Set, Map)} to avoid
 * topological artifacts ("holes") that would complicate later outline extraction (e.g. boundary polygon
 * computation for the room footprint).
 */
public class ClusterSampler {

    private final BaseGrid grid;
    private final Random random;

    /**
     * Creates a sampler bound to a specific {@link BaseGrid}.
     *
     * @param grid   the grid providing vertices and neighborhood relations
     * @param random RNG used for all stochastic decisions
     * @throws NullPointerException if any argument is {@code null}
     */
    public ClusterSampler(BaseGrid grid, Random random) {
        this.grid = Objects.requireNonNull(grid, "grid");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Picks a random start vertex for cluster growth that is not already used by previous rooms.
     * <p>
     * Candidates are taken from {@link BaseGrid#getValidVertexNeighbors()}, i.e. vertices that have a "valid"
     * neighbor set (typically excluding boundary vertices).
     *
     * @param usedVertices vertex indices that are already occupied/assigned by previously accepted rooms
     * @return a randomly selected start vertex index, or {@code -1} if no candidate is available
     * @throws NullPointerException if {@code usedVertices} is {@code null}
     */
    public int pickRandomStartVertex(Set<Integer> usedVertices) {
        Objects.requireNonNull(usedVertices, "usedVertices");

        List<Integer> candidates = new ArrayList<>();
        for (Integer v : grid.getValidVertexNeighbors().keySet()) {
            if (!usedVertices.contains(v)) candidates.add(v);
        }
        if (candidates.isEmpty()) return -1;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Grows a connected vertex cluster starting at {@code start} until {@code count} vertices are reached
     * (or no further expansion is possible).
     * <p>
     * Algorithm sketch:
     * <ol>
     *   <li>Initialize cluster with {@code start} and add it to a frontier queue.</li>
     *   <li>While the cluster is smaller than {@code count} and the frontier is not empty:</li>
     *   <li>Pick a random vertex from the frontier.</li>
     *   <li>Pick a random neighbor of that vertex that is not yet in the cluster and add it to both cluster and frontier.</li>
     *   <li>If the chosen frontier vertex cannot expand further, remove it from the frontier.</li>
     * </ol>
     * Afterwards, {@link #fillAllHoles(Set, Map)} is applied to include any vertices that are completely enclosed
     * by the cluster (to avoid interior gaps).
     * <p>
     * The returned {@link VertexCluster} is represented via its internal edges between vertices that are part of the
     * cluster. Edges are constructed using the grid's valid vertex neighborhood relation.
     *
     * @param start start vertex index (should be a non-boundary vertex for best results)
     * @param count desired cluster size (must be {@code >= 1})
     * @return a {@link VertexCluster} induced by the sampled vertex set
     * @throws IllegalArgumentException if {@code count < 1}
     */
    public VertexCluster getConnectedCluster(int start, int count) {
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");

        if (count == 1) {
            return new VertexCluster(Set.of(start), Set.of());
        }

        Set<Integer> vertices = new HashSet<>();
        ArrayDeque<Integer> frontier = new ArrayDeque<>();

        vertices.add(start);
        frontier.add(start);

        while (vertices.size() < count && !frontier.isEmpty()) {
            int[] frontierArray = frontier.stream().mapToInt(Integer::intValue).toArray();
            int current = frontierArray[random.nextInt(frontierArray.length)];

            Set<Integer> neighbors = grid.getValidVertexNeighbors().getOrDefault(current, Set.of());
            if (neighbors.isEmpty()) {
                frontier.remove(current);
                continue;
            }

            List<Integer> available = neighbors.stream()
                    .filter(v -> !vertices.contains(v))
                    .toList();

            if (available.isEmpty()) {
                frontier.remove(current);
                continue;
            }

            int newVertex = available.get(random.nextInt(available.size()));
            vertices.add(newVertex);
            frontier.add(newVertex);
        }

        // Ensure no interior gaps remain
        fillAllHoles(vertices, grid.getVertexNeighbors());

        Set<Edge> edges = new HashSet<>();
        for (int v : vertices) {
            for (int n : grid.getValidVertexNeighbors().getOrDefault(v, Set.of())) {
                if (vertices.contains(n)) edges.add(new Edge(v, n));
            }
        }
        return new VertexCluster(edges);
    }

    /**
     * Fills topological holes inside a cluster.
     * <p>
     * A "hole" is a vertex (or region of vertices) that is not in {@code cluster} but is fully enclosed by the
     * cluster, meaning it is not reachable from the grid boundary without crossing a cluster vertex.
     * <p>
     * Implementation detail:
     * <ul>
     *   <li>Compute the set {@code outside} of all non-cluster vertices that are reachable from boundary vertices
     *       via flood fill (BFS) without entering the cluster.</li>
     *   <li>Every remaining non-cluster vertex that is not marked {@code outside} must lie inside an enclosed region,
     *       thus it is added to the cluster.</li>
     * </ul>
     * Boundary vertices are approximated as those vertices that are <em>not</em> part of
     * {@link BaseGrid#getValidVertexNeighbors()} (i.e. vertices excluded from "valid" neighbor sets).
     *
     * @param cluster   the cluster vertex set; will be modified in-place to include hole vertices
     * @param neighbors adjacency map (typically {@link BaseGrid#getVertexNeighbors()}) used for flood fill
     * @throws NullPointerException if any argument is {@code null}
     */
    private void fillAllHoles(Set<Integer> cluster,
                              Map<Integer, Set<Integer>> neighbors) {

        Objects.requireNonNull(cluster, "cluster");
        Objects.requireNonNull(neighbors, "neighbors");

        ArrayDeque<Integer> q = new ArrayDeque<>();
        Set<Integer> outside = new HashSet<>(neighbors.size() * 2);

        // Non-boundary vertices are those included in validVertexNeighbors
        Set<Integer> nonBoundary = grid.getValidVertexNeighbors().keySet();

        // Seed BFS with boundary vertices that are not part of the cluster
        for (int v : neighbors.keySet()) {
            boolean isBoundary = !nonBoundary.contains(v);
            if (isBoundary && !cluster.contains(v)) {
                outside.add(v);
                q.add(v);
            }
        }

        // Flood fill "outside" region without entering cluster vertices (BFS)
        while (!q.isEmpty()) {
            int cur = q.poll();
            Set<Integer> nbs = neighbors.get(cur);
            if (nbs == null) continue;

            for (int nb : nbs) {
                if (cluster.contains(nb)) continue;
                if (outside.add(nb)) q.add(nb);
            }
        }

        // Every vertex that is neither in cluster nor reachable from boundary is inside -> fill it
        for (int v : neighbors.keySet()) {
            if (!cluster.contains(v) && !outside.contains(v)) {
                cluster.add(v);
            }
        }
    }
}