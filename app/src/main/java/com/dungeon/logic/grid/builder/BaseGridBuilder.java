package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.topology.TopologyBuilder;
import com.jme3.math.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.*;

/**
 * Constructs a {@link BaseGrid} from a {@link BaseGridConfig}.
 * <p>
 * The builder implements a deterministic pipeline (except for the random triangle pairing step):
 * </p>
 * <ol>
 *   <li>Generate a hex-like point set ({@link HexPointGenerator}).</li>
 *   <li>Connect the points to an initial triangle mesh ({@link TriangleConnector}).</li>
 *   <li>Randomly merge adjacent triangle pairs into larger base polygons ({@link TrianglePairer}).</li>
 *   <li>Split base polygons into quads by inserting polygon centers and edge midpoints
 *       ({@link PolygonQuadSplitter}).</li>
 *   <li>Build topology maps once ({@link TopologyBuilder}) (topology is index-based and thus stable).</li>
 *   <li>Apply Laplacian relaxation to smooth vertex positions ({@link LaplacianRelaxer}).</li>
 *   <li>Recompute polygon centers after relaxation (because positions changed).</li>
 * </ol>
 */
public final class BaseGridBuilder {

    private static final Logger LOGGER = Logger.getLogger(BaseGridBuilder.class.getName());
    private static boolean LOGGER_CONFIGURED = false;

    /**
     * Builds a {@link BaseGrid} using the provided configuration.
     *
     * @param cfg configuration parameters (grid size, edge length, relaxation parameters)
     * @param seed random seed for any stochastic steps
     * @return fully constructed base grid including derived topology maps
     */
    public BaseGrid build(BaseGridConfig cfg, long seed) {
        if (cfg == null) throw new IllegalArgumentException("cfg must not be null");
        configureLoggerOnce();

        LOGGER.info("Building BaseGrid with sideCount=" + cfg.sideCount + ", edgeLength=" + cfg.edgeLength);
        LOGGER.info("Starting grid generation...");

        // 1) Generate point grid
        LOGGER.info("Step 1: Generating point grid...");
        List<Vector2f> vertices = HexPointGenerator.generate(cfg.sideCount, cfg.edgeLength);

        // marker for original vertex count (before adding split vertices)
        int originalVertexCount = vertices.size();

        // 2) Connect points into triangles
        LOGGER.info("Step 2: Connecting points into triangles...");
        List<Polygon> triangles = TriangleConnector.connect(vertices, cfg.sideCount);

        // 3) Random pairing of adjacent triangles into larger base polygons
        LOGGER.info("Step 3: Pairing triangles into base polygons...");
        TrianglePairer.PairingResult pairing = TrianglePairer.pair(vertices, triangles, new Random(seed));
        List<Polygon> basePolys = pairing.basePolygons();

        // 4) Split polygons into quads (adds vertices: edge midpoints and polygon centers)
        LOGGER.info("Step 4: Splitting polygons into quads...");
        PolygonQuadSplitter.SplitResult split = PolygonQuadSplitter.splitToQuads(vertices, basePolys);
        List<Polygon> allPolygons = split.polygons();

        // 5) Build topology maps ONCE (indices/connectivity stay stable; relaxation only moves positions)
        LOGGER.info("Step 5: Building topology...");
        TopologyBuilder.Result topo = TopologyBuilder.build(vertices, allPolygons);

        // 6) Relax vertices in-place (positions change, indices/topology remain unchanged)
        LOGGER.info("Step 6: Relaxing vertices...");
        LaplacianRelaxer.relax(vertices, topo.vertexNeighbors(), cfg.relaxAlpha, cfg.relaxIterations);

        // 7) Recompute polygon centers ONLY (because vertex positions changed)
        LOGGER.info("Step 7: Recomputing polygon centers...");
        Map<Polygon, Vector2f> polygonCenters = TopologyBuilder.computePolygonCenters(vertices, allPolygons);

        LOGGER.info("BaseGrid build complete.");

        return new BaseGrid(
                cfg.sideCount,
                cfg.edgeLength,
                originalVertexCount,
                vertices,
                triangles,
                allPolygons,
                polygonCenters,
                topo.vertexNeighbors(),
                topo.validVertexNeighbors(),
                topo.vertexToPolygons(),
                topo.polygonNeighbors()
        );
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
                return "[BaseGridBuilder] " + r.getLevel().getName() + ": "
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