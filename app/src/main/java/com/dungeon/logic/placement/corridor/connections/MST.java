package com.dungeon.logic.placement.corridor.connections;

import com.dungeon.domain.Room;
import org.jgrapht.Graph;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes a Minimum Spanning Tree (MST) over a set of {@link Room}s using candidate {@link RoomEdge}s.
 * <p>
 * This utility builds an undirected weighted graph (JGraphT) where rooms are vertices and the provided
 * candidate edges represent possible connections with weights (typically distance-based costs).
 * It then runs Kruskal's algorithm to obtain a minimum spanning tree.
 * </p>
 *
 * <h2>Input expectations</h2>
 * <ul>
 *   <li>{@code rooms} should contain all rooms that must be connected.</li>
 *   <li>{@code edges} is the candidate set (from Delaunay triangulation).</li>
 *   <li>Each {@link RoomEdge} must have its weight set before calling this method.</li>
 * </ul>
 *
 * <h2>Duplicate edges</h2>
 * <p>
 * If multiple candidate {@link RoomEdge}s connect the same pair of rooms, this implementation keeps
 * the smallest weight for that undirected pair.
 * </p>
 *
 * <h2>Disconnected graphs</h2>
 * <p>
 * If the candidate edge set is insufficient to connect all rooms, JGraphT will compute a minimum
 * spanning <em>forest</em> (i.e., one MST per connected component). In that case, the returned list
 * contains fewer than {@code rooms.size() - 1} edges.
 * </p>
 */
public final class MST {

    /**
     * Computes the minimum spanning tree (or forest) over the given rooms using the given candidate edges.
     *
     * @param rooms the rooms to use as vertices
     * @param edges candidate undirected edges between rooms (must have weights assigned)
     * @return a list of {@link RoomEdge}s forming the MST; may be smaller than {@code rooms.size() - 1}
     *         if the graph is disconnected
     */
    public static List<RoomEdge> minimumSpanningTree(List<Room> rooms, List<RoomEdge> edges) {
        Graph<Room, DefaultWeightedEdge> g =
                new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

        // Vertices
        for (Room r : rooms) g.addVertex(r);

        // Edges + weights
        for (RoomEdge e : edges) {
            Room a = e.getA();
            Room b = e.getB();

            DefaultWeightedEdge ge = g.getEdge(a, b);
            if (ge == null) {
                ge = g.addEdge(a, b);
                if (ge != null) g.setEdgeWeight(ge, e.getWeight());
            } else {
                double wOld = g.getEdgeWeight(ge);
                if (e.getWeight() < wOld) g.setEdgeWeight(ge, e.getWeight());
            }
        }

        // MST (Kruskal)
        var mst = new KruskalMinimumSpanningTree<>(g).getSpanningTree();

        // Convert back to room edge
        List<RoomEdge> out = new ArrayList<>(mst.getEdges().size());
        for (DefaultWeightedEdge ge : mst.getEdges()) {
            Room a = g.getEdgeSource(ge);
            Room b = g.getEdgeTarget(ge);

            RoomEdge re = new RoomEdge(a, b);
            re.setWeight(g.getEdgeWeight(ge));
            out.add(re);
        }
        return out;
    }
}