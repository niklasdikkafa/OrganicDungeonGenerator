package com.dungeon.logic.placement.corridor.routing.block;

import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.jme3.math.Vector2f;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.logic.geometry.Utilities.*;

/**
 * Rasterizes a room footprint into routing-grid polygon IDs.
 *
 * <p>This utility converts a continuous 2D room footprint (the room's horizontal ground polygon)
 * into a discrete set of grid cells (polygons) that should be treated as blocked/occupied by the
 * corridor routing system.</p>
 *
 * <h2>Use case</h2>
 * <p>The corridor pathfinding runs on a refined routing grid (see {@link GridIndex}). Before routing,
 * rooms must be marked as obstacles so the path finding algorithm can use its rules to find corridor paths. This rasterizer
 * selects all routing-grid polygons that either:</p>
 * <ul>
 *   <li>are inside the room footprint,</li>
 *   <li>overlap/intersect the footprint boundary, or</li>
 *   <li>are within a configurable clearance distance to the footprint (safety margin).</li>
 * </ul>
 * <p>The correctness of this algorithm depends on the chosen {@code clearanceRadius}.
 * Because room footprints are organic/irregular, parts of the room geometry can extend beyond the
 * footprint used for rasterization (e.g., due to offsets, chamfers, or simplification). If the
 * clearance is too small, some occupied cells may not be marked, which can allow corridors to
 * route through (or too close to) rooms.</p>
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 *   <li><b>Room polygon creation:</b> Converts the footprint to a JTS polygon.</li>
 *   <li><b>Expanded envelope gate:</b> Uses the room polygon's {@link Envelope} expanded by
 *       {@code clearanceRadius} as a cheap pre-filter.</li>
 *   <li><b>Per-cell processing:</b> For each routing-grid polygon:
 *     <ol>
 *       <li>Center point must lie inside the expanded envelope (fast reject).</li>
 *       <li>If the center is inside the room polygon ({@code covers}), accept immediately.</li>
 *       <li>Convert the cell polygon to a JTS polygon (small ring, usually quad).</li>
 *       <li>Cell envelope must intersect the expanded room envelope (fast reject).</li>
 *       <li>If polygons overlap (intersection/containment), accept.</li>
 *       <li>If {@code clearanceRadius > 0} and polygon distance is within the clearance, accept.</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <h2>Geometric robustness</h2>
 * <ul>
 *   <li>Containment uses {@code covers} (not {@code contains}) so boundary-touching cases are included.</li>
 *   <li>Overlap and distance computations delegate to JTS-based helpers from {@code Utilities} to avoid
 *       fragile hand-written segment intersection logic.</li>
 * </ul>
 *
 * <h2>Performance notes</h2>
 * <ul>
 *   <li>This method is called for each room and iterates over all routing-grid cells.
 *       The envelope checks are crucial to keep it fast.</li>
 *   <li>As written, it constructs a JTS polygon for each candidate grid cell inside the envelope gate.
 *       If profiling shows this to be a bottleneck, consider caching JTS cell polygons or their envelopes
 *       in {@link GridIndex}.</li>
 * </ul>
 *
 * <h2>Assumptions / requirements</h2>
 * <ul>
 *   <li>The room footprint ring is expected to be a valid simple polygon (no self-intersections).</li>
 *   <li>Routing-grid polygons referenced by {@link GridIndex#polys} are assumed valid and small.</li>
 *   <li>Coordinates are treated as planar 2D (world/grid X/Y). The vertical dimension is handled elsewhere
 *       (2.5D routing via z-bands).</li>
 * </ul>
 */
public final class RoomRasterizer {

    /**
     * Rasterizes a room volume footprint into routing-grid polygon IDs.
     *
     * <p>The returned list contains IDs of routing-grid polygons that should be considered blocked by the
     * room at the relevant z-band(s) (handled by the caller/voxel state builder). This method only performs
     * the 2D footprint-to-cell selection.</p>
     *
     * @param index           routing grid index providing cell polygons and their precomputed centers
     * @param vol             room volume wrapper providing the 2D footprint ring
     * @param clearanceRadius additional clearance distance around the footprint; may be {@code 0}
     * @return list of polygon IDs (indices into {@code index.polys}) that overlap or lie within the
     *         (optionally expanded) room footprint
     */
    public static List<Integer> rasterRoom(GridIndex index, RoomVolume2_5D vol, float clearanceRadius) {
        List<Vector2f> room = vol.footprint();
        if (room == null || room.size() < 3) return List.of();

        org.locationtech.jts.geom.Polygon roomPoly = toJtsPolygon(room);

        Envelope roomEnv = roomPoly.getEnvelopeInternal();
        if (clearanceRadius > 0f) {
            roomEnv = new Envelope(roomEnv);
            roomEnv.expandBy(clearanceRadius);
        }

        List<Vector2f> verts = index.grid.getVertices();
        List<Polygon> polys = index.polys;

        ArrayList<Integer> out = new ArrayList<>();

        for (int pid = 0; pid < index.centers.length; pid++) {
            Vector2f c = index.centers[pid];

            // 1) cheap gate: center must be inside expanded envelope
            if (!roomEnv.contains(c.x, c.y)) continue;

            // 2) fast accept: center inside room
            if (pointInPolygonCovers(roomPoly, c)) {
                out.add(pid);
                continue;
            }

            // 3) build cell polygon once
            Polygon cell = polys.get(pid);
            int[] idx = cell.getVertexIndices();
            org.locationtech.jts.geom.Polygon cellPoly = toJtsPolygon(idx, verts);

            // 4) cheap gate: envelopes must overlap
            if (!cellPoly.getEnvelopeInternal().intersects(roomEnv)) continue;

            // 5) overlap test (intersection / containment)
            if (polygonsOverlap(cellPoly, roomPoly)) {
                out.add(pid);
                continue;
            }

            // 6) clearance (distance to boundary)
            if (clearanceRadius > 0f && polygonDistance(cellPoly, roomPoly) <= clearanceRadius) {
                out.add(pid);
            }
        }

        return out;
    }
}