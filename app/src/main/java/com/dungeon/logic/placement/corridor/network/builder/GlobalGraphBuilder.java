package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.geometry.Utilities;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import java.util.Map;

/**
 * Builds the global corridor graph for a {@link CorridorNetwork} by merging all {@link CorridorPath}s
 * into a single node/edge structure with strict semantic node reuse.
 *
 * <p>Each {@link PathPoint3D} is mapped to a {@link GraphNode} via a semantic key
 * (see {@code CorridorGraph.getOrCreateNodeBySemanticKey}). If multiple corridor paths contain
 * semantically identical points (same voxel center / edge midpoint / room center at the same height),
 * they will share the same graph node.</p>
 *
 * <h2>Endpoint handling</h2>
 * <p>The first and last raw point of each path are marked as room endpoints using
 * {@link GraphNode#markAsEndpoint(int)}. Endpoints may require special positioning:</p>
 * <ul>
 *   <li>If the computed path endpoint position (usually the routing polygon center) lies inside the room
 *       footprint, it is kept.</li>
 *   <li>If it lies outside the room footprint (which can happen for very small/organic rooms where the
 *       routing polygon center is not guaranteed to be inside the room), the node position is replaced
 *       with the room's {@code interiorPoint} in 3D.</li>
 * </ul>
 *
 * <h2>Edges</h2>
 * <p>Edges are inserted between consecutive graph node ids along each path. Consecutive duplicates
 * within a single path are skipped to avoid zero-length segments and redundant edges.</p>
 */
class GlobalGraphBuilder {

    /**
     * Populates {@code net.graph} from {@code net.paths} and updates each path's {@link CorridorPath#nodeIds}
     * to reference the created/reused graph nodes.
     *
     * @param net corridor network containing raw paths and the global graph instance
     * @param roomsById map from room id to {@link Room}, used for endpoint validation and fallback positioning
     */
    static void buildGlobalGraph(CorridorNetwork net, Map<Integer, Room> roomsById) {
        for (CorridorPath path : net.paths) {
            path.nodeIds.clear();

            Integer prev = null;
            for (int i = 0; i < path.rawPoints.size(); i++) {
                PathPoint3D p = path.rawPoints.get(i);

                int nodeId = net.graph.getOrCreateNodeBySemanticKey(p, path.corridorIndex);

                // Mark path endpoints and optionally relocate them into the room footprint.
                if (i == 0) {
                    GraphNode n = net.graph.nodes.get(nodeId);
                    n.markAsEndpoint(path.fromRoom);
                    if (!polyCenterInPolygon(p, path.fromRoom, roomsById)) {
                        n.position = getRoomInteriorPoint3D(path.fromRoom, roomsById, n.position);
                    }
                }
                if (i == path.rawPoints.size() - 1) {
                    GraphNode n = net.graph.nodes.get(nodeId);
                    n.markAsEndpoint(path.toRoom);
                    if (!polyCenterInPolygon(p, path.toRoom, roomsById)) {
                        n.position = getRoomInteriorPoint3D(path.toRoom, roomsById, n.position);
                    }
                }

                // Remove consecutive duplicates inside one path.
                if (!path.nodeIds.isEmpty() && path.nodeIds.getLast() == nodeId) {
                    continue;
                }

                path.nodeIds.add(nodeId);

                if (prev != null && prev != nodeId) {
                    net.graph.addUndirectedEdge(prev, nodeId);
                }
                prev = nodeId;
            }
        }
    }

    /**
     * Checks whether the 2D projection (x,z) of a path point lies inside the room's inner footprint.
     * This is used to decide whether an endpoint can stay at the routing polygon center or must be
     * snapped to the room's interior point.
     *
     * @param p path point that has to be checked
     * @param roomId ID of the room to check
     * @param roomsById maps the room IDs to the specific room objects
     * @return {@code true} if point is inside of room, otherwise {@code false}
     */
    private static boolean polyCenterInPolygon(PathPoint3D p, int roomId, Map<Integer, Room> roomsById) {
        Room r = roomsById.get(roomId);
        return Utilities.pointInPolygonContains(r.getInnerCorners(), new Vector2f(p.position.x, p.position.z));
    }

    /**
     * Converts a room's 2D interior point into a 3D position at corridor mid-height for the room's z-band.
     */
    private static Vector3f getRoomInteriorPoint3D(int roomId, Map<Integer, Room> roomsById, Vector3f currentPos) {
        Room room = roomsById.get(roomId);
        Vector2f interiorPoint = room.getInteriorPoint();
        return new Vector3f(interiorPoint.x, currentPos.y, interiorPoint.y);
    }
}