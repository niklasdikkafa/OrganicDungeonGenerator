package com.dungeon.logic.placement.corridor.network.builder;

import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.graph.CorridorGraph;
import com.dungeon.logic.placement.corridor.network.graph.GraphEdge;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.corridor.network.junction.JunctionCorner;
import com.dungeon.logic.placement.corridor.network.junction.JunctionPortalLink;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.locationtech.jts.geom.*;

import java.util.*;

import static com.dungeon.config.DungeonConfig.CORRIDOR_WIDTH;

/**
 * Builds junction-specific portal connectivity and corner geometry for a {@link CorridorNetwork}.
 *
 * <p>This builder operates on the already constructed {@link CorridorGraph} inside {@link CorridorNetwork}.
 * It assumes that:</p>
 * <ul>
 *   <li>the global corridor graph has been built (nodes and adjacency),</li>
 *   <li>junction flags have been computed (see {@code GraphNode.isJunction}),</li>
 *   <li>frame points for non-junction nodes (portal candidates) have been computed.</li>
 * </ul>
 *
 * <p>For each connected component of junction nodes (a cluster of adjacent nodes marked as junctions),
 * the builder collects portal nodes (non-junction neighbors with frames enabled), sorts them around a
 * computed cluster center, and links them in a cyclic clockwise ring. For every consecutive portal pair that neighbors
 * the same junction node, it computes chamfered corner points (inner/outer, bottom/top) in the XZ plane.These corners are
 * later consumed by corridor mesh generation to form watertight junction walls. If a pair doesn't neighbor the same
 * junction node they will get connected directly from frame to frame.</p>
 *
 * <h2>Outputs</h2>
 * <p>Results are written into {@link CorridorNetwork#junctionLinksByJunctionAndPortal} using a composite key
 * {@code (junctionId, portalId)} produced by {@link #jpKey(int, int)}. Each stored {@link JunctionPortalLink}
 * contains:</p>
 * <ul>
 *   <li>the portal node id,</li>
 *   <li>the assigned junction component id (seed junction id),</li>
 *   <li>the clockwise successor portal id,</li>
 *   <li>and a {@link JunctionCorner} describing the corner geometry to the next portal.</li>
 * </ul>
 *
 * <h2>Scaling / stability</h2>
 * <p>Corner construction thresholds are derived from both the corridor width
 * ({@link com.dungeon.config.DungeonConfig#CORRIDOR_WIDTH}) and the routing grid resolution
 * ({@code routingGridEdgeLength}). This keeps the chamfer behavior stable across different routing
 * grid densities.</p>
 */
public class JunctionBuilder {

    /**
     * Chamfer distance measured backwards from the ideal ray intersection (in XZ).
     * <p>Higher values create wider bevels; too high values may overly round tight junctions.</p>
     */
    private static float JUNCTION_CHAMFER_BACKOFF;

    /**
     * Minimum distance from the portal frame point towards the intersection (in XZ).
     * <p>Prevents the chamfer from collapsing onto the portal frame point when the intersection is very close.</p>
     */
    private static float JUNCTION_MIN_CORNER_DIST;

    /**
     * Fallback length used to build finite segments when intersecting rays (in XZ).
     * <p>If this is too small, valid intersections might be missed; if too large, intersections may become
     * numerically unstable for nearly parallel directions.</p>
     */
    private static float JUNCTION_FALLBACK_LEN;

    /** Shared JTS geometry factory used for centroid, point and polygon operations. */
    private static final GeometryFactory GF = new GeometryFactory();

    /**
     * Computes portal ordering and corner geometry for all junction components in {@code net}.
     *
     * <p>This method clears and repopulates {@link CorridorNetwork#junctionLinksByJunctionAndPortal}.
     * It should be called after the corridor network graph has been created and frames have been computed
     * for portal candidate nodes.</p>
     *
     * <p>Corner thresholds are derived dynamically using both corridor width and routing grid resolution:</p>
     * <ul>
     *   <li>{@link #JUNCTION_MIN_CORNER_DIST}: avoids corner collapse close to portals</li>
     *   <li>{@link #JUNCTION_CHAMFER_BACKOFF}: backoff from intersection along the rays</li>
     *   <li>{@link #JUNCTION_FALLBACK_LEN}: segment length used for finite ray intersection checks</li>
     * </ul>
     *
     * @param net corridor network to enrich with junction portal links
     * @param routingGridEdgeLength edge length of the routing grid (used to scale thresholds)
     * @throws NullPointerException if {@code net} is {@code null}
     */
    static void buildJunctionCornerLinks(CorridorNetwork net, float routingGridEdgeLength) {
        Objects.requireNonNull(net, "net");
        net.junctionLinksByJunctionAndPortal.clear();

        // Scale thresholds by both corridor cross-section and routing resolution to keep behavior stable.
        JUNCTION_MIN_CORNER_DIST = Math.max(0.05f * CORRIDOR_WIDTH, 0.02f * routingGridEdgeLength);
        JUNCTION_CHAMFER_BACKOFF = Math.max(0.25f * CORRIDOR_WIDTH, 0.02f * routingGridEdgeLength);
        JUNCTION_FALLBACK_LEN    = Math.max(3.0f  * CORRIDOR_WIDTH, routingGridEdgeLength);

        CorridorGraph g = net.graph;
        List<Integer> visitedJunctions = new ArrayList<>();

        for (GraphNode seed : g.nodes) {
            if (seed == null || !seed.isJunction) continue;
            if (visitedJunctions.contains(seed.id)) continue;

            List<Integer> junctionIds = new ArrayList<>();
            List<Integer> portalIds   = new ArrayList<>();
            Map<Integer, Set<Integer>> junctionNbsPerPortal = new HashMap<>();

            collectJunctionCluster(g, seed, junctionIds, portalIds, junctionNbsPerPortal);
            visitedJunctions.addAll(junctionIds);

            // A junction ring needs at least two portals to form any meaningful boundary.
            if (portalIds.size() < 2) continue;

            Vector3f center = computeJunctionClusterCenter(g, junctionIds);

            // Defensive heuristics for known but rare problematic layouts.
            if (junctionIds.size() >= 4) removePortalsIfCircle(junctionIds, portalIds, net);
            if (junctionIds.size() >= 2) fixPotentialSpecialNodes(portalIds, junctionNbsPerPortal);

            List<JunctionPortalLink> portals = buildPortalLinks(g, portalIds, seed.id, center);

            sortPortalsAroundCenter(portals, center);
            linkPortalRing(portals, net, junctionNbsPerPortal);

            for (JunctionPortalLink pl : portals) {
                net.junctionLinksByJunctionAndPortal.put(jpKey(pl.junctionNodeId, pl.portalNodeId), pl);
            }
        }
    }

    // =========================================================================================
    // Cluster collection
    // =========================================================================================

    /**
     * Collects a connected component of junction nodes and its portal neighbors.
     *
     * <p>This method performs a DFS over junction-only adjacency starting from {@code j} and appends
     * all junction node ids in the connected component to {@code junctionIds}. For every junction node,
     * all non-junction neighbors are treated as portal candidates. Portals are included only if
     * their frame is enabled ({@code !frameDisabled}).</p>
     *
     * <p>{@code junctionNbsPerPortal} records the set of adjacent junction ids for each portal id.
     * This is later used to ensure that portal pairs are associated to exactly one common junction node
     * when computing corners.</p>
     *
     * @param g                    corridor graph (nodes + adjacency)
     * @param j                    DFS seed node (must be a junction)
     * @param junctionIds          output list receiving all junction ids in this component
     * @param portalIds            output list receiving all portal node ids adjacent to this component
     * @param junctionNbsPerPortal mapping portalId -> set of adjacent junction node ids
     */
    private static void collectJunctionCluster(CorridorGraph g,
                                               GraphNode j,
                                               List<Integer> junctionIds,
                                               List<Integer> portalIds,
                                               Map<Integer, Set<Integer>> junctionNbsPerPortal) {
        if (j == null || !j.isJunction) return;
        if (junctionIds.contains(j.id)) return;

        junctionIds.add(j.id);

        for (GraphEdge e : g.adjacency.getOrDefault(j.id, List.of())) {
            int nbId = e.to;
            if (nbId < 0 || nbId >= g.nodes.size()) continue;

            GraphNode nb = g.nodes.get(nbId);
            if (nb == null) continue;

            if (nb.isJunction) {
                collectJunctionCluster(g, nb, junctionIds, portalIds, junctionNbsPerPortal);
            } else {
                junctionNbsPerPortal.computeIfAbsent(nbId, _ -> new HashSet<>()).add(j.id);
                if (!nb.frameDisabled && !portalIds.contains(nbId)) {
                    portalIds.add(nbId);
                }
            }
        }
    }

    // =========================================================================================
    // Cluster center
    // =========================================================================================

    /**
     * Computes a stable center point for a junction component.
     *
     * <p>The center is used for:</p>
     * <ul>
     *   <li>sorting portals by polar angle around the junction component (XZ plane),</li>
     *   <li>defining a consistent "view" direction when orienting portal left/right corners.</li>
     * </ul>
     *
     * @param g corridor graph
     * @param junctionIds list of junction node ids in the component (must not be empty)
     * @return the computed 3D center point (XZ centroid + average Y)
     */
    private static Vector3f computeJunctionClusterCenter(CorridorGraph g, List<Integer> junctionIds) {
        if (junctionIds.isEmpty()) return new Vector3f(0, 0, 0);

        float sumX = 0f;
        float sumY = 0f;
        float sumZ = 0f;
        int count = junctionIds.size();

        for (int id : junctionIds) {
            Vector3f pos = g.nodes.get(id).position;
            sumX += pos.x;
            sumY += pos.y;
            sumZ += pos.z;
        }

        return new Vector3f(sumX / count, sumY / count, sumZ / count);
    }

    // =========================================================================================
    // Circle detection + portal culling
    // =========================================================================================

    /**
     * Removes portal nodes whose XZ position lies inside a detected junction cycle polygon.
     *
     * <p>This is a defensive heuristic for junction clusters that form a loop/cycle. In such cases,
     * portals inside the loop can break the portal ordering and lead to degenerate corner geometry.</p>
     *
     * @param junctionIds junction ids of the component
     * @param portalIds   portal ids to potentially remove (modified in place)
     * @param net         owning corridor network (used to look up node positions)
     */
    private static void removePortalsIfCircle(List<Integer> junctionIds,
                                              List<Integer> portalIds,
                                              CorridorNetwork net) {
        CorridorGraph g = net.graph;

        List<List<Integer>> cycles = findCycles(junctionIds, g, 4);
        if (cycles.isEmpty()) return;

        List<Integer> toRemove = new ArrayList<>();

        for (List<Integer> cycle : cycles) {
            Coordinate[] coords = new Coordinate[cycle.size() + 1];
            for (int i = 0; i < cycle.size(); i++) {
                Vector3f p = g.nodes.get(cycle.get(i)).position;
                coords[i] = new Coordinate(p.x, p.z);
            }
            coords[cycle.size()] = coords[0];

            Polygon cyclePolygon = GF.createPolygon(coords);

            for (int pid : portalIds) {
                if (toRemove.contains(pid)) continue;
                Vector3f p = g.nodes.get(pid).position;
                Point point = GF.createPoint(new Coordinate(p.x, p.z));
                if (cyclePolygon.covers(point)) {
                    toRemove.add(pid);
                }
            }
        }

        portalIds.removeAll(toRemove);
    }

    /**
     * Finds simple cycles inside the junction-only induced subgraph.
     *
     * <p>Cycles are returned as sequences of junction node ids. A canonical key is used to avoid
     * duplicates due to rotation or different DFS start points.</p>
     *
     * @param junctionIds set of junction ids participating in the component
     * @param g           corridor graph
     * @param minSize     minimum cycle length to report
     * @return list of cycles, each represented as an ordered list of node ids
     */
    private static List<List<Integer>> findCycles(List<Integer> junctionIds, CorridorGraph g, int minSize) {
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        Set<Integer> onPath = new HashSet<>();
        Set<String> seen = new HashSet<>();

        for (int startId : junctionIds) {
            path.clear();
            onPath.clear();
            dfsCycles(startId, startId, junctionIds, g, path, onPath, result, seen, minSize);
        }

        return result;
    }

    /**
     * DFS helper for cycle discovery.
     *
     * @param startId       fixed cycle start id
     * @param currentId     current node id in DFS
     * @param junctionIds   induced set of junction ids
     * @param g             corridor graph
     * @param path          current DFS path (modified in place)
     * @param onPath        membership set for quick back-edge detection
     * @param result        output list of cycles
     * @param seen          canonical cycle deduplication set
     * @param minSize       minimum cycle length to report
     */
    private static void dfsCycles(int startId,
                                  int currentId,
                                  List<Integer> junctionIds,
                                  CorridorGraph g,
                                  List<Integer> path,
                                  Set<Integer> onPath,
                                  List<List<Integer>> result,
                                  Set<String> seen,
                                  int minSize) {
        path.add(currentId);
        onPath.add(currentId);

        for (GraphEdge e : g.adjacency.getOrDefault(currentId, List.of())) {
            int nbId = e.to;
            if (!junctionIds.contains(nbId)) continue;

            if (nbId == startId && path.size() >= minSize) {
                List<Integer> cycle = new ArrayList<>(path);
                if (seen.add(canonicalCycleKey(cycle))) result.add(cycle);
            } else if (!onPath.contains(nbId) && nbId >= startId) {
                dfsCycles(startId, nbId, junctionIds, g, path, onPath, result, seen, minSize);
            }
        }

        path.removeLast();
        onPath.remove(currentId);
    }

    /**
     * Produces a bidirectional canonical representation of a cycle by rotating it to start at the smallest node id.
     *
     * @param cycle cycle node sequence
     * @return canonical cycle key string
     */
    private static String canonicalCycleKey(List<Integer> cycle) {
        int n = cycle.size();

        int minIdx = 0;
        for (int i = 1; i < n; i++) {
            if (cycle.get(i) < cycle.get(minIdx)) minIdx = i;
        }

        StringBuilder forward = new StringBuilder();
        StringBuilder backward = new StringBuilder();

        for (int i = 0; i < n; i++) {
            forward.append(cycle.get((minIdx + i) % n)).append(",");
            backward.append(cycle.get((minIdx - i + n) % n)).append(",");
        }

        String f = forward.toString();
        String b = backward.toString();
        return f.compareTo(b) < 0 ? f : b;
    }

    // =========================================================================================
    // Special node handling
    // =========================================================================================

    /**
     * Removes "special portal nodes" that are adjacent to exactly two junction nodes.
     *
     * <p>These nodes sit between two junction nodes and often produce unstable junction wall geometry,
     * because their frame orientation is ambiguous. This method removes them from the portal set so they
     * are not part of the portal ring.</p>
     *
     * @param portalIds             portal ids to filter (modified in place)
     * @param junctionNbsPerPortal  portalId -> adjacent junction ids mapping
     */
    private static void fixPotentialSpecialNodes(List<Integer> portalIds,
                                                 Map<Integer, Set<Integer>> junctionNbsPerPortal) {
        List<Integer> toRemove = new ArrayList<>();
        for (Integer pid : portalIds) {
            Set<Integer> nbs = junctionNbsPerPortal.get(pid);
            if (nbs == null || nbs.size() != 2) continue;
            toRemove.add(pid);
        }
        portalIds.removeAll(toRemove);
    }

    // =========================================================================================
    // Portal links + ring construction
    // =========================================================================================

    /**
     * Builds {@link JunctionPortalLink} instances for all portal node ids.
     *
     * <p>The {@code forward} vector is computed as {@code portal.position - center} (XZ only) and is used by
     * {@link JunctionPortalLink#fromPortalNodeForJunctionComponent(GraphNode, int, Vector3f, Vector3f)} to
     * orient the portal's left/right frame corners consistently relative to the junction component.</p>
     *
     * @param g         corridor graph
     * @param portalIds portal node ids (non-junction neighbors with frames enabled)
     * @param seedId    junction component id (typically the seed junction node id)
     * @param center    junction component center used for forward direction
     * @return list of portal links, one per portal id
     */
    private static List<JunctionPortalLink> buildPortalLinks(CorridorGraph g,
                                                             List<Integer> portalIds,
                                                             int seedId,
                                                             Vector3f center) {
        List<JunctionPortalLink> portals = new ArrayList<>(portalIds.size());
        for (int pid : portalIds) {
            GraphNode p = g.nodes.get(pid);
            if (p == null) continue;

            Vector3f forward = p.position.subtract(center, new Vector3f());
            forward.y = 0f;

            portals.add(JunctionPortalLink.fromPortalNodeForJunctionComponent(p, seedId, center, forward));
        }
        return portals;
    }

    /**
     * Sorts portals by polar angle around {@code center} in the XZ plane (CCW).
     *
     * @param portals portals to sort (modified in place)
     * @param center  junction component center
     */
    private static void sortPortalsAroundCenter(List<JunctionPortalLink> portals, Vector3f center) {
        portals.sort(Comparator.comparingDouble(pl ->
                Math.atan2(pl.center.z - center.z, pl.center.x - center.x)
        ));
    }

    /**
     * Links portals into a cyclic clockwise ring and computes the corner geometry for each portal-to-next pair.
     *
     * @param portals portals sorted around the junction center
     * @param net     owning corridor network (used to resolve junction node positions)
     * @param junctionNbsPerPortal portalId -> adjacent junction ids mapping
     */
    private static void linkPortalRing(List<JunctionPortalLink> portals,
                                       CorridorNetwork net,
                                       Map<Integer, Set<Integer>> junctionNbsPerPortal) {
        for (int i = 0; i < portals.size(); i++) {
            JunctionPortalLink cur = portals.get(i);
            JunctionPortalLink nxt = portals.get((i + 1) % portals.size());

            cur.nextCwPortalNodeId = nxt.portalNodeId;
            cur.cornerToNext = computeCornerForJunction(cur, nxt, net, junctionNbsPerPortal);
        }
    }

    // =========================================================================================
    // Corner computation
    // =========================================================================================

    /**
     * Computes chamfered corner geometry between two portals.
     *
     * <p>The corner is computed by intersecting rays in the XZ plane that originate from the relevant
     * portal frame points and point towards the associated junction node (not the component center).
     * The intersection is then chamfered using {@link #chamferPoint(Vector3f, Vector2f, float)}.</p>
     *
     * <p>If the two portals do not share exactly one common junction neighbor, or if ray intersection fails, or
     * if the distance between the junction portals is smaller than 1,
     * the returned corner is marked {@code directConnect=true}. This signals the downstream mesh builder to
     * directly connect portal frame points without a computed corner.</p>
     *
     * @param cur current portal link
     * @param nxt next portal link (clockwise)
     * @param net owning corridor network
     * @param junctionNbsPerPortal portalId -> adjacent junction ids mapping
     * @return computed corner geometry (or {@code directConnect=true} as fallback)
     */
    private static JunctionCorner computeCornerForJunction(JunctionPortalLink cur,
                                                           JunctionPortalLink nxt,
                                                           CorridorNetwork net,
                                                           Map<Integer, Set<Integer>> junctionNbsPerPortal) {
        JunctionCorner out = new JunctionCorner();

        Set<Integer> curNbs = junctionNbsPerPortal.get(cur.portalNodeId);
        Set<Integer> nxtNbs = junctionNbsPerPortal.get(nxt.portalNodeId);

        if (curNbs == null || nxtNbs == null || curNbs.size() != 1 || nxtNbs.size() != 1 || !curNbs.equals(nxtNbs)) {
            out.directConnect = true;
            return out;
        }

        if (cur.outerRightBottom.distance(nxt.outerLeftBottom) < 1f) {
            out.directConnect = true;
            return out;
        }

        int jid = curNbs.iterator().next();
        Vector3f junctionPos = net.graph.nodes.get(jid).position;

        Vector2f d0 = dirToPointXZ(cur.center, junctionPos);
        Vector2f d1 = dirToPointXZ(nxt.center, junctionPos);

        Vector2f I_in_bot  = segmentIntersectionXZ(cur.innerLeftBottom,  d0, nxt.innerRightBottom, d1);
        Vector2f I_in_top  = segmentIntersectionXZ(cur.innerLeftTop,     d0, nxt.innerRightTop,    d1);
        Vector2f I_out_bot = segmentIntersectionXZ(cur.outerLeftBottom,  d0, nxt.outerRightBottom, d1);
        Vector2f I_out_top = segmentIntersectionXZ(cur.outerLeftTop,     d0, nxt.outerRightTop,    d1);

        if (I_in_bot == null || I_in_top == null || I_out_bot == null || I_out_top == null) {
            out.directConnect = true;
            return out;
        }

        float yInBot = 0.5f * (cur.innerLeftBottom.y + nxt.innerRightBottom.y);
        float yInTop = 0.5f * (cur.innerLeftTop.y    + nxt.innerRightTop.y);
        float yOuBot = 0.5f * (cur.outerLeftBottom.y + nxt.outerRightBottom.y);
        float yOuTop = 0.5f * (cur.outerLeftTop.y    + nxt.outerRightTop.y);

        out.innerBottom0 = chamferPoint(cur.innerLeftBottom,  I_in_bot,  yInBot);
        out.innerBottom1 = chamferPoint(nxt.innerRightBottom, I_in_bot,  yInBot);
        out.innerTop0    = chamferPoint(cur.innerLeftTop,     I_in_top,  yInTop);
        out.innerTop1    = chamferPoint(nxt.innerRightTop,    I_in_top,  yInTop);
        out.outerBottom0 = chamferPoint(cur.outerLeftBottom,  I_out_bot, yOuBot);
        out.outerBottom1 = chamferPoint(nxt.outerRightBottom, I_out_bot, yOuBot);
        out.outerTop0    = chamferPoint(cur.outerLeftTop,     I_out_top, yOuTop);
        out.outerTop1    = chamferPoint(nxt.outerRightTop,    I_out_top, yOuTop);

        return out;
    }

    // =========================================================================================
    // Geometry helpers
    // =========================================================================================

    /**
     * Computes a normalized direction vector in the XZ plane from {@code from} to {@code to}.
     *
     * @param from start point (uses {@code x} and {@code z})
     * @param to   target point (uses {@code x} and {@code z})
     * @return normalized direction vector where {@code x} is X and {@code y} is Z
     */
    private static Vector2f dirToPointXZ(Vector3f from, Vector3f to) {
        float dx = to.x - from.x;
        float dz = to.z - from.z;

        Vector2f dir = new Vector2f(dx, dz);
        float lenSq = dir.lengthSquared();

        if (lenSq < 1e-12f) return new Vector2f(1f, 0f);

        return dir.normalizeLocal();
    }

    /**
     * Intersects two rays in XZ by intersecting finite segments of length {@link #JUNCTION_FALLBACK_LEN}.
     *
     * <p>This returns {@code null} if the segments do not intersect. In that case, the caller should fall back to
     * direct portal-to-portal connections to avoid producing invalid corner geometry.</p>
     *
     * @param aStart ray start A (uses {@code x} and {@code z})
     * @param aDir   normalized direction A ({@code x}=X, {@code y}=Z)
     * @param bStart ray start B (uses {@code x} and {@code z})
     * @param bDir   normalized direction B ({@code x}=X, {@code y}=Z)
     * @return intersection point in XZ ({@code x}=X, {@code y}=Z), or {@code null} if no intersection found
     */
    private static Vector2f segmentIntersectionXZ(Vector3f aStart, Vector2f aDir,
                                                  Vector3f bStart, Vector2f bDir) {
        LineSegment segA = new LineSegment(
                aStart.x, aStart.z,
                aStart.x + aDir.x * JUNCTION_FALLBACK_LEN,
                aStart.z + aDir.y * JUNCTION_FALLBACK_LEN
        );
        LineSegment segB = new LineSegment(
                bStart.x, bStart.z,
                bStart.x + bDir.x * JUNCTION_FALLBACK_LEN,
                bStart.z + bDir.y * JUNCTION_FALLBACK_LEN
        );

        Coordinate I = segA.intersection(segB);
        if (I != null && Double.isFinite(I.x) && Double.isFinite(I.y)) {
            return new Vector2f((float) I.x, (float) I.y);
        }
        return null;
    }

    /**
     * Computes a chamfered corner point along the segment from {@code start} to the intersection point (XZ).
     *
     * <p>The chamfer point is placed {@link #JUNCTION_CHAMFER_BACKOFF} units before the intersection (along the segment),
     * but never closer than {@link #JUNCTION_MIN_CORNER_DIST} to {@code start}. The Y component is taken from {@code y},
     * while X/Z are interpolated along the segment. If the calculated {@code fraction} would result in a chamfer point
     * that is not on the segment or if the distance between {@code start} and {@code intersectionXZ} is 0, it will be
     * set to 0.5 (i.e. the chamfer point will get set in the middle of the vector {@code start}->{@code intersectionXZ}).</p>
     *
     * @param start          3D segment start (uses {@code x} and {@code z})
     * @param intersectionXZ intersection point in XZ ({@code x}=X, {@code y}=Z)
     * @param y              Y coordinate for the returned 3D point
     * @return chamfered corner point in 3D space
     */
    private static Vector3f chamferPoint(Vector3f start, Vector2f intersectionXZ, float y) {
        LineSegment seg = new LineSegment(start.x, start.z, intersectionXZ.x, intersectionXZ.y);
        double d = seg.getLength();
        if (d == 0) {
            Coordinate p = seg.pointAlong(0.5);
            return new Vector3f((float) p.x, y, (float) p.y);
        }
        double target = Math.max(JUNCTION_MIN_CORNER_DIST, d - JUNCTION_CHAMFER_BACKOFF);
        if (!Double.isFinite(target)) target = JUNCTION_MIN_CORNER_DIST;

        double fraction = (d < 1e-12) ? 0 : target / d;
        if (fraction < 0 || fraction > 1) {
            fraction = 0.5f; // fallback to prevent invalid junction mesh
        }
        Coordinate p = seg.pointAlong(fraction);
        return new Vector3f((float) p.x, y, (float) p.y);
    }

    // =========================================================================================
    // Key helper
    // =========================================================================================

    /**
     * Computes the composite key for {@link CorridorNetwork#junctionLinksByJunctionAndPortal}.
     *
     * <p>The high 32 bits store {@code junctionId}, the low 32 bits store {@code portalId}. This allows fast lookup of
     * the portal link belonging to a specific junction component.</p>
     *
     * @param junctionId id of the junction node (component seed)
     * @param portalId   id of the portal node
     * @return composite key {@code (junctionId << 32) | portalId}
     */
    public static long jpKey(int junctionId, int portalId) {
        return (((long) junctionId) << 32) | (portalId & 0xffffffffL);
    }
}