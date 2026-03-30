package com.dungeon.logic.geometry;

/**
 * Represents an undirected edge between two vertices of a graph/mesh.
 * <p>
 * The edge is defined purely by the two vertex indices {@code v1} and {@code v2}.
 * It is treated as <b>undirected</b>, i.e. {@code Edge(a, b)} is considered equal to
 * {@code Edge(b, a)}. This is ensured by normalizing the order of the indices
 * in the constructor to {@code (min(a,b), max(a,b))}.
 * <p>
 */
public class Edge {

    /** The smaller (normalized) vertex index. */
    final int v1;

    /** The larger (normalized) vertex index. */
    final int v2;

    /**
     * Creates an undirected edge between the two given vertex indices.
     * <p>
     * The constructor normalizes the ordering such that {@code v1 <= v2}. This guarantees
     * that {@code new Edge(a,b).equals(new Edge(b,a))} is {@code true}.
     *
     * @param v1 index of the first vertex
     * @param v2 index of the second vertex
     */
    public Edge(int v1, int v2) {
        if (v1 < v2) {
            this.v1 = v1;
            this.v2 = v2;
        } else {
            this.v1 = v2;
            this.v2 = v1;
        }
    }

    /**
     * @return the first (normalized, smaller) vertex index of this edge
     */
    public int getV1() {
        return v1;
    }

    /**
     * @return the second (normalized, larger) vertex index of this edge
     */
    public int getV2() {
        return v2;
    }

    /**
     * Compares this edge to another object for equality.
     * <p>
     * Two edges are equal if they connect the same pair of vertex indices
     * (independent of order due to normalization).
     *
     * @param o the object to compare to
     * @return {@code true} if {@code o} is an {@code Edge} with the same normalized indices
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Edge e)) return false;
        return v1 == e.v1 && v2 == e.v2;
    }

    /**
     * Computes a hash code consistent with {@link #equals(Object)}.
     * <p>
     * Because indices are normalized in the constructor, this hash code is also
     * independent of the original input order.
     *
     * @return hash code for this edge
     */
    @Override
    public int hashCode() {
        return 31 * v1 + v2;
    }

    /**
     * @return a human-readable representation in the form {@code [v1 - v2]}
     */
    @Override
    public String toString() {
        return "[" + v1 + " - " + v2 + "]";
    }
}