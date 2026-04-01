package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.graph.GraphEdge;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import java.util.List;
import java.util.Map;

import static com.dungeon.config.DungeonConfig.*;
import static com.dungeon.logic.geometry.Utilities.*;

/**
 * Builds per-node corridor frames (tangent/normal/binormal + inner/outer profile corners)
 * for a {@link CorridorNetwork}.
 *
 * <p>The builder operates on the global {@link CorridorGraph} and produces a single local frame
 * for each graph node that is <em>frame-enabled</em> (i.e. {@link GraphNode#frameDisabled} is {@code false}).</p>
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li><b>Reset</b> all frame vectors on every node.</li>
 *   <li><b>Orient tangents</b> consistently along degree-≤2 chains:
 *       for a chain segment {@code prev -- node -- succ} (degree 2), the tangent at {@code node}
 *       points from {@code prev} to {@code succ} (projected to XZ).
 *       For degree 1 endpoints, the tangent points from the neighbor towards the endpoint.</li>
 *   <li><b>Endpoint correction</b>: for room endpoints (degree 1), rotate the tangent in small yaw steps
 *       until the inner frame points (left/right) lie inside the room’s inner footprint.
 *       This avoids corridor frames “sticking out” of small rooms.</li>
 *   <li><b>Profile generation</b>: compute the horizontal normal as a perpendicular vector to the tangent
 *       in XZ and write inner/outer frame corner points using {@link com.dungeon.config.DungeonConfig#CORRIDOR_WIDTH},
 *       {@link com.dungeon.config.DungeonConfig#CORRIDOR_HEIGHT} and {@link com.dungeon.config.DungeonConfig#WALL_THICKNESS}.</li>
 * </ol>
 *
 * <p><b>Notes</b>:</p>
 * <ul>
 *   <li>All orientation is computed in the XZ plane (Y is treated as up-axis and not used for heading).</li>
 *   <li>Junction nodes (degree >= 3) are expected to have {@link GraphNode#frameDisabled} set by the
 *       network builder and are skipped here.</li>
 *   <li>Geometry tests for endpoint correction use JTS polygons via {@link com.dungeon.logic.geometry.Utilities#toJtsPolygon(List)}
 *       and {@link com.dungeon.logic.geometry.Utilities#pointInPolygonContains(List, Vector2f)}.</li>
 * </ul>
 */
class FrameBuilder {

    /** Half of the inner corridor width used for inner frame corner offsets. */
    static final float HALF_INNER = CORRIDOR_WIDTH * 0.5f;

    /** Half of the outer corridor width (inner half-width + wall thickness). */
    static final float HALF_OUTER = HALF_INNER + WALL_THICKNESS;

    /**
     * Computes tangents and profile corner points for all eligible nodes in {@code net.graph}.
     *
     * @param net corridor network containing the global graph and node positions
     * @param roomsById lookup map used for endpoint tangent correction
     */
    static void buildFrames(CorridorNetwork net, Map<Integer, Room> roomsById) {
        CorridorGraph g = net.graph;

        resetFrames(g);
        orientTangents(g);
        adjustEndpointTangentsToFitRoom(g, roomsById);
        buildProfilePoints(g);
    }

    // =========================================================================================
    // Step 1: Reset
    // =========================================================================================

    /**
     * Resets all per-node frame vectors (tangent/normal/binormal) to zero.
     * Profile corner points are not reset here and will be overwritten during {@link #buildProfilePoints(CorridorGraph)}.
     */
    private static void resetFrames(CorridorGraph g) {
        for (GraphNode n : g.nodes) {
            n.tangent.set(0, 0, 0);
            n.normal.set(0, 0, 0);
            n.binormal.set(0, 0, 0);
        }
    }

    // =========================================================================================
    // Step 2: Tangents oriented along traversal direction
    // =========================================================================================

    /**
     * Orients tangents consistently along all degree-<=2 chains in the graph.
     * The traversal starts from junctions first and then from remaining degree-1 endpoints.
     */
    private static void orientTangents(CorridorGraph g) {
        boolean[] visited = new boolean[g.nodes.size()];
        traverseFromJunctions(g, visited);
        traverseFromEndpoints(g, visited);
    }

    /**
     * Traverses chains starting from each junction (degree >= 3) and sets tangents on the neighboring chains.
     */
    private static void traverseFromJunctions(CorridorGraph g, boolean[] visited) {
        for (GraphNode jn : g.nodes) {
            if (jn == null || g.degree(jn.id) < 3) continue;

            for (GraphEdge e : g.adjacency.getOrDefault(jn.id, List.of())) {
                if (!visited[e.to]) traverseChain(g, visited, jn.id, e.to);
            }
        }
    }

    /**
     * Traverses remaining chains starting from degree-1 nodes that are frame-enabled.
     */
    private static void traverseFromEndpoints(CorridorGraph g, boolean[] visited) {
        for (GraphNode ep : g.nodes) {
            if (ep == null || ep.frameDisabled || g.degree(ep.id) != 1) continue;

            List<GraphEdge> adj = g.adjacency.get(ep.id);
            if (adj == null || adj.isEmpty()) continue;

            int nbId = adj.getFirst().to;
            if (!visited[ep.id] && !visited[nbId]) traverseChain(g, visited, ep.id, nbId);
        }
    }

    /**
     * Walks a degree-<=2 chain starting at {@code seedId -> startId} and assigns tangents.
     *
     * <p>Tangent rules:</p>
     * <ul>
     *   <li>If {@code node} has degree 2, tangent is {@code prev -> succ} in XZ.</li>
     *   <li>If {@code node} has degree 1, tangent is {@code prev -> node} in XZ.</li>
     *   <li>The traversal stops when reaching degree 0, degree 1, degree >= 3 (junctions), or a visited successor.</li>
     * </ul>
     */
    private static void traverseChain(CorridorGraph g, boolean[] visited, int seedId, int startId) {
        GraphNode prev = g.nodes.get(seedId);
        GraphNode node = g.nodes.get(startId);

        // if start at endpoint -> give the endpoint a tangent first
        if (g.degree(seedId) == 1) {
            prev.tangent.set(
                    node.position.x - prev.position.x, 0f,
                    node.position.z - prev.position.z
            );
            prev.tangent.normalizeLocal();
        }

        while (node != null) {
            visited[node.id] = true;

            int deg = g.degree(node.id);
            if (deg <= 0 || deg >= 3) break;

            if (deg == 1) {
                node.tangent.set(
                        node.position.x - prev.position.x, 0f,
                        node.position.z - prev.position.z
                );
                node.tangent.normalizeLocal();
                break;
            }

            int succId = pickOtherNeighbor(g, node.id, prev.id);
            if (succId < 0) { // arrived endpoint with degree 1
                node.tangent.set(
                        node.position.x - prev.position.x, 0f,
                        node.position.z - prev.position.z
                );
                node.tangent.normalizeLocal();
                break;
            }

            GraphNode succ = g.nodes.get(succId);

            node.tangent.set( // default tangent points from prev to succ in XZ
                    succ.position.x - prev.position.x, 0f,
                    succ.position.z - prev.position.z
            );
            node.tangent.normalizeLocal();

            if (visited[succ.id]) break; // stop if successor was already visited (e.g. another junction)

            prev = node;
            node = succ;
        }
    }

    /**
     * Returns the neighbor id of {@code nodeId} that is not {@code excludeId}.
     *
     * @return neighbor id, or {@code -1} if no other neighbor exists
     */
    private static int pickOtherNeighbor(CorridorGraph g, int nodeId, int excludeId) {
        for (GraphEdge e : g.adjacency.getOrDefault(nodeId, List.of())) {
            if (e.to != excludeId) return e.to;
        }
        return -1;
    }

    // =========================================================================================
    // Step 3: Endpoint tangent correction to fit into rooms
    // =========================================================================================

    /**
     * For endpoint nodes (degree 1) that represent room connections, this method ensures that the
     * inner left/right frame points lie inside the room polygon. If the initial tangent would place
     * those points outside, the tangent is rotated in yaw steps (±5°, ±10°, ... up to ±90°).
     */
    private static void adjustEndpointTangentsToFitRoom(CorridorGraph g, Map<Integer, Room> roomsById) {
        if (roomsById == null || roomsById.isEmpty()) return;

        final float halfInner = CORRIDOR_WIDTH * 0.5f;

        for (GraphNode n : g.nodes) {
            if (n == null || n.frameDisabled) continue;
            if (!n.isEndpoint || g.degree(n.id) != 1) continue;

            Room room = roomsById.get(n.roomId);
            if (room == null) continue;

            List<Vector2f> poly = room.getInnerCorners();
            if (poly == null || poly.size() < 3) continue;

            Vector3f t = new Vector3f(n.tangent.x, 0f, n.tangent.z);
            if (t.lengthSquared() < 1e-8f) continue;
            t.normalizeLocal();

            if (framePointsInsideRoom(n.position, t, halfInner, poly)) continue;

            for (int deg = 5; deg <= 90; deg += 5) {
                Vector3f right = rotateYaw(t, +deg);
                if (framePointsInsideRoom(n.position, right, halfInner, poly)) {
                    n.tangent.set(right);
                    break;
                }

                Vector3f left = rotateYaw(t, -deg);
                if (framePointsInsideRoom(n.position, left, halfInner, poly)) {
                    n.tangent.set(left);
                    break;
                }
            }
        }
    }

    /**
     * Checks whether the inner frame points (left/right) for a given tangent at {@code center}
     * are inside the given room polygon.
     */
    private static boolean framePointsInsideRoom(Vector3f center, Vector3f tangent,
                                                 float halfInner, List<Vector2f> poly) {
        Vector3f t = new Vector3f(tangent.x, 0f, tangent.z).normalizeLocal();
        Vector3f normal = new Vector3f(-t.z, 0f, t.x);

        Vector2f left  = new Vector2f(center.x + normal.x * halfInner, center.z + normal.z * halfInner);
        Vector2f right = new Vector2f(center.x - normal.x * halfInner, center.z - normal.z * halfInner);

        return pointInPolygonContains(poly, left) && pointInPolygonContains(poly, right);
    }

    /**
     * Rotates a tangent vector around the Y axis by {@code degrees} (yaw), returning a normalized vector.
     */
    private static Vector3f rotateYaw(Vector3f t, float degrees) {
        float rad = degrees * FastMath.DEG_TO_RAD;
        float cos = FastMath.cos(rad);
        float sin = FastMath.sin(rad);
        return new Vector3f(
                t.x * cos - t.z * sin,
                0f,
                t.x * sin + t.z * cos
        ).normalizeLocal();
    }

    // =========================================================================================
    // Step 4: Profile points from tangent
    // =========================================================================================

    /**
     * Computes {@link GraphNode#normal} and {@link GraphNode#binormal} from the node tangent
     * (XZ-only) and writes the inner/outer profile corner points.
     */
    private static void buildProfilePoints(CorridorGraph g) {
        for (GraphNode n : g.nodes) {
            if (n == null || n.frameDisabled) continue;

            Vector3f t = new Vector3f(n.tangent.x, 0f, n.tangent.z);
            if (t.lengthSquared() < 1e-8f) t.set(1f, 0f, 0f);
            else t.normalizeLocal();

            Vector3f normal = new Vector3f(-t.z, 0f, t.x);
            n.normal.set(normal);
            n.binormal.set(Vector3f.UNIT_Y);

            writeFramePoints(n, normal);
        }
    }

    /**
     * Writes the inner and outer profile corner points of a node using the provided horizontal normal.
     * The resulting points define a rectangular corridor cross-section centered at {@link GraphNode#position}
     * with height {@link com.dungeon.config.DungeonConfig#CORRIDOR_HEIGHT} and wall thickness
     * {@link com.dungeon.config.DungeonConfig#WALL_THICKNESS}.
     *
     * @param n graph node to write into
     * @param normal horizontal (XZ) normal pointing to the "left" side of the corridor cross-section
     */
    public static void writeFramePoints(GraphNode n, Vector3f normal) {
        Vector3f c = n.position;
        float yBottom = c.y - CORRIDOR_HEIGHT * 0.5f;
        float yTop    = c.y + CORRIDOR_HEIGHT * 0.5f;

        n.innerLeftBottom.set (c.x + normal.x * HALF_INNER, yBottom, c.z + normal.z * HALF_INNER);
        n.innerLeftTop.set    (c.x + normal.x * HALF_INNER, yTop,    c.z + normal.z * HALF_INNER);
        n.innerRightBottom.set(c.x - normal.x * HALF_INNER, yBottom, c.z - normal.z * HALF_INNER);
        n.innerRightTop.set   (c.x - normal.x * HALF_INNER, yTop,    c.z - normal.z * HALF_INNER);

        n.outerLeftBottom.set (c.x + normal.x * HALF_OUTER, yBottom - WALL_THICKNESS, c.z + normal.z * HALF_OUTER);
        n.outerLeftTop.set    (c.x + normal.x * HALF_OUTER, yTop    + WALL_THICKNESS, c.z + normal.z * HALF_OUTER);
        n.outerRightBottom.set(c.x - normal.x * HALF_OUTER, yBottom - WALL_THICKNESS, c.z - normal.z * HALF_OUTER);
        n.outerRightTop.set   (c.x - normal.x * HALF_OUTER, yTop    + WALL_THICKNESS, c.z - normal.z * HALF_OUTER);
    }
}