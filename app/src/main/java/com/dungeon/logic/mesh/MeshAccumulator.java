package com.dungeon.logic.mesh;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;

/**
 * Accumulates vertex positions and triangle indices for incremental mesh construction,
 * and produces a jMonkeyEngine {@link Mesh} on demand.
 *
 * <h2>Usage</h2>
 * <p>Call {@link #addVertex}, {@link #addTri}, and/or {@link #addQuad} to populate the mesh,
 * then call {@link #toMesh()} to finalize it. The accumulator can be reused conceptually
 * but offers no reset.</p>
 *
 * <h2>Vertex sharing</h2>
 * <p>No vertex welding is performed. Each call to {@link #addVertex} appends a new entry,
 * and {@link #addQuad} always emits four independent vertices. This is intentional:
 * flat shading requires that adjacent faces carry independent normals at shared positions,
 * which is only possible if those positions are stored as separate vertices.</p>
 *
 * <h2>Normal computation</h2>
 * <p>Normals are computed automatically in {@link #toMesh()} using <b>flat shading</b>:
 * each triangle's face normal is assigned to all three of its corner vertices.</p>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Index validity is not enforced on {@link #addTri} -> callers must ensure indices
 *       are within bounds of the current vertex list.</li>
 *   <li>Only triangle primitives ({@link Mesh.Mode#Triangles}) are supported.</li>
 * </ul>
 */
public final class MeshAccumulator {
    private final ArrayList<Vector3f> verts = new ArrayList<>();
    private final ArrayList<Integer> idx = new ArrayList<>();

    /**
     * Returns the number of vertices currently accumulated.
     *
     * @return current vertex count
     */
    public int vertexCount() {
        return verts.size();
    }

    /**
     * Appends a vertex at the given position and returns its index.
     *
     * <p>The position is defensively cloned; the caller may reuse or modify {@code p}
     * after this call without affecting the stored vertex.</p>
     *
     * @param p the vertex position (not {@code null})
     * @return the index of the newly added vertex
     */
    public int addVertex(Vector3f p) {
        verts.add(p.clone());
        return verts.size() - 1;
    }

    /**
     * Appends a triangle defined by three existing vertex indices.
     *
     * <p>No bounds-checking is performed. Passing out-of-range indices will result in
     * an {@link ArrayIndexOutOfBoundsException} during {@link #toMesh()}.</p>
     *
     * @param a index of the first vertex
     * @param b index of the second vertex
     * @param c index of the third vertex
     */
    public void addTri(int a, int b, int c) {
        idx.add(a);
        idx.add(b);
        idx.add(c);
    }

    /**
     * Appends a quad as two triangles {@code (a, b, c)} and {@code (a, c, d)},
     * each with their own independent vertices (no sharing between the two triangles
     * or with any previously added geometry).
     *
     * <p>Vertex order should be consistent (e.g. CW when viewed from the front face)
     * to ensure correct winding for the generated normals.</p>
     *
     * @param a first quad vertex (bottom-left by convention)
     * @param b second quad vertex (top-left by convention)
     * @param c third quad vertex (top-right by convention)
     * @param d fourth quad vertex (bottom-right by convention)
     */
    public void addQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        int ia = addVertex(a);
        int ib = addVertex(b);
        int ic = addVertex(c);
        int id = addVertex(d);
        addTri(ia, ib, ic);
        addTri(ia, ic, id);
    }

    /**
     * Finalizes the accumulated geometry into a jMonkeyEngine {@link Mesh}.
     *
     * <p>The returned mesh contains:</p>
     * <ul>
     *   <li>{@link VertexBuffer.Type#Position}: one entry per accumulated vertex</li>
     *   <li>{@link VertexBuffer.Type#Index}: triangle index list</li>
     *   <li>{@link VertexBuffer.Type#Normal}: flat-shaded face normals</li>
     * </ul>
     *
     * <p>Returns an empty (but valid) mesh if no vertices or indices have been accumulated.</p>
     *
     * @return the finalized mesh (never {@code null})
     */
    public Mesh toMesh() {
        Mesh m = new Mesh();
        if (verts.isEmpty() || idx.isEmpty()) {
            m.updateBound();
            m.updateCounts();
            return m;
        }

        Vector3f[] vArr = verts.toArray(new Vector3f[0]);
        int[] iArr = idx.stream().mapToInt(Integer::intValue).toArray();

        m.setMode(Mesh.Mode.Triangles);
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vArr));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(iArr));

        Vector3f[] nArr = computeNormals(vArr, iArr);
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nArr));

        m.updateBound();
        m.updateCounts();
        return m;
    }

    public static Vector3f[] computeNormals(Vector3f[] v, int[] idx) {
        Vector3f[] n = new Vector3f[v.length];
        for (int i = 0; i < n.length; i++) n[i] = Vector3f.UNIT_Y.clone();

        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i], ib = idx[i + 1], ic = idx[i + 2];
            if (ia >= v.length || ib >= v.length || ic >= v.length) continue;

            Vector3f fn = v[ib].subtract(v[ia], new Vector3f())
                    .cross(v[ic].subtract(v[ia], new Vector3f()));
            if (fn.lengthSquared() < 1e-12f) continue;
            fn.normalizeLocal();

            n[ia] = fn.clone();
            n[ib] = fn.clone();
            n[ic] = fn.clone();
        }
        return n;
    }
}
