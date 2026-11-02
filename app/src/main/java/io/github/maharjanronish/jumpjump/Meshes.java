package io.github.maharjanronish.jumpjump;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Procedural geometry (no asset files): a cube, a tapered cylinder, a sphere,
 * a flat disc and an annulus. Everything in the scene is built from these.
 */
public final class Meshes {

    /** Static mesh: interleaved [x,y,z, nx,ny,nz], uploaded to a VBO. */
    public static final class Mesh {
        public final FloatBuffer verts;
        public final int count;
        private int vbo = -1;

        Mesh(float[] data) {
            verts = ByteBuffer.allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            verts.put(data).flip();
            count = data.length / 6;
        }

        void upload() {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vbo = ids[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.capacity() * 4, verts, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        /** Binds the VBO to the given attribute locations and draws. */
        public void draw(int aPos, int aNormal) {
            if (vbo <= 0) upload();   // safety: never draw with a missing VBO
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0);
            if (aNormal >= 0) {
                GLES20.glEnableVertexAttribArray(aNormal);
                GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, 12);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    /** Flat-colored mesh without normals (discs/rings for shadows & effects). */
    public static final class FlatMesh {
        public final FloatBuffer verts;
        public final int count;
        private int vbo = -1;

        FlatMesh(float[] data) {
            verts = ByteBuffer.allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            verts.put(data).flip();
            count = data.length / 3;
        }

        void upload() {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vbo = ids[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.capacity() * 4, verts, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        public void draw(int aPos) {
            if (vbo <= 0) upload();   // safety: never draw with a missing VBO
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    // ---------------------------------------------------------------- builders

    /** Unit cube centered at origin, extents 0.5. */
    static Mesh cube() {
        float[] p = {
                // x, y, z, nx, ny, nz
                // top
                -0.5f, 0.5f, -0.5f, 0, 1, 0,   0.5f, 0.5f, -0.5f, 0, 1, 0,   0.5f, 0.5f, 0.5f, 0, 1, 0,
                -0.5f, 0.5f, -0.5f, 0, 1, 0,   0.5f, 0.5f, 0.5f, 0, 1, 0,   -0.5f, 0.5f, 0.5f, 0, 1, 0,
                // bottom
                -0.5f, -0.5f, 0.5f, 0, -1, 0,  0.5f, -0.5f, 0.5f, 0, -1, 0, 0.5f, -0.5f, -0.5f, 0, -1, 0,
                -0.5f, -0.5f, 0.5f, 0, -1, 0,  0.5f, -0.5f, -0.5f, 0, -1, 0, -0.5f, -0.5f, -0.5f, 0, -1, 0,
                // +z
                -0.5f, -0.5f, 0.5f, 0, 0, 1,   0.5f, -0.5f, 0.5f, 0, 0, 1,   0.5f, 0.5f, 0.5f, 0, 0, 1,
                -0.5f, -0.5f, 0.5f, 0, 0, 1,   0.5f, 0.5f, 0.5f, 0, 0, 1,   -0.5f, 0.5f, 0.5f, 0, 0, 1,
                // -z
                0.5f, -0.5f, -0.5f, 0, 0, -1,  -0.5f, -0.5f, -0.5f, 0, 0, -1, -0.5f, 0.5f, -0.5f, 0, 0, -1,
                0.5f, -0.5f, -0.5f, 0, 0, -1,  -0.5f, 0.5f, -0.5f, 0, 0, -1,  0.5f, 0.5f, -0.5f, 0, 0, -1,
                // +x
                0.5f, -0.5f, 0.5f, 1, 0, 0,    0.5f, -0.5f, -0.5f, 1, 0, 0,  0.5f, 0.5f, -0.5f, 1, 0, 0,
                0.5f, -0.5f, 0.5f, 1, 0, 0,    0.5f, 0.5f, -0.5f, 1, 0, 0,   0.5f, 0.5f, 0.5f, 1, 0, 0,
                // -x
                -0.5f, -0.5f, -0.5f, -1, 0, 0, -0.5f, -0.5f, 0.5f, -1, 0, 0, -0.5f, 0.5f, 0.5f, -1, 0, 0,
                -0.5f, -0.5f, -0.5f, -1, 0, 0, -0.5f, 0.5f, 0.5f, -1, 0, 0,  -0.5f, 0.5f, -0.5f, -1, 0, 0,
        };
        return new Mesh(p);
    }

    /**
     * Cylinder with top cap, y from 0 (bottom) to 1 (top);
     * bottom ring radius r0, top ring radius r1 (allows tapers and horns).
     */
    static Mesh cyl(int segs, float r0, float r1) {
        Builder b = new Builder();
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            // side quad (two tris); normals ignore the taper slant (fine at these sizes)
            b.tri(r0 * x0, 0, r0 * z0, x0, 0, z0, r0 * x1, 0, r0 * z1, x1, 0, z1, r1 * x1, 1, r1 * z1, x1, 0, z1);
            b.tri(r0 * x0, 0, r0 * z0, x0, 0, z0, r1 * x1, 1, r1 * z1, x1, 0, z1, r1 * x0, 1, r1 * z0, x0, 0, z0);
            // top cap
            b.tri(0, 1, 0, 0, 1, 0, r1 * x0, 1, r1 * z0, 0, 1, 0, r1 * x1, 1, r1 * z1, 0, 1, 0);
        }
        return new Mesh(b.data());
    }

    /** UV sphere of unit radius centered at origin. */
    static Mesh sphere(int stacks, int slices) {
        Builder b = new Builder();
        for (int i = 0; i < stacks; i++) {
            double lat0 = Math.PI * i / stacks, lat1 = Math.PI * (i + 1) / stacks;
            float y0 = (float) Math.cos(lat0), r0 = (float) Math.sin(lat0);
            float y1 = (float) Math.cos(lat1), r1 = (float) Math.sin(lat1);
            for (int j = 0; j < slices; j++) {
                double a0 = 2 * Math.PI * j / slices, a1 = 2 * Math.PI * (j + 1) / slices;
                float x00 = r0 * (float) Math.cos(a0), z00 = r0 * (float) Math.sin(a0);
                float x01 = r0 * (float) Math.cos(a1), z01 = r0 * (float) Math.sin(a1);
                float x10 = r1 * (float) Math.cos(a0), z10 = r1 * (float) Math.sin(a0);
                float x11 = r1 * (float) Math.cos(a1), z11 = r1 * (float) Math.sin(a1);
                b.tri(x00, y0, z00, x00, y0, z00, x10, y1, z10, x10, y1, z10, x11, y1, z11, x11, y1, z11);
                b.tri(x00, y0, z00, x00, y0, z00, x11, y1, z11, x11, y1, z11, x01, y0, z01, x01, y0, z01);
            }
        }
        return new Mesh(b.data());
    }

    /** Unit disc on the XZ plane (y = 0), normal +Y, radius 1. */
    static FlatMesh disc(int segs) {
        Builder b = new Builder();
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            b.tri(0, 0, 0, 0, 1, 0,
                    (float) Math.cos(a0), 0, (float) Math.sin(a0), 0, 1, 0,
                    (float) Math.cos(a1), 0, (float) Math.sin(a1), 0, 1, 0);
        }
        return new FlatMesh(stripNormals(b.data()));
    }

    /** Unit annulus on XZ (inner 0.78, outer 1.0). */
    static FlatMesh ring(int segs) {
        float[] d = new float[segs * 18];
        int k = 0;
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            // outer 1.0, inner 0.78
            d[k++] = x1; d[k++] = 0; d[k++] = z1;
            d[k++] = x0; d[k++] = 0; d[k++] = z0;
            d[k++] = 0.78f * x0; d[k++] = 0; d[k++] = 0.78f * z0;
            d[k++] = x1; d[k++] = 0; d[k++] = z1;
            d[k++] = 0.78f * x0; d[k++] = 0; d[k++] = 0.78f * z0;
            d[k++] = 0.78f * x1; d[k++] = 0; d[k++] = 0.78f * z1;
        }
        return new FlatMesh(d);
    }

    private static float[] stripNormals(float[] withNormals) {
        float[] out = new float[withNormals.length / 2];
        for (int i = 0, j = 0; i < withNormals.length; i += 6) {
            out[j++] = withNormals[i];
            out[j++] = withNormals[i + 1];
            out[j++] = withNormals[i + 2];
        }
        return out;
    }

    private static final class Builder {
        private float[] a = new float[1024];
        private int n = 0;

        void tri(float... v) {
            if (n + v.length > a.length) {
                float[] b = new float[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            System.arraycopy(v, 0, a, n, v.length);
            n += v.length;
        }

        float[] data() {
            float[] out = new float[n];
            System.arraycopy(a, 0, out, 0, n);
            return out;
        }
    }

    private Meshes() {}
}
