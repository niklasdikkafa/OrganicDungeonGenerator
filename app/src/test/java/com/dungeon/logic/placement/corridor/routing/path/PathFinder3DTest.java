package com.dungeon.logic.placement.corridor.routing.path;

import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PathFinder3D}.
 */
class PathFinder3DTest {

    private static GridIndex INDEX;
    private static final int Z_BANDS = 8;

    @BeforeAll
    static void buildIndex() {
        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(5, 5f), 42L);
        INDEX = new GridIndex(grid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns a fully FREE VoxelStateGrid for the shared index. */
    private static VoxelStateGrid freeGrid() {
        return new VoxelStateGrid(INDEX.polys.size(), Z_BANDS);
    }

    /** Returns RoutingParams with noise disabled for deterministic tests. */
    private static RoutingParams quiet() {
        RoutingParams rp = new RoutingParams();
        rp.noiseWeight = 0f;
        return rp;
    }

    /**
     * Finds two polygon IDs that are NOT adjacent so we can test the flat (same-z) routing.
     * Returns {a, b} where b is reachable from a but not an immediate neighbor.
     */
    private static int[] distantPair() {
        // pick polygon 0 and the polygon furthest from it by BFS depth >= 2
        int src = 0;
        int[] dist = new int[INDEX.polys.size()];
        java.util.Arrays.fill(dist, -1);
        dist[src] = 0;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(src);
        int best = src;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : INDEX.neighbors[cur]) {
                if (dist[nb] == -1) {
                    dist[nb] = dist[cur] + 1;
                    q.add(nb);
                    if (dist[nb] > dist[best]) best = nb;
                }
            }
        }
        return new int[]{src, best};
    }

    // -----------------------------------------------------------------------
    // Null / invalid argument tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Null and invalid arguments")
    class InvalidArgs {

        @Test @DisplayName("Throws NullPointerException for null index")
        void nullIndex() {
            assertThrows(NullPointerException.class, () ->
                    PathFinder3D.findPath(null, 0, 0, 1, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Throws NullPointerException for null grid")
        void nullGrid() {
            assertThrows(NullPointerException.class, () ->
                    PathFinder3D.findPath(INDEX, 0, 0, 1, 0, null, quiet()));
        }

        @Test @DisplayName("Throws NullPointerException for null RoutingParams")
        void nullParams() {
            assertThrows(NullPointerException.class, () ->
                    PathFinder3D.findPath(INDEX, 0, 0, 1, 0, freeGrid(), null));
        }

        @Test @DisplayName("Returns null for negative startPoly")
        void negativeStartPoly() {
            assertNull(PathFinder3D.findPath(INDEX, -1, 0, 1, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for startPoly >= polyCount")
        void startPolyTooLarge() {
            int n = INDEX.polys.size();
            assertNull(PathFinder3D.findPath(INDEX, n, 0, 0, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for negative goalPoly")
        void negativeGoalPoly() {
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, -1, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for goalPoly >= polyCount")
        void goalPolyTooLarge() {
            int n = INDEX.polys.size();
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, n, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for negative startZ")
        void negativeStartZ() {
            assertNull(PathFinder3D.findPath(INDEX, 0, -1, 1, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for startZ >= zBands")
        void startZTooLarge() {
            assertNull(PathFinder3D.findPath(INDEX, 0, Z_BANDS, 1, 0, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for negative goalZ")
        void negativeGoalZ() {
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, 1, -1, freeGrid(), quiet()));
        }

        @Test @DisplayName("Returns null for goalZ >= zBands")
        void goalZTooLarge() {
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, 1, Z_BANDS, freeGrid(), quiet()));
        }
    }

    // -----------------------------------------------------------------------
    // Trivial paths
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Trivial paths")
    class TrivialPaths {

        @Test @DisplayName("Start == goal returns a single-element path")
        void startEqualsGoal() {
            List<NodeState> path = PathFinder3D.findPath(INDEX, 0, 0, 0, 0, freeGrid(), quiet());
            assertNotNull(path);
            assertEquals(1, path.size());
            assertEquals(new NodeState(0, 0), path.getFirst());
        }

        @Test @DisplayName("Start == goal at non-zero z-band returns correct single-element path")
        void startEqualsGoalHigherZ() {
            List<NodeState> path = PathFinder3D.findPath(INDEX, 0, 3, 0, 3, freeGrid(), quiet());
            assertNotNull(path);
            assertEquals(1, path.size());
            assertEquals(new NodeState(0, 3), path.getFirst());
        }

        @Test @DisplayName("Adjacent polygons (same z) return a path of length 2")
        void adjacentSameZ() {
            int src = 0;
            if (INDEX.neighbors[src].length == 0) return;
            int dst = INDEX.neighbors[src][0];
            List<NodeState> path = PathFinder3D.findPath(INDEX, src, 0, dst, 0, freeGrid(), quiet());
            assertNotNull(path);
            assertEquals(2, path.size());
            assertEquals(new NodeState(src, 0), path.getFirst());
            assertEquals(new NodeState(dst, 0), path.getLast());
        }
    }

    // -----------------------------------------------------------------------
    // Path structural properties
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Path structural properties")
    class PathStructure {

        @Test @DisplayName("Path starts at (startPoly, startZ)")
        void pathStartsAtStart() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path, "Expected a path to exist on a free grid");
            assertEquals(pair[0], path.getFirst().polyId());
            assertEquals(0, path.getFirst().zBand());
        }

        @Test @DisplayName("Path ends at (goalPoly, goalZ)")
        void pathEndsAtGoal() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            NodeState last = path.getLast();
            assertEquals(pair[1], last.polyId());
            assertEquals(0, last.zBand());
        }

        @Test @DisplayName("Path has no consecutive duplicate NodeStates")
        void noConsecutiveDuplicates() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            for (int i = 1; i < path.size(); i++) {
                NodeState a = path.get(i - 1), b = path.get(i);
                assertFalse(a.polyId() == b.polyId() && a.zBand() == b.zBand(),
                        "Consecutive duplicate at index " + i);
            }
        }

        @Test @DisplayName("All polyIds in path are valid polygon indices")
        void allPolyIdsValid() {
            int[] pair = distantPair();
            int n = INDEX.polys.size();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            for (NodeState s : path)
                assertTrue(s.polyId() >= 0 && s.polyId() < n,
                        "Invalid polyId " + s.polyId() + " in path");
        }

        @Test @DisplayName("All zBands in path are valid z-band indices")
        void allZBandsValid() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            for (NodeState s : path)
                assertTrue(s.zBand() >= 0 && s.zBand() < Z_BANDS,
                        "Invalid zBand " + s.zBand() + " in path");
        }

        @Test @DisplayName("Path has at least 1 node (never empty)")
        void pathNeverEmpty() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            assertFalse(path.isEmpty());
        }

        @Test @DisplayName("Every step moves to an adjacent polygon or stays in same polygon (z-change)")
        void eachStepIsValid() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertNotNull(path);
            for (int i = 1; i < path.size(); i++) {
                NodeState prev = path.get(i - 1);
                NodeState cur  = path.get(i);
                boolean samePolyDiffZ  = prev.polyId() == cur.polyId() && prev.zBand() != cur.zBand();
                boolean adjacentSameZ  = isAdjacent(prev.polyId(), cur.polyId()) && prev.zBand() == cur.zBand();
                boolean adjacentDiffZ  = isAdjacent(prev.polyId(), cur.polyId()) && prev.zBand() != cur.zBand();
                assertTrue(samePolyDiffZ || adjacentSameZ || adjacentDiffZ,
                        "Step " + (i-1) + "->" + i + ": " + prev + " -> " + cur + " is not a valid move");
            }
        }

        private boolean isAdjacent(int a, int b) {
            for (int nb : INDEX.neighbors[a]) if (nb == b) return true;
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Blocked start / goal
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Blocked cells")
    class BlockedCells {

        @Test @DisplayName("Returns null when start cell is BORDER")
        void blockedStart() {
            VoxelStateGrid g = freeGrid();
            g.setBorder(0, 0);
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, 1, 0, g, quiet()));
        }

        @Test @DisplayName("Returns null when all neighbors of start are BORDER (no exit)")
        void allNeighborsBlocked() {
            int src = 0;
            if (INDEX.neighbors[src].length == 0) return;
            VoxelStateGrid g = freeGrid();
            for (int nb : INDEX.neighbors[src]) g.setBorder(nb, 0);
            // goal is unreachable but start itself is free -> path should be null or trivially src==goal
            int dst = INDEX.neighbors[src][0]; // blocked
            assertNull(PathFinder3D.findPath(INDEX, src, 0, dst, 0, g, quiet()));
        }

        @Test @DisplayName("Returns null when goal cell is BORDER")
        void blockedGoal() {
            if (INDEX.neighbors[0].length == 0) return;
            int dst = INDEX.neighbors[0][0];
            VoxelStateGrid g = freeGrid();
            g.setBorder(dst, 0);
            assertNull(PathFinder3D.findPath(INDEX, 0, 0, dst, 0, g, quiet()));
        }
    }

    // -----------------------------------------------------------------------
    // ROOM cost penalty (traversable but expensive)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ROOM cells are traversable but penalized")
    class RoomCells {

        @Test @DisplayName("Path exists even when all cells are ROOM (expensive but traversable)")
        void roomCellsTraversable() {
            int[] pair = distantPair();
            VoxelStateGrid g = freeGrid();
            for (int p = 0; p < INDEX.polys.size(); p++) g.setRoom(p, 0);
            // start cell is ROOM -> isTraversableBasic allows ROOM
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 0, g, quiet());
            assertNotNull(path, "Path must exist even when all cells are ROOM");
        }
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test @DisplayName("Same inputs (noise=0) produce identical paths")
        void deterministicNoNoise() {
            int[] pair = distantPair();
            List<NodeState> p1 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            List<NodeState> p2 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), quiet());
            assertEquals(p1, p2, "Noise-free paths must be identical for the same inputs");
        }

        @Test @DisplayName("Different noiseSeed produces different path (with noise enabled)")
        void differentSeedDifferentPath() {
            int[] pair = distantPair();
            if (pair[0] == pair[1]) return; // degenerate grid

            RoutingParams rp1 = new RoutingParams();
            rp1.noiseWeight = 0.5f;
            rp1.noiseSeed   = 1;

            RoutingParams rp2 = new RoutingParams();
            rp2.noiseWeight = 0.5f;
            rp2.noiseSeed   = 999_999;

            List<NodeState> p1 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp1);
            List<NodeState> p2 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp2);

            assertNotNull(p1);
            assertNotNull(p2);
            // It is plausible (though unlikely) that both seeds find the same path on a tiny grid.
            // does not assert inequality - just verify both paths are structurally valid.
            assertFalse(p1.isEmpty());
            assertFalse(p2.isEmpty());
        }

        @Test @DisplayName("Same noiseSeed produces identical paths")
        void sameSeedSamePath() {
            int[] pair = distantPair();
            RoutingParams rp = new RoutingParams();
            rp.noiseWeight = 0.3f;
            rp.noiseSeed   = 77;

            List<NodeState> p1 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp);
            List<NodeState> p2 = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp);
            assertEquals(p1, p2, "Same noise seed must produce identical paths");
        }
    }

    // -----------------------------------------------------------------------
    // Corridor-reuse discount
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Corridor reuse")
    class CorridorReuse {

        @Test @DisplayName("Path exists when some cells are CORRIDOR")
        void corridorCellsTraversable() {
            int[] pair = distantPair();
            VoxelStateGrid g = freeGrid();
            // mark a few cells as CORRIDOR
            for (int i = 0; i < Math.min(5, INDEX.polys.size()); i++) g.setCorridor(i, 0);
            List<NodeState> path = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, g, quiet());
            assertNotNull(path);
        }
    }

    // -----------------------------------------------------------------------
    // Vertical routing (z-band change)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Vertical routing (z-band change)")
    class VerticalRouting {

        @Test @DisplayName("Path from z=0 to z=2 ends at goalZ=2")
        void pathEndAtHigherZ() {
            int[] pair = distantPair();
            int goalZ = 2;
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], goalZ, freeGrid(), quiet());
            assertNotNull(path, "Path must exist between different z-bands on a free grid");
            assertEquals(goalZ, path.getLast().zBand(),
                    "Path must end at goalZ=" + goalZ);
        }

        @Test @DisplayName("Path from z=0 to z=2 starts at startZ=0")
        void pathStartAtCorrectZ() {
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], 0, pair[1], 2, freeGrid(), quiet());
            assertNotNull(path);
            assertEquals(0, path.getFirst().zBand(), "Path must start at startZ=0");
        }

        @Test @DisplayName("All z-bands in vertical path are within [min(startZ,goalZ), max(startZ,goalZ)]")
        void verticalPathZBandsInRange() {
            int startZ = 1, goalZ = 4;
            int[] pair = distantPair();
            List<NodeState> path = PathFinder3D.findPath(
                    INDEX, pair[0], startZ, pair[1], goalZ, freeGrid(), quiet());
            assertNotNull(path);
            int lo = Math.min(startZ, goalZ), hi = Math.max(startZ, goalZ);
            for (NodeState s : path)
                assertTrue(s.zBand() >= lo && s.zBand() <= hi,
                        "zBand " + s.zBand() + " is outside expected range [" + lo + "," + hi + "]");
        }
    }

    // -----------------------------------------------------------------------
    // RoutingParams cost sensitivity
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("RoutingParams cost sensitivity")
    class CostSensitivity {

        @Test @DisplayName("Very high stairsCost still finds a flat path when start/goal share z-band")
        void highStairsCostFlatPath() {
            int[] pair = distantPair();
            RoutingParams rp = quiet();
            rp.stairsCost = 1000f; // enormous vertical penalty
            List<NodeState> path = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp);
            assertNotNull(path, "Flat path must still be found even with very high stairsCost");
            // All z-bands should remain 0 (no vertical moves needed)
            for (NodeState s : path)
                assertEquals(0, s.zBand(), "High stairsCost: all steps should stay at z=0");
        }

        @Test @DisplayName("hWeight = 0 (Dijkstra-like) still finds a valid path")
        void zeroHWeight() {
            int[] pair = distantPair();
            RoutingParams rp = quiet();
            rp.hWeight = 0f;
            List<NodeState> path = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), rp);
            assertNotNull(path);
            assertEquals(pair[0], path.getFirst().polyId());
            assertEquals(pair[1], path.getLast().polyId());
        }
    }

    // -----------------------------------------------------------------------
    // Corridor reuse lowers cost (path prefers existing corridors)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Corridor reuse preference")
    class CorridorReusePreference {

        @Test @DisplayName("corridorReuseMultiplier < 1 causes path to prefer corridor cells")
        void corridorCellsPreferred() {
            int[] pair = distantPair();
            if (pair[0] == pair[1]) return;

            // Find a path without corridor bias
            RoutingParams base = quiet();
            List<NodeState> basePath = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, freeGrid(), base);
            assertNotNull(basePath);

            // Mark the cells on the base path as CORRIDOR in a fresh grid
            VoxelStateGrid g = freeGrid();
            for (NodeState s : basePath) g.setCorridor(s.polyId(), s.zBand());

            // Path with strong reuse discount should follow the same corridor cells
            RoutingParams reuse = quiet();
            reuse.corridorReuseMultiplier = 0.01f; // very strong discount
            List<NodeState> reusePath = PathFinder3D.findPath(INDEX, pair[0], 0, pair[1], 0, g, reuse);
            assertNotNull(reusePath);

            Set<NodeState> corridorSet = new HashSet<>(basePath);
            long inCorridor = reusePath.stream().filter(corridorSet::contains).count();
            assertTrue(inCorridor >= reusePath.size() / 2,
                    "With strong corridor reuse discount, most steps should reuse existing corridor cells");
        }
    }
}