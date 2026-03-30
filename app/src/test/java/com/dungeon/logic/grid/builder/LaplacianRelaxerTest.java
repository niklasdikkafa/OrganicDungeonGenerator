package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LaplacianRelaxer")
class LaplacianRelaxerTest {

    private static final float EPSILON = 1e-5f;

    // -------------------------------------------------------------------------
    // No-op cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("No-op cases (vertices unchanged)")
    class NoOp {

        @Test
        @DisplayName("zero iterations leaves all vertices unchanged")
        void zeroIterations() {
            List<Vector2f> v = threeVertices();
            Map<Integer, Set<Integer>> nb = chainNeighbors(3);
            List<Vector2f> before = copy(v);
            LaplacianRelaxer.relax(v, nb, 0.5f, 0);
            assertPositionsUnchanged(before, v);
        }

        @Test
        @DisplayName("alpha = 0 leaves all vertices unchanged")
        void zeroAlpha() {
            List<Vector2f> v = threeVertices();
            Map<Integer, Set<Integer>> nb = chainNeighbors(3);
            List<Vector2f> before = copy(v);
            LaplacianRelaxer.relax(v, nb, 0f, 5);
            assertPositionsUnchanged(before, v);
        }

        @Test
        @DisplayName("null vertex list returns without error")
        void nullVertices() {
            assertThatNoException().isThrownBy(
                    () -> LaplacianRelaxer.relax(null, chainNeighbors(3), 0.3f, 1));
        }

        @Test
        @DisplayName("empty vertex list returns without error")
        void emptyVertices() {
            assertThatNoException().isThrownBy(
                    () -> LaplacianRelaxer.relax(new ArrayList<>(), chainNeighbors(3), 0.3f, 1));
        }

        @Test
        @DisplayName("null neighbor map returns without error")
        void nullNeighborMap() {
            assertThatNoException().isThrownBy(
                    () -> LaplacianRelaxer.relax(threeVertices(), null, 0.3f, 1));
        }

        @Test
        @DisplayName("empty neighbor map returns without error")
        void emptyNeighborMap() {
            List<Vector2f> v = threeVertices();
            List<Vector2f> before = copy(v);
            LaplacianRelaxer.relax(v, new HashMap<>(), 0.5f, 3);
            assertPositionsUnchanged(before, v);
        }
    }

    // -------------------------------------------------------------------------
    // Convergence of a symmetric chain
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Convergence behaviour")
    class Convergence {

        @Test
        @DisplayName("isolated vertex (no neighbors) is not moved")
        void isolatedVertexUnchanged() {
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(5f, 5f));

            Map<Integer, Set<Integer>> nb = new HashMap<>(); // no neighbors

            LaplacianRelaxer.relax(v, nb, 0.5f, 10);
            assertThat(v.getFirst().x).isCloseTo(5f, within(EPSILON));
            assertThat(v.getFirst().y).isCloseTo(5f, within(EPSILON));
        }

        @Test
        @DisplayName("symmetric 3-vertex chain: middle vertex moves toward average of neighbours")
        void middleVertexMovesTowardAverage() {
            // v0=(0,0), v1=(3,0), v2=(6,0); v1 neighbours = {v0, v2}
            // After 1 iter with alpha=1: v1 -> avg(v0,v2) = (3,0) -- already at average, no move
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0f, 0f));
            v.add(new Vector2f(3f, 0f));
            v.add(new Vector2f(6f, 0f));

            Map<Integer, Set<Integer>> nb = new HashMap<>();
            nb.put(1, Set.of(0, 2));

            LaplacianRelaxer.relax(v, nb, 1.0f, 1);
            assertThat(v.get(1).x).isCloseTo(3f, within(EPSILON));
        }

        @Test
        @DisplayName("off-center middle vertex with alpha=1 snaps to average after 1 iteration")
        void offCenterMiddleSnapsToAverage() {
            // v0=(0,0), v1=(1,0), v2=(6,0); avg of v0 and v2 = (3,0)
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0f, 0f));
            v.add(new Vector2f(1f, 0f));  // off-center
            v.add(new Vector2f(6f, 0f));

            Map<Integer, Set<Integer>> nb = new HashMap<>();
            nb.put(1, Set.of(0, 2));

            LaplacianRelaxer.relax(v, nb, 1.0f, 1);
            assertThat(v.get(1).x).isCloseTo(3f, within(EPSILON));
            assertThat(v.get(1).y).isCloseTo(0f, within(EPSILON));
        }

        @Test
        @DisplayName("alpha=0.5 moves vertex halfway toward average")
        void halfwayMove() {
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0f, 0f));
            v.add(new Vector2f(0f, 0f));  // v1 at origin
            v.add(new Vector2f(4f, 0f));

            Map<Integer, Set<Integer>> nb = new HashMap<>();
            nb.put(1, Set.of(0, 2));  // avg = (2, 0), v1 at (0,0), half-way -> (1, 0)

            LaplacianRelaxer.relax(v, nb, 0.5f, 1);
            assertThat(v.get(1).x).isCloseTo(1f, within(EPSILON));
            assertThat(v.get(1).y).isCloseTo(0f, within(EPSILON));
        }

        @Test
        @DisplayName("vertices without entries in neighbor map are not moved")
        void vertexMissingFromMapIsUnchanged() {
            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0f, 0f));
            v.add(new Vector2f(5f, 5f)); // index 1 has no neighbors in map

            Map<Integer, Set<Integer>> nb = new HashMap<>();
            nb.put(0, Set.of()); // 0 has empty neighbor set

            LaplacianRelaxer.relax(v, nb, 0.5f, 3);
            // v1 has no entry in nb, should be unchanged
            assertThat(v.get(1).x).isCloseTo(5f, within(EPSILON));
            assertThat(v.get(1).y).isCloseTo(5f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Jacobi (synchronous) update
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Jacobi update semantics")
    class JacobiUpdate {

        @Test
        @DisplayName("positions are updated synchronously (Jacobi), not Gauss-Seidel")
        void synchronousUpdate() {
            // Chain: v0=0, v1=1, v2=0 with neighbors v0<->v1, v1<->v2
            // Under Jacobi one step with alpha=1:
            //   v0_new = avg({v1}) = 1
            //   v1_new = avg({v0, v2}) = avg(0, 0) = 0   (uses OLD positions)
            //   v2_new = avg({v1}) = 1

            List<Vector2f> v = new ArrayList<>();
            v.add(new Vector2f(0f, 0f)); // v0
            v.add(new Vector2f(1f, 0f)); // v1
            v.add(new Vector2f(0f, 0f)); // v2

            Map<Integer, Set<Integer>> nb = new HashMap<>();
            nb.put(0, Set.of(1));
            nb.put(1, Set.of(0, 2));
            nb.put(2, Set.of(1));

            LaplacianRelaxer.relax(v, nb, 1.0f, 1);

            // Jacobi: v1_new = avg(old_v0, old_v2) = avg(0, 0) = 0
            assertThat(v.get(1).x).isCloseTo(0f, within(EPSILON));
            // v0 and v2 both move to avg({v1=1}) = 1
            assertThat(v.get(0).x).isCloseTo(1f, within(EPSILON));
            assertThat(v.get(2).x).isCloseTo(1f, within(EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Multiple iterations
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple iterations")
    class MultipleIterations {

        @ParameterizedTest(name = "iterations = {0}")
        @ValueSource(ints = {1, 2, 5, 10})
        @DisplayName("running N single iterations equals one call with N iterations")
        void singleVsMultipleCallsEquivalent(int n) {
            List<Vector2f> va = buildGrid();
            List<Vector2f> vb = buildGrid();
            Map<Integer, Set<Integer>> nb = chainNeighbors(va.size());

            LaplacianRelaxer.relax(va, nb, 0.3f, n);
            for (int i = 0; i < n; i++) LaplacianRelaxer.relax(vb, nb, 0.3f, 1);

            for (int i = 0; i < va.size(); i++) {
                assertThat(va.get(i).x).as("x[%d]", i).isCloseTo(vb.get(i).x, within(EPSILON));
                assertThat(va.get(i).y).as("y[%d]", i).isCloseTo(vb.get(i).y, within(EPSILON));
            }
        }

        private List<Vector2f> buildGrid() {
            List<Vector2f> v = new ArrayList<>();
            for (int i = 0; i < 5; i++) v.add(new Vector2f(i * 2f, 0f));
            return v;
        }
    }

    // -------------------------------------------------------------------------
    // Integration: full grid pipeline
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Integration: full BaseGrid pipeline")
    class Integration {

        @Test
        @DisplayName("relaxing a full hex grid does not move any vertex outside reasonable bounds")
        void relaxedGridStaysWithinBounds() {
            int sideCount = 3;
            float edgeLength = 1.0f;
            List<Vector2f> v = HexPointGenerator.generate(sideCount, edgeLength);

            // Build minimal neighbor map from triangulation
            List<com.dungeon.logic.geometry.Polygon> tris = TriangleConnector.connect(v, sideCount);
            Map<Integer, Set<Integer>> nb = new HashMap<>();
            for (Polygon t : tris) {
                int[] idx = t.getVertexIndices();
                int k = idx.length;
                for (int i = 0; i < k; i++) {
                    int a = idx[i], b = idx[(i + 1) % k];
                    nb.computeIfAbsent(a, _ -> new HashSet<>()).add(b);
                    nb.computeIfAbsent(b, _ -> new HashSet<>()).add(a);
                }
            }

            // Grid radius: sideCount * edgeLength
            float maxDist = sideCount * edgeLength * 1.5f; // generous bound

            LaplacianRelaxer.relax(v, nb, 0.3f, 3);

            for (Vector2f p : v) {
                float dist = (float) Math.sqrt(p.x * p.x + p.y * p.y);
                assertThat(dist).isLessThanOrEqualTo(maxDist);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Vector2f> threeVertices() {
        List<Vector2f> v = new ArrayList<>();
        v.add(new Vector2f(0f, 0f));
        v.add(new Vector2f(1f, 1f));
        v.add(new Vector2f(2f, 0f));
        return v;
    }

    private static Map<Integer, Set<Integer>> chainNeighbors(int n) {
        Map<Integer, Set<Integer>> nb = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Set<Integer> set = new HashSet<>();
            if (i > 0) set.add(i - 1);
            if (i < n - 1) set.add(i + 1);
            nb.put(i, set);
        }
        return nb;
    }

    private static List<Vector2f> copy(List<Vector2f> src) {
        List<Vector2f> out = new ArrayList<>(src.size());
        for (Vector2f v : src) out.add(new Vector2f(v.x, v.y));
        return out;
    }

    private static void assertPositionsUnchanged(List<Vector2f> expected, List<Vector2f> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i).x).as("x[%d]", i).isCloseTo(expected.get(i).x, within(EPSILON));
            assertThat(actual.get(i).y).as("y[%d]", i).isCloseTo(expected.get(i).y, within(EPSILON));
        }
    }
}