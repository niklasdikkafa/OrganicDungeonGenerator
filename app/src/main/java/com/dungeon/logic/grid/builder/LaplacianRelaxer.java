package com.dungeon.logic.grid.builder;

import com.jme3.math.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies Laplacian relaxation (Laplacian smoothing) to a vertex set.
 * <p>
 * Each iteration moves every vertex towards the arithmetic mean of its neighbors:
 * a vertex {@code v_i} is updated to
 * {@code v_i := v_i + alpha * (avg(neighbors(i)) - v_i)}.
 * </p>
 * <p>
 * Connectivity (the neighbor relation) stays unchanged; only vertex positions are modified.
 * </p>
 */
public final class LaplacianRelaxer {

    /**
     * Performs in-place Laplacian relaxation on the given vertices.
     * <p>
     * For each iteration and each vertex {@code i}:
     * <ul>
     *   <li>If {@code i} has no neighbors, its position remains unchanged.</li>
     *   <li>Otherwise compute the neighbor average {@code a_i} and update:
     *       {@code v_i := (1 - alpha) * v_i + alpha * a_i}.</li>
     * </ul>
     * </p>
     *
     * @param vertices        Vertex positions (modified in place). Index in the list is the vertex id.
     * @param vertexNeighbors Adjacency map: vertex id {@code i ->} set of neighbor vertex ids.
     *                        Neighbor indices are assumed to be valid indices into {@code vertices}.
     * @param alpha           Relaxation factor. Typical range is {@code [0, 1]}:
     *                        {@code 0} means no change, {@code 1} snaps directly to the neighbor average.
     * @param iterations      Number of relaxation iterations. Values {@code <= 0} result in no changes.
     */
    public static void relax(List<Vector2f> vertices,
                             Map<Integer, Set<Integer>> vertexNeighbors,
                             float alpha,
                             int iterations) {

        if (iterations <= 0) return;
        if (alpha == 0f) return;
        if (vertices == null || vertices.isEmpty()) return;
        if (vertexNeighbors == null || vertexNeighbors.isEmpty()) return;

        final int n = vertices.size();

        // Buffers for "next" positions to avoid in-iteration feedback (Jacobi update).
        final float[] nx = new float[n];
        final float[] ny = new float[n];

        for (int iter = 0; iter < iterations; iter++) {

            // 1) Compute next positions based on current positions.
            for (int i = 0; i < n; i++) {
                Set<Integer> nbs = vertexNeighbors.get(i);
                Vector2f cur = vertices.get(i);

                if (nbs == null || nbs.isEmpty()) {
                    // No neighbors -> keep position.
                    nx[i] = cur.x;
                    ny[i] = cur.y;
                    continue;
                }

                float sumX = 0f, sumY = 0f;
                int cnt = 0;

                for (int nb : nbs) {
                    Vector2f p = vertices.get(nb);
                    sumX += p.x;
                    sumY += p.y;
                    cnt++;
                }

                float inv = 1f / cnt;
                float ax = sumX * inv;
                float ay = sumY * inv;

                // v' = v + alpha * (avg - v)
                nx[i] = cur.x + (ax - cur.x) * alpha;
                ny[i] = cur.y + (ay - cur.y) * alpha;
            }

            // 2) Write back to the existing Vector2f instances.
            for (int i = 0; i < n; i++) {
                Vector2f v = vertices.get(i);
                v.x = nx[i];
                v.y = ny[i];
            }
        }
    }
}