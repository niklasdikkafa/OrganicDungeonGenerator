package com.dungeon.logic.grid.builder;

import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HexPointGenerator")
class HexPointGeneratorTest {

    private static final float EPSILON = 1e-4f;

    // -------------------------------------------------------------------------
    // Vertex count
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Vertex count")
    class VertexCount {

        @Test
        @DisplayName("sideCount=0 produces exactly 1 vertex (center only)")
        void sideCountZero() {
            assertThat(HexPointGenerator.generate(0, 1f)).hasSize(1);
        }

        @Test
        @DisplayName("sideCount=1 produces 1 + 6 = 7 vertices")
        void sideCountOne() {
            assertThat(HexPointGenerator.generate(1, 1f)).hasSize(7);
        }

        @Test
        @DisplayName("sideCount=2 produces 1 + 6 + 12 = 19 vertices")
        void sideCountTwo() {
            assertThat(HexPointGenerator.generate(2, 1f)).hasSize(19);
        }

        @Test
        @DisplayName("sideCount=3 produces 1 + 6 + 12 + 18 = 37 vertices")
        void sideCountThree() {
            assertThat(HexPointGenerator.generate(3, 1f)).hasSize(37);
        }

        @ParameterizedTest(name = "sideCount = {0}")
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("total count matches 1 + sum(6*r) formula")
        void formulaHolds(int r) {
            int expected = 1;
            for (int k = 1; k <= r; k++) expected += 6 * k;
            assertThat(HexPointGenerator.generate(r, 1f)).hasSize(expected);
        }
    }

    // -------------------------------------------------------------------------
    // Center vertex
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Center vertex")
    class CenterVertex {

        @Test
        @DisplayName("first vertex is always the origin (0, 0)")
        void firstVertexIsOrigin() {
            Vector2f center = HexPointGenerator.generate(3, 5.0f).getFirst();
            assertThat(center.x).isCloseTo(0f, within(EPSILON));
            assertThat(center.y).isCloseTo(0f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Edge length scaling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Edge length scaling")
    class EdgeLengthScaling {

        @Test
        @DisplayName("all ring-1 vertices are exactly edgeLength away from the origin")
        void ring1DistanceEqualsEdgeLength() {
            float edgeLength = 3.7f;
            List<Vector2f> verts = HexPointGenerator.generate(1, edgeLength);
            for (int i = 1; i <= 6; i++) {
                Vector2f v = verts.get(i);
                float dist = (float) Math.sqrt(v.x * v.x + v.y * v.y);
                assertThat(dist).as("vertex %d distance", i).isCloseTo(edgeLength, within(EPSILON));
            }
        }

        @Test
        @DisplayName("doubling edgeLength doubles all distances from origin")
        void scalingIsLinear() {
            List<Vector2f> v1 = HexPointGenerator.generate(2, 1.0f);
            List<Vector2f> v2 = HexPointGenerator.generate(2, 2.0f);
            for (int i = 1; i < v1.size(); i++) {
                float d1 = v1.get(i).length();
                float d2 = v2.get(i).length();
                assertThat(d2).isCloseTo(2f * d1, within(EPSILON));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("two calls with identical parameters return identical vertex lists")
        void deterministicOutput() {
            List<Vector2f> a = HexPointGenerator.generate(3, 2.0f);
            List<Vector2f> b = HexPointGenerator.generate(3, 2.0f);
            assertThat(a).hasSameSizeAs(b);
            for (int i = 0; i < a.size(); i++) {
                assertThat(a.get(i).x).as("x[%d]", i).isCloseTo(b.get(i).x, within(EPSILON));
                assertThat(a.get(i).y).as("y[%d]", i).isCloseTo(b.get(i).y, within(EPSILON));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Symmetry
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Hex symmetry")
    class Symmetry {

        @Test
        @DisplayName("ring-1 vertices are evenly spaced at 60° intervals")
        void ring1AngularSpacing() {
            List<Vector2f> verts = HexPointGenerator.generate(1, 1.0f);
            double[] angles = new double[6];
            for (int i = 0; i < 6; i++) {
                Vector2f v = verts.get(i + 1);
                angles[i] = Math.toDegrees(Math.atan2(v.y, v.x));
            }
            // Sort angles and check that each consecutive gap is ~60°
            java.util.Arrays.sort(angles);
            for (int i = 1; i < 6; i++) {
                assertThat(angles[i] - angles[i - 1]).isCloseTo(60.0, within(0.5));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Invalid input
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid input")
    class InvalidInput {

        @Test
        @DisplayName("throws for negative sideCount")
        void negativeSideCount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> HexPointGenerator.generate(-1, 1f));
        }

        @ParameterizedTest(name = "edgeLength = {0}")
        @ValueSource(floats = {0f, -1f})
        @DisplayName("throws for non-positive edgeLength")
        void nonPositiveEdgeLength(float bad) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> HexPointGenerator.generate(1, bad));
        }
    }
}