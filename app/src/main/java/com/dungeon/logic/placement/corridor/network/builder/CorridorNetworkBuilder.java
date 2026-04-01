package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.placement.corridor.network.*;
import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.graph.GraphEdge;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathFrameSample;
import com.jme3.math.Vector3f;

import java.util.*;
import java.util.logging.Formatter;
import java.util.logging.*;

import static com.dungeon.config.DungeonConfig.*;
import static com.dungeon.logic.placement.corridor.network.builder.CorridorPathBuilder.buildPath;
import static com.dungeon.logic.placement.corridor.network.builder.FrameBuilder.buildFrames;
import static com.dungeon.logic.placement.corridor.network.builder.GlobalGraphBuilder.buildGlobalGraph;
import static com.dungeon.logic.placement.corridor.network.builder.JunctionBuilder.buildJunctionCornerLinks;

/**
 * Builds a connected 3D corridor centerline network ({@link CorridorNetwork}) from routed {@link Corridor} objects and
 * the set of generated {@link Room}s.
 *
 * <p>The resulting network is the <b>bridge</b> between pathfinding and mesh generation:
 * it converts corridor polylines into a shared global graph with stable node identities, enriches graph nodes with
 * cross-section frame data, and derives additional junction information needed for building watertight corridor meshes.</p>
 *
 * <h2>High-level pipeline</h2>
 * <ol>
 *   <li><b>Path sampling:</b> Convert each {@link Corridor} into a {@link CorridorPath} containing a 3D polyline
 *       (including stair interpolation) that follows the routing grid.</li>
 *   <li><b>Global graph construction:</b> Build one {@link CorridorGraph} for all paths using strict semantic node reuse
 *       (no duplicate nodes at shared voxel centers / edge midpoints).</li>
 *   <li><b>Node classification:</b> Mark junction nodes (degree >= 3) and disable frame computation where appropriate.</li>
 *   <li><b>Graph smoothing:</b> Apply a small number of synchronous smoothing iterations in the horizontal plane (XZ).
 *       Endpoint nodes are kept fixed.</li>
 *   <li><b>Frame computation:</b> Build one corridor cross-section frame per eligible node (degree <= 2, non-junction)
 *       using {@link FrameBuilder}. These frames define inner/outer profile points required for mesh extrusion.</li>
 *   <li><b>Junction geometry preparation:</b> Compute junction portal links and corner geometry via
 *       {@link JunctionBuilder}, using {@code routingGridEdgeLength} as a scale reference.</li>
 *   <li><b>Per-path sampling:</b> Create {@link PathFrameSample}s for each corridor path by referencing the global graph
 *       nodes and copying their computed frame/profile data.</li>
 * </ol>
 *
 * <h2>Configuration and tuning</h2>
 * <p>Smoothing behavior is controlled by {@link #SMOOTH_ITERATIONS} and {@link #SMOOTH_ALPHA_XY}.
 * Corridor dimensions are taken from {@code DungeonConfig} (e.g. {@code CORRIDOR_WIDTH}, {@code CORRIDOR_HEIGHT},
 * {@code WALL_THICKNESS}).</p>
 *
 * <p><b>Important:</b> This builder assumes that the corridor routing stage produced consistent polylines and that
 * {@link GlobalGraphBuilder} correctly assigns endpoints to room centers. Extreme parameter changes (e.g. very small
 * rooms or very large corridor cross-sections) can expose geometric edge cases in junction construction and mesh union.</p>
 */
public final class CorridorNetworkBuilder {

    // ---------------- logger ----------------

    private static final Logger LOGGER = Logger.getLogger(CorridorNetworkBuilder.class.getName());
    private static boolean LOGGER_CONFIGURED = false;

    // ---------------- smoothing ----------------
    private static final int SMOOTH_ITERATIONS = 5;
    private static final float SMOOTH_ALPHA_XY = 0.35f;

    /**
     * Builds a complete {@link CorridorNetwork} from routed {@link Corridor} objects and their corresponding {@link Room}s.
     *
     * <p>This is the main production entry point. It runs the full corridor-network pipeline:
     * corridor-to-path conversion, global graph construction with semantic node reuse, node classification (junctions),
     * smoothing, per-node frame generation, junction portal/corner generation, and per-path sampling.</p>
     *
     * @param corridors             routed corridor results (typically produced by the corridor routing stage)
     * @param rooms                 all rooms that may be referenced by the corridors (used e.g. for endpoints)
     * @param routingGridEdgeLength edge length of the routing grid used during pathfinding; used as a scale reference
     *                              for junction-related geometry heuristics
     * @return a fully built {@link CorridorNetwork} ready for corridor mesh generation
     * @throws NullPointerException if {@code corridors} is {@code null}
     */
    public static CorridorNetwork build(List<Corridor> corridors, List<Room> rooms, float routingGridEdgeLength) {
        Objects.requireNonNull(corridors, "corridors");
        configureLoggerOnce();

        LOGGER.log(Level.INFO, "Building corridor network...");
        CorridorNetwork net = new CorridorNetwork(CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS, routingGridEdgeLength);
        Map<Integer, Room> roomsById = buildRoomMap(rooms);
        LOGGER.log(Level.INFO, "  Building corridor paths...");
        // 1) Build raw paths (per corridor)
        for (int corridorIndex = 0; corridorIndex < corridors.size(); corridorIndex++) {
            Corridor c = corridors.get(corridorIndex);
            if (c == null) continue;

            CorridorPath path = buildPath(corridorIndex, c);
            if (path.rawPoints.size() < 2) continue;
            net.paths.add(path);
        }

        LOGGER.log(Level.INFO, "  Building global graph...");
        // 2) Build global graph (node reuse)
        buildGlobalGraph(net, roomsById);

        // 3) Mark junctions
        markJunctionNodes(net);

        LOGGER.log(Level.INFO, "  Smoothing graph...");
        // 4) Smooth (XZ only), synchronous per iteration
        smoothGraphXZInPlace(net);

        LOGGER.log(Level.INFO, "  Building graph node frames...");
        // 5) Frames ONCE per node (degree <= 2)
        buildFrames(net, roomsById);

        LOGGER.log(Level.INFO, "  Building junctions...");
        // 6) Junction neighbor/corner links
        buildJunctionCornerLinks(net, routingGridEdgeLength);

        // 7) Build per-path samples by referencing graph nodes (copy same frame/profile)
        buildSamplesFromGraph(net);

        LOGGER.log(Level.INFO, "Finished building corridor network.");

        return net;
    }

    /**
     * Builds a lookup map from room id to {@link Room}.
     *
     * <p>This is used by later pipeline stages to resolve endpoint nodes to room information
     * (e.g. placing endpoint node positions at the room's interior point).</p>
     *
     * @param rooms list of rooms; may be empty
     * @return map keyed by {@link Room#getId()}
     */
    private static Map<Integer, Room> buildRoomMap(List<Room> rooms) {
        Map<Integer, Room> roomsById = new HashMap<>();
        for (Room r : rooms) {
            roomsById.put(r.getId(), r);
        }
        return roomsById;
    }

    /**
     * Classifies graph nodes as junctions (if degree >= 3) after the global graph has been constructed.
     *
     * <p>Currently this method:
     * <ul>
     *   <li>Marks a node as a junction if its adjacency degree is {@code >= 3}.</li>
     *   <li>Disables frame computation for junction nodes (frames are only generated for degree {@code <= 2}).</li>
     * </ul>
     *
     *
     * @param net corridor network containing the global graph
     */
    private static void markJunctionNodes(CorridorNetwork net) {
        CorridorGraph g = net.graph;

        for (GraphNode n : g.nodes) {
            int deg = g.adjacency.get(n.id).size();

            n.isJunction = (deg >= 3);

            n.frameDisabled = n.isJunction;
        }
    }

    // ------------------------------------------------------------
    // Global smoothing (XZ only), synchronous
    // ------------------------------------------------------------

    /**
     * Applies iterative laplacian smoothing to the global corridor graph in the XZ plane.
     *
     * <p>Smoothing is performed synchronously per iteration:
     * first all new positions are computed into a temporary array, then applied in a second pass.
     * This prevents ordering artifacts.</p>
     *
     * <p>Endpoint nodes are kept fixed to preserve room attachments.
     * All other nodes are moved towards the weighted average of their neighbors.</p>
     * <p>If the node neighbors a junction node it will get moved away from the junction node to prevent
     * overlapping frames.</p>
     *
     * @param net corridor network whose graph node positions should be smoothed
     */
    private static void smoothGraphXZInPlace(CorridorNetwork net) {
        CorridorGraph g = net.graph;
        if (g.nodes.size() < 3) return;

        for (int it = 0; it < SMOOTH_ITERATIONS; it++) {
            Vector3f[] nextPos = new Vector3f[g.nodes.size()];
            for (GraphNode n : g.nodes) nextPos[n.id] = n.position.clone();

            for (GraphNode n : g.nodes) {
                if (n.isEndpoint) continue;

                List<GraphEdge> adj = g.adjacency.get(n.id);
                if (adj == null || adj.isEmpty()) continue;

                float sumX = 0f, sumZ = 0f;
                float wSum = 0f;

                for (GraphEdge e : adj) {
                    GraphNode nb = g.nodes.get(e.to);

                    float w = 1f;
                    if (nb.isJunction) {
                        w = 0.6f;
                    }

                    sumX += nb.position.x * w;
                    sumZ += nb.position.z * w;
                    wSum += w;
                }

                if (wSum < 1e-6f) continue;

                float avgX = sumX / wSum;
                float avgZ = sumZ / wSum;

                nextPos[n.id].x = n.position.x + SMOOTH_ALPHA_XY * (avgX - n.position.x);
                nextPos[n.id].z = n.position.z + SMOOTH_ALPHA_XY * (avgZ - n.position.z);
            }

            for (GraphNode n : g.nodes) n.position.set(nextPos[n.id]);
        }
    }

    // ------------------------------------------------------------
    // Build per-path samples from graph nodes (samples share node frames)
    // ------------------------------------------------------------

    /**
     * Builds per-path frame samples by referencing global graph nodes.
     *
     * <p>For each {@link CorridorPath}:
     * <ul>
     *   <li>Clears existing {@link PathFrameSample}s.</li>
     *   <li>Iterates the path's {@code nodeIds}.</li>
     *   <li>Copies the node position, classification flags (endpoint/junction), and the precomputed frame/profile points
     *       (inner/outer corners) into a new {@link PathFrameSample}.</li>
     * </ul>
     *
     * <p>This decouples the mesh builder from graph traversal: the mesh builder consumes samples in path order
     * while the graph remains the authoritative source for frame data.</p>
     *
     * @param net corridor network containing global graph nodes and per-path node id sequences
     */
    private static void buildSamplesFromGraph(CorridorNetwork net) {
        CorridorGraph g = net.graph;

        for (CorridorPath path : net.paths) {
            path.samples.clear();
            if (path.nodeIds.isEmpty()) continue;

            for (int nodeId : path.nodeIds) {
                GraphNode node = g.nodes.get(nodeId);

                PathFrameSample s = new PathFrameSample(nodeId, node.position.clone());
                s.kind = node.kind;
                s.zBand = node.zBand;

                s.isRoomEndpointSample = node.isEndpoint;
                s.isJunctionSample = node.isJunction;
                s.frameDisabled = node.frameDisabled;

                // Copy SINGLE node frame/profile into the sample
                s.tangent.set(node.tangent);
                s.normal.set(node.normal);
                s.binormal.set(node.binormal);

                s.innerLeftBottom.set(node.innerLeftBottom);
                s.innerLeftTop.set(node.innerLeftTop);
                s.innerRightBottom.set(node.innerRightBottom);
                s.innerRightTop.set(node.innerRightTop);

                s.outerLeftBottom.set(node.outerLeftBottom);
                s.outerLeftTop.set(node.outerLeftTop);
                s.outerRightBottom.set(node.outerRightBottom);
                s.outerRightTop.set(node.outerRightTop);

                path.samples.add(s);
            }
        }
    }

    // ----- debug -----
    public static CorridorNetwork buildFromPrebuiltPaths(List<CorridorPath> prebuiltPaths, Map<Integer, Room> roomsById, float routingGridEdgeLength) {
        Objects.requireNonNull(prebuiltPaths, "prebuiltPaths");

        CorridorNetwork net = new CorridorNetwork(CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS, routingGridEdgeLength);
        net.paths.addAll(prebuiltPaths);

        // 2) Build global graph (node reuse)
        buildGlobalGraph(net, roomsById);

        // 3) Mark junctions
        markJunctionNodes(net);

        // 4) Smooth (XZ only)
        smoothGraphXZInPlace(net);

        // 5) Frames per node (degree <= 2)
        buildFrames(net, roomsById);

        // 6) Junction neighbor/corner links
        buildJunctionCornerLinks(net, routingGridEdgeLength);

        // 7) Samples from graph nodes
        buildSamplesFromGraph(net);

        return net;
    }

    // --------------- logger config ---------------
    private static void configureLoggerOnce() {
        if (LOGGER_CONFIGURED) return;
        LOGGER_CONFIGURED = true;

        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);

        for (Handler h : LOGGER.getHandlers()) {
            LOGGER.removeHandler(h);
            try { h.close(); } catch (Exception ignored) {}
        }

        Handler h = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord r) {
                return "[CorridorNetworkBuilder] " + r.getLevel().getName() + ": "
                        + formatMessage(r) + System.lineSeparator();
            }
        }) {
            @Override public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };

        h.setLevel(Level.ALL);
        LOGGER.addHandler(h);
    }
}