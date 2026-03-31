package com.dungeon.logic.placement.corridor.routing.occupancy;

/**
 * States of the voxel grid used for path finding.
 */
public enum VoxelState {

    /** Voxel is part of a room object */
    ROOM,

    /** Voxel is a border of a corridor or room and should never be traversed */
    BORDER,

    /** Voxel is part of a corridor object */
    CORRIDOR,

    /** Voxel is free and unused */
    FREE_SPACE,

    /** Voxel is part of a ramp / stairs segment */
    STAIRS
}
