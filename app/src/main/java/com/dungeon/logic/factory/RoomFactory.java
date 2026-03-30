package com.dungeon.logic.factory;

import com.dungeon.domain.Room;
import com.dungeon.logic.placement.room.geometry.RoomGeometry;
import com.jme3.math.Vector2f;

import java.util.List;

import static com.dungeon.logic.geometry.Utilities.polygonIntersectsItself;

/**
 * Factory for constructing {@link Room} instances from a raw (inner) room footprint.
 * <p>
 * A room is created by running a small geometry pipeline:
 * </p>
 * <ol>
 *   <li><b>Validate</b> input footprint and parameters (min corners, positive heights, simple polygon).</li>
 *   <li><b>Quantize floor height</b> via {@code zBandIndex} and {@code Z_BAND_HEIGHT}.</li>
 *   <li><b>Preprocess inner footprint</b> (ensure CCW, chamfer, optional rotation, simplification).</li>
 *   <li><b>Compute interior point</b> of the processed inner polygon.</li>
 *   <li><b>Offset outward</b> by wall thickness to obtain the outer footprint.</li>
 *   <li><b>Generate wall segments</b> by connecting consecutive outer corners.</li>
 * </ol>
 *
 * <p>
 * The created {@link Room} stores both inner and outer footprints as well as derived wall segments,
 * which are later used for collision checks, routing constraints, and mesh generation.
 * </p>
 */
public final class RoomFactory {

    /**
     * Creates a {@link Room} from a raw inner footprint.
     *
     * <h3>Height parameters</h3>
     * <ul>
     *   <li>{@code heightBands}: interior height of the room (floor to ceiling) in discrete z bands, must be {@code > 0}.</li>
     *   <li>{@code floorThickness}: thickness of floor/ceiling slabs used in the volume model, must be {@code > 0}.</li>
     *   <li>{@code zBandIndex}: discrete floor level index; the world-space z-level becomes
     *       {@code zLevel = zBandIndex * Z_BAND_HEIGHT}.</li>
     * </ul>
     *
     * <h3>Geometry parameters</h3>
     * <ul>
     *   <li>{@code rawInnerCorners}: polygon vertices of the inner footprint. Must contain at least 3 points and
     *       must not self-intersect (simple polygon).</li>
     *   <li>{@code rotationDeg}: optional rotation (degrees) applied around the footprint centroid during preprocessing.</li>
     * </ul>
     *
     * @param rawInnerCorners raw inner footprint corners (may be any winding; preprocessing will enforce CCW)
     * @param heightBands     room height in discrete z-bands
     * @param floorThickness  thickness of floor/ceiling slabs (must be positive)
     * @param zBandIndex      discrete z-band index used to compute {@code zLevel}
     * @param rotationDeg     optional footprint rotation in degrees
     * @param gridEdgeLength  edge length of the base grid on which the room is placed
     * @return a fully constructed {@link Room} with inner/outer corners and wall segments
     *
     * @throws IllegalArgumentException if the footprint has fewer than 3 corners
     * @throws IllegalArgumentException if {@code heightBands < 1} or {@code floorThickness <= 0}
     * @throws IllegalArgumentException if {@code zBandIndex < 0}
     * @throws IllegalArgumentException if the footprint polygon self-intersects
     */
    public static Room create(List<Vector2f> rawInnerCorners,
                              int heightBands,
                              float floorThickness,
                              int zBandIndex,
                              float rotationDeg,
                              float gridEdgeLength) {

        if (rawInnerCorners == null || rawInnerCorners.size() < 3) {
            throw new IllegalArgumentException("Room needs >= 3 corners.");
        }
        if (heightBands < 1) {
            throw new IllegalArgumentException("Height bands must be > 0.");
        }
        if (floorThickness <= 0f) {
            throw new IllegalArgumentException("Floor thickness must be positive.");
        }
        if (zBandIndex < 0) {
            throw new IllegalArgumentException("zBandIndex must be non-negative.");
        }
        if (polygonIntersectsItself(rawInnerCorners)) {
            throw new IllegalArgumentException("Inner footprint polygon intersects itself:" + rawInnerCorners + ".");
        }

        // In the current model, wall thickness is aligned with floor thickness.
        float wallThickness = floorThickness;

        // Geometry pipeline:
        // - ensure CCW winding
        // - chamfer sharp corners
        // - rotate around centroid (optional)
        // - simplify nearly-collinear edges
        List<Vector2f> inner = RoomGeometry.preprocessInnerCorners(rawInnerCorners, wallThickness, rotationDeg, gridEdgeLength);

        // Interior point ("visual center") of the processed footprint. May differ from centroid.
        Vector2f visualCenter = RoomGeometry.computeInteriorPoint(inner);

        // Outer footprint: offset inner polygon outward by wall thickness.
        List<Vector2f> outer = RoomGeometry.computeOuterCorners(inner, wallThickness);

        return new Room(inner, outer, visualCenter, floorThickness, wallThickness, zBandIndex, heightBands);
    }
}