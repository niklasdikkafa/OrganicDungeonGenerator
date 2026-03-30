package com.dungeon.logic.grid.builder;

import com.dungeon.logic.geometry.Polygon;
import com.jme3.math.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static com.dungeon.logic.geometry.Utilities.ensureCCW;

/**
 * Builds a triangular connectivity (triangulation) for a hex-ring point set.
 * <p>
 * The input {@code vertices} are assumed to be generated in concentric hex rings
 * (center + rings), e.g. by {@link HexPointGenerator}. This class does not compute
 * the points themselves; it only connects them into triangles in a deterministic pattern.
 * </p>
 *
 * <h2>Indexing model</h2>
 * The point generator produces:
 * <ul>
 *   <li>index {@code 0}: the center vertex</li>
 *   <li>ring {@code r}: exactly {@code 6*r} vertices (for {@code r >= 1})</li>
 * </ul>
 *
 * <h2>Output</h2>
 * The method returns a list of triangle polygons (each with 3 indices) that form a
 * hexagonal tiling topology. Each triangle is normalized to counter-clockwise order
 * to keep downstream geometry consistent.
 */
public final class TriangleConnector {

    /**
     * Connects a concentric hex-ring vertex set into a triangular mesh.
     * <p>
     * Construction happens in two stages:
     * </p>
     * <ol>
     *   <li><b>Ring 1 fan:</b> The first ring is connected to the center vertex,
     *       producing 6 triangles around the origin.</li>
     *   <li><b>Rings r >= 2:</b> Each outer ring is connected to its previous ring.
     *       The ring is processed in 6 "sectors" (one per hex side). Within each sector,
     *       the method creates triangles between the current ring and the inner ring,
     *       with special handling at sector boundaries (first/last vertex in a sector).</li>
     * </ol>
     *
     * <h3>Ring offsets</h3>
     * The array {@code ringOffset[r]} stores the start index of ring {@code r} in the
     * {@code vertices} list:
     * <ul>
     *   <li>{@code ringOffset[0] = 0} (center)</li>
     *   <li>{@code ringOffset[1] = 1}</li>
     *   <li>{@code ringOffset[r] = ringOffset[r-1] + 6*(r-1)} for {@code r >= 2}</li>
     * </ul>
     * This matches the point generator layout: ring {@code (r-1)} has {@code 6*(r-1)} vertices.
     *
     * @param vertices vertex positions in "center + rings" order
     * @param sideCount number of rings (radius) to connect; must be >= 1 for any triangles
     * @return a list of triangle polygons representing the hex tiling connectivity
     */
    public static List<Polygon> connect(List<Vector2f> vertices, int sideCount) {
        List<Polygon> triangles = new ArrayList<>();

        // ringOffset[r] = start index of ring r (r=0 is the center vertex at index 0)
        int[] ringOffset = new int[sideCount + 1];
        ringOffset[0] = 0;
        for (int r = 1; r <= sideCount; r++) {
            if (r == 1) ringOffset[r] = 1;
            else ringOffset[r] = ringOffset[r - 1] + 6 * (r - 1);
        }

        // --- Stage 1: connect center (0) to ring 1 as a triangle fan ---
        if (sideCount >= 1) {
            int start1 = ringOffset[1];
            int cnt1 = 6;

            for (int i = 0; i < cnt1; i++) {
                int a = 0;
                int b = start1 + i;
                int c = start1 + (i + 1) % cnt1;
                triangles.add(ensureCCW(new Polygon(new int[]{a, b, c}), vertices));
            }
        }

        // --- Stage 2: connect rings r >= 2 to ring r-1 ---
        for (int r = 2; r <= sideCount; r++) {
            int currStart = ringOffset[r];
            int prevStart = ringOffset[r - 1];

            // ring r has 6*r vertices
            int currCount = 6 * r;

            // In each of 6 sectors:
            // - the inner ring contributes (r-1) vertices (innerPerSector)
            // - the outer ring contributes r vertices (outerPerSector)
            int innerPerSector = r - 1;
            int outerPerSector = r;

            for (int i = 0; i < currCount; i++) {
                int curr = currStart + i;
                int nextCurr = currStart + (i + 1) % currCount;

                // Determine which of the 6 sectors this vertex belongs to,
                // and its local position within that sector.
                int sector = i / outerPerSector;
                int posInSector = i % outerPerSector;

                // Start index of the inner-ring vertices for this sector.
                int innerSectorStart = prevStart + sector * innerPerSector;

                if (posInSector == 0) {
                    // First vertex of a sector: forms one triangle bridging to the inner sector start.
                    int prevA = innerSectorStart;
                    triangles.add(ensureCCW(new Polygon(new int[]{prevA, curr, nextCurr}), vertices));

                } else if (posInSector == outerPerSector - 1) {
                    // Last vertex of a sector: wraps toward the next inner sector.
                    // We create two triangles to close the sector properly.
                    int prevA = innerSectorStart + (innerPerSector - 1);

                    int nextInnerSector = (sector + 1) % 6;
                    int prevB = prevStart + nextInnerSector * innerPerSector;

                    triangles.add(ensureCCW(new Polygon(new int[]{prevA, curr, prevB}), vertices));
                    triangles.add(ensureCCW(new Polygon(new int[]{prevB, curr, nextCurr}), vertices));

                } else {
                    // Middle vertices of a sector: create two triangles connecting a "quad" strip
                    // between two inner vertices and two consecutive outer vertices.
                    int prevA = innerSectorStart + (posInSector - 1);
                    int prevB = innerSectorStart + posInSector;

                    triangles.add(ensureCCW(new Polygon(new int[]{prevA, curr, prevB}), vertices));
                    triangles.add(ensureCCW(new Polygon(new int[]{prevB, curr, nextCurr}), vertices));
                }
            }
        }

        return triangles;
    }
}