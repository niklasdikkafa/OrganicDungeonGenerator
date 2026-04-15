package com.dungeon.logic.mesh.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.mesh.DungeonMesh;
import com.dungeon.logic.mesh.adapter.JCSGAdapter;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.*;

import static com.dungeon.config.DungeonConfig.INFLATE_F;
import static com.dungeon.logic.mesh.MeshAccumulator.computeNormals;
import static com.dungeon.logic.mesh.MeshUtils.getFloatBuffer;
import static com.dungeon.logic.mesh.MeshUtils.getIndexArray;
import static com.dungeon.logic.mesh.MeshUtils.readPositions;
import static com.dungeon.logic.mesh.builder.CorridorMeshBuilder.buildCorridorMesh;
import static com.dungeon.logic.mesh.builder.RoomMeshBuilder.buildRoomMesh;

/**
 * Builds the final dungeon shell mesh by combining corridor and room meshes via CSG union.
 *
 * <p>This builder is the last stage of the geometry pipeline. It converts the generated
 * corridor network and the placed rooms into triangle meshes (jME {@link Mesh}), delegates
 * the boolean union to {@link JCSGAdapter#union(Mesh, List, double)}, and converts the
 * result back into a jME mesh for rendering. The builder itself has no direct dependency
 * on JCSG types; all CSG-specific logic is encapsulated in {@link JCSGAdapter}.</p>
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li><b>Corridor mesh</b>: Build a corridor shell mesh from the {@link CorridorNetwork}.</li>
 *   <li><b>Room meshes</b>: Build one mesh per {@link Room}.</li>
 *   <li><b>CSG union</b>: Delegate to {@link JCSGAdapter#union(Mesh, List, double)},
 *       which applies a bounding-box pre-filter to restrict the expensive BSP union to
 *       geometrically close polygon pairs and directly appends far-away polygons.</li>
 *   <li><b>Normal recomputation</b>: Recompute flat normals on the unified mesh.
 *       For the inner shell, winding is additionally flipped via
 *       {@link #invertNormalsInPlace(Mesh)}.</li>
 * </ol>
 *
 * <h2>Inner vs. outer shell</h2>
 * <p>The method can build either the outer shell (walls with thickness) or the inner shell
 * (walkable interior). For the inner shell, the final mesh has its winding flipped and normals
 * recomputed using {@link #invertNormalsInPlace(Mesh)}.</p>
 *
 * <h2>Notes and limitations</h2>
 * <ul>
 *   <li>CSG unions are comparatively expensive and scale with the number of rooms.</li>
 *   <li>Robustness depends on JCSG's BSP-tree stability; degenerate or near-coplanar geometry
 *       may still produce artefacts (see {@link JCSGAdapter} for details).</li>
 *   <li>This builder assumes meshes use indexed triangles with
 *       {@link VertexBuffer.Type#Position} and {@link VertexBuffer.Type#Index} buffers.</li>
 * </ul>
 */
public final class DungeonMeshBuilder {

    // ---------------- logger ----------------

    private static final Logger LOGGER = Logger.getLogger(DungeonMeshBuilder.class.getName());
    private static boolean LOGGER_CONFIGURED = false;

    /**
     * Builds a unified dungeon mesh by delegating CSG union of the corridor mesh with all room
     * meshes to {@link JCSGAdapter#union(Mesh, List, double)}.
     *
     * <p>Null room entries in {@code rooms} are silently skipped. The epsilon for bounding-box
     * inflation during union is derived from {@code net.routingCellSize} and {@code DungeonConfig.INFLATE_F}.</p>
     *
     * @param net             corridor network containing the corridor graph, frames, junction geometry, and samples
     * @param rooms           list of rooms to union into the dungeon mesh; null entries are skipped
     * @param buildOuterWalls if {@code true}, build the outer shell; if {@code false}, build the
     *                        inner shell with inverted normals
     * @return the combined dungeon mesh (never {@code null}, but may contain an empty mesh on
     *         degenerate input); contains the raw network mesh, the individual room meshes, and
     *         the final combined mesh
     * @throws NullPointerException if {@code net} or {@code rooms} is {@code null}
     * @throws StackOverflowError in some cases; happens when JCSG's BSP tree construction encounters deep recursion due to degenerate geometry
     */
    public static DungeonMesh buildDungeonMesh(CorridorNetwork net,
                                               List<Room> rooms,
                                               boolean buildOuterWalls) {
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(rooms, "rooms");

        configureLoggerOnce();

        LOGGER.log(Level.INFO, "Building corridor mesh...");
        Mesh corridor = buildCorridorMesh(net, buildOuterWalls);

        List<Mesh> roomMeshes = new ArrayList<>(rooms.size());

        double eps = Math.max(1e-3, net.routingCellSize * INFLATE_F);

        LOGGER.log(Level.INFO, "Building room meshes...");
        for (Room r : rooms) {
            if (r == null) continue;
            LOGGER.log(Level.INFO, "  Building room mesh for room " + r.getId() + "...");
            Mesh rm = buildRoomMesh(r, buildOuterWalls);
            roomMeshes.add(rm);
        }

        LOGGER.log(Level.INFO, "Corridor + room union...");
        Mesh out = JCSGAdapter.union(corridor, roomMeshes, eps);

        if (!buildOuterWalls) invertNormalsInPlace(out);
        LOGGER.log(Level.INFO, "Finished building dungeon mesh.");
        return new DungeonMesh(corridor, roomMeshes, out);
    }

    /**
     * Inverts face orientation of the given mesh by flipping triangle winding and recomputing
     * vertex normals.
     *
     * <p>This is used for the inner shell, where the visible faces must point inward rather than
     * outward. Triangle winding is flipped by swapping indices 1 and 2 for each triangle, then
     * normals are recomputed by accumulating face normals per vertex.</p>
     *
     * @param mesh the mesh to modify in-place; does nothing if {@code null}
     */
    public static void invertNormalsInPlace(Mesh mesh) {
        if (mesh == null) return;

        int[] idx = getIndexArray(mesh);
        if (idx.length >= 3) {
            for (int i = 0; i < idx.length; i += 3) {
                int t = idx[i + 1];
                idx[i + 1] = idx[i + 2];
                idx[i + 2] = t;
            }
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        }

        FloatBuffer pb = getFloatBuffer(mesh);
        if (pb == null || idx.length == 0) {
            mesh.updateBound();
            mesh.updateCounts();
            return;
        }

        Vector3f[] pos = readPositions(pb);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3,
                BufferUtils.createFloatBuffer(computeNormals(pos, idx)));
        mesh.updateBound();
        mesh.updateCounts();
    }

    // --------------- logger config ---------------

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
                return "[DungeonMeshBuilder] " + r.getLevel().getName() + ": "
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