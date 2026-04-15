package com.dungeon.logic.mesh.adapter;

import com.dungeon.logic.mesh.MeshAccumulator;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import eu.mihosoft.vvecmath.Vector3d;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.dungeon.logic.mesh.MeshUtils.*;

/**
 * Adapter utilities for converting between jMonkeyEngine {@link Mesh} and JCSG {@link CSG},
 * and for performing CSG boolean union operations on jME meshes.
 *
 * <p>This class acts as the single point of contact between the rendering pipeline (jME) and
 * the boolean-operations library (JCSG). All JCSG-specific types ({@link CSG},
 * {@link Polygon}, {@link Vertex}) are confined to this class; callers such as
 * {@link com.dungeon.logic.mesh.builder.DungeonMeshBuilder} interact only with jME
 * {@link Mesh} objects.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>Conversion:</b> {@link #meshToCSG(Mesh)} and {@link #csgToJmeMesh(CSG)} translate
 *       between the two mesh representations.</li>
 *   <li><b>Union:</b> {@link #union(Mesh, List, double)} performs the high-level corridor plus
 *       room union, and {@link #unionBoundsFiltered(CSG, CSG, double)} implements the
 *       bounding-box-filtered BSP union that avoids JCSG's built-in
 *       {@code POLYGON_BOUND} artefacts.</li>
 * </ul>
 *
 * <h2>Conversion notes</h2>
 * <ul>
 *   <li><b>Triangle-only assumption:</b> {@link #meshToCSG(Mesh)} reads the mesh index buffer
 *       as triangle indices (triplets). Non-triangular primitives are not supported.</li>
 *   <li><b>Normals:</b> Per-triangle face normals are computed from vertex positions and stored
 *       in the JCSG vertices. Original vertex normals from the jME mesh are ignored.</li>
 *   <li><b>Degenerate triangles:</b> Triangles with near-zero area are skipped during
 *       {@link #meshToCSG(Mesh)}.</li>
 *   <li><b>Vertex duplication:</b> {@link #csgToJmeMesh(CSG)} emits one independent vertex per
 *       polygon corner; no welding is performed across polygon boundaries. This is intentional
 *       for flat-shaded architectural geometry.</li>
 *   <li><b>Topology:</b> The round-trip conversion is intended for boolean operations and
 *       debug/visualization, not for producing highly optimised render meshes.</li>
 * </ul>
 */
public class JCSGAdapter {

    /**
     * Performs the high-level CSG union of a corridor mesh with a list of room meshes.
     *
     * <p>Converts the corridor mesh and all room meshes to JCSG representations, then iterates
     * through the rooms, unioning each one into the accumulating corridor CSG via
     * {@link #unionBoundsFiltered(CSG, CSG, double)}. Rooms whose bounding box does not overlap
     * the current corridor bounds (inflated by {@code eps}) are skipped entirely. After all
     * unions are complete, the result is converted back to a jME {@link Mesh}.</p>
     *
     * @param net   the corridor mesh (the base solid to union rooms into)
     * @param rooms list of room meshes to union into {@code net}
     * @param eps   epsilon for bounding-box inflation; controls how conservatively polygon
     *              proximity is assessed; must be positive
     * @return the unified jME mesh containing all corridor and room geometry
     */
    public static Mesh union(Mesh net, List<Mesh> rooms, double eps) {
        CSG csgNet = meshToCSG(net);
        Aabb corrBounds = Aabb.of(csgNet).inflate(eps);

        List<CSG> csgRooms = new ArrayList<>();
        for (Mesh room : rooms) {
            csgRooms.add(meshToCSG(room));
        }

        for (CSG csgRoom : csgRooms) {
            Aabb roomBounds = Aabb.of(csgRoom).inflate(eps);
            if (!corrBounds.intersects(roomBounds)) continue;
            csgNet = unionBoundsFiltered(csgNet, csgRoom, eps);
            corrBounds = Aabb.of(csgNet).inflate(eps);
        }

        return csgToJmeMesh(csgNet);
    }

    /**
     * Performs a CSG union of two solids using a bounding-box pre-filter to reduce the cost of
     * JCSG's BSP-tree operations.
     *
     * <p>The method partitions the polygons of each solid into two groups:</p>
     * <ul>
     *   <li><b>near</b>: polygons whose AABB intersects the inflated bounds of the other solid;
     *       these may geometrically interact and must go through the full BSP union.</li>
     *   <li><b>far</b>: polygons that cannot possibly intersect the other solid; these are
     *       appended directly via {@code dumbUnion} without BSP processing.</li>
     * </ul>
     *
     * <p>If neither solid has any near polygons (completely disjoint bounds), the entire
     * operation falls back to {@code dumbUnion}.</p>
     *
     * <p><b>Why not POLYGON_BOUND?</b> JCSG's built-in {@code POLYGON_BOUND} optimisation was
     * found to produce open (non-manifold) edges after union on overlapping geometry due to
     * boundary misclassification. This method avoids that issue by inflating bounds by
     * {@code eps} before classifying polygons.</p>
     *
     * @param a   first CSG solid (e.g. the corridor network so far)
     * @param b   second CSG solid (e.g. a single room)
     * @param eps epsilon used to inflate bounding boxes; must be positive and ideally proportional
     *            to the routing cell size
     * @return the CSG union of {@code a} and {@code b}
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    public static CSG unionBoundsFiltered(CSG a, CSG b, double eps) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        List<Polygon> aPolys = a.getPolygons();
        List<Polygon> bPolys = b.getPolygons();

        if (aPolys == null || aPolys.isEmpty()) return b;
        if (bPolys == null || bPolys.isEmpty()) return a;

        Aabb aInfl = boundsOf(aPolys).inflate(eps);
        Aabb bInfl = boundsOf(bPolys).inflate(eps);

        List<Polygon> nearA = new ArrayList<>(), farA = new ArrayList<>();
        List<Polygon> nearB = new ArrayList<>(), farB = new ArrayList<>();
        splitByBounds(aPolys, bInfl, nearA, farA);
        splitByBounds(bPolys, aInfl, nearB, farB);

        if (nearA.isEmpty() && nearB.isEmpty()) return a.dumbUnion(b);

        CSG nearUnion;
        if (nearA.isEmpty()) {
            nearUnion = CSG.fromPolygons(nearB);
        } else if (nearB.isEmpty()) {
            nearUnion = CSG.fromPolygons(nearA);
        } else {
            nearUnion = CSG.fromPolygons(nearA).union(CSG.fromPolygons(nearB));
        }

        List<Polygon> all = new ArrayList<>(nearUnion.getPolygons());
        CSG out = CSG.fromPolygons(all);
        if (!farA.isEmpty()) out = out.dumbUnion(CSG.fromPolygons(farA));
        if (!farB.isEmpty()) out = out.dumbUnion(CSG.fromPolygons(farB));
        return out;
    }

    /**
     * Converts a jMonkeyEngine {@link Mesh} to a JCSG {@link CSG} object.
     * <p>
     * The mesh is interpreted as an indexed triangle list. For each triangle,
     * a face normal is computed and assigned to all three vertices of the corresponding
     * JCSG polygon.
     * </p>
     *
     * @param mesh the source jME mesh (may be {@code null})
     * @return a JCSG {@link CSG} instance; returns an empty CSG if the mesh is {@code null},
     *         has no position buffer, or has no indices
     */
    public static CSG meshToCSG(Mesh mesh) {
        if (mesh == null) return CSG.fromPolygons(List.of());

        FloatBuffer pb = getFloatBuffer(mesh);
        int[] idx = getIndexArray(mesh);
        if (pb == null || idx.length == 0) return CSG.fromPolygons(List.of());

        Vector3f[] pos = readPositions(pb);
        ArrayList<Polygon> polys = new ArrayList<>();

        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i], ib = idx[i + 1], ic = idx[i + 2];
            Vector3f a = pos[ia], b = pos[ib], c = pos[ic];

            // Face normal (triangle)
            Vector3f fn = b.subtract(a, new Vector3f()).cross(c.subtract(a, new Vector3f()));
            if (fn.lengthSquared() < 1e-12f) continue; // skip degenerate triangles
            fn.normalizeLocal();

            Vector3d n = Vector3d.xyz(fn.x, fn.y, fn.z);

            Vertex va = new Vertex(Vector3d.xyz(a.x, a.y, a.z), n);
            Vertex vb = new Vertex(Vector3d.xyz(b.x, b.y, b.z), n);
            Vertex vc = new Vertex(Vector3d.xyz(c.x, c.y, c.z), n);

            polys.add(new Polygon(Arrays.asList(va, vb, vc)));
        }

        return CSG.fromPolygons(polys);
    }

    /**
     * Converts a JCSG {@link CSG} object to a jMonkeyEngine {@link Mesh}.
     *
     * <p>Each JCSG polygon is triangulated via a triangle fan:
     * {@code (v0, v1, v2), (v0, v2, v3), ...}.
     * Vertices are not welded across polygon boundaries -> each polygon corner
     * gets its own independent vertex. This is required because the normals are
     * computed per face (flat shading) by {@link MeshAccumulator#toMesh()}, and
     * adjacent polygons meeting at hard architectural edges must carry different
     * normals at the same spatial position.</p>
     *
     * <p>This method is intended for use after a JCSG boolean operation (e.g. union),
     * where the output polygon soup has already been clipped and re-triangulated by JCSG.
     * The resulting mesh is suitable for rendering but is not topologically closed
     * (no shared vertices across faces).</p>
     *
     * @param csg the source CSG object (may be {@code null})
     * @return a jME {@link Mesh} with position, normal, and index buffers set;
     *         returns an empty mesh if {@code csg} is {@code null} or has no polygons
     */
    public static Mesh csgToJmeMesh(CSG csg) {
        if (csg == null) return new Mesh();

        List<Polygon> polys = csg.getPolygons();
        if (polys == null || polys.isEmpty()) return new Mesh();

        MeshAccumulator acc = new MeshAccumulator();

        for (Polygon p : polys) {
            List<Vertex> vs = p.vertices;
            if (vs == null || vs.size() < 3) continue;

            int base = acc.vertexCount();

            for (Vertex v : vs) {
                Vector3d vp = v.pos;
                acc.addVertex(new Vector3f((float) vp.x(), (float) vp.y(), (float) vp.z()));
            }

            // Triangle fan
            for (int i = 1; i < vs.size() - 1; i++) {
                acc.addTri(base, base + i, base + i + 1);
            }
        }

        return acc.toMesh();
    }

    // ============================================================================================
    // Private helpers
    // ============================================================================================

    /**
     * Partitions {@code polys} into {@code near} and {@code far} based on whether each polygon's
     * AABB intersects {@code otherInflated}.
     *
     * @param polys         polygons to partition
     * @param otherInflated inflated AABB of the other solid
     * @param near          receives polygons whose AABB intersects {@code otherInflated}
     * @param far           receives polygons whose AABB does not intersect {@code otherInflated}
     */
    private static void splitByBounds(List<Polygon> polys, Aabb otherInflated,
                                      List<Polygon> near, List<Polygon> far) {
        for (Polygon p : polys) {
            if (p == null || p.vertices == null || p.vertices.isEmpty()) continue;
            if (boundsOf(p).intersects(otherInflated)) near.add(p);
            else far.add(p);
        }
    }


    // ============================================================================================
    // AABB helpers
    // ============================================================================================

    /**
     * Computes the axis-aligned bounding box of a list of CSG polygons.
     *
     * @param polys polygon list (must not be empty)
     * @return tight AABB enclosing all vertices; returns a zero-volume AABB at the origin on
     *         empty or invalid input
     */
    private static Aabb boundsOf(List<Polygon> polys) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (Polygon p : polys) {
            if (p == null || p.vertices == null) continue;
            for (Vertex v : p.vertices) {
                Vector3d pos = v.pos;
                double x = pos.x(), y = pos.y(), z = pos.z();
                if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
                if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
            }
        }
        return Double.isFinite(minX)
                ? new Aabb(minX, minY, minZ, maxX, maxY, maxZ)
                : new Aabb(0, 0, 0, 0, 0, 0);
    }

    /**
     * Computes the axis-aligned bounding box of a single CSG polygon.
     *
     * @param p the polygon
     * @return tight AABB enclosing all vertices of {@code p}
     */
    private static Aabb boundsOf(Polygon p) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (Vertex v : p.vertices) {
            Vector3d pos = v.pos;
            double x = pos.x(), y = pos.y(), z = pos.z();
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
        }
        return new Aabb(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Immutable axis-aligned bounding box used for polygon pre-filtering during CSG union.
     *
     * @param minX minimum X coordinate
     * @param minY minimum Y coordinate
     * @param minZ minimum Z coordinate
     * @param maxX maximum X coordinate
     * @param maxY maximum Y coordinate
     * @param maxZ maximum Z coordinate
     */
    private record Aabb(double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {

        /**
         * Returns the AABB of the given CSG solid, or a zero-volume AABB at the origin if the
         * solid has no polygons.
         *
         * @param csg the CSG solid
         * @return AABB enclosing all polygons of {@code csg}
         */
        static Aabb of(CSG csg) {
            Objects.requireNonNull(csg, "csg");
            List<Polygon> polys = csg.getPolygons();
            if (polys == null || polys.isEmpty()) return new Aabb(0, 0, 0, 0, 0, 0);
            return boundsOf(polys);
        }

        /**
         * Returns a new AABB expanded by {@code eps} on all six sides.
         *
         * @param eps expansion amount (must be &ge; 0)
         * @return inflated AABB
         */
        Aabb inflate(double eps) {
            return new Aabb(minX - eps, minY - eps, minZ - eps,
                    maxX + eps, maxY + eps, maxZ + eps);
        }

        /**
         * Returns {@code true} if this AABB overlaps {@code o} (touching boundaries count as
         * intersecting).
         *
         * @param o the other AABB
         * @return {@code true} if the two AABBs overlap
         */
        boolean intersects(Aabb o) {
            return !(maxX < o.minX || minX > o.maxX
                    || maxY < o.minY || minY > o.maxY
                    || maxZ < o.minZ || minZ > o.maxZ);
        }
    }
}