package com.dungeon.logic.placement.corridor.network.path;

import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.jme3.math.Vector3f;

/**
 * Snapshot of a corridor graph node used for per-path sampling and mesh generation.
 * <p>
 * {@code PathFrameSample} is produced by the corridor network build step by iterating the
 * {@code CorridorPath.nodeIds} list and copying the relevant frame/profile data from the
 * referenced graph node. This keeps mesh builders independent of the global graph data
 * structures and allows paths to carry all geometry-relevant information they need.
 * </p>
 *
 * <h2>What this contains</h2>
 * <ul>
 *   <li><b>Identity:</b> {@link #graphNodeId} points back to the global graph node.</li>
 *   <li><b>Classification:</b> {@link #kind}, {@link #zBand}, and flags for junction/endpoint samples.</li>
 *   <li><b>Frame:</b> {@link #tangent}, {@link #normal}, {@link #binormal} (orientation basis).</li>
 *   <li><b>Profile corners:</b> inner/outer rectangle corners (bottom/top, left/right) used to build meshes.</li>
 * </ul>
 *
 * <p>
 * Note: The vectors in this class are mutable and typically populated by copying values from a
 * {@code GraphNode}. The {@link #position} reference is stored as provided by the caller (no defensive copy).
 * </p>
 */
public final class PathFrameSample {

    /** ID of the global corridor graph node this sample was copied from. */
    public final int graphNodeId;

    /** World-space sample position (the graph node position). */
    public final Vector3f position;

    /** Semantic point type (e.g., room center, voxel center, edge midpoint). */
    public PointKind kind;

    /** Discrete vertical band index for this sample. */
    public short zBand;

    /**
     * Whether a frame/profile is considered invalid or intentionally disabled at this sample
     * (e.g. at junctions).
     */
    public boolean frameDisabled;

    /** {@code true} if this sample corresponds to a junction node (degree >= 3). */
    public boolean isJunctionSample;

    /** {@code true} if this sample corresponds to a room endpoint node. */
    public boolean isRoomEndpointSample;

    /** Tangent direction of the corridor at this sample (typically normalized in XZ). */
    public final Vector3f tangent = new Vector3f();

    /** Horizontal normal direction at this sample (perpendicular to {@link #tangent} in XZ). */
    public final Vector3f normal = new Vector3f();

    /** Binormal direction (typically {@code (0,1,0)} for an upright corridor frame). */
    public final Vector3f binormal = new Vector3f(0, 1, 0);

    // ---------------- inner profile corners ----------------

    /** Inner profile: left-bottom corner (walkable interior). */
    public final Vector3f innerLeftBottom = new Vector3f();

    /** Inner profile: left-top corner (walkable interior). */
    public final Vector3f innerLeftTop = new Vector3f();

    /** Inner profile: right-bottom corner (walkable interior). */
    public final Vector3f innerRightBottom = new Vector3f();

    /** Inner profile: right-top corner (walkable interior). */
    public final Vector3f innerRightTop = new Vector3f();

    // ---------------- outer profile corners ----------------

    /** Outer profile: left-bottom corner (including wall thickness). */
    public final Vector3f outerLeftBottom = new Vector3f();

    /** Outer profile: left-top corner (including wall thickness). */
    public final Vector3f outerLeftTop = new Vector3f();

    /** Outer profile: right-bottom corner (including wall thickness). */
    public final Vector3f outerRightBottom = new Vector3f();

    /** Outer profile: right-top corner (including wall thickness). */
    public final Vector3f outerRightTop = new Vector3f();

    /**
     * Creates a new sample container for a given graph node.
     *
     * @param graphNodeId id of the referenced global graph node
     * @param position world-space position for the sample (typically copied/cloned by the caller)
     */
    public PathFrameSample(int graphNodeId, Vector3f position) {
        this.graphNodeId = graphNodeId;
        this.position = position;
    }
}
