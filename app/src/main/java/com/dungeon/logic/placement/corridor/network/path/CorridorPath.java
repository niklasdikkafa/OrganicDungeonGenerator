package com.dungeon.logic.placement.corridor.network.path;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single routed corridor as an ordered 3D centerline path plus derived graph references.
 * <p>
 * A {@code CorridorPath} is created from a {@code Corridor} (domain object) and later consumed by the
 * corridor network builder. The builder converts {@link #rawPoints} into globally reused graph nodes
 * ({@link #nodeIds}) and finally produces per-node frame samples ({@link #samples}) that are used
 * for mesh construction.
 * </p>
 *
 * <h2>Lifecycle / pipeline</h2>
 * <ol>
 *   <li><b>Raw path:</b> {@link #rawPoints} contains the routed polyline points (e.g., room centers,
 *       shared-edge midpoints, voxel centers, stair interpolation points).</li>
 *   <li><b>Graph binding:</b> {@link #nodeIds} stores the global graph node ids corresponding to the
 *       raw points after semantic node reuse and deduplication.</li>
 *   <li><b>Sampling:</b> {@link #samples} stores per-point frame/profile data copied from the
 *       corridor graph nodes (used by mesh builders).</li>
 * </ol>
 *
 * <h2>Endpoints</h2>
 * <p>
 * {@link #fromRoom} and {@link #toRoom} are the ids of the rooms connected by this corridor.
 * </p>
 *
 * <h2>Geometry parameters</h2>
 * <p>
 * {@link #corridorWidth}, {@link #corridorHeight}, and {@link #wallThickness} are copied into the path
 * so that downstream steps (sampling / mesh generation) can remain independent of global configuration.
 * </p>
 */
public final class CorridorPath {

    /** Room id at the start of the corridor path. */
    public final int fromRoom;

    /** Room id at the end of the corridor path. */
    public final int toRoom;

    /** Stable corridor identifier within the generation run (typically the index in the corridor list). */
    public final int corridorIndex;

    /** Corridor inner width (wall-to-wall) used for frame/profile generation. */
    public final float corridorWidth;

    /** Corridor interior height used for frame/profile generation. */
    public final float corridorHeight;

    /** Wall thickness used to derive outer corridor geometry from inner geometry. */
    public final float wallThickness;

    /**
     * Raw ordered 3D path points before graph binding.
     * Points may include room centers, edge midpoints, voxel centers, and stair/interpolation points.
     */
    public final List<PathPoint3D> rawPoints = new ArrayList<>();

    /**
     * Global graph node ids corresponding to {@link #rawPoints} after semantic node reuse and
     * per-path consecutive deduplication.
     */
    public final List<Integer> nodeIds = new ArrayList<>();

    /**
     * Per-point frame/profile samples derived from the global graph.
     * These typically copy the corresponding graph node’s frame and profile points.
     */
    public final List<PathFrameSample> samples = new ArrayList<>();

    /**
     * Creates an empty corridor path container.
     *
     * @param fromRoom       start room id
     * @param toRoom         end room id
     * @param corridorIndex  stable corridor index within the generation run
     * @param corridorWidth  corridor inner width (wall-to-wall)
     * @param corridorHeight corridor interior height
     * @param wallThickness  wall thickness for outer shell construction
     */
    public CorridorPath(int fromRoom, int toRoom, int corridorIndex,
                        float corridorWidth, float corridorHeight, float wallThickness) {
        this.fromRoom = fromRoom;
        this.toRoom = toRoom;
        this.corridorIndex = corridorIndex;
        this.corridorWidth = corridorWidth;
        this.corridorHeight = corridorHeight;
        this.wallThickness = wallThickness;
    }
}
