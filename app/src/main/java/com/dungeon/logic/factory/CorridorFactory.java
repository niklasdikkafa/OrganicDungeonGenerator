package com.dungeon.logic.factory;

import com.dungeon.domain.Corridor;
import com.dungeon.logic.geometry.Edge;
import com.dungeon.logic.placement.corridor.routing.grid.GridIndex;
import com.dungeon.logic.placement.corridor.routing.path.NodeState;
import com.jme3.math.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.logic.geometry.Utilities.computeEdgeMidpoint;

/**
 * Factory for building {@link Corridor} domain objects from a routed voxel/path representation.
 * <p>
 * The generated corridor polyline follows the following pattern:
 * <pre>
 * center(poly0) -> midpoint(sharedEdge01) -> center(poly1) -> midpoint(sharedEdge12) -> center(poly2) -> ...
 * </pre>
 * If two consecutive path states remain in the same polygon (e.g. a pure vertical move between z-bands),
 * only the polygon center is appended for that step (no edge midpoint).
 * <p>
 * The resulting polyline is 2D (XZ plane in world coordinates) and is combined with the per-step polygon ids
 * and z-band indices so downstream systems can reconstruct the full 3D corridor profile.
 */
public final class CorridorFactory {

    /**
     * Creates a {@link Corridor} from a routed path of {@link NodeState}s.
     * <p>
     * The method converts the path into:
     * <ul>
     *   <li>A 2D polyline (world-space) built from polygon centers and shared-edge midpoints.</li>
     *   <li>A parallel list of polygon ids (one per path state).</li>
     *   <li>A parallel list of z-band indices (one per path state).</li>
     * </ul>
     *
     * <h3>Polyline construction</h3>
     * For each consecutive pair of path states {@code (a, b)}:
     * <ul>
     *   <li>If {@code a.polyId == b.polyId}: append {@code center(b)} only.</li>
     *   <li>Otherwise: append {@code midpoint(sharedEdge(a,b))} (if available), then append {@code center(b)}.</li>
     * </ul>
     *
     * <h3>Edge midpoint lookup</h3>
     * The shared edge is resolved via {@link GridIndex#findSharedEdgeById(int, int)}.
     * If no shared edge can be found (should never be the case for well-formed neighbor transitions),
     * the method falls back to adding only the target polygon center for that step.
     *
     * <h3>Input expectations</h3>
     * This method assumes the given {@code path} represents valid neighbor transitions in the routing grid.
     *
     * @param index         routing grid index providing polygon centers and shared-edge queries
     * @param path          routed path as a sequence of voxel states; must contain at least two states
     * @param width         corridor interior width (world units)
     * @param height        corridor interior height (world units)
     * @param wallThickness wall thickness used for mesh generation (world units)
     * @return a {@link Corridor} instance containing the constructed polyline and per-step metadata;
     *         if {@code path} is {@code null} or shorter than 2, an empty corridor is returned
     * @throws NullPointerException if {@code index} is {@code null}
     */
    public static Corridor createFromPath(int fromRoom, int toRoom,
                                          GridIndex index,
                                          List<NodeState> path,
                                          float width,
                                          float height,
                                          float wallThickness) {

        if (path == null || path.size() < 2) {
            return new Corridor(fromRoom, toRoom, List.of(), List.of(), List.of(), width, height, wallThickness);
        }

        ArrayList<Integer> polyIds = new ArrayList<>(path.size());
        ArrayList<Integer> zBands  = new ArrayList<>(path.size());
        for (NodeState s : path) {
            polyIds.add(s.polyId());
            zBands.add(s.zBand());
        }

        ArrayList<Vector2f> pts = new ArrayList<>();
        pts.add(index.centers[path.getFirst().polyId()].clone());

        for (int i = 0; i < path.size() - 1; i++) {
            int a = path.get(i).polyId();
            int b = path.get(i + 1).polyId();

            if (a == b) {
                // Pure vertical transition (same polygon across z-bands) or a degenerate step.
                pts.add(index.centers[b].clone());
                continue;
            }

            Edge shared = index.findSharedEdgeById(a, b);
            if (shared != null) {
                pts.add(computeEdgeMidpoint(shared, index.grid));
            }

            pts.add(index.centers[b].clone());
        }

        return new Corridor(fromRoom, toRoom, pts, polyIds, zBands, width, height, wallThickness);
    }
}