package com.dungeon.logic.mesh.builder;

import com.dungeon.logic.mesh.MeshAccumulator;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.junction.JunctionCorner;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.dungeon.logic.placement.corridor.network.path.CorridorPath;
import com.dungeon.logic.placement.corridor.network.path.PathFrameSample;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;

import java.util.*;

import static com.dungeon.logic.mesh.MeshUtils.*;
import static com.dungeon.logic.placement.corridor.network.builder.JunctionBuilder.jpKey;
import static com.dungeon.logic.placement.corridor.network.graph.CorridorGraph.edgeKey;

/**
 * Builds a corridor shell mesh (either inner walkable surface or outer wall hull) from a
 * {@link CorridorNetwork}.
 *
 * <h2>What this builder produces</h2>
 * <p>The corridor geometry is generated as a triangle mesh by stitching together rectangular
 * cross-section frames that are stored per graph node (see {@link GraphNode}'s frame/profile
 * fields such as {@code innerLeftBottom}, {@code outerRightTop}, etc.).</p>
 *
 * <p>The builder can generate two variants:</p>
 * <ul>
 *   <li><b>Outer shell</b> ({@code buildOuterWalls=true}): includes wall thickness and is suitable for
 *       boolean union / "hull" style rendering.</li>
 *   <li><b>Inner shell</b> ({@code buildOuterWalls=false}): the interior corridor surface (walkable space).</li>
 * </ul>
 *
 * <h2>Pipeline (high level)</h2>
 * <ol>
 *   <li><b>Regular strips:</b> stitch frame-to-frame quads along non-junction graph edges.</li>
 *   <li><b>End caps:</b> close corridor tubes at degree-1 endpoints (deduplicated across paths).</li>
 *   <li><b>Junction patches:</b> fill junction clusters using portal rings from
 *       {@link CorridorNetwork#junctionLinksByJunctionAndPortal}.</li>
 * </ol>
 *
 * <h2>Important prerequisites</h2>
 * <p>This builder assumes the corridor network has already been processed by
 * {@code CorridorNetworkBuilder} (or equivalent) such that:</p>
 * <ul>
 *   <li>Graph nodes and adjacency exist and are consistent.</li>
 *   <li>Junction nodes are flagged via {@code GraphNode.isJunction}.</li>
 *   <li>Frame/profile points for non-junction nodes are available (i.e. frames have been built).</li>
 *   <li>Junction portal links and corner geometry have been computed (see {@code JunctionBuilder}).</li>
 * </ul>
 *
 * <p><b>Note:</b> This builder is intentionally "dumb": it does not attempt to repair broken inputs.
 * If tangents/frames/corners are invalid (NaNs, missing points), the generated mesh may contain holes
 * or degenerate faces.</p>
 */
public class CorridorMeshBuilder {

    /**
     * Builds a corridor mesh for the given network.
     *
     * @param net             the corridor network containing graph nodes, frames, and junction portal links
     * @param buildOuterWalls if {@code true}, build the outer shell (including wall thickness);
     *                        if {@code false}, build the inner shell (walkable surface)
     * @return a jME {@link Mesh} containing the generated corridor geometry (may be empty but never {@code null})
     * @throws NullPointerException if {@code net} is {@code null}
     */
    public static Mesh buildCorridorMesh(CorridorNetwork net,
                                         boolean buildOuterWalls) {
        Objects.requireNonNull(net, "net");

        MeshAccumulator acc = new MeshAccumulator();

        // Tracks which endpoints already received an end cap (same endpoint may appear in multiple paths)
        HashSet<Integer> capped = new HashSet<>();

        // Tracks already-built undirected graph edges (paths share graph nodes; we only want one strip per edge)
        HashSet<Long> builtEdges = new HashSet<>();

        // 1) Regular strips (frame-to-frame quads along non-junction edges)
        for (CorridorPath path : net.paths) {
            if (path == null || path.samples.size() < 2) continue;

            for (int i = 0; i < path.samples.size() - 1; i++) {
                PathFrameSample aS = path.samples.get(i);
                PathFrameSample bS = path.samples.get(i + 1);
                if (aS == null || bS == null) continue;

                int aId = aS.graphNodeId;
                int bId = bS.graphNodeId;
                if (aId == bId) continue;

                // Deduplicate the undirected graph edge
                long eKey = edgeKey(aId, bId);
                if (!builtEdges.add(eKey)) continue;

                GraphNode a = net.graph.nodes.get(aId);
                GraphNode b = net.graph.nodes.get(bId);

                // Junction edges are handled by junction patches, not by regular strips
                if (a.isJunction || b.isJunction) continue;

                // If frames are disabled there is no way to stitch a meaningful tube section
                if (a.frameDisabled || b.frameDisabled) continue;

                // Ensure consistent direction based on tangent to avoid flipped quads
                if (shouldSwapByTangent(a, b)) {
                    GraphNode tmp = a;
                    a = b;
                    b = tmp;
                }

                addFrameToFrameStrips(acc, a, b, buildOuterWalls);
            }
        }

        // 2) End caps (degree-1 endpoints), deduplicated across paths -> we need a closed mesh for boolean operations
        for (CorridorPath path : net.paths) {
            if (path == null || path.samples.size() < 3) continue;

            int firstNodeId = path.samples.getFirst().graphNodeId;
            int lastNodeId  = path.samples.getLast().graphNodeId;

            GraphNode firstNode = net.graph.nodes.get(firstNodeId);
            GraphNode lastNode  = net.graph.nodes.get(lastNodeId);

            int firstDegree = net.graph.adjacency.get(firstNodeId).size();
            int lastDegree  = net.graph.adjacency.get(lastNodeId).size();

            if (firstDegree < 2 && !firstNode.frameDisabled) {
                if (capped.add(firstNodeId)) {
                    addEndCap(acc, firstNode, buildOuterWalls, calcDirectionAwayFromNeighbor(net, firstNode));
                }
            }

            if (lastDegree < 2 && !lastNode.frameDisabled) {
                if (capped.add(lastNodeId)) {
                    addEndCap(acc, lastNode, buildOuterWalls, calcDirectionAwayFromNeighbor(net, lastNode));
                }
            }
        }

        // 3) Junction patches + caps
        addJunctionPatches(acc, net, buildOuterWalls);

        return acc.toMesh();
    }

    /**
     * Calculates a normalized vector for an endpoint node (degree 1) that
     * points directly away from its only neighbor.
     * <p>
     * This method assumes the node is an endpoint. It calculates the direction
     * by subtracting the neighbor's position from the node's position.
     *
     * @param net the corridor network
     * @param node the endpoint node
     * @return a unit vector pointing away from the neighbor node
     */
    private static Vector3f calcDirectionAwayFromNeighbor(CorridorNetwork net, GraphNode node) {
        int neighborId = net.graph.adjacency.get(node.id).getFirst().to;
        Vector3f neighborPos = net.graph.nodes.get(neighborId).position;

        return node.position.subtract(neighborPos).normalize();
    }

    /**
     * Adds the quads that stitch two non-junction frames together to form a corridor tube segment.
     *
     * <p>This method emits four quads per segment: left wall, right wall, floor, and ceiling
     * (for either the inner or outer shell).</p>
     *
     * @param acc             mesh accumulator receiving triangles
     * @param a               start node (must have valid frame/profile points)
     * @param b               end node (must have valid frame/profile points)
     * @param buildOuterWalls {@code true} to use outer frame points, {@code false} to use inner frame points
     */
    private static void addFrameToFrameStrips(MeshAccumulator acc,
                                              GraphNode a,
                                              GraphNode b,
                                              boolean buildOuterWalls) {
        if (buildOuterWalls) {
            acc.addQuad(a.outerLeftTop,      a.outerLeftBottom,  b.outerLeftBottom,  b.outerLeftTop);
            acc.addQuad(b.outerRightBottom,  a.outerRightBottom, a.outerRightTop,    b.outerRightTop);
            acc.addQuad(b.outerLeftBottom,   a.outerLeftBottom,  a.outerRightBottom, b.outerRightBottom); // floor
            acc.addQuad(a.outerRightTop,     a.outerLeftTop,     b.outerLeftTop,     b.outerRightTop);    // ceiling
        } else {
            acc.addQuad(a.innerLeftTop,      a.innerLeftBottom,  b.innerLeftBottom,  b.innerLeftTop);
            acc.addQuad(b.innerRightBottom,  a.innerRightBottom, a.innerRightTop,    b.innerRightTop);
            acc.addQuad(b.innerLeftBottom,   a.innerLeftBottom,  a.innerRightBottom, b.innerRightBottom);  // floor
            acc.addQuad(a.innerRightTop,     a.innerLeftTop,     b.innerLeftTop,     b.innerRightTop);     // ceiling
        }
    }

    /**
     * Adds an end cap quad at a corridor endpoint.
     *
     * <p>The cap is oriented using the desired normal.</p>
     *
     * @param acc               mesh accumulator receiving triangles
     * @param n                 endpoint node (degree 1 recommended)
     * @param buildOuterWalls   {@code true} to cap the outer shell; {@code false} to cap the inner shell
     * @param desiredNormal     the direction vector for the desired normal of the end cap
     */
    private static void addEndCap(MeshAccumulator acc,
                                  GraphNode n,
                                  boolean buildOuterWalls,
                                  Vector3f desiredNormal) {

        if (buildOuterWalls) {
            addQuadFacing(acc,
                    n.outerLeftBottom, n.outerRightBottom, n.outerRightTop, n.outerLeftTop,
                    desiredNormal);
        } else {
            addQuadFacing(acc,
                    n.innerLeftBottom, n.innerRightBottom, n.innerRightTop, n.innerLeftTop,
                    desiredNormal);
        }
    }

    /**
     * Adds a quad with winding chosen so its geometric normal points towards {@code desiredNormal}.
     *
     * <p>This is used to ensure end caps face outwards/inwards consistently.</p>
     *
     * @param acc           mesh accumulator
     * @param a             quad vertex A
     * @param b             quad vertex B
     * @param c             quad vertex C
     * @param d             quad vertex D
     * @param desiredNormal direction the quad normal should point to (approx.)
     */
    private static void addQuadFacing(MeshAccumulator acc,
                                      Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                      Vector3f desiredNormal) {
        Vector3f n = b.subtract(a, new Vector3f()).cross(c.subtract(a, new Vector3f()));
        if (n.lengthSquared() < 1e-12f) return;

        if (n.dot(desiredNormal) < 0f) acc.addQuad(a, d, c, b);
        else acc.addQuad(a, b, c, d);
    }

    /**
     * Returns {@code true} if the segment should be constructed in swapped (b->a) order based on node {@code a}'s tangent.
     *
     * <p>This is a small stabilizer against flipped strip orientation when two nodes appear in inconsistent
     * order across different paths that share graph edges.</p>
     *
     * @param a first node
     * @param b second node
     * @return {@code true} if caller should swap nodes to align the build direction with {@code a}'s tangent
     */
    private static boolean shouldSwapByTangent(GraphNode a,
                                               GraphNode b) {
        if (a.tangent.lengthSquared() < 1e-8f) return false;

        Vector3f ab = b.position.subtract(a.position, new Vector3f());
        ab.y = 0f;
        if (ab.lengthSquared() < 1e-8f) return false;
        ab.normalizeLocal();

        Vector3f t = new Vector3f(a.tangent.x, 0f, a.tangent.z);
        if (t.lengthSquared() < 1e-8f) return false;
        t.normalizeLocal();

        // If tangent points opposite to the (a->b) direction we swap to keep orientation consistent.
        return t.dot(ab) < 0f;
    }

    /**
     * Builds junction wall patches and caps by walking portal rings from
     * {@link CorridorNetwork#junctionLinksByJunctionAndPortal}.
     *
     * <p>For each junction id, portals are expected to form a closed ring via {@code nextCwPortalNodeId}.
     * The ring is then used to:</p>
     * <ul>
     *   <li>Build wall segments between consecutive portals using {@link JunctionCorner} data.</li>
     *   <li>Build a (possibly non-planar) floor and ceiling cap by projecting the 3D polygon to XZ.</li>
     * </ul>
     *
     * <p>If a corner is marked {@code directConnect}, the mesh builder connects portal frames directly and
     * skips the chamfer corner points.</p>
     *
     * @param acc             mesh accumulator
     * @param net             corridor network containing junction portal links
     * @param buildOuterWalls {@code true} to use outer portal corners, {@code false} to use inner portal corners
     */
    private static void addJunctionPatches(MeshAccumulator acc,
                                           CorridorNetwork net,
                                           boolean buildOuterWalls) {

        Map<Long, JunctionPortalLink> map = net.junctionLinksByJunctionAndPortal;
        if (map.isEmpty()) return;

        HashSet<Long> visited = new HashSet<>();

        for (JunctionPortalLink start : map.values()) {
            if (start == null) continue;

            int jId = start.junctionNodeId;
            long startKey = jpKey(jId, start.portalNodeId);
            if (visited.contains(startKey)) continue;

            List<JunctionPortalLink> ring = new ArrayList<>();
            Map<Integer, JunctionPortalLink> byPortal = new HashMap<>();

            int startPortal = start.portalNodeId;
            int curPortal   = startPortal;

            do {
                long key = jpKey(jId, curPortal);
                JunctionPortalLink cur = map.get(key);

                if (cur == null || cur.junctionNodeId != jId) break;
                if (visited.contains(key)) break;

                visited.add(key);
                ring.add(cur);
                byPortal.put(cur.portalNodeId, cur);

                int next = cur.nextCwPortalNodeId;
                if (next < 0) break;

                curPortal = next;
            } while (curPortal != startPortal);

            if (ring.size() < 3) continue;

            // Walls around the ring
            for (JunctionPortalLink cur : ring) {
                JunctionPortalLink nxt = byPortal.get(cur.nextCwPortalNodeId);
                if (nxt == null || cur.cornerToNext == null) continue;
                JunctionCorner c = cur.cornerToNext;

                if (!c.directConnect && buildOuterWalls) {
                    acc.addQuad(cur.outerLeftBottom, cur.outerLeftTop, c.outerTop0, c.outerBottom0);
                    acc.addQuad(c.outerBottom0, c.outerTop0, c.outerTop1, c.outerBottom1);
                    acc.addQuad(c.outerBottom1, c.outerTop1, nxt.outerRightTop, nxt.outerRightBottom);
                } else if (!c.directConnect) {
                    acc.addQuad(cur.innerLeftBottom, cur.innerLeftTop, c.innerTop0, c.innerBottom0);
                    acc.addQuad(c.innerBottom0, c.innerTop0, c.innerTop1, c.innerBottom1);
                    acc.addQuad(c.innerBottom1, c.innerTop1, nxt.innerRightTop, nxt.innerRightBottom);
                } else if (buildOuterWalls) {
                    acc.addQuad(cur.outerLeftBottom, cur.outerLeftTop, nxt.outerRightTop, nxt.outerRightBottom);
                } else {
                    acc.addQuad(cur.innerLeftBottom, cur.innerLeftTop, nxt.innerRightTop, nxt.innerRightBottom);
                }
            }

            // Caps
            List<Vector3f> floorPoly = new ArrayList<>();
            List<Vector3f> ceilPoly  = new ArrayList<>();

            for (JunctionPortalLink cur : ring) {
                JunctionCorner c = cur.cornerToNext;
                if (c == null) continue;

                if (!c.directConnect && buildOuterWalls) {
                    floorPoly.add(cur.outerRightBottom);
                    floorPoly.add(cur.outerLeftBottom);
                    floorPoly.add(c.outerBottom0);
                    floorPoly.add(c.outerBottom1);

                    ceilPoly.add(cur.outerRightTop);
                    ceilPoly.add(cur.outerLeftTop);
                    ceilPoly.add(c.outerTop0);
                    ceilPoly.add(c.outerTop1);
                } else if (!c.directConnect) {
                    floorPoly.add(cur.innerRightBottom);
                    floorPoly.add(cur.innerLeftBottom);
                    floorPoly.add(c.innerBottom0);
                    floorPoly.add(c.innerBottom1);

                    ceilPoly.add(cur.innerRightTop);
                    ceilPoly.add(cur.innerLeftTop);
                    ceilPoly.add(c.innerTop0);
                    ceilPoly.add(c.innerTop1);
                } else if (buildOuterWalls) {
                    floorPoly.add(cur.outerRightBottom);
                    floorPoly.add(cur.outerLeftBottom);

                    ceilPoly.add(cur.outerRightTop);
                    ceilPoly.add(cur.outerLeftTop);
                } else {
                    floorPoly.add(cur.innerRightBottom);
                    floorPoly.add(cur.innerLeftBottom);

                    ceilPoly.add(cur.innerRightTop);
                    ceilPoly.add(cur.innerLeftTop);
                }
            }

            addCap3DProjectedXZ(acc, ceilPoly,  Vector3f.UNIT_Y);
            addCap3DProjectedXZ(acc, floorPoly, Vector3f.UNIT_Y.negate());
        }
    }
}