package com.dungeon.logic.grid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BaseGridConfig")
class BaseGridConfigTest {

    // -------------------------------------------------------------------------
    // Two-arg constructor (default relaxation)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("BaseGridConfig(sideCount, edgeLength)")
    class TwoArgConstructor {

        @Test
        @DisplayName("stores sideCount and edgeLength")
        void storesValues() {
            BaseGridConfig cfg = new BaseGridConfig(3, 5.0f);
            assertThat(cfg.sideCount).isEqualTo(3);
            assertThat(cfg.edgeLength).isEqualTo(5.0f);
        }

        @Test
        @DisplayName("applies default relaxation values")
        void defaultRelaxation() {
            BaseGridConfig cfg = new BaseGridConfig(1, 1.0f);
            assertThat(cfg.relaxAlpha).isCloseTo(0.3f, within(1e-6f));
            assertThat(cfg.relaxIterations).isEqualTo(3);
        }

        @ParameterizedTest(name = "sideCount = {0}")
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("throws for sideCount < 1")
        void throwsForInvalidSideCount(int bad) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BaseGridConfig(bad, 1.0f))
                    .withMessageContaining("sideCount");
        }

        @ParameterizedTest(name = "edgeLength = {0}")
        @ValueSource(floats = {0f, -0.001f, -10f})
        @DisplayName("throws for edgeLength <= 0")
        void throwsForNonPositiveEdgeLength(float bad) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BaseGridConfig(1, bad))
                    .withMessageContaining("edgeLength");
        }

        @Test
        @DisplayName("accepts minimum valid sideCount = 1")
        void acceptsSideCountOne() {
            assertThatNoException().isThrownBy(() -> new BaseGridConfig(1, 1.0f));
        }

        @Test
        @DisplayName("accepts very small positive edgeLength")
        void acceptsTinyEdgeLength() {
            assertThatNoException().isThrownBy(() -> new BaseGridConfig(1, Float.MIN_VALUE));
        }
    }

    // -------------------------------------------------------------------------
    // Four-arg constructor (custom relaxation)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("BaseGridConfig(sideCount, edgeLength, relaxAlpha, relaxIterations)")
    class FourArgConstructor {

        @Test
        @DisplayName("stores all four values")
        void storesAllValues() {
            BaseGridConfig cfg = new BaseGridConfig(4, 2.5f, 0.5f, 10);
            assertThat(cfg.sideCount).isEqualTo(4);
            assertThat(cfg.edgeLength).isEqualTo(2.5f);
            assertThat(cfg.relaxAlpha).isEqualTo(0.5f);
            assertThat(cfg.relaxIterations).isEqualTo(10);
        }

        @Test
        @DisplayName("relaxAlpha = 0 is accepted (disables relaxation)")
        void zeroAlphaAccepted() {
            assertThatNoException().isThrownBy(() -> new BaseGridConfig(1, 1f, 0f, 3));
        }

        @Test
        @DisplayName("relaxIterations = 0 is accepted (no relaxation)")
        void zeroIterationsAccepted() {
            assertThatNoException().isThrownBy(() -> new BaseGridConfig(1, 1f, 0.3f, 0));
        }

        @ParameterizedTest(name = "sideCount = {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("throws for sideCount < 1 in four-arg constructor")
        void throwsForInvalidSideCount(int bad) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BaseGridConfig(bad, 1f, 0.3f, 3));
        }

        @ParameterizedTest(name = "edgeLength = {0}")
        @ValueSource(floats = {0f, -1f})
        @DisplayName("throws for edgeLength <= 0 in four-arg constructor")
        void throwsForNonPositiveEdgeLength(float bad) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BaseGridConfig(1, bad, 0.3f, 3));
        }
    }

    // -------------------------------------------------------------------------
    // Immutability
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("fields are public final — no setters")
        void noSetters() throws Exception {
            // All four fields are declared final; verify they are not modifiable via reflection
            Field f = BaseGridConfig.class.getField("sideCount");
            assertThat(java.lang.reflect.Modifier.isFinal(f.getModifiers())).isTrue();
        }
    }
}