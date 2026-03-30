package com.dungeon.logic.placement.room.debug;

import com.dungeon.domain.Room;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.geometry.VertexCluster;

import java.util.List;

/**
 * Debug container that bundles all intermediate artifacts produced during room placement.
 * <p>
 * This class is intended for visualization, logging, or unit tests where it is useful
 * to inspect not only the final {@link Room}, but also the underlying {@link VertexCluster}
 * and the derived boundary polygons used to compute the room outline.
 * <p>
 */
public final class DebugRoomData {

    /** The final, accepted room instance. */
    public final Room room;

    /** The connected vertex cluster from which the room footprint was derived. */
    public final VertexCluster cluster;

    /**
     * The polygons that form the boundary of the cluster (typically used as input
     * for outline/footprint computation).
     */
    public final List<Polygon> boundaryPolygons;

    /**
     * Creates a new debug bundle for a single room placement attempt/result.
     *
     * @param room             final room that was constructed from the footprint
     * @param cluster          vertex cluster selected for this room
     * @param boundaryPolygons polygons that describe the cluster boundary
     */
    public DebugRoomData(Room room, VertexCluster cluster, List<Polygon> boundaryPolygons) {
        this.room = room;
        this.cluster = cluster;
        this.boundaryPolygons = boundaryPolygons;
    }
}