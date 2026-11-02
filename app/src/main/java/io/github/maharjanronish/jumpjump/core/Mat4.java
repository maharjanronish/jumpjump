package io.github.maharjanronish.jumpjump.core;

/**
 * Column-major 4x4 matrix math (OpenGL layout), pure Java so the camera math
 * is identical on desktop tests and on the device renderer.
 */
public final class Mat4 {

    private Mat4() {}

    public static float[] identity(float[] m) {
        m[0] = 1; m[1] = 0; m[2] = 0; m[3] = 0;
        m[4] = 0; m[5] = 1; m[6] = 0; m[7] = 0;
        m[8] = 0; m[9] = 0; m[10] = 1; m[11] = 0;
        m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
        return m;
    }

    /** out = a * b (apply b first, then a). */
    public static float[] mul(float[] out, float[] a, float[] b) {
        float[] t = TMP.get();
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                t[c * 4 + r] =
                        a[0 * 4 + r] * b[c * 4 + 0] +
                        a[1 * 4 + r] * b[c * 4 + 1] +
                        a[2 * 4 + r] * b[c * 4 + 2] +
                        a[3 * 4 + r] * b[c * 4 + 3];
            }
        }
        System.arraycopy(t, 0, out, 0, 16);
        return out;
    }

    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[16]);

    public static float[] perspective(float[] m, float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        m[0] = f / aspect; m[1] = 0; m[2] = 0; m[3] = 0;
        m[4] = 0; m[5] = f; m[6] = 0; m[7] = 0;
        m[8] = 0; m[9] = 0; m[10] = (far + near) / (near - far); m[11] = -1;
        m[12] = 0; m[13] = 0; m[14] = 2 * far * near / (near - far); m[15] = 0;
        return m;
    }

    public static float[] lookAt(float[] m, float ex, float ey, float ez,
                                 float cx, float cy, float cz,
                                 float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        // right = forward x up
        float rx = fy * uz - fz * uy;
        float ry = fz * ux - fx * uz;
        float rz = fx * uy - fy * ux;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        // up' = right x forward
        float tx = ry * fz - rz * fy;
        float ty = rz * fx - rx * fz;
        float tz = rx * fy - ry * fx;
        m[0] = rx; m[1] = tx; m[2] = -fx; m[3] = 0;
        m[4] = ry; m[5] = ty; m[6] = -fy; m[7] = 0;
        m[8] = rz; m[9] = tz; m[10] = -fz; m[11] = 0;
        m[12] = -(rx * ex + ry * ey + rz * ez);
        m[13] = -(tx * ex + ty * ey + tz * ez);
        m[14] = (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    /** Model matrix: translate * rotateY * scale. */
    public static float[] model(float[] m, float tx, float ty, float tz,
                                float rotYDeg, float sx, float sy, float sz) {
        float a = (float) Math.toRadians(rotYDeg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        // rotY (column-major)
        m[0] = c;  m[1] = 0; m[2] = -s; m[3] = 0;
        m[4] = 0;  m[5] = 1; m[6] = 0;  m[7] = 0;
        m[8] = s;  m[9] = 0; m[10] = c; m[11] = 0;
        m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
        float[] r = TMP2.get();
        System.arraycopy(m, 0, r, 0, 16);
        // scale
        for (int i = 0; i < 3; i++) { m[i] = r[i] * sx; m[4 + i] = r[4 + i] * sy; m[8 + i] = r[8 + i] * sz; }
        // translate (columns are basis; translation column untouched by scale)
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1;
        return m;
    }

    /** Model matrix with pitch (X) and yaw (Y) rotation: translate * rotX * rotY * scale. */
    public static float[] modelPY(float[] m, float tx, float ty, float tz,
                                  float pitchXDeg, float yawYDeg,
                                  float sx, float sy, float sz) {
        model(m, 0, 0, 0, yawYDeg, 1, 1, 1);
        float[] rotY = TMP2.get();
        System.arraycopy(m, 0, rotY, 0, 16);

        float p = (float) Math.toRadians(pitchXDeg);
        float cp = (float) Math.cos(p), sp = (float) Math.sin(p);
        float[] rotX = TMP3.get();
        rotX[0] = 1; rotX[1] = 0; rotX[2] = 0; rotX[3] = 0;
        rotX[4] = 0; rotX[5] = cp; rotX[6] = sp; rotX[7] = 0;
        rotX[8] = 0; rotX[9] = -sp; rotX[10] = cp; rotX[11] = 0;
        rotX[12] = 0; rotX[13] = 0; rotX[14] = 0; rotX[15] = 1;

        mul(m, rotX, rotY);          // rotX * rotY
        // scale columns
        for (int i = 0; i < 3; i++) { m[i] *= sx; m[4 + i] *= sy; m[8 + i] *= sz; }
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1;
        return m;
    }

    private static final ThreadLocal<float[]> TMP2 = ThreadLocal.withInitial(() -> new float[16]);
    private static final ThreadLocal<float[]> TMP3 = ThreadLocal.withInitial(() -> new float[16]);

    /** Transform a point; returns {x, y, z, w} in out (length >= 4). */
    public static float[] transform(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15];
        return out;
    }
}
