package com.dungeon.logic.placement.corridor.network.graph;

import com.jme3.math.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * A single node in the global corridor centerline graph.
 * <p>
 * {@code GraphNode} instances are created during global graph construction and represent
 * semantically unique corridor path points (see {@link NodeKey}).
 * Nodes may be shared across multiple {@code CorridorPath}s; {@link #corridorIndices} tracks
 * which corridor instances reference the node.
 * </p>
 *
 * <h2>Semantic meaning</h2>
 * <ul>
 *   <li>{@link #kind} defines whether this node represents a routing voxel center, an edge midpoint,
 *       or a room center (endpoint).</li>
 *   <li>{@link #zBand} is the discrete vertical band index associated with this node.</li>
 *   <li>{@link #key} is the stable semantic key used for node reuse across paths.</li>
 * </ul>
 *
 * <h2>Special flags</h2>
 * <ul>
 *   <li>{@link #isEndpoint} marks nodes that correspond to a room connection point. If true,
 *       {@link #roomId} identifies the room.</li>
 *   <li>{@link #isJunction} marks nodes with high degree ({@code degree >= 3}).</li>
 *   <li>{@link #frameDisabled} indicates that no cross-section/frame should be generated for this node
 *       (e.g. at junctions or other special cases).</li>
 * </ul>
 *
 * <h2>Frame / profile data</h2>
 * <p>
 * During frame construction, each node may receive a local coordinate frame and precomputed profile
 * points that are later consumed by corridor mesh generation:
 * </p>
 * <ul>
 *   <li>{@link #tangent} is the local forward direction along the centerline (typically XZ only).</li>
 *   <li>{@link #normal} is the horizontal left/right direction derived from the tangent.</li>
 *   <li>{@link #binormal} is the up direction (usually {@code (0,1,0)} in this project).</li>
 *   <li>Inner/outer profile points store the rectangular corridor cross-section at this node.</li>
 * </ul>
 *
 * <p><b>Mutability note:</b> {@link #position} and all frame/profile vectors are mutable and may be
 * updated by smoothing, frame building, and junction processing.</p>
 */
public final class GraphNode {

    /** Stable node id (index in the graph's node list). */
    public final int id;

    /** World-space position of the node (may be smoothed/adjusted). */
    public Vector3f position;

    /** Semantic node kind (voxel center, edge midpoint, room center). */
    public final PointKind kind;

    /** Discrete vertical band index used for routing / reconstruction. */
    public final short zBand;

    /** Semantic identity key used for node reuse across corridor paths. */
    public final NodeKey key;

    /**
     * Room id associated with this node if it is a room endpoint; otherwise {@code -1}.
     * Set by {@link #markAsEndpoint(int)}.
     */
    public int roomId = -1;

    /** Indices of corridor paths that reference this node (useful for debugging and filtering). */
    public final Set<Integer> corridorIndices = new HashSet<>();

    /** True if this node is a room endpoint. */
    public boolean isEndpoint;

    /** True if this node is considered a junction ({@code degree >= 3}). */
    public boolean isJunction;

    /** If true, frame/profile generation should be skipped for this node. */
    public boolean frameDisabled;

    // -------------------------------------------------------------------------
    // Node-level frame (single frame per node)
    // -------------------------------------------------------------------------

    /** Local tangent direction along the corridor centerline. */
    public final Vector3f tangent = new Vector3f();

    /** Local horizontal normal direction (left/right), typically derived from {@link #tangent}. */
    public final Vector3f normal = new Vector3f();

    /** Local up direction (binormal). Default is +Y. */
    public final Vector3f binormal = new Vector3f(0, 1, 0);

    // -------------------------------------------------------------------------
    // Node-level profile points (single cross section per node)
    // -------------------------------------------------------------------------

    /** Inner (walkable) profile rectangle, bottom-left corner. */
    public final Vector3f innerLeftBottom = new Vector3f();
    /** Inner (walkable) profile rectangle, top-left corner. */
    public final Vector3f innerLeftTop = new Vector3f();
    /** Inner (walkable) profile rectangle, bottom-right corner. */
    public final Vector3f innerRightBottom = new Vector3f();
    /** Inner (walkable) profile rectangle, top-right corner. */
    public final Vector3f innerRightTop = new Vector3f();

    /** Outer (wall hull) profile rectangle, bottom-left corner. */
    public final Vector3f outerLeftBottom = new Vector3f();
    /** Outer (wall hull) profile rectangle, top-left corner. */
    public final Vector3f outerLeftTop = new Vector3f();
    /** Outer (wall hull) profile rectangle, bottom-right corner. */
    public final Vector3f outerRightBottom = new Vector3f();
    /** Outer (wall hull) profile rectangle, top-right corner. */
    public final Vector3f outerRightTop = new Vector3f();

    /**
     * Creates a new graph node.
     *
     * @param id       stable node id (usually list index)
     * @param position initial world-space position
     * @param kind     semantic kind of the node
     * @param zBand    discrete vertical band index
     * @param key      semantic identity key for node reuse
     */
    public GraphNode(int id, Vector3f position, PointKind kind, short zBand, NodeKey key) {
        this.id = id;
        this.position = position;
        this.kind = kind;
        this.zBand = zBand;
        this.key = key;
    }

    /**
     * Marks this node as a room endpoint and assigns the associated room id.
     *
     * @param roomId id of the room connected at this endpoint
     */
    public void markAsEndpoint(int roomId) {
        this.isEndpoint = true;
        this.roomId = roomId;
    }
}