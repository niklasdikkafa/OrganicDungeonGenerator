package com.dungeon.logic.placement.corridor.routing.occupancy;

import com.dungeon.logic.placement.corridor.routing.block.RoomRasterizer;
import com.dungeon.logic.placement.corridor.routing.block.RoomVolume2_5D;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.config.DungeonConfig.*;

/**
 * Builds the initial occupancy state ("voxel state") for corridor routing.
 * <p>
 * The routing system operates on a refined 2.5D voxel grid (polygon index + discrete z-band).
 * This builder marks all voxels that belong to rooms as blocked, so the pathfinder will not
 * route corridors through rooms.
 * </p>
 *
 * <h2>Occupancy semantics</h2>
 * <ul>
 *   <li><b>ROOM</b>: solidly occupied space inside the room volume (traversable for corridors but expensive).</li>
 *   <li><b>BORDER</b>: conservative safety/clearance region above and under occupied space (not traversable).</li>
 * </ul>
 *
 * <h2>How room blocking works</h2>
 * <ol>
 *   <li>Rasterize each room footprint onto the routing grid (polygon ids) using {@link RoomRasterizer}.</li>
 *   <li>Block all z-bands covered by the room volume:
 *       <ul>
 *         <li>the first band is written as {@code ROOM}</li>
 *         <li>additional bands above are written as {@code BORDER} (conservative vertical blocking) because
 *         the corridors should only be able to traverse the ground of the room</li>
 *       </ul>
 *   </li>
 *   <li>If {@link com.dungeon.config.DungeonConfig#WALL_THICKNESS} > 0, add extra border bands
 *       below/above the room volume to model wall/ceiling/floor clearance.</li>
 * </ol>
 *
 * <p>
 * Note: The exact meaning of "voxel" here is a logical routing cell. Each voxel corresponds to an organic
 * grid polygon (2D cell) and a discrete z-band index.
 * </p>
 */
public final class VoxelStateBuilder {

    /** Precomputed index over the routing grid (polygons, centers, neighbors, spatial lookup). */
    private final GridIndex index;

    /**
     * Creates a new builder bound to a specific routing grid index.
     *
     * @param index routing grid index (must not be {@code null})
     */
    public VoxelStateBuilder(GridIndex index) {
        this.index = index;
    }

    /**
     * Builds the initial voxel occupancy state by marking all room volumes as blocked.
     *
     * @param volumes         room volumes to rasterize and block
     * @param clearanceRadius additional horizontal clearance applied during rasterization (world units).
     *                        Larger values make routing more conservative and reduce the chance that
     *                        corridors touch or cut into rooms.
     * @return initialized {@link VoxelStateGrid} containing ROOM/BORDER markings for all rooms
     */
    public VoxelStateGrid buildInitialState(List<RoomVolume2_5D> volumes, float clearanceRadius) {
        VoxelStateGrid state = new VoxelStateGrid(index.polys.size(), NUMBER_OF_VOXELS_PER_POLYGON);

        for (RoomVolume2_5D rv : volumes) {
            List<Integer> zBands = zBandsToBlock(rv);
            List<Integer> polys = RoomRasterizer.rasterRoom(index, rv, clearanceRadius);

            for (int pid : polys) {

                // Block the vertical volume: bottom band as ROOM, additional bands as BORDER.
                for (int i = 0; i < zBands.size(); i++) {
                    if (i == 0) {
                        state.setRoom(pid, zBands.get(i));
                    } else {
                        state.setBorder(pid, zBands.get(i));
                    }
                }

                // extra vertical clearance so rooms / corridors can't go directly above / under each other
                if (rv.zBandMin() - 1 >= 0) {
                    state.setBorder(pid, rv.zBandMin() - 1);
                }
                if (rv.zBandMax() + 1 < state.zBands()) {
                    state.setBorder(pid, rv.zBandMax() + 1);
                }
            }
        }
        return state;
    }

    /**
     * Returns the inclusive list of z-bands that the given room volume occupies.
     * <p>
     * The returned list includes both {@link RoomVolume2_5D#zBandMin()} and {@link RoomVolume2_5D#zBandMax()}.
     * </p>
     */
    private static List<Integer> zBandsToBlock(RoomVolume2_5D rv) {
        int minZ = rv.zBandMin();
        int maxZ = rv.zBandMax();
        List<Integer> zBands = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            zBands.add(z);
        }
        return zBands;
    }
}