package com.dungeon.logic.mesh.builder;

import com.dungeon.domain.Room;
import com.dungeon.logic.mesh.MeshAccumulator;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.dungeon.logic.geometry.Utilities.ensureCCW;
import static com.dungeon.logic.mesh.MeshUtils.*;

/**
 * Builds a closed room shell mesh (walls + floor/ceiling caps) from a {@link Room}.
 *
 * <p>The room footprint is defined in 2D (XZ plane) by either the room's inner corners
 * (walkable interior) or outer corners (wall boundary), depending on the requested shell.</p>
 *
 * <h2>Generated geometry</h2>
 * <ul>
 *   <li><b>Wall strip:</b> A vertical quad strip is generated along the footprint edges.</li>
 *   <li><b>Caps:</b> Floor and ceiling are triangulated from the footprint by projecting to XZ
 *       (see {@link com.dungeon.logic.mesh.MeshUtils#addCap3DProjectedXZ}).</li>
 * </ul>
 *
 * <h2>Inner vs. outer shell</h2>
 * <ul>
 *   <li><b>Inner shell</b> ({@code buildOuterWalls=false}):
 *     <ul>
 *       <li>Footprint: {@link Room#getInnerCorners()}</li>
 *       <li>Floor at {@link Room#getZLevel()}</li>
 *       <li>Ceiling at {@code zLevel + height}</li>
 *     </ul>
 *   </li>
 *   <li><b>Outer shell</b> ({@code buildOuterWalls=true}):
 *     <ul>
 *       <li>Footprint: {@link Room#getOuterCorners()}</li>
 *       <li>Includes slab thickness: floor lowered by {@link Room#getFloorThickness()},
 *           ceiling raised by the same amount.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Robustness</h2>
 * <p>The input footprint is sanitized before meshing:</p>
 * <ul>
 *   <li>Consecutive duplicate (or near-duplicate) vertices are removed.</li>
 *   <li>A redundant closing vertex equal to the first vertex is removed (if present).</li>
 *   <li>Orientation is normalized to CCW using {@link com.dungeon.logic.geometry.Utilities#ensureCCW(List)}.</li>
 * </ul>
 *
 * <p><b>Note:</b> This builder assumes the footprint is a simple polygon. Self-intersections,
 * small edges, or highly degenerate polygons can still lead to invalid triangulation
 * or degenerate faces. If this mesh builder will get called on the {@link com.dungeon.logic.placement.room.RoomPlacer}
 * results, this should never be the case.</p>
 */
public class RoomMeshBuilder {

    /**
     * Builds the mesh for a single room as either inner or outer shell.
     *
     * @param room            the source room (immutable domain object)
     * @param buildOuterWalls if {@code true}, build the outer shell using {@link Room#getOuterCorners()}
     *                        and include {@link Room#getFloorThickness()} on both floor/ceiling;
     *                        if {@code false}, build the inner shell from {@link Room#getInnerCorners()}
     * @return a jME {@link Mesh} containing walls + caps; may be empty but never {@code null}
     * @throws NullPointerException if {@code room} is {@code null}
     */
    public static Mesh buildRoomMesh(Room room, boolean buildOuterWalls) {
        Objects.requireNonNull(room, "room");

        MeshAccumulator acc = new MeshAccumulator();

        List<Vector2f> loop2d = buildOuterWalls ? room.getOuterCorners() : room.getInnerCorners();
        if (loop2d == null || loop2d.size() < 3) return new Mesh();

        loop2d = sanitizePolygon(loop2d, 1e-4f);
        if (loop2d.size() < 3) return new Mesh();

        float z = room.getZLevel();
        float h = room.getHeight();
        float t = room.getFloorThickness();

        float yFloor;
        float yCeil;

        if (buildOuterWalls) {
            yFloor = z - t;
            yCeil  = z + h + t;
        } else {
            yFloor = z;
            yCeil  = z + h;
        }

        // walls
        addWallStrip(acc, loop2d, yFloor, yCeil);

        // caps: build 3D polygon at constant Y
        List<Vector3f> floor3 = toPoly3(loop2d, yFloor);
        List<Vector3f> ceil3  = toPoly3(loop2d, yCeil);

        // ceiling faces up, floor faces down
        addCap3DProjectedXZ(acc, ceil3,  Vector3f.UNIT_Y);
        addCap3DProjectedXZ(acc, floor3, Vector3f.UNIT_Y.negate());

        return acc.toMesh();
    }

    /**
     * Adds a vertical quad strip along the polygon boundary.
     *
     * <p>The polygon is normalized to CCW to ensure consistent wall winding.</p>
     *
     * @param acc    mesh accumulator receiving generated quads
     * @param poly2d footprint polygon in XZ coordinates (stored as {@code (x,y)} in {@link Vector2f})
     * @param y0     floor Y coordinate
     * @param y1     ceiling Y coordinate
     */
    private static void addWallStrip(MeshAccumulator acc,
                                     List<Vector2f> poly2d,
                                     float y0,
                                     float y1) {
        List<Vector2f> p = ensureCCW(poly2d);
        int n = p.size();
        if (n < 3) return;

        for (int i = 0; i < n; i++) {
            Vector2f a2 = p.get(i);
            Vector2f b2 = p.get((i + 1) % n);

            Vector3f aB = new Vector3f(a2.x, y0, a2.y);
            Vector3f aT = new Vector3f(a2.x, y1, a2.y);
            Vector3f bB = new Vector3f(b2.x, y0, b2.y);
            Vector3f bT = new Vector3f(b2.x, y1, b2.y);

            acc.addQuad(aB, aT, bT, bB);
        }
    }

    /**
     * Sanitizes a 2D polygon ring for meshing by removing near-duplicate points and normalizing orientation.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Remove consecutive points whose squared distance is {@code <= eps^2}.</li>
     *   <li>If the last point duplicates the first (within {@code eps}), drop the last point.</li>
     *   <li>Return CCW orientation (see {@link com.dungeon.logic.geometry.Utilities#ensureCCW(List)}).</li>
     * </ul>
     *
     * @param poly input polygon ring (not necessarily CCW, may contain duplicates)
     * @param eps  distance threshold used to treat points as equal
     * @return a cleaned, CCW-oriented ring (may be the same reference if no changes were needed)
     */
    private static List<Vector2f> sanitizePolygon(List<Vector2f> poly, float eps) {
        if (poly == null || poly.size() < 3) return poly;

        ArrayList<Vector2f> pts = new ArrayList<>();
        for (Vector2f p : poly) {
            if (pts.isEmpty() || p.distanceSquared(pts.getLast()) > eps * eps) {
                pts.add(p.clone());
            }
        }
        if (pts.size() >= 2 && pts.getFirst().distanceSquared(pts.getLast()) <= eps * eps) {
            pts.removeLast();
        }
        return ensureCCW(pts);
    }

    /**
     * Lifts a 2D polygon ring into 3D by assigning a constant Y value and mapping
     * {@code Vector2f(x,z)} to {@code Vector3f(x,y,z)}.
     *
     * @param poly2d polygon ring in 2D (XZ plane encoded as {@code (x,y)} in {@link Vector2f})
     * @param y      constant Y coordinate for all output vertices
     * @return the 3D polygon ring
     */
    static List<Vector3f> toPoly3(List<Vector2f> poly2d, float y) {
        ArrayList<Vector3f> out = new ArrayList<>();
        for (Vector2f p : poly2d) out.add(new Vector3f(p.x, y, p.y));
        return out;
    }
}