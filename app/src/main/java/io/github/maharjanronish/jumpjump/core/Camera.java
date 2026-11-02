package io.github.maharjanronish.jumpjump.core;

/**
 * Fixed-angle top-view camera for the 2.5D isometric sprite world - the
 * exact composition of the official game: the camera never rotates, it only
 * PANS to follow the (smoothed) focus, and the world is drawn with
 * pre-rendered isometric sprites.
 *
 * Screen mapping (pixels, y down):
 *   x = viewW/2 + (x - camX) * scale
 *   y = viewH*CAM_Y_FRAC - (z - camZ) * scale * RHO - h * scale * RISE_K
 *
 * RHO compresses the ground plane vertically (iso look); heights above the
 * plane (jumps, drop-ins) rise at RISE_K.
 *
 * A smoothed lateral correction keeps the TARGET block's center inside the
 * middle band of the screen even for the widest gaps + lane jitter, so the
 * landing zone is always fully visible.
 */
public final class Camera {

    /** Smoothed focus (world XZ), owned by Game. */
    private float camX, camZ;
    /** Active lateral correction (world units), glides to the needed value. */
    private float corrX;
    /** Pixels per world unit (horizontal). */
    private float scale = 100f;
    private int viewW = 1080, viewH = 2340;

    /** Feed the smoothed focus + viewport + frame dt; cheap, every frame. */
    public void update(Game game, int width, int height, float dt) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        scale = Math.min(Config.SCALE_W * viewW, Config.SCALE_H_CAP * viewH);
        Vec3 f = game.camFocus();
        camX = f.x;
        camZ = f.z;

        // keep the TARGET block's center in the middle band of the screen
        float want = 0f;
        Blocks.Block n = game.next();
        if (n != null) {
            float nx = viewW * 0.5f + (n.center.x - camX) * scale;
            float lo = viewW * 0.24f, hi = viewW * 0.76f;
            // screenX = W/2 + (x - camX)*scale, so adding a positive `want`
            // to camX moves the block LEFT on screen and vice versa.
            if (nx < lo) want = (nx - lo) / scale;      // block too far left
            else if (nx > hi) want = (nx - hi) / scale; // block too far right
        }
        if (dt > 0f) {
            float a = 1f - (float) Math.exp(-12f * dt);
            corrX += (want - corrX) * a;
        } else {
            corrX = want;
        }
        camX += corrX;

        // never push the CURRENT block out of view while correcting
        Blocks.Block c = game.cur();
        if (c != null) {
            float cx = viewW * 0.5f + (c.center.x - camX) * scale;
            float lo = viewW * 0.14f, hi = viewW * 0.86f;
            if (cx < lo) camX -= (lo - cx) / scale;
            else if (cx > hi) camX -= (hi - cx) / scale;
        }
    }

    public int viewW() { return viewW; }
    public int viewH() { return viewH; }
    public float scale() { return scale; }

    /** Screen X of a world point. */
    public float screenX(float x, float z) {
        return viewW * 0.5f + (x - camX) * scale;
    }

    /** Screen Y of a world point at height h above the plane (y down). */
    public float screenY(float x, float z, float h) {
        return viewH * Config.CAM_Y_FRAC
                - (z - camZ) * scale * Config.RHO
                - h * scale * Config.RISE_K;
    }

    /** Screen Y of a ground-plane point (h = 0). */
    public float screenY(float x, float z) { return screenY(x, z, 0f); }

    /** Horizontal screen span of a world radius. */
    public float spanX(float r) { return r * scale; }

    /** Vertical screen span of a world radius ON the ground plane (ellipse). */
    public float spanY(float r) { return r * scale * Config.RHO; }

    /** Screen rise of a height above the plane. */
    public float rise(float h) { return h * scale * Config.RISE_K; }

    /** Pixel -> NDC x. */
    public float ndcX(float px) { return px / viewW * 2f - 1f; }

    /** Pixel -> NDC y (pixels measured top-down). */
    public float ndcY(float py) { return 1f - py / viewH * 2f; }
}
