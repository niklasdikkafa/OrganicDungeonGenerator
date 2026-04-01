package com.dungeon.logic.placement.corridor.network.graph;

/**
 * Directed adjacency entry for the {@link CorridorGraph}.
 * <p>
 * The corridor graph is stored as an adjacency list. Each undirected logical connection is represented
 * by two {@code GraphEdge} instances: one for {@code a -> b} and one for {@code b -> a}.
 * </p>
 *
 * <p>
 * Edges are intentionally lightweight (only store node ids) because the graph is primarily used for:
 * </p>
 * <ul>
 *   <li>degree / neighborhood queries</li>
 *   <li>smoothing passes over node positions</li>
 *   <li>frame/junction construction based on local connectivity</li>
 * </ul>
 */
public final class GraphEdge {

    /** Source node id (index into {@code CorridorGraph.nodes}). */
    public final int from;

    /** Target node id (index into {@code CorridorGraph.nodes}). */
    public final int to;

    /**
     * Creates a directed edge from {@code from} to {@code to}.
     *
     * @param from source node id
     * @param to   target node id
     */
    public GraphEdge(int from, int to) {
        this.from = from;
        this.to = to;
    }
}