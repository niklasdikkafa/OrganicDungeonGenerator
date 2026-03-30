package com.dungeon.logic.grid;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable container for the generated 2D base grid ("Townscaper-style" footprint grid).
 * <p>
 * A {@code BaseGrid} consists of:
 * </p>
 * <ul>
 *   <li><b>Parameters</b> describing how the grid was generated (e.g., {@code sideCount}, {@code edgeLength}),</li>
 *   <li><b>Geometry</b> (vertex positions and polygons),</li>
 *   <li><b>Derived topology</b> (neighbor maps, polygon centers, incidence relations).</li>
 * </ul>
 *
 * <h3>Geometry & topology overview</h3>
 * <ul>
 *   <li>{@code vertices}: all vertex positions referenced by polygon vertex indices.</li>
 *   <li>{@code triangles}: the initial triangle mesh used as a generation intermediate.</li>
 *   <li>{@code allPolygons}: final polygons used for placement and routing (typically quads after splitting).</li>
 *   <li>{@code polygonCenters}: cached centroid/center points (recomputed after relaxation).</li>
 *   <li>{@code vertexNeighbors}: adjacency of vertices in the full mesh.</li>
 *   <li>{@code validVertexNeighbors}: adjacency excluding boundary vertices (useful for sampling/growth).</li>
 *   <li>{@code vertexToPolygons}: incidence map from vertex index to all polygons that contain it.</li>
 *   <li>{@code polygonNeighbors}: polygon adjacency via shared edges (two shared vertices).</li>
 * </ul>
 */
public final class BaseGrid {

    // -------------------- generation parameters --------------------

    /** Hex radius measured in rings (center to corner) used during point generation. */
    private final int sideCount;

    /** Edge length used during point generation. */
    private final float edgeLength;

    /** Vertex count before any polygon splitting steps added additional vertices. */
    private final int originalVertexCount;

    // -------------------- geometry --------------------

    /** All vertex positions referenced by polygons via integer indices. */
    private final List<Vector2f> vertices;

    /** Triangles forming the initial grid connectivity (generation intermediate). */
    private final List<Polygon> triangles;

    /** Final polygon set (e.g., quads after splitting), used for placement/routing. */
    private final List<Polygon> allPolygons;

    // -------------------- derived / cached topology --------------------

    /** Cached center point per polygon (typically recomputed after vertex relaxation). */
    private final Map<Polygon, Vector2f> polygonCenters;

    /** Vertex adjacency for the full mesh (including boundary vertices). */
    private final Map<Integer, Set<Integer>> vertexNeighbors;

    /** Vertex adjacency excluding boundary vertices (used for safer random growth/sampling). */
    private final Map<Integer, Set<Integer>> validVertexNeighbors;

    /** Incidence map: which polygons contain a given vertex index. */
    private final Map<Integer, List<Polygon>> vertexToPolygons;

    /** Polygon adjacency: neighboring polygons that share an edge (two shared vertices). */
    private final Map<Polygon, Set<Polygon>> polygonNeighbors;


    /**
     * Creates a new {@code BaseGrid} from already-generated geometry and topology.
     * <p>
     * This constructor assumes all collections are consistent with each other:
     * polygon indices must be valid indices into {@code vertices}, topology maps must reference existing
     * vertices/polygons, etc.
     * </p>
     *
     * @param sideCount             hex radius (number of rings from center to corner)
     * @param edgeLength            edge length used during grid generation
     * @param originalVertexCount   number of vertices before splitting steps added more vertices
     * @param vertices              list of vertex positions
     * @param triangles             initial triangle polygons
     * @param allPolygons           final polygon set
     * @param polygonCenters        cached polygon centers
     * @param vertexNeighbors       vertex adjacency (full)
     * @param validVertexNeighbors  vertex adjacency excluding boundary vertices
     * @param vertexToPolygons      incidence map vertex -> polygons
     * @param polygonNeighbors      polygon adjacency map
     */
    public BaseGrid(int sideCount,
                    float edgeLength,
                    int originalVertexCount,
                    List<Vector2f> vertices,
                    List<Polygon> triangles,
                    List<Polygon> allPolygons,
                    Map<Polygon, Vector2f> polygonCenters,
                    Map<Integer, Set<Integer>> vertexNeighbors,
                    Map<Integer, Set<Integer>> validVertexNeighbors,
                    Map<Integer, List<Polygon>> vertexToPolygons,
                    Map<Polygon, Set<Polygon>> polygonNeighbors) {

        this.sideCount = sideCount;
        this.edgeLength = edgeLength;
        this.originalVertexCount = originalVertexCount;

        this.vertices = vertices;
        this.triangles = triangles;
        this.allPolygons = allPolygons;

        this.polygonCenters = polygonCenters;
        this.vertexNeighbors = vertexNeighbors;
        this.validVertexNeighbors = validVertexNeighbors;
        this.vertexToPolygons = vertexToPolygons;
        this.polygonNeighbors = polygonNeighbors;
    }

    // -------------------- getters --------------------

    public int getSideCount() { return sideCount; }

    public float getEdgeLength() { return edgeLength; }

    public int getOriginalVertexCount() { return originalVertexCount; }

    public List<Vector2f> getVertices() { return vertices; }

    public List<Polygon> getTriangles() { return triangles; }

    public List<Polygon> getAllPolygons() { return allPolygons; }

    public Map<Polygon, Vector2f> getPolygonCenters() { return polygonCenters; }

    public Map<Integer, Set<Integer>> getVertexNeighbors() { return vertexNeighbors; }

    public Map<Integer, Set<Integer>> getValidVertexNeighbors() { return validVertexNeighbors; }

    public Map<Integer, List<Polygon>> getVertexToPolygons() { return vertexToPolygons; }

    public Map<Polygon, Set<Polygon>> getPolygonNeighbors() { return polygonNeighbors; }
}