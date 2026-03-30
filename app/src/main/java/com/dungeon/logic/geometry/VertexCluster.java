package com.dungeon.logic.geometry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an undirected connected vertex subgraph defined by a set of edges.
 * <p>
 * A {@code VertexCluster} is mainly used during room placement: first a set of vertex indices is sampled,
 * then the induced internal adjacency is stored as undirected {@link Edge} instances. From the edge set,
 * this class derives the corresponding set of vertex indices.
 * <p>
 * Notes / design intent:
 * <ul>
 *   <li>This class is a lightweight data container (no expensive graph algorithms).</li>
 *   <li>Vertices are derived from the edge endpoints and kept in a set for fast membership checks.</li>
 *   <li>The cluster is treated as undirected: {@link Edge} normalizes its endpoints.</li>
 * </ul>
 */
public class VertexCluster {

    /**
     * Undirected edges that constitute the cluster connectivity.
     * <p>
     * Consider making this {@code private final} to prevent accidental external mutation.
     */
    private final Set<Edge> edges;

    /**
     * Set of vertex indices that appear in {@link #edges}.
     */
    private final Set<Integer> vertices;

    /**
     * Creates a cluster from an edge set and derives the vertex set from the edge endpoints.
     *
     * @param edges undirected edges connecting the vertices in this cluster
     * @throws NullPointerException if {@code edges} is {@code null}
     */
    public VertexCluster(Set<Edge> edges) {
        Objects.requireNonNull(edges, "edges");
        this.edges = Set.copyOf(edges);

        Set<Integer> v = new HashSet<>(Math.max(16, this.edges.size() * 2));
        for (Edge e : this.edges) {
            v.add(e.getV1());
            v.add(e.getV2());
        }
        this.vertices = Collections.unmodifiableSet(v);
    }

    /**
     * Creates a cluster from an explicit vertex set and edge set. Checks that all edge endpoints are contained in the vertex set.
     * @param vertices explicit set of vertex indices that belong to this cluster
     * @param edges undirected edges connecting the vertices in this cluster
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code vertices} is empty or if any edge references vertices not contained in {@code vertices}
     */
    public VertexCluster(Set<Integer> vertices, Set<Edge> edges) {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(edges, "edges");

        Set<Integer> vCopy = new HashSet<>(vertices);
        if (vCopy.isEmpty()) {
            throw new IllegalArgumentException("vertices must not be empty");
        }

        Set<Edge> eCopy = new HashSet<>(edges);

        for (Edge e : eCopy) {
            if (!vCopy.contains(e.getV1()) || !vCopy.contains(e.getV2())) {
                throw new IllegalArgumentException(
                        "Edge " + e + " references vertices not contained in vertex set"
                );
            }
        }

        this.vertices = Collections.unmodifiableSet(vCopy);
        this.edges = Collections.unmodifiableSet(eCopy);
    }

    /**
     * Checks whether a vertex index is part of this cluster.
     *
     * @param vertexIndex vertex index to test
     * @return {@code true} if the vertex occurs in any cluster edge; {@code false} otherwise
     */
    public boolean contains(int vertexIndex) {
        return vertices.contains(vertexIndex);
    }

    /**
     * Returns an immutable view of all vertex indices that belong to this cluster.
     *
     * @return immutable set of vertex indices
     */
    public Set<Integer> getVertices() {
        return vertices;
    }

    /**
     * Returns an immutable view of the cluster's edge set.
     *
     * @return immutable set of edges
     */
    public Set<Edge> getEdges() {
        return edges;
    }
}