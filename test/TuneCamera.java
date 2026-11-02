import io.github.maharjanronish.jumpjump.core.*;

/** Tunes the camera target offset so the composition matches the original. */
public class TuneCamera {
    public static void main(String[] args) {
        Game g = new Game(42);
        float aspect = 0.46f;
        float cd = g.camDist();
        System.out.println("cd = " + cd);
        for (float targetY : new float[]{-0.65f, 0f, 1f, 2f, 3f, 4f}) {
            float look = Math.max(Config.LOOK_AHEAD_MIN,
                    Math.min(Config.LOOK_AHEAD_MAX, cd * Config.LOOK_AHEAD_FRACTION));
            float zoom = 1f + Math.max(0f, Math.min(1f, (cd - 4.5f) / 3f)) * (Config.CAM_DIST_SCALE_MAX - 1f);
            Vec3 f = g.camFocus();
            float ex = f.x, ey = f.y + Config.CAM_EYE_Y * zoom, ez = f.z + Config.CAM_EYE_Z * zoom;
            float tx = f.x + Config.U_X * look, ty = f.y + targetY, tz = f.z + Config.U_Z * look;

            float[] view = new float[16], proj = new float[16], vp = new float[16], m = new float[16];
            Mat4.lookAt(m, ex, ey, ez, tx, ty, tz, 0, 1, 0);
            System.arraycopy(m, 0, view, 0, 16);
            Mat4.perspective(proj, Config.CAM_FOV_Y, aspect, Config.CAM_NEAR, Config.CAM_FAR);
            Mat4.mul(vp, proj, view);

            Vec3 cur = g.cur().center, nxt = g.next().center;
            float cx = sx(vp, cur), cy = sy(vp, cur);
            float nx = sx(vp, nxt), ny = sy(vp, nxt);
            float px = sx(vp, g.piece().pos), py = sy(vp, g.piece().pos);
            System.out.printf("targetY=%5.2f  cur=(%.2f, %.2f)  next=(%.2f, %.2f)  piece=(%.2f, %.2f)%n",
                    targetY, cx, cy, nx, ny, px, py);
        }
    }

    static float[] t = new float[4];
    static float sx(float[] vp, Vec3 p) {
        Mat4.transform(vp, p.x, 0, p.z, t);
        return t[0] / t[3] * 0.5f + 0.5f;
    }
    static float sy(float[] vp, Vec3 p) {
        Mat4.transform(vp, p.x, 0, p.z, t);
        return 1f - (t[1] / t[3] * 0.5f + 0.5f);
    }
}
