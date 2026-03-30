package com.dungeon.logic.geometry;

import java.util.Arrays;

/**
 * Represents a polygon by storing indices into an external vertex list.
 * <p>
 * A {@code Polygon} does not own any coordinates itself. Instead, its geometry is defined by
 * an ordered sequence of integer indices referencing a separate vertex array/list (e.g. {@code List<Vector2f>}).
 * The index order is significant: it determines edge connectivity and (depending on convention)
 * the polygon orientation (CW/CCW).
 *
 * <h3>Important</h3>
 * <ul>
 *   <li>This class treats the polygon as an ordered index sequence.</li>
 *   <li>{@link #equals(Object)} / {@link #hashCode()} use {@link Arrays#equals(int[], int[])} and therefore
 *       consider two polygons equal only if they have the same indices in the same order.</li>
 * </ul>
 */
public class Polygon {

    /**
     * Ordered indices into a vertex list. Consecutive indices form edges, and the last index
     * connects back to the first.
     */
    private final int[] vertexIndices;

    /**
     * Creates a polygon from the given vertex indices.
     * <p>
     * The indices are interpreted as an ordered ring (closed polygon).
     *
     * @param vertexIndices ordered indices of the vertices that form the polygon boundary
     * @throws IllegalArgumentException if {@code vertexIndices} is {@code null} or has less than 3 vertices
     */
    public Polygon(int[] vertexIndices) {
        if (vertexIndices == null) {
            throw new IllegalArgumentException("vertexIndices must not be null");
        }
        if (vertexIndices.length < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 vertices");
        }
        this.vertexIndices = vertexIndices.clone();
    }

    /**
     * Returns the number of vertices (corners) of this polygon.
     *
     * @return number of stored vertex indices
     */
    public int vertexCount() {
        return vertexIndices.length;
    }

    /**
     * Returns the backing index array.
     *
     * @return a clone of the vertex index array to prevent external mutation
     */
    public int[] getVertexIndices() {
        return vertexIndices.clone();
    }

    /**
     * Returns the vertex index at a given position in the polygon's index sequence.
     *
     * @param i position in the index sequence (0..{@code vertexCount()-1})
     * @return vertex index at position {@code i}
     * @throws ArrayIndexOutOfBoundsException if {@code i} is out of range
     */
    public int get(int i) {
        return vertexIndices[i];
    }

    /**
     * Compares this polygon to another object.
     * <p>
     * Two polygons are considered equal if and only if their index arrays have the same length
     * and contain the same indices in the same order.
     *
     * @param o object to compare
     * @return {@code true} if {@code o} is a {@code Polygon} with the same ordered indices
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Polygon other)) return false;
        return Arrays.equals(this.vertexIndices, other.vertexIndices);
    }

    /**
     * Computes a hash code consistent with {@link #equals(Object)} based on the ordered index array.
     *
     * @return hash code for this polygon
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(vertexIndices);
    }

    /**
     * @return a human-readable representation, e.g. {@code Polygon[1, 2, 3, 4]}
     */
    @Override
    public String toString() {
        return "Polygon" + Arrays.toString(vertexIndices);
    }
}