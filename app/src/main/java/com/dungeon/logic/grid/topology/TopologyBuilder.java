package com.dungeon.logic.grid.topology;

import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;

import java.util.*;

/**
 * Utility to build various topology maps for a grid.
 */
public final class TopologyBuilder {

    private TopologyBuilder(){}

    /**
     * Result of building topology maps.
     */
    public static final class Result {
        private final Map<Integer, List<Polygon>> vertexToPolygons;
        private final Map<Integer, Set<Integer>> vertexNeighbors;
        private final Map<Integer, Set<Integer>> validVertexNeighbors;
        private final Map<Polygon, Vector2f> polygonCenters;
        private final Map<Polygon, Set<Polygon>> polygonNeighbors;

        Result(Map<Integer, List<Polygon>> vertexToPolygons,
               Map<Integer, Set<Integer>> vertexNeighbors,
               Map<Integer, Set<Integer>> validVertexNeighbors,
               Map<Polygon, Vector2f> polygonCenters,
               Map<Polygon, Set<Polygon>> polygonNeighbors) {

            this.vertexToPolygons = vertexToPolygons;
            this.vertexNeighbors = vertexNeighbors;
            this.validVertexNeighbors = validVertexNeighbors;
            this.polygonCenters = polygonCenters;
            this.polygonNeighbors = polygonNeighbors;
        }

        public Map<Integer, List<Polygon>> vertexToPolygons() { return vertexToPolygons; }
        public Map<Integer, Set<Integer>> vertexNeighbors() { return vertexNeighbors; }
        public Map<Integer, Set<Integer>> validVertexNeighbors() { return validVertexNeighbors; }
        public Map<Polygon, Vector2f> polygonCenters() { return polygonCenters; }
        public Map<Polygon, Set<Polygon>> polygonNeighbors() { return polygonNeighbors; }
    }

    /**
     * Builds all kinds of topology maps for the given vertices and polygons.
     * @param vertices list of vertex positions
     * @param allPolygons list of all polygons in the grid
     * @return Result containing various topology maps
     */
    public static Result build(List<Vector2f> vertices, List<Polygon> allPolygons) {

        Map<Integer, List<Polygon>> vertexToPolygons = buildVertexToPolygonMap(allPolygons);
        boolean[] isBoundary = computeBoundaryVertices(vertices.size(), allPolygons);

        Map<Integer, Set<Integer>> vertexNeighbors = new HashMap<>();
        Map<Integer, Set<Integer>> validVertexNeighbors = new HashMap<>();
        buildVertexNeighborMap(allPolygons, isBoundary, vertexNeighbors, validVertexNeighbors);

        Map<Polygon, Vector2f> polygonCenters = computePolygonCenters(vertices, allPolygons);
        Map<Polygon, Set<Polygon>> polygonNeighbors = buildPolygonNeighborMap(allPolygons);

        return new Result(vertexToPolygons, vertexNeighbors, validVertexNeighbors, polygonCenters, polygonNeighbors);
    }

    /**
     * Builds a map from vertex index to the list of polygons that include that vertex.
     * @param allPolygons list of all polygons
     * @return map from vertex index to list of polygons
     */
    private static Map<Integer, List<Polygon>> buildVertexToPolygonMap(List<Polygon> allPolygons) {
        Map<Integer, List<Polygon>> map = new HashMap<>();
        for (Polygon poly : allPolygons) {
            for (int v : poly.getVertexIndices()) {
                map.computeIfAbsent(v, _ -> new ArrayList<>()).add(poly);
            }
        }
        return map;
    }

    /**
     * Computes which vertices are boundary vertices.
     * @param vertexCount total number of vertices
     * @param allPolygons list of all polygons
     * @return boolean array where true indicates a boundary vertex
     */
    private static boolean[] computeBoundaryVertices(int vertexCount, List<Polygon> allPolygons) {
        boolean[] isBoundary = new boolean[vertexCount];
        Map<Edge, Integer> edgeUseCount = new HashMap<>();

        for (Polygon poly : allPolygons) {
            int k = poly.vertexCount();
            for (int i = 0; i < k; i++) {
                int v1 = poly.get(i);
                int v2 = poly.get((i + 1) % k);
                Edge key = new Edge(v1, v2);
                edgeUseCount.merge(key, 1, Integer::sum);
            }
        }

        for (Map.Entry<Edge, Integer> e : edgeUseCount.entrySet()) {
            if (e.getValue() == 1) {
                isBoundary[e.getKey().getV1()] = true;
                isBoundary[e.getKey().getV2()] = true;
            }
        }
        return isBoundary;
    }

    /**
     * Builds maps of vertex neighbors and valid vertex neighbors (non-boundary).
     * @param allPolygons list of all polygons
     * @param isBoundary boolean array indicating boundary vertices
     * @param vertexNeighbors output map of vertex index to its neighboring vertex indices
     * @param validVertexNeighbors output map of vertex index to its valid neighboring vertex indices
     */
    private static void buildVertexNeighborMap(List<Polygon> allPolygons,
                                               boolean[] isBoundary,
                                               Map<Integer, Set<Integer>> vertexNeighbors,
                                               Map<Integer, Set<Integer>> validVertexNeighbors) {

        for (Polygon poly : allPolygons) {
            int k = poly.vertexCount();
            for (int i = 0; i < k; i++) {
                int v1 = poly.get(i);
                int v2 = poly.get((i + 1) % k);

                vertexNeighbors.computeIfAbsent(v1, _ -> new HashSet<>()).add(v2);
                vertexNeighbors.computeIfAbsent(v2, _ -> new HashSet<>()).add(v1);

                if (!isBoundary[v1] && !isBoundary[v2]) {
                    validVertexNeighbors.computeIfAbsent(v1, _ -> new HashSet<>()).add(v2);
                    validVertexNeighbors.computeIfAbsent(v2, _ -> new HashSet<>()).add(v1);
                }
            }
        }
    }

    /**
     * Computes the center (centroid) of each polygon.
     * @param vertices list of vertex positions
     * @param allPolygons list of all polygons
     * @return map from polygon to its center position
     */
    public static Map<Polygon, Vector2f> computePolygonCenters(List<Vector2f> vertices, List<Polygon> allPolygons) {
        Map<Polygon, Vector2f> polygonCenters = new HashMap<>();
        for (Polygon poly : allPolygons) {
            int[] idx = poly.getVertexIndices();
            Vector2f avg = new Vector2f(0,0);
            for (int v : idx) avg.addLocal(vertices.get(v));
            avg.multLocal(1f / idx.length);
            polygonCenters.put(poly, avg);
        }
        return polygonCenters;
    }

    /**
     * Builds a map of neighboring polygons for each polygon.
     * Two polygons are neighbors if they share exactly two vertices (an edge).
     * @param allPolygons list of all polygons
     * @return map from polygon to its neighboring polygons
     */
    private static Map<Polygon, Set<Polygon>> buildPolygonNeighborMap(List<Polygon> allPolygons) {
        Map<Polygon, Set<Polygon>> polygonNeighbors = new HashMap<>();

        for (int i = 0; i < allPolygons.size(); i++) {
            Polygon p1 = allPolygons.get(i);
            int[] idx1 = p1.getVertexIndices();

            for (int j = i + 1; j < allPolygons.size(); j++) {
                Polygon p2 = allPolygons.get(j);
                int[] idx2 = p2.getVertexIndices();

                int shared = 0;
                for (int v1 : idx1) {
                    for (int v2 : idx2) {
                        if (v1 == v2) shared++;
                    }
                }

                if (shared == 2) {
                    polygonNeighbors.computeIfAbsent(p1, _ -> new HashSet<>()).add(p2);
                    polygonNeighbors.computeIfAbsent(p2, _ -> new HashSet<>()).add(p1);
                }
            }
        }
        return polygonNeighbors;
    }
}