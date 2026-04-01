package com.dungeon.logic.placement.corridor.network.graph;

import com.dungeon.domain.Room;

/**
 * Semantic category of a corridor-network point.
 * <p>
 * Used for building stable {@code NodeKey}s and for interpreting how a graph node
 * relates to the routing grid / room endpoints.
 * </p>
 */
public enum PointKind {

    /** Center of a routing-grid polygon (cell). */
    VOXEL_CENTER,

    /** Midpoint of the shared edge between two neighboring routing-grid polygons. */
    EDGE_MID,

    /** Room anchor point used as corridor endpoint (typically the room's interior point {@link Room#getInteriorPoint()}). */
    ROOM_CENTER
}