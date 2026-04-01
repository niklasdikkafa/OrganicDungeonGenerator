package com.dungeon.logic.placement.corridor.network;

import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the generated corridor centerline network of a dungeon.
 * <p>
 * A {@code CorridorNetwork} is the primary intermediate representation between corridor routing and
 * mesh generation. It combines:
 * </p>
 * <ul>
 *   <li>a global {@link CorridorGraph} with semantic node reuse (shared nodes across paths),</li>
 *   <li>the original per-corridor {@link CorridorPath} inputs (raw points and derived samples),</li>
 *   <li>and derived junction information used to construct junction wall geometry.</li>
 * </ul>
 *
 * <h2>Coordinate system</h2>
 * <p>
 * The network uses the same world coordinate system as the generator:
 * horizontal movement happens in the XZ-plane and the vertical axis is Y.
 * </p>
 *
 * <h2>Geometry parameters</h2>
 * <p>
 * {@code corridorWidth}, {@code corridorHeight}, and {@code wallThickness} define the corridor cross-section
 * that mesh builders use to generate inner/outer shells. {@code routingCellSize} describes the effective
 * cell size of the routing grid that produced the network and can be used for scaling tolerances and
 * thresholds in junction/mesh logic.
 * </p>
 *
 * <h2>Junction portal links</h2>
 * <p>
 * {@link #junctionLinksByJunctionAndPortal} stores per-junction portal metadata (including ring ordering
 * and corner geometry). Keys are packed longs of {@code (junctionNodeId, portalNodeId)} as defined by the
 * corresponding builder (e.g. {@code CorridorNetworkBuilder.jpKey(...)}).
 * </p>
 *
 * <p>
 * This class is a simple immutable container regarding references (fields are {@code final}), but the
 * contained collections and objects ({@link #graph}, {@link #paths}, and map contents) are populated and
 * mutated by builder stages.
 * </p>
 */
public final class CorridorNetwork {
    /** Global corridor graph built from all {@link #paths}, with semantic node reuse. */
    public final CorridorGraph graph = new CorridorGraph();

    /** Per-corridor routed paths used to build {@link #graph} and mesh samples. */
    public final List<CorridorPath> paths = new ArrayList<>();

    /** Corridor inner width (wall-to-wall) used for frame/profile generation and mesh building. */
    public final float corridorWidth;

    /** Corridor interior height used for frame/profile generation and mesh building. */
    public final float corridorHeight;

    /** Wall thickness used to derive outer corridor geometry from inner geometry. */
    public final float wallThickness;

    /** Effective routing grid cell size used during pathfinding; useful for scaling tolerances. */
    public final float routingCellSize;

    /**
     * Junction portal link metadata keyed by a packed (junctionNodeId, portalNodeId).
     * Provides portal ring order and precomputed corner geometry for junction mesh construction.
     */
    public final Map<Long, JunctionPortalLink> junctionLinksByJunctionAndPortal = new HashMap<>();

    /**
     * Creates an empty corridor network container with the given geometric parameters.
     *
     * @param corridorWidth corridor inner width (wall-to-wall)
     * @param corridorHeight corridor interior height
     * @param wallThickness wall thickness for outer shell generation
     * @param routingCellSize effective routing cell size used during pathfinding
     */
    public CorridorNetwork(float corridorWidth, float corridorHeight, float wallThickness, float routingCellSize) {
        this.corridorWidth = corridorWidth;
        this.corridorHeight = corridorHeight;
        this.wallThickness = wallThickness;
        this.routingCellSize = routingCellSize;
    }
}
