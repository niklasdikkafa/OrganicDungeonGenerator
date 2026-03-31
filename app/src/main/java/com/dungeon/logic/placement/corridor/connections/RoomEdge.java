package com.dungeon.logic.placement.corridor.connections;

import com.dungeon.domain.Room;

import java.util.Objects;

/**
 * Undirected weighted edge between two {@link Room} instances.
 * <p>
 * This class is used as a lightweight domain edge for building candidate room connections.
 * </p>
 *
 * <h2>Weight</h2>
 * <p>
 * The {@link #weight} represents the connection cost (2.5D distance) and can be set after
 * construction via {@link #setWeight(double)}.
 * </p>
 */
public final class RoomEdge {

    private final Room a;
    private final Room b;
    private double weight;

    /**
     * Creates an undirected room edge with weight {@code 0.0}.
     *
     * @param a first room endpoint (must be non-null)
     * @param b second room endpoint (must be non-null)
     */
    public RoomEdge(Room a, Room b) {
        this(a, b, 0.0);
    }

    /**
     * Creates an undirected room edge with the given weight.
     * <p>
     * Endpoints are stored in a canonical order so that {@code (a,b)} and {@code (b,a)} are equivalent.
     * </p>
     *
     * @param a      first room endpoint (must be non-null)
     * @param b      second room endpoint (must be non-null)
     * @param weight initial edge weight (cost)
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    public RoomEdge(Room a, Room b, double weight) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        if (System.identityHashCode(a) <= System.identityHashCode(b)) {
            this.a = a;
            this.b = b;
        } else {
            this.a = b;
            this.b = a;
        }
        this.weight = weight;
    }

    public Room getA() { return a; }

    public Room getB() { return b; }

    public double getWeight() { return weight; }

    public void setWeight(double w) { this.weight = w; }

    /**
     * Identity-based equality for undirected edges.
     * <p>
     * Two edges are equal if and only if they reference the exact same {@link Room} instances (by reference)
     * after canonical ordering.
     * </p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomEdge e)) return false;
        return a == e.a && b == e.b;
    }

    /**
     * Identity-based hash code consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(a), System.identityHashCode(b));
    }
}