package com.dungeon.logic.placement.corridor.routing.block;

import com.dungeon.domain.Room;
import com.jme3.math.Vector2f;

import java.util.List;

/**
 * Lightweight 2.5D wrapper around a {@link Room} for corridor routing / voxel occupancy.
 * <p>
 * The routing system operates on a 2D footprint plus a discrete vertical range ("z-bands").
 * This record exposes exactly that information:
 * </p>
 * <ul>
 *   <li><b>Footprint:</b> the room outline used for blocking/rasterization (currently the room's
 *       {@linkplain Room#getOuterCorners() outer corners} to conservatively include walls).</li>
 *   <li><b>Vertical span:</b> an inclusive z-band range [{@link #zBandMin()}, {@link #zBandMax()}]
 *       derived from the room's band index and band height.</li>
 * </ul>
 *
 * @param room the underlying room (must not be {@code null})
 */
public record RoomVolume2_5D(Room room) {

    /**
     * Returns the 2D footprint polygon used for rasterization/occupancy.
     * <p>
     * The returned ring is expected to be in world/grid coordinates and represent a simple polygon.
     * </p>
     *
     * @return the footprint polygon (outer room corners)
     */
    public List<Vector2f> footprint() {
        return room.getOuterCorners();
    }

    /**
     * Lower inclusive z-band occupied by the room.
     *
     * @return minimum z-band index (inclusive)
     */
    public int zBandMin() {
        return room.getZBandIndex();
    }

    /**
     * Upper inclusive z-band occupied by the room.
     * <p>
     * Computed as {@code zBandIndex + zBandHeightBands - 1}.
     * </p>
     *
     * @return maximum z-band index (inclusive)
     */
    public int zBandMax() {
        return room.getZBandIndex() + room.getZBandHeightBands() - 1;
    }
}