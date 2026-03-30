package com.dungeon.logic.placement.room.params;

import java.util.Objects;
import java.util.Random;

import static com.dungeon.config.DungeonConfig.*;

/**
 * Samples stochastic parameters for room instances.
 * <p>
 * This class centralizes all random decisions related to the vertical placement and basic
 * geometric variation of rooms:
 * <ul>
 *   <li>{@code zBandIndex}: discrete floor level (quantized by {@code Z_BAND_HEIGHT})</li>
 *   <li>{@code height}: discrete room height (in multiples of {@code Z_BAND_HEIGHT})</li>
 *   <li>{@code rotationDeg}: small discrete rotation for footprint variety</li>
 * </ul>
 * The sampled values are designed to stay compatible with the voxel / z-band abstraction where each
 * polygon is partitioned into {@code NUMBER_OF_VOXELS_PER_POLYGON} vertical bands.
 */
public class RoomParameterSampler {

    private final Random random;

    /**
     * Creates a new sampler using the provided RNG.
     *
     * @param random random number generator used for all sampling decisions
     * @throws NullPointerException if {@code random} is {@code null}
     */
    public RoomParameterSampler(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Samples a valid z-band start index for a room of a given height.
     * <p>
     * The room height spans a discrete number of z-bands:
     * {@code heightBands = round(height / Z_BAND_HEIGHT)}.
     * A start index {@code zBandIndex} is then chosen uniformly from all indices that keep the full room
     * within {@code NUMBER_OF_VOXELS_PER_POLYGON} bands:
     * {@code zBandIndex ∈ [0, NUMBER_OF_VOXELS_PER_POLYGON - heightBands]}.
     *
     * @param heightBands room height (@code{Z_BAND_HEIGHT} units)
     * @return z-band start index (0-based) such that the room fits vertically in the per-polygon voxel column
     * @throws IllegalArgumentException if {@code height <= 0} or if the room cannot fit into the available bands
     */
    public int generateZBandIndex(int heightBands) {
        if (heightBands <= 0) {
            throw new IllegalArgumentException("height bands must be > 0");
        }

        int possibleZBands = NUMBER_OF_VOXELS_PER_POLYGON - heightBands;
        if (possibleZBands < 0) {
            throw new IllegalArgumentException(
                    "Room height (" + heightBands + ") spans " + heightBands + " bands and does not fit into " +
                            NUMBER_OF_VOXELS_PER_POLYGON + " bands per polygon."
            );
        }

        // 0...possibleZBands (inclusive)
        return random.nextInt(possibleZBands + 1);
    }

    /**
     * Samples a small discrete rotation angle for a room footprint.
     * <p>
     * Rotation is quantized to {@code ROTATION_STEPS} degrees and sampled uniformly
     * from {@code [MIN_ROTATION, MAX_ROTATION]} (inclusive).
     *
     * @return rotation angle in degrees
     * @throws IllegalArgumentException if the configured step size is invalid or if the rotation range is inconsistent
     */
    public float generateRotation() {
        float step = ROTATION_STEPS;
        float min  = MIN_ROTATION;
        float max  = MAX_ROTATION;

        if (step <= 0) throw new IllegalArgumentException("ROTATION_STEPS must be > 0");
        if (max < min) throw new IllegalArgumentException("MAX_ROTATION must be >= MIN_ROTATION");

        // number of discrete values in [min, max] with step size "step"
        int stepsCount = (int) Math.floor((max - min) / step);
        int k = random.nextInt(stepsCount + 1); // inclusive range

        return min + k * step;
    }

    /**
     * Samples a discrete room height band.
     * <p>
     * The sampled number of bands {@code zBands} is chosen uniformly from
     * {@code [MIN_Z_BANDS_HEIGHT, MAX_Z_BANDS_HEIGHT]}.
     *
     * @return room height bands
     * @throws IllegalArgumentException if the configured band range is invalid
     */
    public int generateRoomHeightBands() {
        int span = MAX_Z_BANDS_HEIGHT - MIN_Z_BANDS_HEIGHT;
        if (span < 0) {
            throw new IllegalArgumentException("MAX_Z_BANDS_HEIGHT must be >= MIN_Z_BANDS_HEIGHT");
        }

        return random.nextInt(span + 1) + MIN_Z_BANDS_HEIGHT; // shift to [MIN_Z_BANDS_HEIGHT, MAX_Z_BANDS_HEIGHT]
    }
}