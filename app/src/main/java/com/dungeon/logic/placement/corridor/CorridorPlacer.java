package com.dungeon.logic.placement.corridor;

import com.dungeon.config.DungeonConfig;
import com.dungeon.logic.factory.CorridorFactory;
import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.placement.corridor.connections.DelaunayTriangulation2D;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.placement.corridor.connections.MST;
import com.dungeon.logic.placement.corridor.connections.RoomEdge;
import com.dungeon.logic.placement.corridor.routing.block.RoomVolume2_5D;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateBuilder;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.dungeon.logic.placement.corridor.routing.grid.GridQueries;
import com.dungeon.logic.placement.corridor.routing.path.NodeState;
import com.dungeon.logic.placement.corridor.routing.path.PathFinder3D;
import com.dungeon.logic.placement.corridor.routing.path.RoutingParams;
import com.jme3.math.Vector2f;

import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

import static com.dungeon.config.DungeonConfig.*;

/**
 * Generates corridors between rooms by:
 * <ol>
 *   <li>Building a refined routing grid (separate from the placement grid).</li>
 *   <li>Marking room volumes as occupied in a {@link VoxelStateGrid}.</li>
 *   <li>Building candidate room connections using Delaunay triangulation + MST (+ optional extra edges).</li>
 *   <li>Routing each selected edge with {@link PathFinder3D} in a 2.5D voxel space (poly + z-band).</li>
 *   <li>Committing each routed path to the occupancy grid and creating a {@link Corridor} via {@link CorridorFactory}.</li>
 *   <li>Optionally removing rooms that are not part of the largest connected corridor component and
 *       filtering out corridors that reference removed rooms.</li>
 * </ol>
 *
 * <p>
 * The class is deterministic for a fixed {@code seed}, but corridor routing may still vary with
 * configuration parameters (noise, costs, etc. in {@link RoutingParams}).
 * </p>
 */
public final class CorridorPlacer {

    private static final Logger LOGGER = Logger.getLogger(CorridorPlacer.class.getName());
    private static boolean LOGGER_CONFIGURED = false;

    /**
     * Corridor density preset controlling the number of additional edges added beyond the MST.
     */
    public enum Density {
        /** Only the MST edges (minimal connectivity). */
        SPARSE(0.0),
        /** MST + a small fraction of additional short edges. */
        MEDIUM(0.2),
        /** MST + more additional short edges (denser corridor networks). */
        DENSE(0.35);

        private final double extraRatio;

        Density(double r) { this.extraRatio = r; }

        /**
         * @return ratio in [0..1] used to decide how many extra edges to add from the candidate pool
         */
        public double extraEdgeRatio() { return extraRatio; }
    }

    /** Refined routing grid used for voxel-based 2.5D pathfinding. */
    private final BaseGrid routingGrid;

    /** Corridor network density preset. */
    private final Density density;

    /** Weight factor for vertical distance when computing connection weights (2.5D). */
    private final double lambdaZ;

    /** Corridor width in world units. */
    private final float corridorWidth;

    /** Corridor height in world units. */
    private final float corridorHeight;

    /** Wall thickness used when building corridor meshes / geometry. */
    private final float wallThickness;

    /** Random seed used for routing grid generation and randomized edge selection. */
    private final long seed;

    // --- debug only ---

    /**
     * Last voxel occupancy state that was built during {@link #generateCorridors(List)}.
     * Exposed for visualization/debug tools.
     */
    private transient VoxelStateGrid debugLastState;

    /**
     * @return the voxel state grid from the last corridor generation run (may be {@code null} if never run)
     */
    public VoxelStateGrid getDebugLastState() { return debugLastState; }

    /**
     * Creates a corridor placer that builds an internal refined routing grid from the given base grid.
     *
     * @param baseGrid base grid used to derive the routing grid resolution
     * @param density corridor density preset (controls extra edges beyond MST)
     * @param seed random seed
     */
    public CorridorPlacer(BaseGrid baseGrid, Density density, long seed) {
        this(baseGrid, density,
                LAMBDA_Z,
                DungeonConfig.F,
                NUM_OF_BUFFER_RINGS,
                CORRIDOR_WIDTH,
                CORRIDOR_HEIGHT,
                SAFETY_MARGIN,
                WALL_THICKNESS,
                seed);
    }

    /**
     * Internal constructor allowing full parameter control.
     */
    private CorridorPlacer(BaseGrid baseGrid,
                           Density density,
                           double lambdaZ,
                           int refineFactor,
                           int bufferRings,
                           float corridorWidth,
                           float corridorHeight,
                           float safetyMargin,
                           float wallThickness,
                           long seed) {

        Objects.requireNonNull(baseGrid, "baseGrid");
        this.density = Objects.requireNonNull(density, "density");

        configureLoggerOnce();

        this.lambdaZ = lambdaZ;
        this.corridorWidth = corridorWidth;
        this.corridorHeight = corridorHeight;
        this.wallThickness = wallThickness;
        this.seed = seed;

        int routingSideCount = baseGrid.getSideCount() * refineFactor + bufferRings;
        float routingEdgeLength = Math.max(baseGrid.getEdgeLength(), 4f * (corridorWidth + 2f * safetyMargin + 2f * wallThickness));

        this.routingGrid = new BaseGridBuilder().build(new BaseGridConfig(routingSideCount, routingEdgeLength), seed);
    }

    /**
     * @return the refined routing grid used for voxel pathfinding
     */
    public BaseGrid getRoutingGrid() { return routingGrid; }

    /**
     * Generates corridors between the given rooms.
     *
     * <p>Algorithm outline:</p>
     * <ol>
     *   <li>Build initial voxel occupancy state by rasterizing room volumes.</li>
     *   <li>Build candidate room connections (Delaunay + MST + optional extras).</li>
     *   <li>Route each selected connection with {@link PathFinder3D} and commit the resulting path.</li>
     *   <li>If not all planned edges could be routed, remove rooms not connected to the largest corridor component
     *       and remove corridors referencing removed rooms.</li>
     * </ol>
     *
     * @param rooms list of rooms to connect (modified in-place if unconnected rooms are removed)
     * @return list of generated corridors (may be fewer than the selected edges if routing fails)
     */
    public List<Corridor> generateCorridors(List<Room> rooms) {
        if (rooms == null || rooms.size() < 2) return List.of();

        LOGGER.info("Generating corridors for " + rooms.size() + " rooms with density " + density);
        LOGGER.info("Start building grid index and initial occupancy...");

        GridIndex index = new GridIndex(routingGrid);

        // ---- initial occupancy: mark room voxels as ROOM ----
        List<RoomVolume2_5D> volumes = rooms.stream().map(RoomVolume2_5D::new).toList();

        VoxelStateBuilder vsb = new VoxelStateBuilder(index);
        float clearanceRadius = index.grid.getEdgeLength() * 0.3f;
        VoxelStateGrid state = vsb.buildInitialState(volumes, clearanceRadius);
        this.debugLastState = state;

        // ---- routing params ----
        RoutingParams rp = new RoutingParams();
        rp.noiseSeed = (int) seed;

        // ---- choose edges ----
        LOGGER.info(" Building connections...");
        List<RoomEdge> edges = buildConnections(rooms, lambdaZ, density, seed);

        ArrayList<Corridor> out = new ArrayList<>();
        ArrayList<RoomEdge> foundEdges = new ArrayList<>();
        int corridorId = 1;

        // ---- route corridors ----
        LOGGER.info(" Routing corridors...");
        for (RoomEdge e : edges) {
            Room a = e.getA();
            Room b = e.getB();

            LOGGER.info("     Routing corridor " + corridorId + "/" + edges.size()
                    + " between " + a.getId() + " and " + b.getId());

            int startPoly = findNearestPoly(index, a);
            int goalPoly  = findNearestPoly(index, b);
            if (startPoly < 0 || goalPoly < 0) continue;

            int startZ = clampBand(a.getZBandIndex(), state.zBands());
            int goalZ  = clampBand(b.getZBandIndex(), state.zBands());

            List<NodeState> path = PathFinder3D.findPath(
                    index,
                    startPoly, startZ,
                    goalPoly, goalZ,
                    state,
                    rp
            );

            if (path == null || path.size() < 2) {
                LOGGER.info("     Couldn't find path between " + a.getId() + " and " + b.getId());
                continue;
            }

            commitPath(state, path);

            Corridor c = CorridorFactory.createFromPath(
                    a.getId(), b.getId(),
                    index, path,
                    corridorWidth, corridorHeight, wallThickness
            );

            foundEdges.add(e);
            out.add(c);
            corridorId++;
        }

        // fallback if some rooms ended up unconnected
        if (out.size() < edges.size()) {
            List<Integer> roomsToDelete = findUnconnectedRooms(rooms, foundEdges);

            // 1) filter rooms
            rooms.removeIf(r -> roomsToDelete.contains(r.getId()));

            // 2) allowed roomIds set
            Set<Integer> keep = new HashSet<>();
            for (Room r : rooms) keep.add(r.getId());

            // 3) filter corridors + foundEdges in reverse to avoid index shift issues
            for (int i = foundEdges.size() - 1; i >= 0; i--) {
                RoomEdge e = foundEdges.get(i);
                int aId = e.getA().getId();
                int bId = e.getB().getId();

                if (!keep.contains(aId) || !keep.contains(bId)) {
                    foundEdges.remove(i);
                    out.remove(i);
                }
            }
        }

        LOGGER.info("Finished generating corridors. Total corridors created: " + out.size());
        return out;
    }

    /**
     * Identifies rooms that are not part of the largest connected component induced by {@code foundEdges}.
     *
     * @param rooms rooms currently considered for the dungeon
     * @param foundEdges successfully routed room edges
     * @return list of room IDs that should be removed
     */
    private List<Integer> findUnconnectedRooms(List<Room> rooms, ArrayList<RoomEdge> foundEdges) {
        ArrayList<ConnectedGraph> gs = new ArrayList<>();

        for (RoomEdge e : foundEdges) {
            ConnectedGraph hit = null;
            for (ConnectedGraph g : gs) {
                if (g.add(e)) { hit = g; break; }
            }
            if (hit == null) {
                hit = new ConnectedGraph();
                hit.add(e);
                gs.add(hit);
            }

            // merge everything that now touches "hit"
            for (int i = 0; i < gs.size(); i++) {
                ConnectedGraph g = gs.get(i);
                if (g != hit && hit.touches(g)) {
                    hit.mergeFrom(g);
                    gs.remove(i--);
                }
            }
        }

        ConnectedGraph largest = gs.stream()
                .max(Comparator.comparingInt(g -> g.getRoomIds().size()))
                .orElse(new ConnectedGraph());

        ArrayList<Integer> remove = new ArrayList<>();
        for (Room r : rooms) {
            if (!largest.getRoomIds().contains(r.getId())) remove.add(r.getId());
        }
        return remove;
    }

    // ---------------- start poly selection ----------------

    /**
     * Finds the nearest routing-grid polygon for the room's interior point.
     */
    private int findNearestPoly(GridIndex index, Room room) {
        Vector2f c = room.getInteriorPoint();
        return GridQueries.nearestPoly(index, c);
    }

    // ---------------- commit occupancy ----------------

    /**
     * Commits a routed path to the voxel state grid.
     * <p>
     * Marks STAIRS cells first (with links), then CORRIDOR cells, then blocks vertical clearance using BORDER.
     * </p>
     */
    private static void commitPath(VoxelStateGrid state, List<NodeState> path) {
        if (path == null || path.size() < 2) return;

        final int n = path.size();
        final int zBands = state.zBands();

        final int[] packed = new int[n];
        final int[] poly   = new int[n];
        final int[] z      = new int[n];

        for (int i = 0; i < n; i++) {
            NodeState s = path.get(i);
            poly[i] = s.polyId();
            z[i] = s.zBand();
            packed[i] = state.pack(poly[i], z[i]);
        }

        final boolean[] isStairCell = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean verticalPrev = (i > 0)     && (z[i] != z[i - 1]);
            boolean verticalNext = (i < n - 1) && (z[i] != z[i + 1]);
            isStairCell[i] = verticalPrev || verticalNext;
        }

        // 1) STAIRS
        for (int i = 0; i < n; i++) {
            if (!isStairCell[i]) continue;

            int p = poly[i];
            int zz = z[i];

            if (state.isRoom(p, zz) || state.isRoomBorder(p, zz)) continue;
            if (state.isStairs(p, zz)) continue;
            if (!state.isFree(p, zz)) continue;

            int linkA = (i > 0)     ? packed[i - 1] : -1;
            int linkB = (i < n - 1) ? packed[i + 1] : -1;
            if (linkA == linkB) linkB = -1;

            state.setStairsWalkable(p, zz, linkA, linkB);
        }

        // 2) CORRIDOR
        for (int i = 0; i < n; i++) {
            int p = poly[i];
            int zz = z[i];

            if (state.isRoomBorder(p, zz)) continue;
            if (isStairCell[i]) continue;
            if (state.isStairs(p, zz)) continue;

            state.setCorridor(p, zz);
        }

        // 3) BORDER vertical clearance
        for (int i = 0; i < n; i++) {
            int p  = poly[i];
            int zz = z[i];

            boolean isCommittedCorridor = state.isCorridor(p, zz);
            boolean isCommittedStairs   = state.isStairs(p, zz);
            if (!isCommittedCorridor && !isCommittedStairs) continue;

            markBorderIfFree(state, p, zz - 1, zBands);
            markBorderIfFree(state, p, zz + 1, zBands);
        }
    }

    private static void markBorderIfFree(VoxelStateGrid state, int poly, int z, int zBands) {
        if (z < 0 || z >= zBands) return;

        if (state.isRoom(poly, z)) return;
        if (state.isCorridor(poly, z)) return;
        if (state.isStairs(poly, z)) return;

        if (state.isFree(poly, z)) {
            state.setBorder(poly, z);
        }
    }

    // ---------------- connections (Delaunay + MST + extras) ----------------

    /**
     * Builds the list of room edges to route:
     * Delaunay candidate edges -> weighted -> MST -> optional extra edges depending on {@link Density}.
     */
    private static List<RoomEdge> buildConnections(List<Room> rooms,
                                                   double lambdaZ,
                                                   Density density,
                                                   long seed) {
        DelaunayTriangulation2D dt = new DelaunayTriangulation2D();
        List<RoomEdge> candidates = dt.triangulateEdges(rooms);

        for (RoomEdge e : candidates) {
            double w = weight25D(e.getA(), e.getB(), lambdaZ);
            e.setWeight(w);
        }

        List<RoomEdge> mst = MST.minimumSpanningTree(rooms, candidates);
        if (density == Density.SPARSE) return mst;

        return addExtras(mst, candidates, density, seed);
    }

    /**
     * Computes the 2.5D weight between rooms: 2D Euclidean distance + lambdaZ * vertical distance.
     */
    private static double weight25D(Room a, Room b, double lambdaZ) {
        Vector2f ca = a.getInteriorPoint();
        Vector2f cb = b.getInteriorPoint();

        double dx = ca.x - cb.x;
        double dy = ca.y - cb.y;
        double horizontal = Math.sqrt(dx * dx + dy * dy);

        double dz = Math.abs(a.getZLevel() - b.getZLevel());
        return horizontal + lambdaZ * dz;
    }

    /**
     * Adds a number of extra edges (shortest candidates first, then shuffled) based on density ratio.
     */
    private static List<RoomEdge> addExtras(List<RoomEdge> mst,
                                            List<RoomEdge> candidates,
                                            Density density,
                                            long seed) {
        HashSet<RoomEdge> inMst = new HashSet<>(mst);

        ArrayList<RoomEdge> remaining = new ArrayList<>();
        for (RoomEdge e : candidates) {
            if (!inMst.contains(e)) remaining.add(e);
        }

        remaining.sort(Comparator.comparingDouble(RoomEdge::getWeight));

        double ratio = density.extraEdgeRatio();
        int k = (int) Math.round(ratio * remaining.size());
        if (k <= 0) return mst;

        int pool = Math.min(remaining.size(), Math.max(k, 2 * k));
        ArrayList<RoomEdge> poolEdges = new ArrayList<>(remaining.subList(0, pool));
        Collections.shuffle(poolEdges, new Random(seed));

        ArrayList<RoomEdge> out = new ArrayList<>(mst);
        for (int i = 0; i < Math.min(k, poolEdges.size()); i++) out.add(poolEdges.get(i));
        return out;
    }

    // ---------------- misc ----------------

    /**
     * Clamps a z-band index into [0, zBands-1].
     */
    private static int clampBand(int z, int zBands) {
        if (z < 0) return 0;
        if (z >= zBands) return zBands - 1;
        return z;
    }

    // ---------------- helper class for unconnected rooms cleanup ----------------

    /**
     * Small union/merge helper representing a connected component of room IDs.
     * Used for identifying the largest connected corridor component.
     */
    static class ConnectedGraph {
        private final Set<Integer> roomIds = new HashSet<>();

        /**
         * Adds an edge to this component if it touches the component or if the component is empty.
         *
         * @param e room edge
         * @return true if the edge was merged into this component, false otherwise
         */
        boolean add(RoomEdge e) {
            int a = e.getA().getId();
            int b = e.getB().getId();

            if (roomIds.isEmpty() || roomIds.contains(a) || roomIds.contains(b)) {
                roomIds.add(a);
                roomIds.add(b);
                return true;
            }
            return false;
        }

        /**
         * @return true if this component shares at least one room ID with {@code other}
         */
        boolean touches(ConnectedGraph other) {
            if (roomIds.size() > other.roomIds.size()) return other.touches(this);
            for (int id : roomIds) if (other.roomIds.contains(id)) return true;
            return false;
        }

        /**
         * Merges all room IDs from {@code other} into this component.
         */
        void mergeFrom(ConnectedGraph other) {
            roomIds.addAll(other.roomIds);
        }

        /**
         * @return live set of room IDs in this connected component
         */
        Set<Integer> getRoomIds() { return roomIds; }
    }

    // ---------------- logger config ----------------

    private static void configureLoggerOnce() {
        if (LOGGER_CONFIGURED) return;
        LOGGER_CONFIGURED = true;

        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);

        for (Handler h : LOGGER.getHandlers()) {
            LOGGER.removeHandler(h);
            try { h.close(); } catch (Exception ignored) {}
        }

        Handler h = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord r) {
                return "[CorridorPlacer] " + r.getLevel().getName() + ": "
                        + formatMessage(r) + System.lineSeparator();
            }
        }) {
            @Override public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };

        h.setLevel(Level.ALL);
        LOGGER.addHandler(h);
    }
}