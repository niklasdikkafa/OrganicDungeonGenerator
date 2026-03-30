package com.dungeon.domain;

import com.jme3.math.Vector2f;

import java.util.List;

import static com.dungeon.config.DungeonConfig.Z_BAND_HEIGHT;

/**
 * Immutable room representation used by the dungeon generator.
 * <p>
 * A {@code Room} is defined by an <b>inner footprint</b> (walkable interior) and a derived
 * <b>outer footprint</b> (wall boundary). The outer footprint is typically an outward offset
 * of the inner footprint by {@code wallThickness}.
 *
 * <h3>2.5D interpretation</h3>
 * <ul>
 *   <li>{@code zLevel} is the absolute floor elevation of the room.</li>
 *   <li>{@code height} is the interior height (floor to ceiling).</li>
 *   <li>{@code zBandHeightBands} is the interior height in discrete zBands.</li>
 *   <li>{@code floorThickness} models slab thickness (floor/ceiling) used for collision checks and extrusion.</li>
 *   <li>{@code zBandIndex} is the discrete band index that produced {@code zLevel} (quantization).</li>
 * </ul>
 */
public final class Room extends Structure {

    /** Inner footprint corners (counter-clockwise), already preprocessed (chamfer/rotation/simplification). */
    private final List<Vector2f> innerCorners;

    /** Outer footprint corners (counter-clockwise), derived from {@link #innerCorners} and {@link #wallThickness}. */
    private final List<Vector2f> outerCorners;

    /** Interior room height (floor to ceiling). */
    private final float height;

    /** Thickness of the horizontal slab(s) used for floor/ceiling volume modeling. */
    private final float floorThickness;

    /** Thickness used to offset {@link #innerCorners} outward to form {@link #outerCorners}. */
    private final float wallThickness;

    /** 2D interior point of the room, which may differ from the centroid. */
    private final Vector2f interiorPoint;

    /** Absolute floor elevation in world units. */
    private final float zLevel;

    /** Discrete vertical band index used to derive {@link #zLevel}. */
    private final int zBandIndex;

    /** Room height in discrete z-bands. */
    private final int zBandHeightBands;

    /**
     * Creates a new immutable {@code Room}.
     *
     * @param innerCorners      processed inner footprint corners (CCW); will be defensively copied
     * @param outerCorners      derived outer footprint corners (CCW); will be defensively copied
     * @param interiorPoint     2D visual center of the room (may differ from centroid); will be copied
     * @param floorThickness    thickness used for floor/ceiling slabs in volume/collision modeling
     * @param wallThickness     wall thickness used when offsetting inner to outer footprint
     * @param zBandIndex        discrete z-band index associated with {@code zLevel}
     * @param zBandHeightBands  room height in discrete z-bands (used to compute {@code height})
     */
    public Room(List<Vector2f> innerCorners,
                List<Vector2f> outerCorners,
                Vector2f interiorPoint,
                float floorThickness,
                float wallThickness,
                int zBandIndex,
                int zBandHeightBands) {

        this.innerCorners = List.copyOf(innerCorners);
        this.outerCorners = List.copyOf(outerCorners);

        this.interiorPoint = new Vector2f(interiorPoint);
        this.height = zBandHeightBands * Z_BAND_HEIGHT;
        this.floorThickness = floorThickness;
        this.wallThickness = wallThickness;
        this.zLevel = zBandIndex * Z_BAND_HEIGHT;
        this.zBandIndex = zBandIndex;
        this.zBandHeightBands = zBandHeightBands;
    }

    public List<Vector2f> getInnerCorners() { return innerCorners; }
    public List<Vector2f> getOuterCorners() { return outerCorners; }

    public Vector2f getInteriorPoint() { return interiorPoint; }

    public float getHeight() { return height; }
    public float getFloorThickness() { return floorThickness; }
    public float getWallThickness() { return wallThickness; }

    public float getZLevel() { return zLevel; }
    public int getZBandIndex() { return zBandIndex; }
    public int getZBandHeightBands() { return zBandHeightBands; }

}