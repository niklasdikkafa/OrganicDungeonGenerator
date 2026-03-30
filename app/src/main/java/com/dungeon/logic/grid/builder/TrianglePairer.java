package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for merging (pairing) adjacent triangles into quadrilaterals (quads).
 * <p>
 * The input is assumed to be a triangle mesh where triangles share edges by sharing exactly two
 * vertex indices. This class:
 * <ol>
 *   <li>Finds all adjacent triangle pairs (share exactly one edge).</li>
 *   <li>Selects random pairs without reusing triangles.</li>
 *   <li>Builds a quad from each selected pair by ordering the four distinct vertices around their centroid.</li>
 * </ol>
 *
 * <p>
 * Note: Not all triangles will necessarily be paired. Triangles that are not selected (or that cannot
 * be paired due to conflicts) remain as triangles in the {@link PairingResult}.
 * </p>
 */
public final class TrianglePairer {

    /**
     * Result container for the pairing process.
     * <p>
     * Contains:
     * <ul>
     *   <li>The triangles that were not used in any pair.</li>
     *   <li>A mapping from the chosen triangle pair to its generated quad polygon.</li>
     * </ul>
     */
    public static final class PairingResult {
        private final List<Polygon> remainingTriangles;
        private final Map<TrianglePair, Polygon> triangleToQuadMap;

        PairingResult(List<Polygon> remainingTriangles, Map<TrianglePair, Polygon> triangleToQuadMap) {
            this.remainingTriangles = remainingTriangles;
            this.triangleToQuadMap = triangleToQuadMap;
        }

        /**
         * Returns the "base polygons" set used for subsequent steps.
         * <p>
         * This is the union of:
         * <ul>
         *   <li>all triangles that were not paired</li>
         *   <li>all quads created from paired triangles</li>
         * </ul>
         *
         * @return list containing remaining triangles plus generated quads
         */
        public List<Polygon> basePolygons() {
            List<Polygon> out = new ArrayList<>(remainingTriangles);
            out.addAll(triangleToQuadMap.values());
            return out;
        }

        /** @return triangles that were not paired into any quad */
        public List<Polygon> remainingTriangles() { return remainingTriangles; }

        /** @return mapping from a chosen triangle pair to the resulting quad polygon */
        public Map<TrianglePair, Polygon> triangleToQuadMap() { return triangleToQuadMap; }
    }

    /**
     * Represents two triangles that share an edge.
     * <p>
     * Triangles are identified by their indices ({@link #t1}, {@link #t2}) within the provided triangle list.
     * The shared edge is stored as an {@link Edge} (two vertex indices).
     * </p>
     */
    public static final class TrianglePair {
        /** Index of the first triangle in the triangle list. */
        public final int t1;
        /** Index of the second triangle in the triangle list. */
        public final int t2;
        /** The edge (two vertex indices) shared by both triangles. */
        public final Edge sharedEdge;

        /**
         * Creates a triangle pair description.
         *
         * @param t1 index of the first triangle in the input list
         * @param t2 index of the second triangle in the input list
         * @param sharedEdge the edge shared between the two triangles
         */
        public TrianglePair(int t1, int t2, Edge sharedEdge) {
            this.t1 = t1;
            this.t2 = t2;
            this.sharedEdge = sharedEdge;
        }

        @Override
        public String toString() { return "(" + t1 + ", " + t2 + ")"; }
    }

    /**
     * Randomly pairs adjacent triangles into quads.
     * <p>
     * Workflow:
     * <ol>
     *   <li>Compute all adjacent triangle pairs (share exactly two vertices).</li>
     *   <li>Randomly pick pairs; skip if any triangle has already been used.</li>
     *   <li>For each accepted pair, build a quad polygon and store it in the result map.</li>
     * </ol>
     *
     * @param vertices list of vertex positions used for ordering quad vertices (angle around centroid)
     * @param triangles list of triangles to pair
     * @param rand randomness source used to pick which adjacent pairs are used
     * @return pairing result containing leftover triangles and the generated quads
     */
    public static PairingResult pair(List<Vector2f> vertices, List<Polygon> triangles, Random rand) {
        List<TrianglePair> possiblePairs = findAdjacentPairs(triangles);
        Set<Integer> usedTriangles = new HashSet<>();
        Map<TrianglePair, Polygon> triangleToQuadMap = new HashMap<>();

        while (!possiblePairs.isEmpty()) {
            TrianglePair pair = possiblePairs.remove(rand.nextInt(possiblePairs.size()));
            if (usedTriangles.contains(pair.t1) || usedTriangles.contains(pair.t2)) continue;

            usedTriangles.add(pair.t1);
            usedTriangles.add(pair.t2);

            int[] quadVerts = buildQuadFromPair(vertices, triangles, pair);
            triangleToQuadMap.put(pair, new Polygon(quadVerts));
        }

        List<Polygon> remainingTriangles = new ArrayList<>();
        for (int i = 0; i < triangles.size(); i++) {
            if (!usedTriangles.contains(i)) remainingTriangles.add(triangles.get(i));
        }

        return new PairingResult(remainingTriangles, triangleToQuadMap);
    }

    /**
     * Finds all triangle pairs that are adjacent (share exactly one edge).
     * <p>
     * Two triangles are considered adjacent if their vertex index sets intersect in exactly two vertices.
     * Those two shared vertices define the shared edge.
     * </p>
     *
     * @param triangles list of triangles
     * @return list of all adjacent triangle pairs
     */
    private static List<TrianglePair> findAdjacentPairs(List<Polygon> triangles) {
        List<TrianglePair> pairs = new ArrayList<>();

        for (int i = 0; i < triangles.size(); i++) {
            for (int j = i + 1; j < triangles.size(); j++) {
                int[] t1 = triangles.get(i).getVertexIndices();
                int[] t2 = triangles.get(j).getVertexIndices();

                Set<Integer> s1 = Arrays.stream(t1).boxed().collect(Collectors.toSet());
                Set<Integer> s2 = Arrays.stream(t2).boxed().collect(Collectors.toSet());

                s1.retainAll(s2);
                if (s1.size() == 2) {
                    List<Integer> shared = new ArrayList<>(s1);
                    pairs.add(new TrianglePair(i, j, new Edge(shared.get(0), shared.get(1))));
                }
            }
        }
        return pairs;
    }

    /**
     * Builds a quad polygon from a pair of adjacent triangles.
     * <p>
     * Let the two triangles share vertices {@code s0} and {@code s1} (the common edge). Each triangle then has exactly
     * one additional unique vertex {@code u0} and {@code u1}. The quad is formed by these four distinct vertices:
     * {@code {s0, s1, u0, u1}}.
     * </p>
     *
     * <p>
     * To obtain a valid polygon order, the method computes the centroid of the four vertex positions and sorts the
     * four indices by their polar angle around that centroid. This produces a consistent CW/CCW cycle (depending
     * on coordinate system orientation) and avoids self-crossing quads for typical triangle meshes.
     * </p>
     *
     * @param vertices list of vertex positions
     * @param triangles list of triangles
     * @param pair the triangle pair (indices + shared edge)
     * @return array of 4 vertex indices forming the quad in cyclic order
     */
    private static int[] buildQuadFromPair(List<Vector2f> vertices, List<Polygon> triangles, TrianglePair pair) {
        Polygon triA = triangles.get(pair.t1);
        Polygon triB = triangles.get(pair.t2);

        int s0 = pair.sharedEdge.getV1();
        int s1 = pair.sharedEdge.getV2();

        int u0 = -1, u1 = -1;

        // Find the unique vertex in triangle A (not part of the shared edge).
        for (int v : triA.getVertexIndices()) {
            if (v != s0 && v != s1) u0 = v;
        }

        // Find the unique vertex in triangle B (not part of the shared edge).
        for (int v : triB.getVertexIndices()) {
            if (v != s0 && v != s1) u1 = v;
        }

        Vector2f p0 = vertices.get(s0);
        Vector2f p1 = vertices.get(s1);
        Vector2f p2 = vertices.get(u0);
        Vector2f p3 = vertices.get(u1);

        // Centroid of the four points (simple average) used as angular sorting origin.
        Vector2f center = new Vector2f(
                (p0.x + p1.x + p2.x + p3.x) / 4f,
                (p0.y + p1.y + p2.y + p3.y) / 4f
        );

        List<Integer> quad = new ArrayList<>(4);
        quad.add(s0);
        quad.add(s1);
        quad.add(u0);
        quad.add(u1);

        // Sort the four vertices by polar angle around the centroid.
        // This yields a cyclic ordering of vertices (a simple polygon boundary).
        quad.sort((a, b) -> {
            Vector2f va = vertices.get(a).subtract(center);
            Vector2f vb = vertices.get(b).subtract(center);
            double angA = Math.atan2(va.y, va.x);
            double angB = Math.atan2(vb.y, vb.x);
            return Double.compare(angA, angB);
        });

        return new int[]{ quad.get(0), quad.get(1), quad.get(2), quad.get(3) };
    }
}