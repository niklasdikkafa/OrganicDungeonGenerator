package com.dungeon.logic.placement.corridor.network.junction;

import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.jme3.math.Vector3f;

/**
 * Describes a junction "portal" (an entry/exit into a junction) derived from a frame-enabled
 * neighbor node of a junction.
 * <p>
 * A {@code JunctionPortalLink} stores the portal center and its oriented inner/outer frame corners
 * as seen from the junction center. The builder orders portals clockwise around a junction (or a
 * junction cluster) and links them in a ring via {@link #nextCwPortalNodeId}. For each portal, the
 * junction builder may also compute a {@link JunctionCorner} that connects this portal to the next
 * portal in clockwise order.
 * </p>
 *
 * <h2>Corner orientation</h2>
 * <p>
 * The portal node already contains a left/right frame (from corridor construction). However, for
 * consistent junction geometry we need "left" and "right" to be defined relative to the direction
 * from the junction center to the portal. Therefore, {@link #applySwapIfNeededForJunction(Vector3f, Vector3f)}
 * may swap the left/right corner assignments if the stored frame orientation does not match the
 * expected junction view direction.
 * </p>
 */
public final class JunctionPortalLink {

    /** Junction (or junction-cluster) identifier this portal belongs to. */
    public final int junctionNodeId;

    /** Graph node id of the portal node (neighbor of the junction). */
    public final int portalNodeId;

    /** World-space position of the portal center (copied from the portal graph node). */
    public final Vector3f center;

    /**
     * Oriented inner frame corners as seen from the junction:
     * left/right are relative to the junction-to-portal direction.
     */
    public Vector3f innerLeftBottom, innerLeftTop, innerRightBottom, innerRightTop;

    /**
     * Oriented outer frame corners as seen from the junction:
     * left/right are relative to the junction-to-portal direction.
     */
    public Vector3f outerLeftBottom, outerLeftTop, outerRightBottom, outerRightTop;

    /**
     * Portal node id of the next portal in clockwise order around the junction.
     * {@code -1} means "not linked".
     */
    public int nextCwPortalNodeId = -1;

    /**
     * Optional corner geometry that connects this portal to the next clockwise portal.
     * May be {@code null} if not computed or if a fallback/direct connection is used.
     */
    public JunctionCorner cornerToNext;

    public JunctionPortalLink(int junctionNodeId, int portalNodeId, Vector3f center) {
        this.junctionNodeId = junctionNodeId;
        this.portalNodeId = portalNodeId;
        this.center = center;
    }

    /**
     * Creates a {@code JunctionPortalLink} from a portal node that is adjacent to a junction component.
     * <p>
     * The portal's frame corners are copied from the {@link GraphNode} and then re-oriented if needed
     * so that "left" is consistent relative to the junction-to-portal direction.
     * </p>
     *
     * @param node           the portal graph node (must have valid frame points)
     * @param junctionId     identifier of the junction (or junction cluster) this portal belongs to
     * @param junctionCenter world-space junction center used for orientation decisions
     * @param forwardJ2P     direction from junction to portal (XZ-plane); if near-zero, it is derived from positions
     * @return a new {@code JunctionPortalLink} with oriented frame corners
     */
    public static JunctionPortalLink fromPortalNodeForJunctionComponent(
            GraphNode node,
            int junctionId,
            Vector3f junctionCenter,
            Vector3f forwardJ2P) {
        JunctionPortalLink pl = new JunctionPortalLink(junctionId, node.id, node.position.clone());

        pl.innerLeftBottom  = node.innerLeftBottom.clone();
        pl.innerLeftTop     = node.innerLeftTop.clone();
        pl.innerRightBottom = node.innerRightBottom.clone();
        pl.innerRightTop    = node.innerRightTop.clone();

        pl.outerLeftBottom  = node.outerLeftBottom.clone();
        pl.outerLeftTop     = node.outerLeftTop.clone();
        pl.outerRightBottom = node.outerRightBottom.clone();
        pl.outerRightTop    = node.outerRightTop.clone();

        pl.applySwapIfNeededForJunction(junctionCenter, forwardJ2P);
        return pl;
    }

    /**
     * Ensures that left/right portal corners match the junction view direction.
     * <p>
     * The method computes a "left" direction based on the junction-to-portal forward vector and
     * compares it to the portal's approximate frame normal (left minus right). If the dot product
     * indicates that the portal frame is mirrored relative to the junction view, all left/right
     * corners are swapped.
     * </p>
     *
     * @param junctionPos reference point of the junction (or junction cluster) in world space
     * @param forwardJ2P  direction from junction to portal; if degenerate, it is reconstructed from positions
     */
    public void applySwapIfNeededForJunction(Vector3f junctionPos, Vector3f forwardJ2P) {
        // Forward direction from the junction towards the portal (in XZ).
        Vector3f f = forwardJ2P.clone();
        if (f.lengthSquared() < 1e-8f) {
            f.set(center.x - junctionPos.x, 0f, center.z - junctionPos.z);
        }
        f.y = 0f;
        if (f.lengthSquared() < 1e-8f) f.set(1, 0, 0);
        else f.normalizeLocal();

        // "Left" direction relative to the forward direction.
        Vector3f leftDir = new Vector3f(-f.z, 0f, f.x).normalizeLocal();

        // Approximate frame normal (left minus right) to determine whether the frame is mirrored.
        Vector3f approxNormal = innerLeftBottom.subtract(innerRightBottom, new Vector3f());
        approxNormal.y = 0f;

        boolean swap;
        if (approxNormal.lengthSquared() < 1e-8f) {
            // Fallback using midpoints if the bottom points are degenerate.
            Vector3f midL = innerLeftBottom.add(innerLeftTop, new Vector3f()).multLocal(0.5f);
            Vector3f midR = innerRightBottom.add(innerRightTop, new Vector3f()).multLocal(0.5f);
            Vector3f dirLR = midL.subtract(midR, new Vector3f());
            dirLR.y = 0f;
            if (dirLR.lengthSquared() < 1e-8f) return;
            dirLR.normalizeLocal();
            swap = (dirLR.dot(leftDir) < 0f);
        } else {
            approxNormal.normalizeLocal();
            swap = (approxNormal.dot(leftDir) < 0f);
        }

        if (swap) swapLeftRight();
    }

    /** Swaps all stored left/right corners (inner and outer) in-place. */
    private void swapLeftRight() {
        Vector3f t;

        t = innerLeftBottom;
        innerLeftBottom = innerRightBottom;
        innerRightBottom = t;

        t = innerLeftTop;
        innerLeftTop = innerRightTop;
        innerRightTop = t;

        t = outerLeftBottom;
        outerLeftBottom = outerRightBottom;
        outerRightBottom = t;

        t = outerLeftTop;
        outerLeftTop = outerRightTop;
        outerRightTop = t;
    }
}