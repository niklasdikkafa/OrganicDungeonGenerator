package com.dungeon.logic.grid;

/**
 * Immutable configuration object for generating a {@code BaseGrid}.
 * <p>
 * A {@code BaseGrid} is constructed by:
 * <ol>
 *   <li>Generating a hex-ring vertex set (controlled by {@link #sideCount} and {@link #edgeLength})</li>
 *   <li>Building connectivity/topology from these vertices</li>
 *   <li>Optionally applying Laplacian relaxation to improve visual regularity
 *       (controlled by {@link #relaxAlpha} and {@link #relaxIterations})</li>
 * </ol>
 * </p>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@link #sideCount} determines the number of hex rings around the center (grid radius).</li>
 *   <li>{@link #edgeLength} is the spacing / edge length used during point generation.</li>
 *   <li>{@link #relaxAlpha} controls how strongly each relaxation step moves a vertex towards the
 *       average of its neighbors (typical range: {@code (0, 1]}).</li>
 *   <li>{@link #relaxIterations} specifies how many relaxation passes are performed.</li>
 * </ul>
 */
public class BaseGridConfig {

    /** Number of concentric hex rings around the center vertex (grid radius). Must be {@code >= 1}. */
    public final int sideCount;

    /** Edge length used for generating the underlying hex point grid. Must be {@code > 0}. */
    public final float edgeLength;

    /**
     * Laplacian relaxation blending factor.
     * <p>
     * An {@code alpha} of {@code 0} would mean "no movement" (effectively disabling relaxation),
     * while {@code 1} moves vertices directly to the neighbor average in each iteration.
     * </p>
     */
    public final float relaxAlpha;

    /** Number of Laplacian relaxation iterations to apply. */
    public final int relaxIterations;

    /**
     * Creates a configuration with default relaxation parameters.
     * <p>
     * Defaults are chosen as a conservative smoothing pass that improves regularity without
     * overly distorting the original layout.
     * </p>
     *
     * @param sideCount hex radius in number of triangles from center to corner (number of rings)
     * @param edgeLength length of each hex edge used to generate the base grid
     * @throws IllegalArgumentException if {@code sideCount < 1} or {@code edgeLength <= 0}
     */
    public BaseGridConfig(int sideCount, float edgeLength) {
        this(sideCount, edgeLength, 0.3f, 3);
    }

    /**
     * Creates a configuration with custom relaxation parameters.
     *
     * @param sideCount       hex radius in number of triangles from center to corner (number of rings)
     * @param edgeLength      length of each hex edge used to generate the base grid
     * @param relaxAlpha      Laplacian relaxation factor; typically in {@code (0, 1]}
     * @param relaxIterations number of Laplacian relaxation iterations; typically {@code >= 0}
     * @throws IllegalArgumentException if {@code sideCount < 1} or {@code edgeLength <= 0}
     */
    public BaseGridConfig(int sideCount, float edgeLength, float relaxAlpha, int relaxIterations) {
        if (sideCount < 1) throw new IllegalArgumentException("sideCount must be at least 1");
        if (edgeLength <= 0) throw new IllegalArgumentException("edgeLength must be positive");

        this.sideCount = sideCount;
        this.edgeLength = edgeLength;
        this.relaxAlpha = relaxAlpha;
        this.relaxIterations = relaxIterations;
    }
}