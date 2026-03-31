package com.dungeon.logic.placement.corridor.routing.path;

/**
 * Helper that describes a "voxel" in a 3D routing grid
 * @param polyId 2D polygon id of a routing grid polygon
 * @param zBand discrete vertical height band of the voxel
 */
public record NodeState(int polyId, int zBand) {}