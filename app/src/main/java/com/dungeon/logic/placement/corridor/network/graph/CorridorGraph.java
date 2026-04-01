package com.dungeon.logic.placement.corridor.network.graph;

import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global corridor centerline graph built from all {@link com.dungeon.logic.placement.corridor.network.path.CorridorPath}s.
 * <p>
 * The graph uses <b>semantic node reuse</b>: identical path points (same {@link NodeKey}) resolve to the same
 * {@link GraphNode} instance across all corridors. This prevents duplicate nodes at shared voxel centers or shared
 * edge-midpoints and keeps the network topologically consistent for later steps (smoothing, frame building, junction
 * construction, mesh generation).
 * </p>
 *
 * <h2>Storage model</h2>
 * <ul>
 *   <li>{@link #nodes} stores nodes by id (index == node id).</li>
 *   <li>{@link #adjacency} is an adjacency list mapping {@code nodeId -> outgoing edges}.</li>
 *   <li>Undirected connections are represented by two directed {@link GraphEdge} entries ({@code a->b} and {@code b->a}).</li>
 * </ul>
 *
 * <h2>Deduplication</h2>
 * <p>
 * Node reuse is implemented via {@link #nodeByKey}. Edge deduplication is implemented via {@link #edgeMap}
 * using the canonical undirected {@link #edgeKey(int, int)}. This guarantees that repeated insertions of the same
 * undirected edge do not create duplicate adjacency entries.
 * </p>
 */
public final class CorridorGraph {

    /** All graph nodes. The index in this list equals the node id. */
    public final List<GraphNode> nodes = new ArrayList<>();

    /**
     * Adjacency list: maps a node id to its outgoing edges.
     * <p>
     * For an undirected edge (a,b), both adjacency lists contain one entry (a->b) and (b->a).
     * </p>
     */
    public final Map<Integer, List<GraphEdge>> adjacency = new HashMap<>();

    /** Semantic node reuse: maps a {@link NodeKey} to an existing node id. */
    private final Map<NodeKey, Integer> nodeByKey = new HashMap<>();

    /** Undirected edge deduplication: maps {@link #edgeKey(int, int)} to one representative directed edge. */
    private final Map<Long, GraphEdge> edgeMap = new HashMap<>();

    /**
     * Returns the id of an existing node with the same semantic identity as {@code p},
     * or creates a new node if none exists.
     * <p>
     * If the node already exists, the given {@code corridorIndex} is recorded in
     * {@link GraphNode#corridorIndices}.
     * </p>
     *
     * @param p path point describing the semantic identity (kind/poly ids/z-band)
     * @param corridorIndex index of the corridor that contributed this point
     * @return node id in {@link #nodes}
     */
    public int getOrCreateNodeBySemanticKey(PathPoint3D p, int corridorIndex) {
        NodeKey key = NodeKey.from(p);

        Integer existing = nodeByKey.get(key);
        if (existing != null) {
            nodes.get(existing).corridorIndices.add(corridorIndex);
            return existing;
        }

        int id = nodes.size();
        GraphNode n = new GraphNode(id, p.position.clone(), p.kind, p.zBand, key);
        n.corridorIndices.add(corridorIndex);

        nodes.add(n);
        adjacency.put(id, new ArrayList<>());
        nodeByKey.put(key, id);
        return id;
    }

    /**
     * Adds an undirected edge between two node ids if it does not already exist.
     * <p>
     * Internally, this inserts two directed edges into {@link #adjacency}:
     * {@code a->b} and {@code b->a}. The undirected pair is deduplicated using {@link #edgeKey(int, int)}.
     * </p>
     *
     * @param a first node id
     * @param b second node id
     */
    public void addUndirectedEdge(int a, int b) {
        if (a == b) return;
        long key = edgeKey(a, b);
        if (edgeMap.containsKey(key)) return;

        GraphEdge ab = new GraphEdge(a, b);
        GraphEdge ba = new GraphEdge(b, a);

        adjacency.get(a).add(ab);
        adjacency.get(b).add(ba);

        edgeMap.put(key, ab);
    }

    /**
     * Returns the (outgoing) degree of a node.
     * <p>
     * Because undirected edges are stored as two directed edges, this value is equivalent to the usual
     * undirected degree.
     * </p>
     *
     * @param nodeId node id
     * @return number of adjacent neighbors (0 if node id has no adjacency entry)
     */
    public int degree(int nodeId) {
        return adjacency.getOrDefault(nodeId, List.of()).size();
    }

    /**
     * Computes a canonical undirected edge key for the pair {@code (a,b)}.
     * <p>
     * The smaller id is stored in the high 32 bits and the larger id in the low 32 bits, so
     * {@code edgeKey(a,b) == edgeKey(b,a)}.
     * </p>
     *
     * @param a node id
     * @param b node id
     * @return canonical undirected key for use in hash maps/sets
     */
    public static long edgeKey(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return (((long) lo) << 32) | (hi & 0xffffffffL);
    }
}