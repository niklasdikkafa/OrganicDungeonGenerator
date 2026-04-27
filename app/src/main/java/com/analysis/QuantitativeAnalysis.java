package com.analysis;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.mesh.DungeonMesh;
import com.dungeon.logic.mesh.builder.CorridorMeshBuilder;
import com.dungeon.logic.mesh.builder.DungeonMeshBuilder;
import com.dungeon.logic.mesh.builder.RoomMeshBuilder;
import com.dungeon.logic.placement.corridor.CorridorPlacer;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.builder.CorridorNetworkBuilder;
import com.dungeon.logic.placement.corridor.network.graph.GraphEdge;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.junction.JunctionCorner;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathFrameSample;
import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;
import com.dungeon.logic.placement.room.RoomPlacer;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extended quantitative analysis covering:
 *   1. Corridor length analysis
 *   2. Room mesh overlap check
 *   3. Corridor mesh self-intersection check
 *   4. Endpoint degree distribution
 *   5. Minimum viable grid size
 *   6. 2-manifold check (room meshes, network mesh, combined mesh)
 *   7. Room inner corner angle distribution (must be > 90 degrees)
 *   8. Corridor frame angle distribution (non-junction, non-endpoint nodes)
 *   9. Junction portal angle distribution
 *  10. Room count vs target (grid estimate validation)
 *  11. Graph connectivity check (all room ids reachable from corridor graph)
 */
public final class QuantitativeAnalysis {

    private static final int MIN_ROOM_SIZE = 15;
    private static final int MAX_ROOM_SIZE = 25;
    private static final CorridorPlacer.Density DENSITY = CorridorPlacer.Density.MEDIUM;
    private static final long BASE_SEED = 12345L;
    private static final int[] TARGET_ROOMS = {4, 8, 16, 32, 64};
    private static final int RUNS_PER_SIZE = 5;
    private static final int GRID_SEARCH_START_SIDES = 20;
    private static final int GRID_SEARCH_MIN_SIDES = 3;
    private static final int GRID_SEARCH_SEEDS = 3;
    private static final float EDGE_KEY_EPS = 1e-5f;

    private static final Logger LOG = Logger.getLogger(QuantitativeAnalysis.class.getName());

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Path resultsDir = Path.of("results");
        Files.createDirectories(resultsDir);
        String ts = Instant.now().toString().replace(":", "-");
        Path out = resultsDir.resolve("quantitative_analysis_" + ts + ".csv");
        System.out.println("Writing results to: " + out.toAbsolutePath());

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writeSection(w, "# 1. CORRIDOR LENGTH ANALYSIS");
            writeCorridorLengthAnalysis(w);

            writeSection(w, "# 2. ROOM MESH OVERLAP CHECK");
            writeRoomOverlapAnalysis(w);

            writeSection(w, "# 3. CORRIDOR MESH SELF-INTERSECTION CHECK");
            writeCorridorSelfIntersectionAnalysis(w);

            writeSection(w, "# 4. ENDPOINT DEGREE DISTRIBUTION");
            writeEndpointDegreeAnalysis(w);

            writeSection(w, "# 5. MINIMUM VIABLE GRID SIZE");
            writeMinimumGridSizeAnalysis(w);

            writeSection(w, "# 6. 2-MANIFOLD CHECK");
            writeManifoldCheck(w);

            writeSection(w, "# 7. ROOM INNER CORNER ANGLE DISTRIBUTION");
            writeRoomInnerCornerAngles(w);

            writeSection(w, "# 8. CORRIDOR FRAME ANGLE DISTRIBUTION (non-junction, non-endpoint)");
            writeCorridorFrameAngles(w);

            writeSection(w, "# 9. JUNCTION PORTAL ANGLE DISTRIBUTION");
            writeJunctionPortalAngles(w);

            writeSection(w, "# 10. ROOM COUNT VS TARGET (grid estimate validation)");
            writeRoomCountValidation(w);

            writeSection(w, "# 11. GRAPH CONNECTIVITY CHECK");
            writeGraphConnectivityCheck(w);
        }
        System.out.println("Done.");
    }

    // =========================================================================
    // 1. Corridor length analysis
    // =========================================================================

    private static void writeCorridorLengthAnalysis(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,corridorCount,minLength,maxLength,meanLength,totalLength");
        w.newLine();
        List<Double> allMeans = new ArrayList<>();
        for (int n : TARGET_ROOMS) {
            List<Double> meansForN = new ArrayList<>();
            for (int run = 0; run < RUNS_PER_SIZE; run++) {
                long seed = deriveSeed(n, run);
                CorridorNetwork network = buildNetwork(n, estimateGrid(n), seed);
                if (network == null) continue;
                List<Double> lengths = computeCorridorLengths(network);
                if (lengths.isEmpty()) continue;
                double min  = lengths.stream().mapToDouble(d -> d).min().orElse(0);
                double max  = lengths.stream().mapToDouble(d -> d).max().orElse(0);
                double mean = lengths.stream().mapToDouble(d -> d).average().orElse(0);
                double sum  = lengths.stream().mapToDouble(d -> d).sum();
                meansForN.add(mean);
                allMeans.add(mean);
                w.write(fmt(n)+","+seed+","+lengths.size()+","+fmt4(min)+","+fmt4(max)+","+fmt4(mean)+","+fmt4(sum));
                w.newLine();
            }
            w.write("# N="+n+" aggregate mean corridor length: "+fmt4(meansForN.stream().mapToDouble(d->d).average().orElse(0)));
            w.newLine();
        }
        w.write("# Overall mean corridor length across all runs: "+fmt4(allMeans.stream().mapToDouble(d->d).average().orElse(0)));
        w.newLine(); w.newLine();
    }

    // =========================================================================
    // 2. Room overlap
    // =========================================================================

    private static void writeRoomOverlapAnalysis(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,roomCount,overlappingPairs,result"); w.newLine();
        int total = 0, checks = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            List<Mesh> rooms = buildRoomMeshes(n, estimateGrid(n), seed, true);
            if (rooms == null) continue;
            int overlaps = countRoomMeshOverlaps(rooms);
            total += overlaps; checks++;
            w.write(fmt(n)+","+seed+","+rooms.size()+","+overlaps+","+(overlaps>0?"OVERLAP":"OK"));
            w.newLine();
        }
        w.write("# Total runs: "+checks+"  Total overlap incidents: "+total+"  Result: "+(total==0?"NO_OVERLAPS_FOUND":"OVERLAPS_PRESENT"));
        w.newLine(); w.newLine();
    }

    // =========================================================================
    // 3. Corridor mesh self-intersection
    // =========================================================================

    private static void writeCorridorSelfIntersectionAnalysis(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,triangleCount,selfIntersectingPairs,result"); w.newLine();
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            Mesh netMesh = buildNetworkMesh(n, estimateGrid(n), seed, true);
            if (netMesh == null) continue;
            SelfIntersectionResult r = checkSelfIntersections(netMesh);
            w.write(fmt(n)+","+seed+","+r.triangleCount+","+r.selfIntersectingPairs+","+(r.selfIntersectingPairs>0?"SELF_INTERSECTIONS":"OK"));
            w.newLine();
        }
        w.newLine();
    }

    // =========================================================================
    // 4. Endpoint degree distribution
    // =========================================================================

    private static void writeEndpointDegreeAnalysis(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,degree,count"); w.newLine();
        Map<Integer,Integer> agg = new TreeMap<>();
        for (int n : TARGET_ROOMS) {
            Map<Integer,Integer> forN = new TreeMap<>();
            for (int run = 0; run < RUNS_PER_SIZE; run++) {
                long seed = deriveSeed(n, run);
                CorridorNetwork net = buildNetwork(n, estimateGrid(n), seed);
                if (net == null) continue;
                computeEndpointDegrees(net).forEach((deg, cnt) -> {
                    try { w.write(fmt(n)+","+seed+","+deg+","+cnt+"\n"); } catch (IOException e) { throw new RuntimeException(e); }
                    forN.merge(deg, cnt, Integer::sum);
                    agg.merge(deg, cnt, Integer::sum);
                });
            }
            w.write("# N="+n+" aggregate: "+forN); w.newLine();
        }
        w.write("# Overall distribution: "+agg); w.newLine(); w.newLine();
    }

    // =========================================================================
    // 5. Minimum viable grid size
    // =========================================================================

    private static void writeMinimumGridSizeAnalysis(BufferedWriter w) throws IOException {
        w.write("targetRooms,sideCount,successfulSeeds,totalSeeds,successRate,viable"); w.newLine();
        for (int n : TARGET_ROOMS) {
            int lastFull = -1;
            for (int sides = GRID_SEARCH_START_SIDES; sides >= GRID_SEARCH_MIN_SIDES; sides--) {
                int succ = 0;
                for (int run = 0; run < GRID_SEARCH_SEEDS; run++) {
                    long seed = deriveSeed(n, run + 200) + sides * 7L;
                    try {
                        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(sides, estimateGrid(n).edgeLength), seed);
                        if (new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(n).size() >= n) succ++;
                    } catch (Exception ignored) {}
                }
                double rate = (double) succ / GRID_SEARCH_SEEDS;
                boolean allOk = succ == GRID_SEARCH_SEEDS;
                w.write(fmt(n)+","+sides+","+succ+","+GRID_SEARCH_SEEDS+","+fmt4(rate)+","+(allOk?"ALL":succ>0?"PARTIAL":"FAIL"));
                w.newLine();
                if (allOk) lastFull = sides; else break;
            }
            w.write("# N="+n+"  minViableSides="+(lastFull==-1?"NOT_FOUND":lastFull)); w.newLine();
        }
        w.newLine();
    }

    // =========================================================================
    // 6. 2-manifold check
    // =========================================================================

    private static void writeManifoldCheck(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,meshType,nonManifoldEdges,result"); w.newLine();
        w.write("# Room meshes and network mesh are expected to be 2-manifold (0 non-manifold edges)"); w.newLine();
        w.write("# Combined dungeon mesh is NEVER expected to be 2-manifold (T-junctions from JCSG)"); w.newLine();

        int roomFails = 0, netFails = 0, totalChecks = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            DungeonBundle b = buildDungeon(n, estimateGrid(n), seed, true);
            if (b == null) continue;
            totalChecks++;

            // Check each room mesh individually
            int roomNonManifoldTotal = 0;
            for (Mesh rm : b.dungeonMesh.getRooms()) {
                int nm = countNonManifoldEdges(rm);
                roomNonManifoldTotal += nm;
            }
            if (roomNonManifoldTotal > 0) roomFails++;
            w.write(fmt(n)+","+seed+",ROOM_MESHES,"+roomNonManifoldTotal+","+(roomNonManifoldTotal==0?"OK":"NON_MANIFOLD"));
            w.newLine();

            // Check network mesh
            int netNM = countNonManifoldEdges(b.dungeonMesh.getNetwork());
            if (netNM > 0) netFails++;
            w.write(fmt(n)+","+seed+",NETWORK_MESH,"+netNM+","+(netNM==0?"OK":"NON_MANIFOLD"));
            w.newLine();

            // Check combined mesh -> expected to fail due to T-junctions
            int combinedNM = countNonManifoldEdges(b.dungeonMesh.getDungeon());
            w.write(fmt(n)+","+seed+",COMBINED_MESH,"+combinedNM+","+(combinedNM==0?"UNEXPECTEDLY_OK":"T_JUNCTIONS_PRESENT"));
            w.newLine();
        }
        w.write("# Room mesh 2-manifold failures: "+roomFails+"/"+totalChecks); w.newLine();
        w.write("# Network mesh 2-manifold failures: "+netFails+"/"+totalChecks); w.newLine();
        w.newLine();
    }

    /**
     * Counts edges that are not shared by exactly 2 triangles (non-manifold edges).
     * An edge is represented as a canonical (min,max) vertex-index pair.
     * For a 2-manifold triangle mesh every edge appears exactly twice.
     */
    private static int countNonManifoldEdges(Mesh mesh) {
        if (mesh == null) return 0;

        int[] idx = extractIndices(mesh);
        float[] pos = extractPositions(mesh);
        if (idx == null || pos == null) return 0;

        Map<Long, Integer> edgeCount = new HashMap<>(idx.length * 2);

        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i], ib = idx[i + 1], ic = idx[i + 2];

            addEdge(edgeCount, pos, ia, ib);
            addEdge(edgeCount, pos, ib, ic);
            addEdge(edgeCount, pos, ic, ia);
        }

        int nonManifold = 0;
        for (int cnt : edgeCount.values()) {
            if (cnt != 2) nonManifold++;
        }
        return nonManifold;
    }

    /**
     * Adds an undirected edge key based on quantized endpoint positions.
     * Endpoints are ordered (min,max) by their point-hash so the edge is undirected.
     */
    private static void addEdge(Map<Long, Integer> map, float[] pos, int aIdx, int bIdx) {
        long ha = pointHash(pos, aIdx);
        long hb = pointHash(pos, bIdx);

        long lo = Math.min(ha, hb);
        long hi = Math.max(ha, hb);

        long edgeKey = mix64(lo) ^ rotl64(mix64(hi), 1);
        map.merge(edgeKey, 1, Integer::sum);
    }

    // =========================================================================
    // 7. Room inner corner angle distribution
    // =========================================================================

    private static void writeRoomInnerCornerAngles(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,roomId,minAngleDeg,maxAngleDeg,meanAngleDeg,anglesBelow90,anglesAbove270,totalAngles");
        w.newLine();

        int totalAngles = 0, totalBelow90 = 0, totalAbove270 = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            List<Room> rooms = buildRooms(n, estimateGrid(n), seed);
            if (rooms == null) continue;

            for (Room room : rooms) {
                List<Vector2f> corners = room.getInnerCorners();
                if (corners == null || corners.size() < 3) continue;

                List<Double> angles = computePolygonInteriorAngles2D(corners);
                double min  = angles.stream().mapToDouble(d -> d).min().orElse(0);
                double max  = angles.stream().mapToDouble(d -> d).max().orElse(0);
                double mean = angles.stream().mapToDouble(d -> d).average().orElse(0);
                long below  = angles.stream().filter(a -> a < 90.0).count();
                long above = angles.stream().filter(a -> a > 270.0).count();

                totalAngles  += angles.size();
                totalBelow90 += below;
                totalAbove270  += above;

                w.write(fmt(n)+","+seed+","+room.getId()+","
                        +fmt4(min)+","+fmt4(max)+","+fmt4(mean)+","
                        +below+","+above+","+angles.size());
                w.newLine();
            }
        }
        w.write("# Total inner corner angles measured: "+totalAngles);     w.newLine();
        w.write("# Angles below 90 deg:  "+totalBelow90
                +" ("+fmt4(100.0*totalBelow90/Math.max(1,totalAngles))+"%)"); w.newLine();
        w.write("# Angles above 270 deg: "+totalAbove270
                +" ("+fmt4(100.0*totalAbove270 /Math.max(1,totalAngles))+"%)"); w.newLine();
        w.newLine();
    }

    /**
     * Computes interior angles of a 2D polygon in degrees, correctly handling
     * reflex (>180°) angles in concave polygons.
     */
    private static List<Double> computePolygonInteriorAngles2D(List<Vector2f> poly) {
        int n = poly.size();
        List<Double> angles = new ArrayList<>();

        // Signed area via shoelace formula -> positive = CCW winding
        double signedArea = 0;
        for (int i = 0; i < n; i++) {
            Vector2f a = poly.get(i);
            Vector2f b = poly.get((i + 1) % n);
            signedArea += (double) a.x * b.y - (double) b.x * a.y;
        }
        boolean isCCW = signedArea > 0;

        for (int i = 0; i < n; i++) {
            Vector2f prev = poly.get((i - 1 + n) % n);
            Vector2f curr = poly.get(i);
            Vector2f next = poly.get((i + 1) % n);

            // Edge vectors arriving at and leaving curr
            float e0x = curr.x - prev.x, e0y = curr.y - prev.y; // incoming edge
            float e1x = next.x - curr.x, e1y = next.y - curr.y; // outgoing edge

            double lenE0 = Math.sqrt(e0x*e0x + e0y*e0y);
            double lenE1 = Math.sqrt(e1x*e1x + e1y*e1y);
            if (lenE0 < 1e-8 || lenE1 < 1e-8) continue;

            // Dot product for the unsigned angle between the two edges
            double dot = (e0x*e1x + e0y*e1y) / (lenE0 * lenE1);
            dot = Math.max(-1.0, Math.min(1.0, dot));

            double edgeAngleDeg = Math.toDegrees(Math.acos(dot));
            double interiorDeg  = 180.0 - edgeAngleDeg;

            // Cross product (Z component in 2D) of incoming x outgoing edge
            double cross = (double) e0x * e1y - (double) e0y * e1x;

            // Reflex check: in CCW polygon a right-turn (cross < 0) is a reflex angle
            boolean isReflex = isCCW ? (cross < 0) : (cross > 0);
            if (isReflex) interiorDeg = 360.0 - interiorDeg;

            angles.add(interiorDeg);
        }
        return angles;
    }

    // =========================================================================
    // 8. Corridor frame angle distribution
    // =========================================================================

    private static void writeCorridorFrameAngles(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,chainNodes,minLeftDeg,maxLeftDeg,meanLeftDeg,minRightDeg,maxRightDeg,meanRightDeg,"
                + "leftBelow90,rightBelow90");
        w.newLine();
        w.write("# Interior wall angle at each degree-2 chain node (non-junction, non-endpoint)");
        w.newLine();
        w.write("# Left wall:  angle at innerLeftBottom[prev->curr->next]");  w.newLine();
        w.write("# Right wall: angle at innerRightBottom[prev->curr->next]"); w.newLine();
        w.write("# INFO: the calculated angle will always be between 0-180, so it's not always the interior angle"); w.newLine();

        long totalLeft = 0, totalRight = 0, totalLBelow90 = 0, totalRBelow90 = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            CorridorNetwork net = buildNetwork(n, estimateGrid(n), seed);
            if (net == null) continue;

            List<double[]> angles = computeCorridorWallAngles(net);
            if (angles.isEmpty()) continue;

            double minL = Double.MAX_VALUE, sumL = 0;
            double minR = Double.MAX_VALUE, sumR = 0;
            double maxL = Double.MIN_VALUE, maxR = Double.MIN_VALUE;
            long lBelow = 0, rBelow = 0;

            for (double[] lr : angles) {
                minL = Math.min(minL, lr[0]);
                maxL = Math.max(maxL, lr[0]);
                sumL += lr[0];
                if (lr[0] < 90) lBelow++;
                minR = Math.min(minR, lr[1]);
                maxR = Math.max(maxR, lr[1]);
                sumR += lr[1];
                if (lr[1] < 90) rBelow++;
            }
            int sz = angles.size();
            totalLeft    += sz;   totalRight   += sz;
            totalLBelow90 += lBelow; totalRBelow90 += rBelow;

            w.write(fmt(n)+","+seed+","+sz+","
                    +fmt4(minL)+","+fmt4(maxL)+","+fmt4(sumL/sz)+","
                    +fmt4(minR)+","+fmt4(maxR)+","+fmt4(sumR/sz)+","
                    +lBelow+","+rBelow);
            w.newLine();
        }
        w.write("# Total chain nodes measured: left="+totalLeft+" right="+totalRight); w.newLine();
        w.write("# Left wall angles < 90 deg: "+totalLBelow90
                +" ("+fmt4(100.0*totalLBelow90/Math.max(1,totalLeft))+"%)");  w.newLine();
        w.write("# Right wall angles < 90 deg: "+totalRBelow90
                +" ("+fmt4(100.0*totalRBelow90/Math.max(1,totalRight))+"%)"); w.newLine();
        w.newLine();
    }

    /**
     * For every non-junction, non-endpoint, degree-2 chain node, computes the
     * interior wall angles on both the left and right side. Angle is between 0 and 180 degrees.
     */
    private static List<double[]> computeCorridorWallAngles(CorridorNetwork net) {
        List<double[]> result = new ArrayList<>();

        // Build nodeId -> PathFrameSample lookup from all paths
        Map<Integer, PathFrameSample> sampleById = new HashMap<>();
        for (CorridorPath path : net.paths) {
            for (PathFrameSample s : path.samples) {
                sampleById.putIfAbsent(s.graphNodeId, s);
            }
        }

        for (GraphNode node : net.graph.nodes) {
            if (node == null || node.isJunction || node.isEndpoint || node.frameDisabled) continue;
            List<GraphEdge> adj = net.graph.adjacency.getOrDefault(node.id, List.of());
            if (adj.size() != 2) continue;

            PathFrameSample curr = sampleById.get(node.id);
            if (curr == null) continue;

            GraphNode nbA = net.graph.nodes.get(adj.get(0).to);
            GraphNode nbB = net.graph.nodes.get(adj.get(1).to);
            if (nbA == null || nbB == null) continue;

            PathFrameSample sA = sampleById.get(nbA.id);
            PathFrameSample sB = sampleById.get(nbB.id);
            if (sA == null || sB == null || sA.frameDisabled || sB.frameDisabled) continue;

            PathFrameSample prev = sA, next = sB;

            double leftAngle  = angle3DinXZ(
                    prev.innerLeftBottom, curr.innerLeftBottom,  next.innerLeftBottom);
            double rightAngle = angle3DinXZ(
                    prev.innerRightBottom, curr.innerRightBottom, next.innerRightBottom);

            result.add(new double[]{leftAngle, rightAngle});
        }
        return result;
    }

    /**
     * Interior angle at point B formed by A->B->C, measured in the XZ plane.
     * Returns a value in [0, 180]. Always the smaller geometric angle;
     * corridor walls cannot be reflex by construction.
     */
    private static double angle3DinXZ(Vector3f a, Vector3f b, Vector3f c) {
        float bax = a.x - b.x, baz = a.z - b.z;
        float bcx = c.x - b.x, bcz = c.z - b.z;
        double lenBA = Math.sqrt(bax*bax + baz*baz);
        double lenBC = Math.sqrt(bcx*bcx + bcz*bcz);
        if (lenBA < 1e-8 || lenBC < 1e-8) return 180.0;
        double dot = (bax*bcx + baz*bcz) / (lenBA * lenBC);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    // =========================================================================
    // 9. Junction portal angle distribution
    // =========================================================================

    private static void writeJunctionPortalAngles(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,junctionCount,portalPairs,minAngleDeg,meanAngleDeg,anglesBelow90");
        w.newLine();
        w.write("# Interior wall angle at each junction corner (directConnect or chamfer)"); w.newLine();

        long totalPairs = 0, totalBelow90 = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            CorridorNetwork net = buildNetwork(n, estimateGrid(n), seed);
            if (net == null) continue;

            List<Double> angles = computeJunctionWallAngles(net);
            if (angles.isEmpty()) continue;

            double min  = angles.stream().mapToDouble(d->d).min().orElse(180);
            double mean = angles.stream().mapToDouble(d->d).average().orElse(180);
            long below90 = angles.stream().filter(a -> a < 90.0).count();

            long jCount = net.graph.nodes.stream()
                    .filter(nd -> nd != null && nd.isJunction).count();

            totalPairs   += angles.size();
            totalBelow90 += below90;

            w.write(fmt(n)+","+seed+","+jCount+","+angles.size()+","
                    +fmt4(min)+","+fmt4(mean)+","+below90);
            w.newLine();
        }
        w.write("# Total junction wall angles measured: "+totalPairs);     w.newLine();
        w.write("# Angles < 90 deg:  "+totalBelow90
                +" ("+fmt4(100.0*totalBelow90/Math.max(1,totalPairs))+"%)"); w.newLine();
        w.newLine();
    }

    /**
     * Iterates every consecutive portal pair in every junction ring and measures
     * the minimum interior wall angle in the section between the two portals.
     *
     * Returns one value per portal pair (the minimum angle in that section).
     */
    private static List<Double> computeJunctionWallAngles(CorridorNetwork net) {
        List<Double> angles = new ArrayList<>();
        Map<Long, JunctionPortalLink> linkMap = net.junctionLinksByJunctionAndPortal;

        // Group links by junction component id (junctionNodeId)
        Map<Integer, List<JunctionPortalLink>> byJunction = new HashMap<>();
        for (JunctionPortalLink pl : linkMap.values()) {
            if (pl != null) byJunction.computeIfAbsent(pl.junctionNodeId, k -> new ArrayList<>()).add(pl);
        }

        for (List<JunctionPortalLink> ring : byJunction.values()) {
            Map<Integer, JunctionPortalLink> byPortal = new HashMap<>();
            for (JunctionPortalLink pl : ring) byPortal.put(pl.portalNodeId, pl);

            for (JunctionPortalLink cur : ring) {
                if (cur.nextCwPortalNodeId < 0) continue;
                JunctionPortalLink nxt = byPortal.get(cur.nextCwPortalNodeId);
                if (nxt == null || cur.cornerToNext == null) continue;

                JunctionCorner c = cur.cornerToNext;

                if (c.directConnect) {
                    GraphNode nbA = net.graph.adjacency.get(cur.portalNodeId).stream().filter(e ->
                                    !net.graph.nodes.get(e.to).isJunction).findFirst()
                            .map(e -> net.graph.nodes.get(e.to)).orElse(null);
                    if (nbA != null) {
                        Vector3f[] frameA = alignFrameForJunction(cur, nbA);
                        angles.add(angle3DinXZ(frameA[0], cur.innerLeftBottom, nxt.innerRightBottom));
                    } // nodes between two junctions are skipped.

                    GraphNode nbB = net.graph.adjacency.get(nxt.portalNodeId).stream().filter(e ->
                                    !net.graph.nodes.get(e.to).isJunction).findFirst()
                            .map(e -> net.graph.nodes.get(e.to)).orElse(null);
                    if (nbB != null) {
                        Vector3f[] frameB = alignFrameForJunction(nxt, nbB);
                        angles.add(angle3DinXZ(cur.innerLeftBottom, nxt.innerRightBottom, frameB[1]));
                    }
                } else {
                    // Chamfer path: cur.innerLeftBottom -> innerBottom0 -> innerBottom1 -> nxt.innerRightBottom
                    // Measure at both chamfer vertices.
                    double a0 = angle3DinXZ(
                            cur.innerLeftBottom, c.innerBottom0, c.innerBottom1);
                    double a1 = angle3DinXZ(
                            c.innerBottom0, c.innerBottom1, nxt.innerRightBottom);
                    angles.add(a0);
                    angles.add(a1);

                    GraphNode nbA = net.graph.adjacency.get(cur.portalNodeId).stream().filter(e ->
                                    !net.graph.nodes.get(e.to).isJunction).findFirst()
                            .map(e -> net.graph.nodes.get(e.to)).orElse(null);
                    if (nbA != null) {
                        Vector3f[] frameA = alignFrameForJunction(cur, nbA);
                        angles.add(angle3DinXZ(frameA[0], cur.innerLeftBottom, c.innerBottom0));
                    } // nodes between two junctions are skipped.

                    GraphNode nbB = net.graph.adjacency.get(nxt.portalNodeId).stream().filter(e ->
                                    !net.graph.nodes.get(e.to).isJunction).findFirst()
                            .map(e -> net.graph.nodes.get(e.to)).orElse(null);
                    if (nbB != null) {
                        Vector3f[] frameB = alignFrameForJunction(nxt, nbB);
                        angles.add(angle3DinXZ(c.innerBottom1, nxt.innerRightBottom, frameB[1]));
                    }
                }

            }
        }
        return angles;
    }

    private static Vector3f[] alignFrameForJunction(JunctionPortalLink cur, GraphNode nb) {
        Vector3f[] frame = new Vector3f[2];
        LineSegment segL = new LineSegment(cur.innerLeftBottom.x, cur.innerLeftBottom.z, nb.innerLeftBottom.x, nb.innerLeftBottom.z);
        LineSegment segR = new LineSegment(cur.innerRightBottom.x, cur.innerRightBottom.z, nb.innerRightBottom.x, nb.innerRightBottom.z);
        Coordinate I = segL.intersection(segR);
        if (I == null) {
            // No intersection, use original frame points
            frame[0] = nb.innerLeftBottom.clone();
            frame[1] = nb.innerRightBottom.clone();
        } else {
            // Intersection found, switch left/right to maintain consistent ordering
            frame[0] = nb.innerRightBottom.clone();
            frame[1] = nb.innerLeftBottom.clone();
        }
        return frame;
    }

    // =========================================================================
    // 10. Room count vs. target - grid estimate validation
    // =========================================================================

    private static void writeRoomCountValidation(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,actualRooms,achieved,gridSides"); w.newLine();
        w.write("# Tests whether estimateGrid(N) reliably produces exactly N rooms"); w.newLine();
        w.write("# Note: placement is limited by MAX_ROOM_PLACEMENT_ATTEMPTS=50."); w.newLine();
        w.write("# In rare cases the target cannot be reached even with a sufficient grid."); w.newLine();

        int totalRuns = 0, fullSuccess = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            GridEstimate ge = estimateGrid(n);
            int actual;
            try {
                BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
                List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(n);
                actual = rooms.size();
            } catch (Exception e) {
                actual = 0;
            }
            boolean ok = actual >= n;
            if (ok) fullSuccess++;
            totalRuns++;
            w.write(fmt(n)+","+seed+","+actual+","+(ok?"OK":"SHORT")+","+ge.sides);
            w.newLine();
        }
        w.write("# Full success rate: "+fullSuccess+"/"+totalRuns+
                " ("+fmt4(100.0*fullSuccess/Math.max(1,totalRuns))+"%)");
        w.newLine(); w.newLine();
    }

    // =========================================================================
    // 11. Graph connectivity check
    // =========================================================================

    private static void writeGraphConnectivityCheck(BufferedWriter w) throws IOException {
        w.write("targetRooms,seed,totalRooms,reachableEndpoints,missingRoomIds,result"); w.newLine();

        int totalFails = 0, totalRuns = 0;
        for (int n : TARGET_ROOMS) for (int run = 0; run < RUNS_PER_SIZE; run++) {
            long seed = deriveSeed(n, run);
            DungeonBundle b = buildDungeon(n, estimateGrid(n), seed, true);
            if (b == null) continue;
            totalRuns++;

            Set<Integer> roomIds = new HashSet<>();
            for (Room r : b.rooms) roomIds.add(r.getId());

            // Collect all room ids reachable as endpoint nodes in the corridor graph
            Set<Integer> reachableRoomIds = new HashSet<>();
            GraphNode start = b.network.graph.nodes.getFirst();
            collectReachableNodes(b.network, start, reachableRoomIds, new HashSet<>());

            // Find room ids present in rooms list but not in graph endpoints
            Set<Integer> missing = new HashSet<>(roomIds);
            missing.removeAll(reachableRoomIds);

            if (!missing.isEmpty()) totalFails++;
            w.write(fmt(n)+","+seed+","+roomIds.size()+","+reachableRoomIds.size()+","+missing.size()+","+(missing.isEmpty()?"ALL_CONNECTED":"MISSING_ROOMS"));
            w.newLine();
        }
        w.write("# Connectivity failures: "+totalFails+"/"+totalRuns); w.newLine();
        w.newLine();
    }

    /** Depth-first traversal to collect room ids reachable from the start node via endpoint nodes. */
    private static void collectReachableNodes(CorridorNetwork network, GraphNode current, Set<Integer> reachableRoomIds, Set<Integer> visited) {
        if (current == null || visited.contains(current.id)) return;
        visited.add(current.id);
        if (current.roomId >= 0 && current.isEndpoint) {
            reachableRoomIds.add(current.roomId);
        }
        List<GraphEdge> nbs = network.graph.adjacency.get(current.id);
        for (GraphEdge e : nbs) {
            GraphNode next = network.graph.nodes.get(e.to);
            collectReachableNodes(network, next, reachableRoomIds, visited);
        }
    }

    // =========================================================================
    // Geometry utilities
    // =========================================================================

    private static int countRoomMeshOverlaps(List<Mesh> meshes) {
        float[][] aabbs = new float[meshes.size()][6];
        for (int i = 0; i < meshes.size(); i++) aabbs[i] = computeAabb3D(meshes.get(i));
        int overlaps = 0;
        for (int i = 0; i < meshes.size(); i++)
            for (int j = i + 1; j < meshes.size(); j++)
                if (aabb3DOverlap(aabbs[i], aabbs[j])) overlaps++;
        return overlaps;
    }

    private static SelfIntersectionResult checkSelfIntersections(Mesh mesh) {
        if (mesh == null) return new SelfIntersectionResult(0, 0);
        float[] pos = extractPositions(mesh);
        int[] idx   = extractIndices(mesh);
        if (pos == null || idx == null) return new SelfIntersectionResult(0, 0);
        int triCount = idx.length / 3;
        Vector3f[][] tris = new Vector3f[triCount][3];
        float[][] taabb   = new float[triCount][6];
        for (int t = 0; t < triCount; t++) {
            for (int v = 0; v < 3; v++) {
                int i = idx[t*3+v];
                tris[t][v] = new Vector3f(pos[i*3], pos[i*3+1], pos[i*3+2]);
            }
            taabb[t] = triAabb(tris[t]);
        }
        int self = 0;
        for (int i = 0; i < triCount; i++)
            for (int j = i+1; j < triCount; j++) {
                if (shareVertexByPosition(tris[i], tris[j])) continue;
                if (!aabb3DOverlap(taabb[i], taabb[j])) continue;
                if (trianglesIntersect(tris[i], tris[j])) self++;
            }
        return new SelfIntersectionResult(triCount, self);
    }

    private static List<Double> computeCorridorLengths(CorridorNetwork net) {
        List<Double> lengths = new ArrayList<>();
        for (CorridorPath path : net.paths) {
            List<PathPoint3D> pts = path.rawPoints;
            if (pts.size() < 2) continue;
            double len = 0;
            for (int i = 1; i < pts.size(); i++) len += pts.get(i-1).position.distance(pts.get(i).position);
            lengths.add(len);
        }
        return lengths;
    }

    private static Map<Integer, Integer> computeEndpointDegrees(CorridorNetwork net) {
        Map<Integer, Integer> freq = new TreeMap<>();
        net.graph.nodes.forEach(n -> {
            if (!n.isEndpoint) return;
            freq.merge(net.graph.adjacency.get(n.id).size(), 1, Integer::sum);
        });
        return freq;
    }

    private static float[] computeAabb3D(Mesh mesh) {
        float[] pos = extractPositions(mesh);
        if (pos == null || pos.length < 3) return new float[]{0,0,0,0,0,0};
        float minX=pos[0],maxX=pos[0],minY=pos[1],maxY=pos[1],minZ=pos[2],maxZ=pos[2];
        for (int i = 3; i < pos.length; i += 3) {
            if (pos[i]<minX) minX=pos[i]; if (pos[i]>maxX) maxX=pos[i];
            if (pos[i+1]<minY) minY=pos[i+1]; if (pos[i+1]>maxY) maxY=pos[i+1];
            if (pos[i+2]<minZ) minZ=pos[i+2]; if (pos[i+2]>maxZ) maxZ=pos[i+2];
        }
        return new float[]{minX,maxX,minY,maxY,minZ,maxZ};
    }

    private static boolean aabb3DOverlap(float[] a, float[] b) {
        return a[0]<b[1] && b[0]<a[1] && a[2]<b[3] && b[2]<a[3] && a[4]<b[5] && b[4]<a[5];
    }

    private static float[] triAabb(Vector3f[] t) {
        float minX=t[0].x,maxX=t[0].x,minY=t[0].y,maxY=t[0].y,minZ=t[0].z,maxZ=t[0].z;
        for (int i=1;i<3;i++) {
            if(t[i].x<minX)minX=t[i].x; if(t[i].x>maxX)maxX=t[i].x;
            if(t[i].y<minY)minY=t[i].y; if(t[i].y>maxY)maxY=t[i].y;
            if(t[i].z<minZ)minZ=t[i].z; if(t[i].z>maxZ)maxZ=t[i].z;
        }
        return new float[]{minX,maxX,minY,maxY,minZ,maxZ};
    }

    private static boolean shareVertexByPosition(Vector3f[] a, Vector3f[] b) {
        final float eps2 = 1e-8f;
        for (Vector3f va : a) for (Vector3f vb : b) if (va.distanceSquared(vb) <= eps2) return true;
        return false;
    }

    /** Separating Axis Theorem for triangle-triangle intersection in 3D. */
    private static boolean trianglesIntersect(Vector3f[] t1, Vector3f[] t2) {
        Vector3f n1 = t1[1].subtract(t1[0]).cross(t1[2].subtract(t1[0]));
        Vector3f n2 = t2[1].subtract(t2[0]).cross(t2[2].subtract(t2[0]));
        List<Vector3f> axes = new ArrayList<>(Arrays.asList(n1, n2));
        Vector3f[] e1={t1[1].subtract(t1[0]),t1[2].subtract(t1[1]),t1[0].subtract(t1[2])};
        Vector3f[] e2={t2[1].subtract(t2[0]),t2[2].subtract(t2[1]),t2[0].subtract(t2[2])};
        for (Vector3f a : e1) for (Vector3f b : e2) axes.add(a.cross(b));
        for (Vector3f axis : axes) {
            if (axis.lengthSquared() < 1e-10f) continue;
            float min1=Float.MAX_VALUE,max1=-Float.MAX_VALUE,min2=Float.MAX_VALUE,max2=-Float.MAX_VALUE;
            for (Vector3f v : t1) { float p=v.dot(axis); if(p<min1)min1=p; if(p>max1)max1=p; }
            for (Vector3f v : t2) { float p=v.dot(axis); if(p<min2)min2=p; if(p>max2)max2=p; }
            if (max1 <= min2 || max2 <= min1) return false;
        }
        return true;
    }

    // =========================================================================
    // jME mesh buffer helpers
    // =========================================================================

    private static float[] extractPositions(Mesh mesh) {
        if (mesh == null) return null;
        VertexBuffer pb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (pb == null) return null;
        FloatBuffer fb = (FloatBuffer) pb.getData(); fb.rewind();
        float[] arr = new float[fb.remaining()]; fb.get(arr); return arr;
    }

    private static int[] extractIndices(Mesh mesh) {
        if (mesh == null) return null;
        VertexBuffer ib = mesh.getBuffer(VertexBuffer.Type.Index);
        if (ib == null) return null;
        if (ib.getData() instanceof IntBuffer buf) {
            buf.rewind(); int[] arr = new int[buf.remaining()]; buf.get(arr); return arr;
        }
        if (ib.getData() instanceof java.nio.ShortBuffer buf) {
            buf.rewind(); int[] arr = new int[buf.remaining()];
            for (int i = 0; i < arr.length; i++) arr[i] = buf.get() & 0xFFFF;
            return arr;
        }
        return null;
    }

    // =========================================================================
    // Grid estimation and helpers
    // =========================================================================

    private record GridEstimate(int sides, float edgeLength) {}

    private static GridEstimate estimateGrid(int roomCount) {
        float avgRoom = 0.5f * (MIN_ROOM_SIZE + MAX_ROOM_SIZE);
        int sides = Math.max(6, Math.min(20, (int) Math.ceil(Math.sqrt(roomCount) * 1.4f + 1.0f)));
        float edge = Math.max(6f, avgRoom * 0.70f);
        return new GridEstimate(sides, edge);
    }

    private record SelfIntersectionResult(int triangleCount, int selfIntersectingPairs) {}
    private record DungeonBundle(CorridorNetwork network, DungeonMesh dungeonMesh, List<Room> rooms) {}

    private static DungeonBundle buildDungeon(int targetRooms, GridEstimate ge, long seed, boolean outerWalls) {
        try {
            BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
            List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(targetRooms);
            if (rooms.isEmpty()) return null;
            CorridorPlacer cp = new CorridorPlacer(grid, DENSITY, seed);
            List<Corridor> corridors = cp.generateCorridors(rooms);
            CorridorNetwork net = CorridorNetworkBuilder.build(corridors, rooms, cp.getRoutingGrid().getEdgeLength());
            DungeonMesh dm = DungeonMeshBuilder.buildDungeonMesh(net, rooms, outerWalls);
            return new DungeonBundle(net, dm, rooms);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildDungeon failed for N="+targetRooms+" seed="+seed, e);
            return null;
        }
    }

    private static CorridorNetwork buildNetwork(int targetRooms, GridEstimate ge, long seed) {
        try {
            BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
            List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(targetRooms);
            if (rooms.isEmpty()) return null;
            CorridorPlacer cp = new CorridorPlacer(grid, DENSITY, seed);
            List<Corridor> corridors = cp.generateCorridors(rooms);
            return CorridorNetworkBuilder.build(corridors, rooms, cp.getRoutingGrid().getEdgeLength());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildNetwork failed for N="+targetRooms+" seed="+seed, e);
            return null;
        }
    }

    private static List<Mesh> buildRoomMeshes(int targetRooms, GridEstimate ge, long seed, boolean outerWalls) {
        try {
            BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
            List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(targetRooms);
            if (rooms.isEmpty()) return null;
            List<Mesh> roomMeshes = new ArrayList<>();
            for (Room r : rooms) {
                Mesh m = RoomMeshBuilder.buildRoomMesh(r, outerWalls);
                roomMeshes.add(m);
            }
            return roomMeshes;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildRoomMeshes failed for N="+targetRooms+" seed="+seed, e);
            return null;
        }
    }

    private static Mesh buildNetworkMesh(int targetRooms, GridEstimate ge, long seed, boolean outerWalls) {
        try {
            CorridorNetwork net = buildNetwork(targetRooms, ge, seed);
            return CorridorMeshBuilder.buildCorridorMesh(net, outerWalls);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildNetworkMesh failed for N="+targetRooms+" seed="+seed, e);
            return null;
        }
    }

    private static List<Room> buildRooms(int targetRooms, GridEstimate ge, long seed) {
        try {
            BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(ge.sides, ge.edgeLength), seed);
            List<Room> rooms = new RoomPlacer(grid, MIN_ROOM_SIZE, MAX_ROOM_SIZE, seed).generateRooms(targetRooms);
            if (rooms.isEmpty()) return null;
            return rooms;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "buildRooms failed for N="+targetRooms+" seed="+seed, e);
            return null;
        }
    }

    // ---- hashing helpers ----

    /**
     * Hashes a vertex position by quantizing to a grid to make it stable under float noise.
     */
    private static long pointHash(float[] pos, int vIdx) {
        float x = pos[vIdx * 3];
        float y = pos[vIdx * 3 + 1];
        float z = pos[vIdx * 3 + 2];

        int qx = quantize(x, EDGE_KEY_EPS);
        int qy = quantize(y, EDGE_KEY_EPS);
        int qz = quantize(z, EDGE_KEY_EPS);

        // pack three ints into a 64-bit hash via mixing
        long h = 0x9E3779B97F4A7C15L;
        h ^= mix64(qx);
        h ^= rotl64(mix64(qy), 21);
        h ^= rotl64(mix64(qz), 42);
        return mix64(h);
    }

    private static int quantize(float v, float eps) {
        return Math.round(v / eps);
    }

    private static long mix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long rotl64(long x, int r) {
        return (x << r) | (x >>> (64 - r));
    }

    private static void writeSection(BufferedWriter w, String h) throws IOException { w.newLine(); w.write(h); w.newLine(); }
    private static String fmt(int v)     { return String.valueOf(v); }
    private static String fmt4(double v) { return String.format(Locale.ROOT, "%.4f", v); }
    private static long deriveSeed(int n, int run) { return BASE_SEED + n * 31L + run * 1_000_003L; }
}