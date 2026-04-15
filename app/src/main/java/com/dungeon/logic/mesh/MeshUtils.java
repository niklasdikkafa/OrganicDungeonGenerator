package com.dungeon.logic.mesh;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Static utility methods shared across mesh builders for geometry construction,
 * polygon triangulation, and jMonkeyEngine buffer extraction.
 *
 * <h2>Cap generation</h2>
 * <p>{@link #addCap3DProjectedXZ} is the shared entry point for building floor and ceiling
 * caps for both rooms and junction patches. It projects the polygon to XZ for triangulation
 * but retains original 3D Y coordinates in the output vertices, supporting non-planar caps
 * such as those found at corridor junctions with varying heights.</p>
 *
 * <h2>Triangulation</h2>
 * <p>Polygons are triangulated using an ear-clipping algorithm. The implementation handles
 * convex and simple concave polygons. Self-intersecting polygons or polygons with
 * near-duplicate vertices may produce incorrect results or an empty triangle list.</p>
 *
 * <h2>Buffer helpers</h2>
 * <p>The {@code getFloatBuffer}, {@code getIndexArray}, and {@code readPositions} methods
 * provide safe, null-tolerant access to jME mesh buffers and are used throughout the
 * mesh builder and adapter pipeline.</p>
 */
public class MeshUtils {

    /**
     * Triangulates a 3D polygon and appends the resulting triangles to the given
     * {@link MeshAccumulator}, with winding corrected to match a desired face normal.
     *
     * <p>This method is shared by {@link com.dungeon.logic.mesh.builder.RoomMeshBuilder}
     * and {@link com.dungeon.logic.mesh.builder.CorridorMeshBuilder} for generating
     * floor and ceiling caps.</p>
     *
     * <h2>Algorithm</h2>
     * <ol>
     *   <li>Project all 3D vertices onto the XZ plane ({@code y} is ignored for triangulation).</li>
     *   <li>Ensure CCW orientation in XZ; reverse both the 2D and 3D lists if CW.</li>
     *   <li>Triangulate the 2D projection using ear-clipping.</li>
     *   <li>For each output triangle, compute its face normal from the original 3D vertices.</li>
     *   <li>If the face normal opposes {@code desiredNormal}, flip the triangle winding.</li>
     * </ol>
     *
     * @param acc           mesh accumulator receiving the triangulated cap geometry
     * @param poly3         polygon vertices in 3D; must contain at least 3 points (mutated if CW)
     * @param desiredNormal the direction the cap face normal should point toward
     *                      (e.g. {@link Vector3f#UNIT_Y} for ceiling, its negation for floor)
     */
    public static void addCap3DProjectedXZ(MeshAccumulator acc,
                                           List<Vector3f> poly3,
                                           Vector3f desiredNormal) {
        if (poly3 == null || poly3.size() < 3) return;

        Vector3f dn = desiredNormal.clone();
        if (dn.lengthSquared() < 1e-12f) return;
        dn.normalizeLocal();

        // JTS polygon from XZ coordinates
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] coords = new Coordinate[poly3.size() + 1];
        for (int i = 0; i < poly3.size(); i++) {
            Vector3f p = poly3.get(i);
            coords[i] = new Coordinate(p.x, p.z);
        }
        coords[poly3.size()] = coords[0];

        LinearRing ring;
        try {
            ring = gf.createLinearRing(coords);
        } catch (IllegalArgumentException e) {
            return;
        }

        Polygon jtsPolygon = gf.createPolygon(ring);
        if (!jtsPolygon.isValid()) {
            jtsPolygon = (Polygon) jtsPolygon.buffer(0);
            if (jtsPolygon.isEmpty()) return;
        }

        // triangulation
        Geometry triangles;
        try {
            triangles = PolygonTriangulator.triangulate(jtsPolygon);
        } catch (Exception e) {
            return;
        }

        int base = acc.vertexCount();
        for (Vector3f p : poly3) acc.addVertex(p);

        // for every JTS triangle: map vertices to poly3 indices
        for (int t = 0; t < triangles.getNumGeometries(); t++) {
            Geometry tri = triangles.getGeometryN(t);
            Coordinate[] tc = tri.getCoordinates();
            if (tc.length < 4) continue;

            int ia = findClosestIndex(poly3, tc[0]);
            int ib = findClosestIndex(poly3, tc[1]);
            int ic = findClosestIndex(poly3, tc[2]);

            if (ia < 0 || ib < 0 || ic < 0 || ia == ib || ib == ic || ia == ic) continue;

            Vector3f a = poly3.get(ia), b = poly3.get(ib), c = poly3.get(ic);
            Vector3f n = b.subtract(a, new Vector3f()).cross(c.subtract(a, new Vector3f()));
            if (n.lengthSquared() < 1e-12f) continue;
            n.normalizeLocal();

            if (n.dot(dn) >= 0f) acc.addTri(base + ia, base + ib, base + ic);
            else acc.addTri(base + ia, base + ic, base + ib);
        }
    }

    /**
     * Finds the index of the poly3 vertex, that is nearest to the given JTS coordinate. Returns -1 if there is no
     * vertex within the tolerance range.
     */
    private static int findClosestIndex(List<Vector3f> poly3, Coordinate c) {
        float bestDist = 1e-3f;
        int bestIdx = -1;
        for (int i = 0; i < poly3.size(); i++) {
            Vector3f p = poly3.get(i);
            float dx = p.x - (float) c.x;
            float dz = p.z - (float) c.y; // JTS: y = Z
            float d = dx*dx + dz*dz;
            if (d < bestDist * bestDist) {
                bestDist = (float) Math.sqrt(d);
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    // ============================================================================================
    // jME buffer helpers
    // ============================================================================================

    /**
     * Returns the position {@link FloatBuffer} from a jME mesh, or {@code null} if unavailable.
     *
     * @param mesh source mesh
     * @return the position buffer, or {@code null} if the mesh has no position data
     */
    public static FloatBuffer getFloatBuffer(Mesh mesh) {
        VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (vb == null) return null;
        Buffer data = vb.getData();
        return (data instanceof FloatBuffer fb) ? fb : null;
    }

    /**
     * Extracts the index buffer from a jME mesh as a plain {@code int[]} array.
     *
     * <p>Handles both {@code ShortBuffer} and {@code IntBuffer} index formats transparently
     * via {@link IndexBuffer#wrapIndexBuffer}.</p>
     *
     * @param mesh source mesh
     * @return index array, or an empty array if the mesh has no index buffer
     */
    public static int[] getIndexArray(Mesh mesh) {
        VertexBuffer ib = mesh.getBuffer(VertexBuffer.Type.Index);
        if (ib == null) return new int[0];
        IndexBuffer idx = IndexBuffer.wrapIndexBuffer(ib.getData());
        int[] out = new int[idx.size()];
        for (int i = 0; i < out.length; i++) out[i] = idx.get(i);
        return out;
    }

    /**
     * Reads all vertex positions from a {@link FloatBuffer} into a {@link Vector3f} array.
     *
     * <p>The buffer is rewound before reading. Each three consecutive floats are
     * interpreted as one {@code (x, y, z)} position.</p>
     *
     * @param pb position float buffer (will be rewound)
     * @return array of vertex positions
     */
    public static Vector3f[] readPositions(FloatBuffer pb) {
        pb.rewind();
        int count = pb.limit() / 3;
        Vector3f[] out = new Vector3f[count];
        for (int i = 0; i < count; i++) out[i] = new Vector3f(pb.get(), pb.get(), pb.get());
        return out;
    }
}
