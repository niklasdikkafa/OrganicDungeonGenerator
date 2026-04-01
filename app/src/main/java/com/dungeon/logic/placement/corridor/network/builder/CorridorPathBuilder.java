package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.domain.Corridor;
import com.dungeon.logic.placement.corridor.network.graph.PointKind;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathPoint3D;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import java.util.List;

import static com.dungeon.config.DungeonConfig.*;

/**
 * Builds a {@link CorridorPath} (raw 3D centerline samples) from a routed {@link Corridor}.
 *
 * <p>The input corridor provides:
 * <ul>
 *   <li>a 2D polyline ({@code polyline2D}) representing the routed centerline in world/grid space,</li>
 *   <li>per-step polygon ids ({@code polyIds}) and discrete vertical layers ({@code zBands}) produced by the pathfinder.</li>
 * </ul>
 *
 * <p>This builder converts the routing output into a stable sequence of {@link PathPoint3D} samples that later
 * become semantic graph nodes (via {@code NodeKey}) and are used for corridor mesh generation.</p>
 *
 * <h3>Output format</h3>
 * <ul>
 *   <li>{@link PointKind#VOXEL_CENTER}: points located at polygon/cell centers</li>
 *   <li>{@link PointKind#EDGE_MID}: points located at shared-edge midpoints between consecutive polygons</li>
 * </ul>
 *
 * <h3>Stairs</h3>
 * <p>The router may emit an <em>expanded stair macro</em> which encodes a vertical transition as the pattern
 * {@code A0, B0, B1, C0, C1, D1}. If detected, this builder inserts intermediate points with interpolated Y
 * coordinates to form a smooth-ish 3D stair centerline.</p>
 */
class CorridorPathBuilder {

    /**
     * Converts a routed {@link Corridor} into a {@link CorridorPath} containing raw 3D centerline samples.
     *
     * <p>The resulting {@link CorridorPath#rawPoints} starts with a voxel center point and then alternates between
     * edge-mid points and voxel center points for each polygon-to-polygon transition. If an expanded stair pattern is
     * detected at a given index, the corresponding 3D stair sequence is emitted instead.</p>
     *
     * @param corridorIndex index of this corridor inside the overall corridor list (used for debug/correlation)
     * @param corridor      routed corridor containing polyline, polygon ids and z-band information
     * @return a {@link CorridorPath} with populated {@link CorridorPath#rawPoints}; may be empty if the input is empty or 1
     */
    static CorridorPath buildPath(int corridorIndex, Corridor corridor) {
        List<Vector2f> polyline2D = corridor.getPolyline2D();
        int[] polyIds = corridor.getPolyIds();
        short[] zBands = corridor.getZBands();

        CorridorPath out = new CorridorPath(corridor.getFromRoomId(), corridor.getToRoomId(), corridorIndex, CORRIDOR_WIDTH, CORRIDOR_HEIGHT, WALL_THICKNESS);

        if (polyIds.length == 0 || polyline2D == null || polyline2D.size() < 2) return out;

        int stateIdx = 0;
        int ptIdx;

        out.rawPoints.add(makeVoxelCenterPoint(polyIds[0], zBands[0], polyline2D.getFirst()));
        ptIdx = 1;

        while (stateIdx < polyIds.length - 1) {
            // Expanded stair pattern: A0, B0, B1, C0, C1, D1
            if (looksLikeExpandedStair(polyIds, zBands, stateIdx)) {
                int a  = stateIdx;
                int b0 = stateIdx + 1;
                int b1 = stateIdx + 2;
                int c0 = stateIdx + 3;
                int c1 = stateIdx + 4;
                int d  = stateIdx + 5;

                if (ptIdx + 7 >= polyline2D.size()) break;

                short zA = zBands[a];
                short zD = zBands[d];

                Vector2f abMid = polyline2D.get(ptIdx); // A-B edge point
                Vector2f bC    = polyline2D.get(ptIdx + 1); // B center
                Vector2f bcMid = polyline2D.get(ptIdx + 3); // B-C edge point
                Vector2f cC    = polyline2D.get(ptIdx + 4); // C center
                Vector2f cdMid = polyline2D.get(ptIdx + 6); // C-D edge point
                Vector2f dC    = polyline2D.get(ptIdx + 7); // D center

                float yA  = voxelCenterY(zA); // zFrom mid (y for jME)
                float yD  = voxelCenterY(zD); // zTo mid (y for jME)
                float yBC = 0.5f * (yA + yD); // 3D y between zFrom and zTo

                float yAB = yA;
                float yB  = yA + (yBC - yA) / 2f; // y between yA and yBC
                float yC  = yBC + (yD - yBC) / 2f; // y between yBC and yD
                float yCD = yD;

                out.rawPoints.add(new PathPoint3D(
                        PointKind.EDGE_MID, new Vector3f(abMid.x, yAB, abMid.y),
                        polyIds[a], polyIds[b0], zBands[a]));

                out.rawPoints.add(new PathPoint3D(
                        PointKind.VOXEL_CENTER, new Vector3f(bC.x, yB, bC.y),
                        polyIds[b0], -1, zBands[b0]));

                out.rawPoints.add(new PathPoint3D(
                        PointKind.EDGE_MID, new Vector3f(bcMid.x, yBC, bcMid.y),
                        polyIds[b1], polyIds[c0], zBands[b1]));

                out.rawPoints.add(new PathPoint3D(
                        PointKind.VOXEL_CENTER, new Vector3f(cC.x, yC, cC.y),
                        polyIds[c0], -1, zBands[c0]));

                out.rawPoints.add(new PathPoint3D(
                        PointKind.EDGE_MID, new Vector3f(cdMid.x, yCD, cdMid.y),
                        polyIds[c1], polyIds[d], zBands[c1]));

                out.rawPoints.add(makeVoxelCenterPoint(polyIds[d], zBands[d], dC));

                stateIdx += 5;
                ptIdx += 8;
                continue;
            }

            int aPoly = polyIds[stateIdx];
            int bPoly = polyIds[stateIdx + 1];
            short bZ  = zBands[stateIdx + 1];

            if (aPoly == bPoly) {
                // same poly -> just add voxel center point (should never be the case but just in case)
                if (ptIdx >= polyline2D.size()) break;
                out.rawPoints.add(makeVoxelCenterPoint(bPoly, bZ, polyline2D.get(ptIdx)));
                ptIdx += 1;
            } else {
                // edge -> add edge-mid + voxel center
                if (ptIdx + 1 >= polyline2D.size()) break;
                out.rawPoints.add(makeEdgeMidPoint(aPoly, bPoly, zBands[stateIdx], polyline2D.get(ptIdx)));
                out.rawPoints.add(makeVoxelCenterPoint(bPoly, bZ, polyline2D.get(ptIdx + 1)));
                ptIdx += 2;
            }

            stateIdx += 1;
        }

        return out;
    }

    /**
     * Detects whether the routing state sequence at index {@code i} matches the expanded stairs macro:
     * {@code A0, B0, B1, C0, C1, D1}.
     *
     * <p>Constraints enforced by this check:
     * <ul>
     *   <li>{@code B0} and {@code B1} refer to the same polygon id</li>
     *   <li>{@code C0} and {@code C1} refer to the same polygon id</li>
     *   <li>{@code B} and {@code C} are different polygon ids</li>
     *   <li>{@code zFrom != zTo} and {@code |zTo - zFrom| == 1}</li>
     *   <li>{@code (B0, C0)} are on {@code zFrom} and {@code (B1, C1)} are on {@code zTo}</li>
     * </ul>
     *
     * @param polyIds polygon id sequence from routing
     * @param zBands  z-band sequence from routing (same length as {@code polyIds})
     * @param i       start index in the state sequence
     * @return {@code true} if a stairs macro begins at {@code i}, otherwise {@code false}
     */
    private static boolean looksLikeExpandedStair(int[] polyIds, short[] zBands, int i) {
        if (i + 5 >= polyIds.length) return false;

        int a=i, b0=i+1, b1=i+2, c0=i+3, c1=i+4, d=i+5;

        if (polyIds[b0] != polyIds[b1]) return false;
        if (polyIds[c0] != polyIds[c1]) return false;
        if (polyIds[b0] == polyIds[c0]) return false;

        short zFrom = zBands[a];
        short zTo   = zBands[d];

        if (zBands[b0] != zFrom || zBands[c0] != zFrom) return false;
        if (zBands[b1] != zTo   || zBands[c1] != zTo)   return false;
        if (zFrom == zTo) return false;
        if (Math.abs(zTo - zFrom) != 1) return false;

        return true;
    }

    /**
     * Creates a {@link PointKind#VOXEL_CENTER} sample at the given 2D position and z-band.
     *
     * <p>The Y coordinate is derived from {@link #voxelCenterY(short)}.</p>
     *
     * @param polyId polygon id the voxel center belongs to
     * @param zBand  discrete z-band index
     * @param p2     2D position (x,y) where y maps to world Z
     * @return a new {@link PathPoint3D} of kind {@link PointKind#VOXEL_CENTER}
     */
    private static PathPoint3D makeVoxelCenterPoint(int polyId, short zBand, Vector2f p2) {
        return new PathPoint3D(
                PointKind.VOXEL_CENTER,
                new Vector3f(p2.x, voxelCenterY(zBand), p2.y),
                polyId, -1, zBand
        );
    }

    /**
     * Creates a {@link PointKind#EDGE_MID} sample at the given 2D position and z-band.
     *
     * <p>This represents a transition between two consecutive routing polygons.</p>
     *
     * @param polyA polygon id on one side of the shared edge
     * @param polyB polygon id on the other side of the shared edge
     * @param zBand discrete z-band index (as int, will be cast to short)
     * @param p2    2D edge-midpoint position (x,y) where y maps to world Z
     * @return a new {@link PathPoint3D} of kind {@link PointKind#EDGE_MID}
     */
    private static PathPoint3D makeEdgeMidPoint(int polyA, int polyB, int zBand, Vector2f p2) {
        return new PathPoint3D(
                PointKind.EDGE_MID,
                new Vector3f(p2.x, voxelCenterY((short) zBand), p2.y),
                polyA, polyB, (short) zBand
        );
    }

    /**
     * Computes the world-space Y coordinate for the center of a voxel cell at the given z-band.
     *
     * <p>A small number will get added so the corridor ground / ceiling won't be coplanar with the room ground / ceiling. This will reduce errors
     * for the mesh generation. It can be set to 0 if you want coplanar corridor-room connections. This may result in degenerate results.</p>
     *
     * <p>The coordinate uses {@link com.dungeon.config.DungeonConfig#Z_BAND_HEIGHT} as vertical spacing and places the center at
     * half corridor height above the band base (so a corridor sits centered within its band).</p>
     *
     * @param zBand discrete z-band index
     * @return world-space Y coordinate for the voxel center
     */
    private static float voxelCenterY(short zBand) {
        return zBand * Z_BAND_HEIGHT + CORRIDOR_HEIGHT * 0.5f + C_REDUCER * 0.5f;
    }
}
