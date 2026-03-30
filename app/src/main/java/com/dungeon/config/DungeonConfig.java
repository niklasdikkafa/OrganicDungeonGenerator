package com.dungeon.config;

/**
 * Central configuration container for the dungeon generator.
 * <p>
 * This class defines globally used constants and tunables for the generation pipeline,
 * including room placement, the 2.5D voxelization/height quantization, and corridor routing.
 * The values are referenced by multiple subsystems (placement, routing, meshing), therefore
 * changing a parameter may affect geometric validity and performance.
 * </p>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>Quantized height model:</b> The world height is discretized into {@link #NUMBER_OF_VOXELS_PER_POLYGON}
 *       vertical voxels per polygon, each of height {@link #Z_BAND_HEIGHT}. Rooms typically occupy
 *       {@link #MIN_Z_BANDS_HEIGHT}..{@link #MAX_Z_BANDS_HEIGHT} bands.</li>
 *   <li><b>Changing config parameters:</b> Changing parameters that are not final may lead to inconsistent behaviour.
 *       To see the limitations of this PDG, see {@code README.md}.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> This class only contains static fields. Parameters marked as {@code final}
 * should be treated as immutable. Non-final fields are effectively global mutable state; changing them
 * at runtime can lead to inconsistent behavior unless the entire pipeline is re-run deterministically.</p>
 */
public final class DungeonConfig {

    // -------------------------------------------------------------------------
    // Rooms
    // -------------------------------------------------------------------------

    /**
     * Thickness of walls.
     * Used during geometry generation and collision/clearance checks.
     */
    public static final float WALL_THICKNESS = 0.3f;

    /**
     * Maximum number of attempts to place a single room before the generator gives up on it.
     * Higher values improve success rate but increase runtime in crowded scenarios.
     */
    public static final int MAX_ROOM_PLACEMENT_ATTEMPTS = 50;

    /**
     * Number of vertical voxels (z-bands) that exist per polygon in the routing grid.
     * This defines the vertical resolution of the 2.5D occupancy/state representation.
     */
    public static int NUMBER_OF_VOXELS_PER_POLYGON = 10;

    /**
     * Minimum number of z-bands a room may occupy (room interior height in band units).
     * The corresponding physical height is {@code MIN_Z_BANDS_HEIGHT * Z_BAND_HEIGHT}.
     */
    public static int MIN_Z_BANDS_HEIGHT = 1;

    /**
     * Maximum number of z-bands a room may occupy (room interior height in band units).
     * The corresponding physical height is {@code MAX_Z_BANDS_HEIGHT * Z_BAND_HEIGHT}.
     */
    public static int MAX_Z_BANDS_HEIGHT = 2;

    /**
     * Height of one z-band (one voxel) in world units.
     * <p>
     * Room floor heights are typically aligned to multiples of this value.
     * Corridor ceilings are also often based on band height (see {@link #CORRIDOR_HEIGHT}).
     * </p>
     */
    public static float Z_BAND_HEIGHT = 2.5f;

    /**
     * Number of discrete rotation steps (e.g., 5 means rotations in increments of steps of 5°).
     */
    public static int ROTATION_STEPS = 5;

    /**
     * Minimum rotation angle for room footprints, in degrees. Must be a multiple of the step size defined by {@link #ROTATION_STEPS}.
     */
    public static float MIN_ROTATION = 0f;

    /**
    * Maximum rotation angle for room footprints, in degrees. Must be a multiple of the step size defined by {@link #ROTATION_STEPS}.
    */
    public static float MAX_ROTATION = 30f;

    // -------------------------------------------------------------------------
    // Corridors
    // -------------------------------------------------------------------------

    /**
     * Inner corridor width (clear walking width between corridor walls), in world units.
     */
    public static float CORRIDOR_WIDTH = 2f;

    /**
     * If this is 0, then the corridor's floor and ceiling Y will be the same as the room ones. If this is >0, then the
     * corridor will be {@code C_REDUCER} smaller than the room's height and the floor will be {@code C_REDUCER}/2 above
     * the room's ground and the corridor's ceiling will be {@code C_REDUCER}/2 below the room's ceiling. This will
     * reduce errors for the CSG union.
     */
    public static final float C_REDUCER = 0.1f;

    /**
     * Corridor interior height (floor-to-ceiling) in world units.
     * By default, corridors are constrained to a single z-band height.
     */
    public static final float CORRIDOR_HEIGHT = Z_BAND_HEIGHT - C_REDUCER;

    /**
     * Clearance margin used during corridor placement to keep paths away from obstacles
     * (e.g., room boundaries) by a fixed buffer.
     */
    public static final float SAFETY_MARGIN = 0.3f;

    /**
     * Additional ring padding around the routing grid to give the router extra space
     * to navigate around boundaries and obstacles.
     */
    public static final int NUM_OF_BUFFER_RINGS = 3;

    /**
     * Refinement factor for building the routing grid from the base grid.
     * Larger values increase routing resolution at the cost of more polygons and slower A*.
     */
    public static final int F = 1;

    /**
     * Weight applied to vertical movement during corridor routing.
     * Higher values penalize z-changes more strongly and encourage flatter corridors.
     */
    public static final float LAMBDA_Z = 8.0f;

    // -------------------------------------------------------------------------
    // Mesh
    // -------------------------------------------------------------------------

    /**
     * Inflates the AABB of a mesh to get better results for the CSG union.
     * Higher values will have better results but higher time complexity and a higher risk of {@code StackOverflowError}s.
     */
    public static float INFLATE_F = 0.5f;
}