package com.dungeon.logic.placement.corridor.connections;

import com.dungeon.domain.Room;
import com.jme3.math.Vector2f;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;

import java.util.*;

/**
 * Computes candidate {@link RoomEdge}s by running a 2D Delaunay triangulation over room center points.
 * <p>
 * This class uses the Java Topology Suite (JTS) {@link DelaunayTriangulationBuilder} to generate a set
 * of candidate connections between rooms. The produced edges are used as an input for a
 * minimum spanning tree (MST) and for adding a limited number of extra connections.
 * </p>
 *
 * <h2>Input points ("sites")</h2>
 * <p>
 * Each room contributes one site: the room's {@code interiorPoint} in the X/Y plane.
 * The triangulation is then computed on the set of sites.
 * </p>
 *
 * <h2>Mapping triangulation edges back to rooms</h2>
 * <p>
 * JTS returns edges as {@link LineString}s whose endpoints are {@link Coordinate}s. To map those
 * coordinates back to {@link Room} instances, this class keeps a lookup table from a stable coordinate
 * key ({@link CoordinateKey}) to the room(s) that share the same center coordinate.
 * If multiple rooms share identical center coordinates, a stable representative is chosen (the first
 * inserted room for that coordinate).
 * </p>
 */
public final class DelaunayTriangulation2D {

    private final GeometryFactory gf = new GeometryFactory();

    /**
     * Performs Delaunay triangulation on the rooms' 2D centers and returns unique undirected candidate edges.
     *
     * @param rooms rooms to triangulate (sites are derived from {@link Room#getInteriorPoint()})
     * @return a list of unique candidate {@link RoomEdge}s; empty if triangulation is not possible or yields no edges
     */
    public List<RoomEdge> triangulateEdges(List<Room> rooms) {
        if (rooms == null || rooms.size() < 2) return List.of();

        // 1) Build sites
        TriangulationSites sites = buildSitesFromRooms(rooms);

        // If all coords are identical, Delaunay produces no useful edges.
        if (allSame(sites.coords)) {
            return List.of();
        }

        // 2) Delaunay triangulation
        Geometry edgesGeom = buildDelaunayEdgesGeometry(sites.coords);
        if (edgesGeom == null) return List.of();

        // 3) Convert edges -> RoomEdges
        Set<RoomEdge> edges = jtsEdgesToRoomEdges(edgesGeom, sites.coordToRooms);

        return new ArrayList<>(edges);
    }

    /**
     * Builds the Delaunay "sites" (input points) from rooms:
     * <ul>
     *   <li>An array of {@link Coordinate} for JTS.</li>
     *   <li>A mapping from coordinate key to room(s) for resolving triangulation edges back to rooms.</li>
     * </ul>
     *
     * @param rooms input rooms
     * @return derived triangulation site data
     */
    private TriangulationSites buildSitesFromRooms(List<Room> rooms) {
        Map<CoordinateKey, List<Room>> coordToRooms = new HashMap<>();
        Coordinate[] coords = new Coordinate[rooms.size()];

        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            Vector2f c = r.getInteriorPoint();

            Coordinate coord = new Coordinate(c.x, c.y);
            coords[i] = coord;

            coordToRooms.computeIfAbsent(new CoordinateKey(coord), _ -> new ArrayList<>()).add(r);
        }

        return new TriangulationSites(coords, coordToRooms);
    }

    /**
     * Runs JTS Delaunay triangulation and returns the resulting edge geometry.
     *
     * @param coords input site coordinates
     * @return a {@link Geometry} containing the triangulation edges (typically a {@link MultiLineString});
     *         may be {@code null} if JTS returns no edges
     */
    private Geometry buildDelaunayEdgesGeometry(Coordinate[] coords) {
        DelaunayTriangulationBuilder dtb = new DelaunayTriangulationBuilder();
        dtb.setSites(gf.createMultiPointFromCoords(coords));
        return dtb.getEdges(gf);
    }

    /**
     * Converts the edge geometry returned by JTS into a set of {@link RoomEdge}s.
     *
     * <p>
     * Each geometry element is expected to be a {@link LineString}. The first and last coordinate of the
     * line are used as the endpoints of the corresponding room edge.
     * </p>
     *
     * @param edgesGeom geometry returned by {@link #buildDelaunayEdgesGeometry(Coordinate[])}
     * @param coordToRooms mapping from coordinate key to rooms at that coordinate
     * @return a set of unique undirected {@link RoomEdge}s
     */
    private Set<RoomEdge> jtsEdgesToRoomEdges(Geometry edgesGeom, Map<CoordinateKey, List<Room>> coordToRooms) {
        Set<RoomEdge> edges = new HashSet<>();
        int n = edgesGeom.getNumGeometries();

        for (int i = 0; i < n; i++) {
            Geometry g = edgesGeom.getGeometryN(i);
            if (!(g instanceof LineString ls)) continue;

            Coordinate[] ec = ls.getCoordinates();
            if (ec.length < 2) continue;

            Room a = pickRoom(coordToRooms, ec[0]);
            Room b = pickRoom(coordToRooms, ec[ec.length - 1]);
            if (a == null || b == null) continue;
            if (a == b) continue; // ignore self-edge

            edges.add(new RoomEdge(a, b));
        }

        return edges;
    }

    /**
     * Returns {@code true} if all coordinates in the array are exactly equal (same x/y values).
     * This is used to detect a degenerate input where Delaunay cannot produce meaningful edges.
     *
     * @param coords coordinate array
     * @return {@code true} if the array is empty or all entries share the same x/y values
     */
    private boolean allSame(Coordinate[] coords) {
        if (coords.length == 0) return true;
        Coordinate first = coords[0];
        for (int i = 1; i < coords.length; i++) {
            Coordinate c = coords[i];
            if (c.x != first.x || c.y != first.y) return false;
        }
        return true;
    }

    /**
     * Resolves a {@link Coordinate} to a {@link Room} using the coordinate-to-rooms map.
     * If multiple rooms share identical center coordinates, the first one is returned to keep behavior stable.
     *
     * @param map coordinate key to rooms
     * @param c coordinate to resolve
     * @return a room at that coordinate, or {@code null} if none exists
     */
    private Room pickRoom(Map<CoordinateKey, List<Room>> map, Coordinate c) {
        List<Room> list = map.get(new CoordinateKey(c));
        if (list == null || list.isEmpty()) return null;
        return list.getFirst();
    }

    /**
     * Holds the derived site data for triangulation.
     *
     * @param coords array of input site coordinates (one per room)
     * @param coordToRooms mapping from a stable coordinate key to the room(s) using that coordinate
     */
    private record TriangulationSites(Coordinate[] coords, Map<CoordinateKey, List<Room>> coordToRooms) {}

    /**
     * Hash key for {@link Coordinate} that uses the exact IEEE-754 bit representation of x/y.
     * <p>
     * This avoids issues with {@code -0.0} vs {@code +0.0} and ensures stable hashing with exact matches.
     * It intentionally does not apply any tolerance; only exactly identical coordinate values are grouped.
     * </p>
     */
    private static final class CoordinateKey {
        final long xb;
        final long yb;

        CoordinateKey(Coordinate c) {
            this.xb = Double.doubleToLongBits(c.x);
            this.yb = Double.doubleToLongBits(c.y);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoordinateKey k)) return false;
            return xb == k.xb && yb == k.yb;
        }

        @Override public int hashCode() {
            return Objects.hash(xb, yb);
        }
    }
}