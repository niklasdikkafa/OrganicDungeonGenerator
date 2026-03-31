package com.dungeon.logic.placement.corridor.routing.path;

/**
 * Configuration container for corridor pathfinding and cost evaluation.
 *
 * <h2>Cost model (movement)</h2>
 * <ul>
 *   <li>{@link #horizontalCost}: base cost for a horizontal step (same z-band).</li>
 *   <li>{@link #stairsCost}: base cost for a vertical transition (stairs step between z-bands).</li>
 * </ul>
 *
 * <h2>Heuristic scaling</h2>
 * <ul>
 *   <li>{@link #hWeight}: multiplier applied to the heuristic term; values > 1 make the search more
 *       greedy (potentially faster but less optimal depending on implementation).</li>
 * </ul>
 *
 * <h2>Terrain modifiers</h2>
 * <ul>
 *   <li>{@link #roomCostMultiplier}: penalty for traversing cells marked as ROOM (if allowed by the router).</li>
 *   <li>{@link #corridorReuseMultiplier}: discount for stepping onto an existing corridor (encourages reuse).</li>
 * </ul>
 *
 * <h2>Noise</h2>
 * <p>
 * Noise is applied to <em>edge costs</em> (movement cost), not to the heuristic.
 * This can help break ties and reduce overly regular-looking paths.
 * </p>
 * <ul>
 *   <li>{@link #noiseWeight}: strength of the noise contribution.</li>
 *   <li>{@link #noiseSeed}: seed used to produce deterministic noise for a given run.</li>
 * </ul>
 */
public final class RoutingParams {

    /** Base cost for a horizontal step (same z-band). */
    public float horizontalCost = 1.0f;

    /** Base cost for a vertical step (stairs transition between z-bands). */
    public float stairsCost = 3.0f;

    /**
     * Heuristic weight factor.
     * <p>
     * Typical meaning: {@code f = g + hWeight * h}. Interpretation depends on the pathfinder.
     * </p>
     */
    public float hWeight = 1.0f;

    /**
     * Multiplier applied when traversing ROOM cells (if the router allows it).
     * Values > 1 make passing through rooms less attractive.
     */
    public float roomCostMultiplier = 3.0f;

    /**
     * Multiplier applied when stepping onto an already existing corridor cell.
     * Values < 1 encourage corridor reuse.
     */
    public float corridorReuseMultiplier = 0.6f;

    /**
     * Strength of random noise added to movement costs (not the heuristic).
     * <p>
     * Set to {@code 0} for fully deterministic, noise-free costs (given the same inputs).
     * </p>
     */
    public float noiseWeight = 0.25f;

    /** Seed for deterministic noise generation. */
    public int noiseSeed;
}