package com.analysis;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.mesh.DungeonMesh;
import com.dungeon.logic.mesh.builder.DungeonMeshBuilder;
import com.dungeon.logic.placement.corridor.CorridorPlacer;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.builder.CorridorNetworkBuilder;
import com.dungeon.logic.placement.room.RoomPlacer;
import com.jme3.scene.Mesh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Empirical performance benchmark for the Organic Dungeon Generator pipeline.
 *
 * <p>Measures step times for:</p>
 * <ol>
 *   <li>Base grid build</li>
 *   <li>Room placement</li>
 *   <li>Corridor generation</li>
 *   <li>Corridor network build</li>
 *   <li>Dungeon mesh build (outer + inner)</li>
 * </ol>
 *
 * <p><b>Important benchmarking notes</b>:</p>
 * <ul>
 *   <li><b>Warm-up</b>: The first iterations are typically dominated by JVM class loading and JIT compilation.
 *       Therefore, this benchmark performs per-size warm-up runs and does not record them in the CSV.</li>
 *   <li><b>Seed variance</b>: Dungeon topology can vary significantly with different seeds
 *       (junction counts, corridor lengths, overlap situations, and thus mesh complexity).
 *       To reduce noise, multiple seeds are measured per problem size.</li>
 * </ul>
 *
 * <p>Problem size doubles each run: 2, 4, 8, ... up to the configured maximum.</p>
 * <p>Writes all measured runs to a CSV file for later aggregation.</p>
 */
public final class EmpiricalPerformanceBenchmark {

    private static final int MIN_ROOMS = 2;

    private static final int MAX_ROOMS = 128;

    private static final int MIN_ROOM_SIZE = 15;
    private static final int MAX_ROOM_SIZE = 25;

    private static final CorridorPlacer.Density DENSITY = CorridorPlacer.Density.MEDIUM;

    /**
     * Fixed base seed for reproducibility.
     */
    private static final long BASE_SEED = 1337L;

    /** Warm-up runs per N (not written to CSV). */
    private static final int WARMUP_RUNS = 2;

    /** Measured runs (different seeds) per N (written to CSV). */
    private static final int MEASURE_RUNS = 5;

    private EmpiricalPerformanceBenchmark() {}

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);

        Path resultsDir = Path.of("results");
        Files.createDirectories(resultsDir);

        Path out = resultsDir.resolve("benchmark_results_" + Instant.now().toString().replace(":", "-") + ".csv");
        System.out.println("Writing benchmark CSV to: " + out.toAbsolutePath());

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writeHeader(w);

            for (int targetRooms = MIN_ROOMS; targetRooms <= MAX_ROOMS; targetRooms *= 2) {
                GridEstimate ge = estimateGrid(targetRooms, MIN_ROOM_SIZE, MAX_ROOM_SIZE);

                // -------------------------
                // Warm-up (discarded)
                // -------------------------
                for (int i = 0; i < WARMUP_RUNS; i++) {
                    long seed = deriveSeed(targetRooms, i);
                    BenchmarkRow row = runOnce("warmup", targetRooms, ge, seed, i);
                    // not written
                    System.out.println("[warmup] N=" + targetRooms + " run=" + i
                            + " | roomsPlaced=" + row.roomsPlaced
                            + " corridors=" + row.corridorsGenerated
                            + " totalMs=" + String.format(Locale.ROOT, "%.2f", row.totalMs));
                }

                // -------------------------
                // Measurements (recorded)
                // -------------------------
                for (int i = 0; i < MEASURE_RUNS; i++) {
                    long seed = deriveSeed(targetRooms, i + WARMUP_RUNS);

                    BenchmarkRow row = runOnce("measure", targetRooms, ge, seed, i);
                    writeRow(w, row);
                    w.flush();

                    System.out.println("[measure] N=" + targetRooms + " run=" + i
                            + " | roomsPlaced=" + row.roomsPlaced
                            + " corridors=" + row.corridorsGenerated
                            + " totalMs=" + String.format(Locale.ROOT, "%.2f", row.totalMs));
                }
            }
        }
    }

    private static long deriveSeed(int targetRooms, int seedIndex) {
        return BASE_SEED + targetRooms * 31L + seedIndex * 1_000_003L;
    }

    private static BenchmarkRow runOnce(String phase, int targetRooms, GridEstimate ge, long seed, int runIndex) {
        double t0 = nowMs();

        // 1) Grid
        double gridStart = nowMs();
        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
        double gridMs = nowMs() - gridStart;

        // 2) Rooms
        double roomsStart = nowMs();
        List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed + 1).generateRooms(targetRooms);
        double roomsMs = nowMs() - roomsStart;

        // 3) Corridors
        double corridorsStart = nowMs();
        CorridorPlacer corridorPlacer = new CorridorPlacer(grid, DENSITY, seed + 2);
        List<Corridor> corridors = corridorPlacer.generateCorridors(rooms);
        double corridorsMs = nowMs() - corridorsStart;

        // 4) Network
        double networkStart = nowMs();
        CorridorNetwork net = CorridorNetworkBuilder.build(
                corridors,
                rooms,
                corridorPlacer.getRoutingGrid().getEdgeLength()
        );
        double networkMs = nowMs() - networkStart;

        // 5) Mesh (outer + inner)
        double meshOuterMs = -1;
        double meshInnerMs = -1;
        int outerTriCount = -1;
        int innerTriCount = -1;

        if (!rooms.isEmpty()) {
            double meshOuterStart = nowMs();
            DungeonMesh outer = DungeonMeshBuilder.buildDungeonMesh(net, rooms, true);
            meshOuterMs = nowMs() - meshOuterStart;
            outerTriCount = safeTriangleCount(outer.getDungeon());

            double meshInnerStart = nowMs();
            DungeonMesh inner = DungeonMeshBuilder.buildDungeonMesh(net, rooms, false);
            meshInnerMs = nowMs() - meshInnerStart;
            innerTriCount = safeTriangleCount(inner.getDungeon());
        }

        double totalMs = nowMs() - t0;

        int roomsPlaced = rooms.size();
        int corridorsGenerated = corridors.size();
        int graphNodes = net.graph.nodes.size();
        int junctionLinks = net.junctionLinksByJunctionAndPortal.size();

        return new BenchmarkRow(
                phase,
                runIndex,
                targetRooms,
                ge.sides,
                ge.edgeLength,
                seed,
                roomsPlaced,
                corridorsGenerated,
                graphNodes,
                junctionLinks,
                gridMs,
                roomsMs,
                corridorsMs,
                networkMs,
                meshOuterMs,
                meshInnerMs,
                outerTriCount,
                innerTriCount,
                totalMs
        );
    }

    // ------------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------------

    private static void writeHeader(BufferedWriter w) throws IOException {
        w.write(String.join(",",
                "phase",
                "runIndex",
                "targetRooms",
                "gridSides",
                "gridEdgeLength",
                "seed",
                "roomsPlaced",
                "corridorsGenerated",
                "graphNodes",
                "junctionLinks",
                "gridMs",
                "roomsMs",
                "corridorsMs",
                "networkMs",
                "meshOuterMs",
                "meshInnerMs",
                "outerTriangles",
                "innerTriangles",
                "totalMs"
        ));
        w.newLine();
    }

    private static void writeRow(BufferedWriter w, BenchmarkRow r) throws IOException {
        Objects.requireNonNull(r);

        w.write(r.phase + ","
                + r.runIndex + ","
                + r.targetRooms + ","
                + r.gridSides + ","
                + fmt(r.gridEdgeLength) + ","
                + r.seed + ","
                + r.roomsPlaced + ","
                + r.corridorsGenerated + ","
                + r.graphNodes + ","
                + r.junctionLinks + ","
                + fmt(r.gridMs) + ","
                + fmt(r.roomsMs) + ","
                + fmt(r.corridorsMs) + ","
                + fmt(r.networkMs) + ","
                + fmt(r.meshOuterMs) + ","
                + fmt(r.meshInnerMs) + ","
                + r.outerTriangles + ","
                + r.innerTriangles + ","
                + fmt(r.totalMs)
        );
        w.newLine();
    }

    private static String fmt(double v) {
        if (v < 0) return "";
        return String.format(Locale.ROOT, "%.4f", v);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static double nowMs() {
        return System.nanoTime() / 1_000_000.0;
    }

    private static int safeTriangleCount(Mesh m) {
        if (m == null) return -1;
        var ib = m.getBuffer(com.jme3.scene.VertexBuffer.Type.Index);
        if (ib == null || ib.getNumElements() <= 0) return 0;
        return ib.getNumElements() / 3;
    }

    // ------------------------------------------------------------------------
    // Grid estimate (copied from OrganicDungeonGenerator)
    // ------------------------------------------------------------------------

    private record GridEstimate(int sides, float edgeLength) {}

    /**
     * Estimates grid parameters based on the number of rooms and room size range.
     * This is copied 1:1 from the OrganicDungeonGenerator for consistent benchmarking.
     *
     * @param roomCount number of rooms (problem size)
     * @param minRoom   min room size (in grid cells / samples)
     * @param maxRoom   max room size (in grid cells / samples)
     * @return grid estimate (number of sides and edge length)
     */
    private static GridEstimate estimateGrid(int roomCount, int minRoom, int maxRoom) {
        float avgRoom = 0.5f * (minRoom + maxRoom);
        int   sides   = Math.max(6, Math.min(20, (int) Math.ceil(Math.sqrt(roomCount) * 1.4f + 1.0f)));
        float edge    = Math.max(6f, avgRoom * 0.70f);
        return new GridEstimate(sides, edge);
    }

    // ------------------------------------------------------------------------
    // Row model
    // ------------------------------------------------------------------------

    private record BenchmarkRow(
            String phase,
            int runIndex,
            int targetRooms,
            int gridSides,
            float gridEdgeLength,
            long seed,
            int roomsPlaced,
            int corridorsGenerated,
            int graphNodes,
            int junctionLinks,
            double gridMs,
            double roomsMs,
            double corridorsMs,
            double networkMs,
            double meshOuterMs,
            double meshInnerMs,
            int outerTriangles,
            int innerTriangles,
            double totalMs
    ) {}
}