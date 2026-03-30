package com.dungeon.logic.placement.room;

import com.dungeon.logic.factory.RoomFactory;
import com.dungeon.logic.placement.room.validation.RoomValidator;
import com.dungeon.domain.Room;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.geometry.VertexCluster;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.placement.room.boundary.BoundaryPolygonCollector;
import com.dungeon.logic.placement.room.boundary.RoomOutlineBuilder;
import com.dungeon.logic.placement.room.cluster.ClusterSampler;
import com.dungeon.logic.placement.room.debug.DebugRoomData;
import com.dungeon.logic.placement.room.params.RoomParameterSampler;
import com.jme3.math.Vector2f;

import java.util.*;
import java.util.logging.Formatter;
import java.util.logging.*;

import static com.dungeon.config.DungeonConfig.MAX_ROOM_PLACEMENT_ATTEMPTS;
import static com.dungeon.config.DungeonConfig.WALL_THICKNESS;

/**
 * Places {@link Room} instances on a {@link BaseGrid} by sampling connected vertex clusters and
 * converting them into valid 2.5D room volumes.
 *
 * <p>The placer is intentionally <b>stochastic</b> (seed-based) and uses a retry strategy.
 * For each requested room it repeatedly:
 * <ol>
 *   <li>Selects a random start vertex that is not already occupied by a previously accepted room.</li>
 *   <li>Grows a connected {@link VertexCluster} around that vertex to match a target size.</li>
 *   <li>Extracts valid boundary polygons via {@link BoundaryPolygonCollector}.</li>
 *   <li>Builds a simple room outline (footprint) using {@link RoomOutlineBuilder}.</li>
 *   <li>Samples 2.5D parameters (height bands, z-band index, rotation) using {@link RoomParameterSampler}.</li>
 *   <li>Creates a {@link Room} candidate via {@link RoomFactory}.</li>
 *   <li>Validates the candidate against already placed rooms using {@link RoomValidator}.</li>
 * </ol>
 *
 * <h2>Collision / validity model</h2>
 * <p>Room collisions are checked conservatively by {@link RoomValidator}. This is designed to be fast and
 * may reject some placements that would be valid with an exact polygon intersection test
 * (i.e., it can produce false positives), but should avoid accepting obvious overlaps.</p>
 *
 * <h2>Determinism</h2>
 * <p>Given the same input grid, configuration constants (see {@code DungeonConfig}), and seed,
 * the placement process is reproducible. Changing parameters such as grid resolution, wall thickness,
 * or maximum attempts can change the outcome.</p>
 */
public final class RoomPlacer {

    private static final Logger LOGGER = Logger.getLogger(RoomPlacer.class.getName());
    private static boolean LOGGER_CONFIGURED = false;

    /** Random source driving all stochastic sampling steps in this placer instance. */
    private final Random random;

    /**
     * Thickness used for the room "floor slab" in the generated {@link Room}.
     * <p>Currently derived from {@link com.dungeon.config.DungeonConfig#WALL_THICKNESS} to keep the
     * existing project configuration consistent.</p>
     */
    private final float roomFloorThickness;

    /** Minimum desired cluster size (in vertices) for room footprints. */
    private final int minRoomSize;

    /** Maximum desired cluster size (in vertices) for room footprints. */
    private final int maxRoomSize;

    /** side length of the base grid (used for chamfer). */
    private final float gridEdgeLength;

    /** Samples connected vertex clusters on the grid (start vertex + growth). */
    private final ClusterSampler clusterSampler;

    /** Extracts boundary polygons from a cluster for outline generation. */
    private final BoundaryPolygonCollector polygonCollector;

    /** Builds a single footprint polygon (room outline) from boundary polygons and cluster state. */
    private final RoomOutlineBuilder outlineBuilder;

    /** Samples 2.5D room parameters (height, z band, rotation). */
    private final RoomParameterSampler paramSampler;

    /**
     * Creates a room placer for a given base grid.
     *
     * @param grid        base grid used for cluster sampling and footprint construction
     * @param minRoomSize minimum desired cluster size (in vertices), must be {@code >= 1}
     * @param maxRoomSize maximum desired cluster size (in vertices), must be {@code >= minRoomSize}
     * @param seed        seed for stochastic steps (cluster growth, parameter sampling)
     * @throws IllegalArgumentException if {@code grid} is {@code null} or size parameters are invalid
     */
    public RoomPlacer(BaseGrid grid, int minRoomSize, int maxRoomSize, long seed) {
        if (grid == null) throw new IllegalArgumentException("grid must not be null");
        if (minRoomSize < 1) throw new IllegalArgumentException("minRoomSize must be >= 1");
        if (maxRoomSize < minRoomSize) throw new IllegalArgumentException("maxRoomSize must be >= minRoomSize");

        configureLoggerOnce();

        this.roomFloorThickness = WALL_THICKNESS;
        this.random = new Random(seed);

        this.clusterSampler = new ClusterSampler(grid, random);
        this.polygonCollector = new BoundaryPolygonCollector(grid);
        this.outlineBuilder = new RoomOutlineBuilder(grid);
        this.paramSampler = new RoomParameterSampler(random);

        this.minRoomSize = minRoomSize;
        this.maxRoomSize = maxRoomSize;
        this.gridEdgeLength = grid.getEdgeLength();
    }

    /**
     * Attempts to generate up to {@code numRooms} rooms.
     *
     * <p>Rooms are produced sequentially. For each room index, the placer retries up to
     * {@link com.dungeon.config.DungeonConfig#MAX_ROOM_PLACEMENT_ATTEMPTS} times until a valid candidate
     * is found. If no valid room can be produced within the attempt budget, that room is skipped and
     * generation continues with the next index.</p>
     *
     * @param numRooms number of rooms to attempt to generate
     * @return list of accepted rooms (may be smaller than {@code numRooms})
     */
    public List<Room> generateRooms(int numRooms) {
        LOGGER.log(Level.INFO, "Starting to generate " + numRooms + " rooms...");

        Set<Integer> usedVertices = new HashSet<>();
        List<Room> generatedRooms = new ArrayList<>();

        for (int i = 0; i < numRooms; i++) {
            LOGGER.log(Level.INFO, "Generating room " + (i + 1) + " of " + numRooms + "...");

            Room accepted = null;
            VertexCluster acceptedCluster = null;

            int attempts = 0;
            while (attempts++ < MAX_ROOM_PLACEMENT_ATTEMPTS) {
                LOGGER.log(Level.INFO,
                        "  Attempt (" + attempts + "/" + MAX_ROOM_PLACEMENT_ATTEMPTS + ") to generate room " + (i + 1));
                LOGGER.info("    Picking random start vertex...");

                int startVertex = clusterSampler.pickRandomStartVertex(usedVertices);
                if (startVertex == -1) break;

                int roomSize = minRoomSize + random.nextInt(maxRoomSize - minRoomSize + 1);

                LOGGER.log(Level.INFO, "    Generating cluster of size " + roomSize + "...");
                VertexCluster cluster = clusterSampler.getConnectedCluster(startVertex, roomSize);

                LOGGER.info("    Collecting boundary polygons for cluster...");
                List<Polygon> polygons = polygonCollector.getValidPolygonsForCluster(cluster);
                if (polygons.isEmpty()) continue;

                LOGGER.info("    Computing room outline...");
                List<Vector2f> corners = outlineBuilder.computeRoomPolygon(polygons, cluster);
                if (corners.size() < 3) continue;

                int heightBands = paramSampler.generateRoomHeightBands();
                int zBand = paramSampler.generateZBandIndex(heightBands);
                float rot = paramSampler.generateRotation();

                LOGGER.info("    Creating room candidate...");
                Room candidate = RoomFactory.create(
                        corners,
                        heightBands,
                        roomFloorThickness,
                        zBand,
                        rot,
                        gridEdgeLength
                );

                LOGGER.info("    Validating room candidate...");
                if (RoomValidator.isValid(candidate, generatedRooms)) {
                    LOGGER.info("    Room candidate accepted.");
                    accepted = candidate;
                    acceptedCluster = cluster;
                    break;
                }

                LOGGER.info("    Room candidate rejected. Trying again...");
            }

            if (accepted != null) {
                LOGGER.log(Level.INFO, "  Room " + (i + 1) + " generated successfully.");
                generatedRooms.add(accepted);
                usedVertices.addAll(acceptedCluster.getVertices());
            } else {
                LOGGER.log(Level.INFO,
                        "  Failed to generate room " + (i + 1) + " after " + MAX_ROOM_PLACEMENT_ATTEMPTS + " attempts.");
            }
        }

        return generatedRooms;
    }

    // ---------------- debug ----------------
    public DebugRoomData generateSingleDebugRoom(int desiredRoomSize) {
        final int MAX_ATTEMPTS = 100;

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            int startVertex = clusterSampler.pickRandomStartVertex(Set.of());
            if (startVertex == -1) break;

            VertexCluster cluster = clusterSampler.getConnectedCluster(startVertex, desiredRoomSize);

            List<Polygon> polygons = polygonCollector.getValidPolygonsForCluster(cluster);
            if (polygons.isEmpty()) continue;

            List<Vector2f> corners = outlineBuilder.computeRoomPolygon(polygons, cluster);
            if (corners.size() < 3) continue;

            int heightBands = paramSampler.generateRoomHeightBands();
            int zBand = paramSampler.generateZBandIndex(heightBands);
            float rot = 0;

            Room room = RoomFactory.create(corners, heightBands, roomFloorThickness, zBand, rot, gridEdgeLength);
            return new DebugRoomData(room, cluster, polygons);
        }

        return null;
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
                return "[RoomPlacer] " + r.getLevel().getName() + ": "
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