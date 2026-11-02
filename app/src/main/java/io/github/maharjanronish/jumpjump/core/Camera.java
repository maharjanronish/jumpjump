package io.github.maharjanronish.jumpjump.core;

/**
 * Camera rig shared by the Android renderer and the desktop tests, so the
 * on-screen composition is verified without a device.
 */
public final class Camera {

    public final Vec3 eye = new Vec3();
    public final Vec3 target = new Vec3();
    public final float[] view = new float[16];
    public final float[] proj = new float[16];
    public final float[] viewProj = new float[16];

    private final float[] tmp = new float[4];

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public void update(Game game, float aspect) {
        float cd = game.camDist();
        float look = clamp(cd * Config.LOOK_AHEAD_FRACTION, Config.LOOK_AHEAD_MIN, Config.LOOK_AHEAD_MAX);
        float zoom = 1f + clamp((cd - 4.5f) / 3.0f, 0f, 1f) * (Config.CAM_DIST_SCALE_MAX - 1f);

        Vec3 f = game.camFocus();
        eye.set(f.x, f.y + Config.CAM_EYE_Y * zoom, f.z + Config.CAM_EYE_Z * zoom);
        target.set(f.x + Config.U_X * look, f.y + Config.CAM_TARGET_RISE_Y, f.z + Config.U_Z * look);

        Mat4.lookAt(view, eye.x, eye.y, eye.z, target.x, target.y, target.z, 0, 1, 0);
        Mat4.perspective(proj, Config.CAM_FOV_Y, aspect, Config.CAM_NEAR, Config.CAM_FAR);
        Mat4.mul(viewProj, proj, view);
    }

    /**
     * Projects a world point to normalized device coordinates.
     * Returns {x, y, z, w}; visible when |x|<=1, |y|<=1, w > 0.
     */
    public float[] project(float x, float y, float z) {
        Mat4.transform(viewProj, x, y, z, tmp);
        tmp[0] /= tmp[3];
        tmp[1] /= tmp[3];
        tmp[2] /= tmp[3];
        return tmp;
    }

    /** Screen fraction (0..1, y measured from the top) for a world point. */
    public float screenX(float x, float y, float z) { return project(x, y, z)[0] * 0.5f + 0.5f; }
    public float screenY(float x, float y, float z) { return 1f - (project(x, y, z)[1] * 0.5f + 0.5f); }
}
