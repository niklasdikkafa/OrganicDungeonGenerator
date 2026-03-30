package com.dungeon.logic.placement.room.params;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.dungeon.config.DungeonConfig.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("RoomParameterSampler")
class RoomParameterSamplerTest {

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("throws for null Random")
    void nullRandom() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RoomParameterSampler(null));
    }

    // -------------------------------------------------------------------------
    // generateZBandIndex
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateZBandIndex")
    class GenerateZBandIndex {

        @RepeatedTest(50)
        @DisplayName("result is always in [0, NUMBER_OF_VOXELS_PER_POLYGON - heightBands]")
        void inBounds() {
            RoomParameterSampler s = new RoomParameterSampler(new Random());
            int hb = MIN_Z_BANDS_HEIGHT;
            int idx = s.generateZBandIndex(hb);
            assertThat(idx).isBetween(0, NUMBER_OF_VOXELS_PER_POLYGON - hb);
        }

        @Test
        @DisplayName("heightBands = 0 throws")
        void zeroHeightBands() {
            RoomParameterSampler s = new RoomParameterSampler(new Random(0));
            assertThatIllegalArgumentException().isThrownBy(() -> s.generateZBandIndex(0));
        }

        @Test
        @DisplayName("negative heightBands throws")
        void negativeHeightBands() {
            RoomParameterSampler s = new RoomParameterSampler(new Random(0));
            assertThatIllegalArgumentException().isThrownBy(() -> s.generateZBandIndex(-1));
        }

        @Test
        @DisplayName("heightBands > NUMBER_OF_VOXELS_PER_POLYGON throws")
        void tooManyBands() {
            RoomParameterSampler s = new RoomParameterSampler(new Random(0));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> s.generateZBandIndex(NUMBER_OF_VOXELS_PER_POLYGON + 1));
        }

        @RepeatedTest(100)
        @DisplayName("room does not exceed the voxel column: zBandIndex + heightBands <= NUMBER_OF_VOXELS_PER_POLYGON")
        void roomFitsInColumn() {
            RoomParameterSampler s = new RoomParameterSampler(new Random());
            int hb = s.generateRoomHeightBands();
            int idx = s.generateZBandIndex(hb);
            assertThat(idx + hb).isLessThanOrEqualTo(NUMBER_OF_VOXELS_PER_POLYGON);
        }
    }

    // -------------------------------------------------------------------------
    // generateRotation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateRotation")
    class GenerateRotation {

        @RepeatedTest(100)
        @DisplayName("result is always in [MIN_ROTATION, MAX_ROTATION]")
        void inRange() {
            RoomParameterSampler s = new RoomParameterSampler(new Random());
            float rot = s.generateRotation();
            assertThat(rot).isBetween(MIN_ROTATION, MAX_ROTATION);
        }

        @RepeatedTest(100)
        @DisplayName("result is always a multiple of ROTATION_STEPS")
        void isMultipleOfStep() {
            RoomParameterSampler s = new RoomParameterSampler(new Random());
            float rot = s.generateRotation();
            // (rot - MIN_ROTATION) should be divisible by ROTATION_STEPS
            float remainder = (rot - MIN_ROTATION) % ROTATION_STEPS;
            assertThat(remainder).isCloseTo(0f, within(1e-4f));
        }

        @Test
        @DisplayName("all discrete rotation values are reachable")
        void allValuesReachable() {
            RoomParameterSampler s = new RoomParameterSampler(new Random(0));
            Set<Float> seen = new HashSet<>();
            for (int i = 0; i < 10_000; i++) seen.add(s.generateRotation());

            int expectedCount = (int) Math.floor((MAX_ROTATION - MIN_ROTATION) / ROTATION_STEPS) + 1;
            assertThat(seen).hasSize(expectedCount);
        }
    }

    // -------------------------------------------------------------------------
    // generateRoomHeightBands
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateRoomHeightBands")
    class GenerateRoomHeightBands {

        @RepeatedTest(100)
        @DisplayName("result is always in [MIN_Z_BANDS_HEIGHT, MAX_Z_BANDS_HEIGHT]")
        void inRange() {
            RoomParameterSampler s = new RoomParameterSampler(new Random());
            int hb = s.generateRoomHeightBands();
            assertThat(hb).isBetween(MIN_Z_BANDS_HEIGHT, MAX_Z_BANDS_HEIGHT);
        }

        @Test
        @DisplayName("both extremes (MIN and MAX) are reachable")
        void extremesReachable() {
            RoomParameterSampler s = new RoomParameterSampler(new Random(0));
            boolean sawMin = false, sawMax = false;
            for (int i = 0; i < 10_000; i++) {
                int hb = s.generateRoomHeightBands();
                if (hb == MIN_Z_BANDS_HEIGHT) sawMin = true;
                if (hb == MAX_Z_BANDS_HEIGHT) sawMax = true;
                if (sawMin && sawMax) break;
            }
            assertThat(sawMin).as("MIN_Z_BANDS_HEIGHT reachable").isTrue();
            assertThat(sawMax).as("MAX_Z_BANDS_HEIGHT reachable").isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("same seed produces same sequence of generateZBandIndex values")
        void deterministicZBand() {
            int hb = MIN_Z_BANDS_HEIGHT;
            RoomParameterSampler s1 = new RoomParameterSampler(new Random(777));
            RoomParameterSampler s2 = new RoomParameterSampler(new Random(777));
            for (int i = 0; i < 20; i++) {
                assertThat(s1.generateZBandIndex(hb)).isEqualTo(s2.generateZBandIndex(hb));
            }
        }

        @Test
        @DisplayName("same seed produces same sequence of generateRotation values")
        void deterministicRotation() {
            RoomParameterSampler s1 = new RoomParameterSampler(new Random(42));
            RoomParameterSampler s2 = new RoomParameterSampler(new Random(42));
            for (int i = 0; i < 20; i++) {
                assertThat(s1.generateRotation()).isEqualTo(s2.generateRotation());
            }
        }
    }
}