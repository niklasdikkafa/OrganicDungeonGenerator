package com.dungeon.logic.placement.corridor.routing.path;

import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelState;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.jme3.math.Vector2f;

import java.util.*;


/**
 * Grid-based 2.5D pathfinding on a {@link VoxelStateGrid}.
 * <p>
 * The search operates on a discrete state space of {@code (polyId, zBand)} cells where:
 * <ul>
 *   <li>{@code polyId} is a cell/polygon index from {@link GridIndex}</li>
 *   <li>{@code zBand} is a discrete vertical layer (see {@link VoxelStateGrid#zBands()})</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * <p>
 * Uses an A*-style search (priority queue ordered by {@code f = g + h}) with:
 * <ul>
 *   <li>Horizontal transitions to neighboring polygons in the same {@code zBand}</li>
 *   <li>Optional “stairs macro” transitions that model a vertical change as a compact multi-step pattern</li>
 * </ul>
 *
 * <h2>Occupancy & traversal semantics</h2>
 * <p>
 * Traversability and costs are determined by {@link VoxelStateGrid} and {@link RoutingParams}.
 * In particular:
 * <ul>
 *   <li>{@code BORDER} cells are never traversable</li>
 *   <li>{@code ROOM} cells may be traversable but are penalized via {@link RoutingParams#roomCostMultiplier}</li>
 *   <li>{@code CORRIDOR} cells are cheaper to reuse via {@link RoutingParams#corridorReuseMultiplier}</li>
 *   <li>{@code STAIRS} cells may be “walkable” only along stored links (no branching)</li>
 * </ul>
 *
 * <h2>Stairs macro moves</h2>
 * <p>
 * Besides normal neighbor expansion, the router can attempt a macro move that directly connects
 * {@code A(zFrom) -> D(zTo)} while temporarily reserving intermediate cells {@code B} and {@code C}
 * on both layers to avoid collisions and branching. During reconstruction, the macro is expanded
 * back into a sequence of {@link NodeState} steps.
 * </p>
 */
public final class PathFinder3D {

    /** Priority queue entry: packed state id + current best f-score (g + h). */
    private record PQEntry(int stateId, double fScore) {}

    /** Move annotation for normal step transitions. */
    private static final byte MOVE_NORMAL = 0;

    /** Move annotation for macro stairs transitions (expanded during reconstruction). */
    private static final byte MOVE_STAIRS_MACRO = 1;

    /**
     * Finds a path from {@code (startPoly,startZ)} to {@code (goalPoly,goalZ)} on the given voxel grid.
     *
     * <p>
     * Returns {@code null} if no path can be found or if inputs are out of bounds / start is not traversable.
     * The returned list is a sequence of {@link NodeState} steps. If stairs macros were used, they are
     * expanded to a step-by-step path in {@link #reconstructExpanded(int, int[], byte[], int[], int[], int)}.
     * </p>
     *
     * @param index     routing index containing polygon adjacency and polygon centers
     * @param startPoly start polygon id (0-based)
     * @param startZ    start z-band (0-based)
     * @param goalPoly  goal polygon id (0-based)
     * @param goalZ     goal z-band (0-based)
     * @param grid      voxel occupancy grid defining traversal constraints
     * @param rp        routing parameters controlling costs, heuristic scaling, and noise
     * @return path as list of {@link NodeState}, or {@code null} if no path exists
     */
    public static List<NodeState> findPath(GridIndex index,
                                           int startPoly, int startZ,
                                           int goalPoly, int goalZ,
                                           VoxelStateGrid grid,
                                           RoutingParams rp) {

        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(rp, "rp");

        final int polyCount = index.polys.size();
        final int zBands = grid.zBands();

        if (startPoly < 0 || startPoly >= polyCount) return null;
        if (goalPoly < 0 || goalPoly >= polyCount) return null;
        if (startZ < 0 || startZ >= zBands) return null;
        if (goalZ < 0 || goalZ >= zBands) return null;

        // State space = only packed voxel (poly,z)
        final int N = polyCount * zBands;

        final double[] g = new double[N];
        final double[] f = new double[N];
        final int[] parent = new int[N];

        // move annotation for reconstruction / temp stairs reservation checks
        final byte[] moveType = new byte[N];
        final int[] macroBPoly = new int[N];
        final int[] macroCPoly = new int[N];
        Arrays.fill(macroBPoly, -1);
        Arrays.fill(macroCPoly, -1);

        final boolean[] closed = new boolean[N];

        Arrays.fill(g, Double.POSITIVE_INFINITY);
        Arrays.fill(f, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);

        if (!isTraversableBasic(grid, startPoly, startZ)) return null;

        final int startState = packVoxel(startPoly, startZ, zBands);

        final float cellSize = Math.max(1e-6f, index.grid.getEdgeLength() / 2f);
        final Vector2f goalCenter = index.centers[goalPoly];

        PriorityQueue<PQEntry> open = new PriorityQueue<>(Comparator.comparingDouble(e -> e.fScore));

        g[startState] = 0.0;
        f[startState] = heuristic(index, startPoly, startZ, goalZ, goalCenter, cellSize, rp);
        open.add(new PQEntry(startState, f[startState]));

        while (!open.isEmpty()) {
            PQEntry curE = open.poll();
            int curState = curE.stateId;

            if (curE.fScore != f[curState]) continue;
            if (closed[curState]) continue;
            closed[curState] = true;

            int curPoly = unpackPoly(curState, zBands);
            int curZ = unpackZ(curState, zBands);

            // goal test
            if (curPoly == goalPoly && curZ == goalZ) {
                return reconstructExpanded(curState, parent, moveType, macroBPoly, macroCPoly, zBands);
            }

            // If current is existing walkable stairs: only follow stored links
            if (grid.isStairsWalkable(curPoly, curZ)) {
                long raw = grid.getStairsLinksRaw(curPoly, curZ);
                int a = VoxelStateGrid.linkA(raw);
                int b = VoxelStateGrid.linkB(raw);

                if (a != -1) {
                    expandFromStairsLink(index, grid, rp,
                            curState, curPoly, curZ, a,
                            goalZ, goalCenter, cellSize,
                            g, f, parent, moveType, macroBPoly, macroCPoly, closed, open, zBands);
                }
                if (b != -1 && b != a) {
                    expandFromStairsLink(index, grid, rp,
                            curState, curPoly, curZ, b,
                            goalZ, goalCenter, cellSize,
                            g, f, parent, moveType, macroBPoly, macroCPoly, closed, open, zBands);
                }
                continue;
            }

            // 1) Horizontal moves
            for (int nbPoly : index.neighbors[curPoly]) {
                int nbZ = curZ;

                if (!isTraversableBasic(grid, nbPoly, nbZ)) continue;

                if (isTempReservedOrAdjacentToPath(curState, nbPoly, nbZ, index, parent, moveType, macroBPoly, macroCPoly, zBands)) continue;

                // entering existing stairs only from valid linked endpoint
                if (grid.isStairsWalkable(nbPoly, nbZ)) {
                    if (!grid.stairsAllowsEnterFrom(nbPoly, nbZ, curState)) continue;
                }

                double step = stepCost(grid, nbPoly, nbZ, rp);
                step += edgeNoise(curPoly, curZ, nbPoly, nbZ, rp);

                int nbState = packVoxel(nbPoly, nbZ, zBands);

                relaxNormal(index, rp,
                        curState, nbState,
                        step,
                        nbPoly, nbZ,
                        goalZ, goalCenter, cellSize,
                        g, f, parent, moveType, macroBPoly, macroCPoly,
                        closed, open);
            }

            // 2) NEW stairs macro moves (A -> D directly), no cooldown
            if (canStartNewStairsFrom(grid, curPoly, curZ)) {
                for (int dz : new int[]{-1, +1}) {
                    int toZ = curZ + dz;
                    if (toZ < 0 || toZ >= zBands) continue;

                    int[] nbsA = index.neighbors[curPoly];
                    for (int bPoly : nbsA) {
                        int cPoly = straightestForward(index, curPoly, bPoly);
                        if (cPoly < 0) continue;

                        int dPoly = straightestForward(index, bPoly, cPoly);
                        if (dPoly < 0) continue;

                        // sanity
                        if (bPoly == curPoly || cPoly == curPoly || dPoly == curPoly) continue;
                        if (cPoly == bPoly || dPoly == bPoly || dPoly == cPoly) continue;

                        // D must be traversable and not ROOM
                        if (!isTraversableBasic(grid, dPoly, toZ)) continue;
                        if (grid.isRoom(dPoly, toZ)) continue;

                        // B/C must be free on both levels + not temp-reserved on current parent path
                        if (!stairsModuleCellsAreFreeAndTemporarilyUnreserved(
                                grid, bPoly, cPoly, dPoly, curZ, toZ,
                                curState, index, parent, moveType, macroBPoly, macroCPoly, zBands)) {
                            continue;
                        }

                        double step = rp.stairsCost + 2.0 * rp.horizontalCost;
                        step += edgeNoise(curPoly, curZ, dPoly, toZ, rp);

                        int dState = packVoxel(dPoly, toZ, zBands);

                        relaxStairsMacro(index, rp,
                                curState, dState,
                                step,
                                dPoly, toZ,
                                bPoly, cPoly,
                                goalZ, goalCenter, cellSize,
                                g, f, parent, moveType, macroBPoly, macroCPoly,
                                closed, open);
                    }
                }
            }
        }

        return null;
    }

    // --------------------------------------------------------------------------------------------
    // expansion helpers
    // --------------------------------------------------------------------------------------------

    /**
     * Expands a single successor when the current cell is a <em>walkable</em> STAIRS cell.
     * <p>
     * In this mode, expansion is restricted to the pre-defined stairs links stored in
     * {@link VoxelStateGrid}. The {@code nextPacked} argument is already a packed state id
     * (i.e. {@code pack(poly,z)}), so no additional packing is required.
     * </p>
     *
     * <p>
     * The step cost is computed based on the destination cell state (ROOM/CORRIDOR/FREE) and
     * optional edge noise (see {@link #edgeNoise(int, int, int, int, RoutingParams)}).
     * </p>
     */
    private static void expandFromStairsLink(GridIndex index,
                                             VoxelStateGrid grid,
                                             RoutingParams rp,
                                             int curState,
                                             int curPoly,
                                             int curZ,
                                             int nextPacked,
                                             int goalZ,
                                             Vector2f goalCenter,
                                             float cellSize,
                                             double[] g, double[] f,
                                             int[] parent,
                                             byte[] moveType,
                                             int[] macroBPoly,
                                             int[] macroCPoly,
                                             boolean[] closed,
                                             PriorityQueue<PQEntry> open,
                                             int zBands) {

        int nbPoly = unpackPoly(nextPacked, zBands);
        int nbZ = unpackZ(nextPacked, zBands);

        if (!isTraversableBasic(grid, nbPoly, nbZ)) return;

        int nextState = nextPacked;

        double step = stepCost(grid, nbPoly, nbZ, rp);
        step += edgeNoise(curPoly, curZ, nbPoly, nbZ, rp);

        relaxNormal(index, rp,
                curState, nextState,
                step,
                nbPoly, nbZ,
                goalZ, goalCenter, cellSize,
                g, f, parent, moveType, macroBPoly, macroCPoly,
                closed, open);
    }

    /**
     * Convenience wrapper for relaxing a standard (non-macro) move.
     * <p>
     * Records the parent pointer and A* scores and annotates the move as {@link #MOVE_NORMAL}
     * for later path reconstruction.
     * </p>
     */
    private static void relaxNormal(GridIndex index,
                                    RoutingParams rp,
                                    int curState,
                                    int nbState,
                                    double stepCost,
                                    int nbPoly, int nbZ,
                                    int goalZ,
                                    Vector2f goalCenter,
                                    float cellSize,
                                    double[] g, double[] f,
                                    int[] parent,
                                    byte[] moveType,
                                    int[] macroBPoly,
                                    int[] macroCPoly,
                                    boolean[] closed,
                                    PriorityQueue<PQEntry> open) {
        relaxCore(index, rp,
                curState, nbState,
                stepCost,
                nbPoly, nbZ,
                goalZ, goalCenter, cellSize,
                g, f, parent,
                moveType, macroBPoly, macroCPoly,
                closed, open,
                MOVE_NORMAL, -1, -1);
    }

    /**
     * Convenience wrapper for relaxing a stairs macro move.
     * <p>
     * The macro move is stored as a single transition in the parent graph but is annotated with
     * {@link #MOVE_STAIRS_MACRO} and stores the intermediate helper polygons {@code B} and {@code C}.
     * During reconstruction, the macro is expanded into multiple {@link NodeState} steps.
     * </p>
     *
     * @param bPoly intermediate polygon B reserved on both layers
     * @param cPoly intermediate polygon C reserved on both layers
     */
    private static void relaxStairsMacro(GridIndex index,
                                         RoutingParams rp,
                                         int curState,
                                         int nbState,
                                         double stepCost,
                                         int nbPoly, int nbZ,
                                         int bPoly, int cPoly,
                                         int goalZ,
                                         Vector2f goalCenter,
                                         float cellSize,
                                         double[] g, double[] f,
                                         int[] parent,
                                         byte[] moveType,
                                         int[] macroBPoly,
                                         int[] macroCPoly,
                                         boolean[] closed,
                                         PriorityQueue<PQEntry> open) {
        relaxCore(index, rp,
                curState, nbState,
                stepCost,
                nbPoly, nbZ,
                goalZ, goalCenter, cellSize,
                g, f, parent,
                moveType, macroBPoly, macroCPoly,
                closed, open,
                MOVE_STAIRS_MACRO, bPoly, cPoly);
    }

    /**
     * Core A* relaxation step shared by both normal and macro transitions.
     * <p>
     * If the neighbor is not closed and the new {@code gScore} improves, this updates:
     * <ul>
     *   <li>{@code parent[nbState]} to enable reconstruction</li>
     *   <li>{@code g[nbState]} and {@code f[nbState] = g + h}</li>
     *   <li>move annotations (type + macro B/C ids)</li>
     * </ul>
     * A stale-entry check is performed by the main loop using the stored {@code f[]} values.
     * </p>
     */
    private static void relaxCore(GridIndex index,
                                  RoutingParams rp,
                                  int curState,
                                  int nbState,
                                  double stepCost,
                                  int nbPoly, int nbZ,
                                  int goalZ,
                                  Vector2f goalCenter,
                                  float cellSize,
                                  double[] g, double[] f,
                                  int[] parent,
                                  byte[] moveType,
                                  int[] macroBPoly,
                                  int[] macroCPoly,
                                  boolean[] closed,
                                  PriorityQueue<PQEntry> open,
                                  byte mt,
                                  int bPoly,
                                  int cPoly) {

        if (closed[nbState]) return;

        double tentative = g[curState] + stepCost;
        if (tentative >= g[nbState]) return; // if current path is not better, skip

        parent[nbState] = curState;
        g[nbState] = tentative;

        double h = heuristic(index, nbPoly, nbZ, goalZ, goalCenter, cellSize, rp);
        double fScore = tentative + h;
        f[nbState] = fScore;

        moveType[nbState] = mt;
        macroBPoly[nbState] = bPoly;
        macroCPoly[nbState] = cPoly;

        open.add(new PQEntry(nbState, fScore));
    }

    // --------------------------------------------------------------------------------------------
    // rules + cost
    // --------------------------------------------------------------------------------------------

    /**
     * Returns whether a cell is traversable under the grid’s basic rules.
     * <p>
     * This delegates to {@link VoxelStateGrid#isTraversableBasic(int, int)} which:
     * <ul>
     *   <li>rejects BORDER</li>
     *   <li>allows FREE/ROOM/CORRIDOR</li>
     *   <li>allows STAIRS only if walkable (has links)</li>
     * </ul>
     * </p>
     */
    private static boolean isTraversableBasic(VoxelStateGrid grid, int poly, int z) {
        return grid.isTraversableBasic(poly, z);
    }

    /**
     * Returns {@code true} if a new stairs macro is allowed to start at {@code (poly,z)}.
     * <p>
     * A stairs macro can only originate from {@code FREE_SPACE} to avoid reusing corridor/room/stairs
     * cells and to keep the macro reservation logic well-defined.
     * </p>
     */    private static boolean canStartNewStairsFrom(VoxelStateGrid grid, int poly, int z) {
        if (grid.isCorridor(poly, z)) return false;
        if (grid.isRoomBorder(poly, z)) return false;
        if (grid.isRoom(poly, z)) return false;
        if (grid.isStairs(poly, z)) return false;
        return true;
    }

    /**
     * Validates that the candidate stairs macro cells {@code B}, {@code C}, and destination {@code D}
     * satisfy both:
     * <ol>
     *   <li><b>Occupancy constraints:</b> B/C must be FREE on both layers; D must be FREE on the target layer.</li>
     *   <li><b>Temporary reservation constraints:</b> B/C must not collide with (or be adjacent to) already
     *       reserved macro cells on the current parent chain.</li>
     * </ol>
     *
     * <p>
     * This prevents “branching stairs”, overlapping macro modules, and overly tight interleaving with
     * the partially constructed path during search.
     * </p>
     */
    private static boolean stairsModuleCellsAreFreeAndTemporarilyUnreserved(
            VoxelStateGrid grid,
            int bPoly, int cPoly, int dPoly,
            int zFrom, int zTo,
            int curState,
            GridIndex index,
            int[] parent,
            byte[] moveType,
            int[] macroBPoly,
            int[] macroCPoly,
            int zBands) {

        // real grid occupancy check
        if (!(grid.isFree(bPoly, zFrom)
                && grid.isFree(bPoly, zTo)
                && grid.isFree(cPoly, zFrom)
                && grid.isFree(cPoly, zTo)
                && grid.isFree(dPoly, zTo) )) {
            return false;
        }

        // temporary reservation on current parent chain
        if (isTempReservedOrAdjacentToPath(curState, bPoly, zFrom, index, parent, moveType, macroBPoly, macroCPoly, zBands)) return false;
        if (isTempReservedOrAdjacentToPath(curState, bPoly, zTo,   index, parent, moveType, macroBPoly, macroCPoly, zBands)) return false;
        if (isTempReservedOrAdjacentToPath(curState, cPoly, zFrom, index, parent, moveType, macroBPoly, macroCPoly, zBands)) return false;
        if (isTempReservedOrAdjacentToPath(curState, cPoly, zTo,   index, parent, moveType, macroBPoly, macroCPoly, zBands)) return false;

        return true;
    }

    /**
     * Returns {@code true} if {@code (poly,z)} is either:
     * <ul>
     *   <li>already reserved by the current partial path, or</li>
     *   <li>adjacent to temporarily reserved stairs macro cells (B/C) on the relevant layers</li>
     * </ul>
     *
     * <p>
     * This is a stronger check than {@link #isTempReservedByPath(int, int, int, int[], byte[], int[], int[], int)} and is
     * used to avoid placing macro modules immediately next to each other where the mesh/junction geometry tends to break.
     * </p>
     */
    private static boolean isTempReservedOrAdjacentToPath(int state,
                                                          int poly,
                                                          int z,
                                                          GridIndex index,
                                                          int[] parent,
                                                          byte[] moveType,
                                                          int[] macroBPoly,
                                                          int[] macroCPoly,
                                                          int zBands) {
        // 1) directly reserved?
        if (isTempReservedByPath(state, poly, z, parent, moveType, macroBPoly, macroCPoly, zBands)) {
            return true;
        }

        // 2) is one of the temporary reserved stair voxels a neighbor of (poly, z)?
        int cur = state;
        while (cur != -1) {
            if (moveType[cur] == MOVE_STAIRS_MACRO) {
                int par = parent[cur];
                if (par != -1) {
                    int zFrom = unpackZ(par, zBands);
                    int zTo   = unpackZ(cur, zBands);

                    int b = macroBPoly[cur];
                    int c = macroCPoly[cur];

                    if (z == zFrom) {
                        if (poly == b || poly == c) return true;
                        if (areNeighbors(index, poly, b) || areNeighbors(index, poly, c)) return true;
                    }
                    if (z == zTo) {
                        if (poly == b || poly == c) return true;
                        if (areNeighbors(index, poly, b) || areNeighbors(index, poly, c)) return true;
                    }
                }
            }
            cur = parent[cur];
        }

        return false;
    }

    /**
     * Returns {@code true} if polygons {@code a} and {@code b} are the same cell or direct neighbors
     * according to {@link GridIndex#neighbors}.
     */
    private static boolean areNeighbors(GridIndex index, int a, int b) {
        if (a == b) return true;
        for (int nb : index.neighbors[a]) {
            if (nb == b) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code (poly,z)} is temporarily reserved by the current partial solution.
     * <p>
     * Reservation sources:
     * <ul>
     *   <li><b>Normal path cells:</b> any voxel on the parent chain, including one band above/below (vertical clearance).</li>
     *   <li><b>Stairs macros:</b> the intermediate polygons {@code B} and {@code C} are reserved on <em>both</em> layers.</li>
     * </ul>
     * </p>
     *
     * <p>
     * This reservation is used only during search to prevent invalid self-intersections and to keep stairs modules
     * from overlapping other tentative steps.
     * </p>
     */
    private static boolean isTempReservedByPath(int state,
                                                int poly,
                                                int z,
                                                int[] parent,
                                                byte[] moveType,
                                                int[] macroBPoly,
                                                int[] macroCPoly,
                                                int zBands) {

        // 1) normal path: if (poly, z) is part of the parent chain or above / under a voxel of the parent chain -> reserved
        if (isVoxelAlreadyOnAboveOrBelowParentPath(state, poly, z, parent, zBands)) {
            return true;
        }

        // 2) reserved by stair macros (B/C)
        int cur = state;
        while (cur != -1) {
            if (moveType[cur] == MOVE_STAIRS_MACRO) {
                int par = parent[cur];
                if (par != -1) {
                    int zFrom = unpackZ(par, zBands);
                    int zTo   = unpackZ(cur, zBands);

                    int zFromMin = Math.min(zFrom, zTo);
                    int zFromMax = Math.max(zFrom, zTo);

                    int b = macroBPoly[cur];
                    int c = macroCPoly[cur];

                    // B/C are blocked on both z bands + border below and above to prevent adjacency
                    if ((poly == b || poly == c) && (z == zFromMin || z == zFromMax || z == zFromMin - 1 || z == zFromMax + 1)) {
                        return true;
                    }
                }
            }
            cur = parent[cur];
        }
        return false;
    }


    /**
     * Returns {@code true} if the given polygon appears on the current parent chain at {@code z},
     * {@code z-1}, or {@code z+1}.
     * <p>
     * This acts as a conservative vertical clearance rule during search to reduce near-collisions
     * and to keep corridor volumes from clipping each other across z-bands.
     * </p>
     */
    private static boolean isVoxelAlreadyOnAboveOrBelowParentPath(int state,
                                                                  int poly,
                                                                  int z,
                                                                  int[] parent,
                                                                  int zBands) {
        int cur = state;
        while (cur != -1) {
            int p = unpackPoly(cur, zBands);
            int zz = unpackZ(cur, zBands);
            if (p == poly && zz == z) return true;
            if (p == poly && zz == z - 1) return true;
            if (p == poly && zz == z + 1) return true;
            cur = parent[cur];
        }
        return false;
    }

    /**
     * Computes the cost of a transition into {@code (toPoly,toZ)}.
     * <p>
     * This includes a base horizontal cost and applies a multiplier based on the destination cell state
     * (ROOM penalty, CORRIDOR reuse discount).
     * </p>
     */
    private static double stepCost(VoxelStateGrid grid, int toPoly, int toZ, RoutingParams rp) {
        double base = rp.horizontalCost;
        base *= cellCostMultiplier(grid, toPoly, toZ, rp);
        return base;
    }

    /**
     * Returns a multiplicative factor for destination-cell cost:
     * <ul>
     *   <li>{@code ROOM} → {@link RoutingParams#roomCostMultiplier}</li>
     *   <li>{@code CORRIDOR} → {@link RoutingParams#corridorReuseMultiplier}</li>
     *   <li>otherwise → {@code 1.0}</li>
     * </ul>
     */
    private static double cellCostMultiplier(VoxelStateGrid grid, int poly, int z, RoutingParams rp) {
        VoxelState s = grid.getState(poly, z);
        if (s == VoxelState.ROOM) return rp.roomCostMultiplier;
        if (s == VoxelState.CORRIDOR) return rp.corridorReuseMultiplier;
        return 1.0;
    }

    // --------------------------------------------------------------------------------------------
    // helper for stair segments
    // --------------------------------------------------------------------------------------------
    /**
     * Heuristic helper for stairs macros: picks a “straight-ish” next polygon.
     * <p>
     * Given {@code prev -> mid}, this selects a neighbor of {@code mid} (excluding {@code prev}) whose polygon
     * shares no vertex with {@code prev}. In the underlying quad grid topology this corresponds to continuing
     * forward rather than turning back or choosing a tightly connected adjacent cell.
     * </p>
     * @param index grid index for fast lookup
     * @param prev start polygon
     * @param mid next polygon (should be neighbor of {@code prev})
     * @return the chosen neighbor polygon id, or {@code -1} if no suitable candidate exists
     */
    private static int straightestForward(GridIndex index, int prev, int mid) {
        int[] midNbs = index.neighbors[mid];
        int[] prevVs = index.grid.getAllPolygons().get(prev).getVertexIndices();

        for (int nb : midNbs) {
            if (nb == prev) continue;

            int[] nbVs = index.grid.getAllPolygons().get(nb).getVertexIndices();
            if (!sharesAnyVertex(prevVs, nbVs)) {
                return nb;
            }
        }
        return -1;
    }

    /**
     * Helper; Returns {@code true} if the two vertex-index lists share at least one vertex index.
     * <p>
     * Used as a cheap topology test when approximating “straightness” for macro moves.
     * </p>
     */
    private static boolean sharesAnyVertex(int[] a, int[] b) {
        for (int va : a) {
            for (int vb : b) {
                if (va == vb) return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------
    // heuristic + noise
    // --------------------------------------------------------------------------------------------

    /**
     * A* heuristic for the remaining distance to the goal in a 2.5D setting.
     * <p>
     * The heuristic combines:
     * <ul>
     *   <li>Horizontal distance in the routing plane (between polygon centers), normalized by an approximate cell size</li>
     *   <li>Vertical distance as discrete {@code zBand} steps</li>
     * </ul>
     * and scales the result by {@link RoutingParams#hWeight}.
     * </p>
     *
     * <p>
     * The heuristic is designed to be fast and stable rather than perfectly admissible under all cost settings
     * (especially when noise or strong multipliers are used).
     * </p>
     */
    private static double heuristic(GridIndex index,
                                    int poly, int z,
                                    int goalZ,
                                    Vector2f goalCenter,
                                    float cellSize,
                                    RoutingParams rp) {

        Vector2f c = index.centers[poly];
        float dx = c.x - goalCenter.x;
        float dy = c.y - goalCenter.y;

        double hSteps = Math.sqrt((double) dx * dx + (double) dy * dy) / cellSize;

        int dz = Math.abs(goalZ - z);

        if (dz == Integer.MAX_VALUE) dz = 0;

        double h = hSteps * rp.horizontalCost + dz * rp.stairsCost;
        return rp.hWeight * h;
    }

    /**
     * Adds deterministic pseudo-random noise to an edge cost to break ties and avoid overly regular paths.
     * <p>
     * Noise is based on {@link RoutingParams#noiseSeed} and the directed edge descriptor {@code (aPoly,aZ)->(bPoly,bZ)}.
     * The result is in approximately {@code [-0.5*noiseWeight, +0.5*noiseWeight]}.
     * </p>
     */
    private static double edgeNoise(int aPoly, int aZ, int bPoly, int bZ, RoutingParams rp) {
        if (rp.noiseWeight == 0f) return 0.0;

        long h = 0x9E3779B97F4A7C15L;
        h ^= mix64(((long) rp.noiseSeed) * 0xD1B54A32D192ED03L);
        h ^= mix64(pack4(aPoly, aZ, bPoly, bZ));
        h = mix64(h);

        double u = ((h >>> 11) * (1.0 / (1L << 53)));
        return (u - 0.5) * rp.noiseWeight;
    }

    private static long pack4(int aPoly, int aZ, int bPoly, int bZ) {
        long x = 0;
        x |= (aPoly & 0xFFFFFFFFL);
        x = (x << 12) ^ (aZ & 0xFFFL);
        x = (x << 32) ^ (bPoly & 0xFFFFFFFFL);
        x = (x << 12) ^ (bZ & 0xFFFL);
        return x;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    // --------------------------------------------------------------------------------------------
    // packing helpers (voxel only)
    // --------------------------------------------------------------------------------------------

    /**
     * Packs {@code (poly,z)} into a single integer state id.
     * <p>
     * Layout: {@code state = poly * zBands + z}. This matches {@link VoxelStateGrid#pack(int, int)}.
     * </p>
     */
    private static int packVoxel(int poly, int z, int zBands) { return poly * zBands + z; }

    /** Extracts {@code poly} from a packed voxel id produced by {@link #packVoxel(int, int, int)}. */
    private static int unpackPoly(int packedVoxel, int zBands) { return packedVoxel / zBands; }

    /** Extracts {@code z} from a packed voxel id produced by {@link #packVoxel(int, int, int)}. */
    private static int unpackZ(int packedVoxel, int zBands) { return packedVoxel - (packedVoxel / zBands) * zBands; }

    // --------------------------------------------------------------------------------------------
    // reconstruction
    // --------------------------------------------------------------------------------------------

    /**
     * Reconstructs the final path by following {@code parent[]} links from {@code goalState} back to the start.
     * <p>
     * Normal steps become a single {@link NodeState}. If a step was annotated as {@link #MOVE_STAIRS_MACRO},
     * it is expanded into the corresponding multi-step sequence:
     * </p>
     * <pre>
     * A(zFrom) -> B(zFrom) -> B(zTo) -> C(zFrom) -> C(zTo) -> D(zTo)
     * </pre>
     *
     * <p>
     * After reconstruction, consecutive duplicate {@link NodeState}s are removed.
     * </p>
     *
     * @param goalState end state id (packed)
     * @return expanded, cleaned path from start to goal
     */
    private static List<NodeState> reconstructExpanded(int goalState,
                                                       int[] parent,
                                                       byte[] moveType,
                                                       int[] macroBPoly,
                                                       int[] macroCPoly,
                                                       int zBands) {

        ArrayDeque<NodeState> out = new ArrayDeque<>();
        int cur = goalState;

        while (cur != -1) {
            int curPoly = unpackPoly(cur, zBands);
            int curZ = unpackZ(cur, zBands);

            int par = parent[cur];
            if (par == -1) {
                out.addFirst(new NodeState(curPoly, curZ));
                break;
            }

            byte mt = moveType[cur];
            if (mt == MOVE_STAIRS_MACRO) {
                int aPoly = unpackPoly(par, zBands);
                int aZ = unpackZ(par, zBands);

                int dPoly = curPoly;
                int dZ = curZ;

                int bPoly = macroBPoly[cur];
                int cPoly = macroCPoly[cur];

                // A(zFrom) -> B(zFrom) -> B(zTo) -> C(zFrom) -> C(zTo) -> D(zTo)
                out.addFirst(new NodeState(dPoly, dZ));
                out.addFirst(new NodeState(cPoly, dZ));
                out.addFirst(new NodeState(cPoly, aZ));
                out.addFirst(new NodeState(bPoly, dZ));
                out.addFirst(new NodeState(bPoly, aZ));
                out.addFirst(new NodeState(aPoly, aZ));

                cur = par;
                continue;
            }

            out.addFirst(new NodeState(curPoly, curZ));
            cur = par;
        }

        ArrayList<NodeState> cleaned = new ArrayList<>(out.size());
        NodeState prev = null;
        for (NodeState s : out) {
            if (prev != null && prev.polyId() == s.polyId() && prev.zBand() == s.zBand()) continue;
            cleaned.add(s);
            prev = s;
        }
        return cleaned;
    }
}