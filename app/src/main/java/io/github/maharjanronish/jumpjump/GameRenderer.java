package io.github.maharjanronish.jumpjump;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import io.github.maharjanronish.jumpjump.core.Blocks;
import io.github.maharjanronish.jumpjump.core.Camera;
import io.github.maharjanronish.jumpjump.core.Config;
import io.github.maharjanronish.jumpjump.core.Game;
import io.github.maharjanronish.jumpjump.core.GameEvent;
import io.github.maharjanronish.jumpjump.core.Piece;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * All rendering for the game: a fixed-angle top-view 2.5D isometric sprite
 * renderer, exactly like the official game's presentation.
 *
 *  - blocks are pre-rendered isometric sprites (assets/blocks), anchored at
 *    their top-face center so art and landing zone coincide
 *  - the camera never rotates; it pans smoothly with the piece and the
 *    chain reveals progressively (only ~3 blocks ever exist on screen)
 *  - the lane runs 3-6 blocks one way, then switches; each switch glides
 *    the background to the next warm tint
 *  - the current block squashes down together with the piece while charging
 *  - a small ring marks the perfect (dead-center) zone on the target
 *  - landing bursts of world-space particles; no big flash effects
 *
 * Frame budget on a 2015-class phone: ~20 draw calls, ~40 quads, 60 fps.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {

    public interface Ui {
        /** Called from the GL thread when the run ends. */
        void onGameOver(int score);
        /** Called from the GL thread after the very first jump (hide hint). */
        void onFirstJump();
    }

    private static final float SETTLE_T = 0.18f;   // must match Game's settle

    private final Game game;
    private final Ui ui;
    private final Sfx sfx;
    private final Camera camera = new Camera();
    private final Sprites sprites = new Sprites();
    private final Context context;

    private ShaderUtil.TexProgram texProg;
    private ShaderUtil.SpriteProgram spriteProg;
    private ShaderUtil.FontProgram fontProg;

    /** Light elegant face for the big score; medium for popups. */
    private final FontAtlas scoreFont = new FontAtlas("sans-serif-light", android.graphics.Typeface.NORMAL);
    private final FontAtlas popupFont = new FontAtlas("sans-serif-medium", android.graphics.Typeface.NORMAL);

    // background glide (phase -> warm tint)
    private final float[] bgTop = new float[3];
    private final float[] bgBot = new float[3];
    private int bgPhaseSeen = -1;

    // transient effects
    private static final class Popup {
        float wx, wy, wz; String text; float age; boolean gold;
    }
    /** Small expanding ring (special bonus only; subtle). */
    private static final class Ring {
        float wx, wz; float age;
    }
    /** World-space particle with gravity + bounce (landing bursts). */
    private static final class Particle {
        float x, y, z, vx, vy, vz, age, size; boolean gold;
    }
    private final List<Popup> popups = new ArrayList<>(8);
    private final List<Ring> rings = new ArrayList<>(4);
    private final List<Particle> particles = new ArrayList<>(48);
    private final Random fxRandom = new Random();
    private final ShaderUtil.DynBuf dynBuf = new ShaderUtil.DynBuf();
    private final int[] tmpIds = new int[1];

    private long lastFrameNanos;
    private boolean firstJumpSeen;

    /** Persistent VBO for screen-space quads (background / particles / text). */
    private int dynVbo = -1;

    public GameRenderer(Context ctx, Game game, Sfx sfx, Ui ui) {
        this.context = ctx;
        this.game = game;
        this.sfx = sfx;
        this.ui = ui;
    }

    // ---------------------------------------------------------------- input (GL thread)

    /** Called via GLSurfaceView.queueEvent from the UI thread. */
    public void onPress() { game.press(); }

    /** Called via GLSurfaceView.queueEvent from the UI thread. */
    public void onRelease() { game.release(); }

    /** Restarts the run; call on the GL thread. */
    public void resetGame(long seed) {
        game.reset(seed);
        popups.clear();
        rings.clear();
        particles.clear();
        bgPhaseSeen = -1;
        lastFrameNanos = System.nanoTime();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        texProg = new ShaderUtil.TexProgram();
        spriteProg = new ShaderUtil.SpriteProgram();
        fontProg = new ShaderUtil.FontProgram();
        scoreFont.create();
        popupFont.create();
        sprites.create(context);

        // one persistent dynamic VBO, reused every frame (no per-frame
        // buffer create/delete churn, and no stale attrib pointers)
        GLES20.glGenBuffers(1, tmpIds, 0);
        dynVbo = tmpIds[0];

        // sprite world: no depth buffer usage at all - painter's algorithm
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
        camera.update(game, Math.max(1, width), Math.max(1, height), 0f);
    }

    @Override public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameNanos) * 1e-9f);
        lastFrameNanos = now;

        game.update(dt);
        handleEvents();
        updateEffects(dt);
        updateBackground(dt);
        camera.update(game, camera.viewW(), camera.viewH(), dt);

        GLES20.glClearColor(bgBot[0], bgBot[1], bgBot[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        drawBackground();
        drawWorld();
        drawParticles();
        drawText();
    }

    // ---------------------------------------------------------------- events

    private void handleEvents() {
        List<GameEvent> ev = game.drainEvents();
        if (ev.isEmpty()) return;
        for (GameEvent e : ev) {
            switch (e.type) {
                case JUMP:
                    sfx.play(Sfx.JUMP);
                    if (!firstJumpSeen) { firstJumpSeen = true; ui.onFirstJump(); }
                    break;
                case MINIHOP:
                    sfx.play(Sfx.LAND, 0.5f);
                    spawnBurst(e.x, e.z, Config.DUST_MINIHOP, false);
                    break;
                case LAND:
                    // plain landing: dust only - the original shows no "+1" text
                    sfx.play(Sfx.LAND);
                    spawnBurst(e.x, e.z, Config.DUST_LAND, false);
                    break;
                case PERFECT:
                    sfx.play(Sfx.PERFECT, Math.min(e.intArg2, 8));
                    spawnBurst(e.x, e.z, Config.DUST_PERFECT, false);
                    spawnBurst(e.x, e.z, Config.SPARK_PERFECT, true);
                    addPopup(e.x, e.y + 1.1f, e.z, "+" + e.intArg, false);
                    break;
                case SPECIAL_BONUS:
                    sfx.play(Sfx.special(e.kind()));
                    addPopup(e.x, e.y + 0.9f, e.z, "+" + e.intArg, true);
                    spawnBurst(e.x, e.z, Config.DUST_SPECIAL, true);
                    addRing(e.x, e.z);
                    break;
                case FALL:
                    sfx.play(Sfx.FALL);
                    break;
                case GAME_OVER:
                    sfx.play(Sfx.GAME_OVER);
                    ui.onGameOver(e.intArg);
                    break;
            }
        }
    }

    private void addPopup(float x, float y, float z, String text, boolean gold) {
        Popup p = new Popup();
        p.wx = x; p.wy = y; p.wz = z;
        p.text = text; p.gold = gold; p.age = 0;
        popups.add(p);
    }

    private void addRing(float x, float z) {
        Ring r = new Ring();
        r.wx = x; r.wz = z; r.age = 0;
        rings.add(r);
    }

    /** Burst of particles at a world point: ivory dust, or gold sparks. */
    private void spawnBurst(float x, float z, int n, boolean gold) {
        for (int i = 0; i < n; i++) {
            if (particles.size() >= 72) break;   // hard cap for low-end phones
            Particle p = new Particle();
            p.x = x; p.y = 0.10f; p.z = z;
            double a = fxRandom.nextDouble() * Math.PI * 2;
            float sp = 0.6f + fxRandom.nextFloat() * (gold ? 1.6f : 1.2f);
            p.vx = (float) Math.cos(a) * sp;
            p.vz = (float) Math.sin(a) * sp;
            p.vy = (gold ? 1.5f : 0.8f) + fxRandom.nextFloat() * (gold ? 1.6f : 1.0f);
            p.age = 0;
            p.size = (gold ? 0.075f : 0.05f) + fxRandom.nextFloat() * 0.04f;
            p.gold = gold;
            particles.add(p);
        }
    }

    private void updateEffects(float dt) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            popups.get(i).age += dt;
            if (popups.get(i).age > Config.POPUP_T) popups.remove(i);
        }
        for (int i = rings.size() - 1; i >= 0; i--) {
            rings.get(i).age += dt;
            if (rings.get(i).age > Config.RING_T) rings.remove(i);
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.age += dt;
            if (p.age > Config.PARTICLE_T) { particles.remove(i); continue; }
            p.vy -= Config.PARTICLE_G * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            if (p.y < 0f && p.vy < 0f) {       // one bounce on the block top
                p.y = 0f;
                p.vy = -p.vy * Config.PARTICLE_BOUNCE;
                p.vx *= 0.65f;
                p.vz *= 0.65f;
            }
        }
    }

    // ---------------------------------------------------------------- background

    /** Glides the warm gradient toward the current phase's tint. */
    private void updateBackground(float dt) {
        int phase = game.blocksPhase();
        float[] top = Config.BG_PHASES[phase % Config.BG_PHASES.length];
        if (bgPhaseSeen < 0) {
            // first frame: snap to the phase color
            bgPhaseSeen = phase;
            System.arraycopy(top, 0, bgTop, 0, 3);
            System.arraycopy(top, 3, bgBot, 0, 3);
            return;
        }
        bgPhaseSeen = phase;
        float a = 1f - (float) Math.exp(-Config.BG_LERP_RATE * dt);
        for (int i = 0; i < 3; i++) {
            bgTop[i] += (top[i] - bgTop[i]) * a;
            bgBot[i] += (top[i + 3] - bgBot[i]) * a;
        }
    }

    private void drawBackground() {
        GLES20.glUseProgram(spriteProg.id);
        FloatBuffer b = dynBuf.get(36);
        float[] t = bgTop, bo = bgBot;
        // two triangles: top edge uses bgTop, bottom edge bgBot
        b.put(-1); b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.put(-1); b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.put(-1); b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.flip();
        uploadAndDrawSprites(b, 6);
    }

    private void uploadAndDrawSprites(FloatBuffer b, int verts) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dynVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * 24, b, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glEnableVertexAttribArray(spriteProg.aPos);
        GLES20.glVertexAttribPointer(spriteProg.aPos, 2, GLES20.GL_FLOAT, false, 24, 0);
        GLES20.glEnableVertexAttribArray(spriteProg.aColor);
        GLES20.glVertexAttribPointer(spriteProg.aColor, 4, GLES20.GL_FLOAT, false, 24, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        GLES20.glDisableVertexAttribArray(spriteProg.aColor);
        GLES20.glDisableVertexAttribArray(spriteProg.aPos);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // ---------------------------------------------------------------- world (sprites)

    private void drawWorld() {
        List<Blocks.Block> vis = game.visible();
        Piece p = game.piece();

        // charging: the current block squashes down together with the piece
        float power = game.power();
        float curQ = 1f - Config.BLOCK_CHARGE_SQUASH * power;
        float curDipPx = 0f;
        Blocks.Block cur = game.cur();
        if (curQ < 1f) {
            Sprites.BlockSprite cs = sprites.blocks[Sprites.idOf(cur.kind, cur.style)];
            float w = 2 * cur.halfW * camera.scale();
            float h = w * cs.h / (float) cs.w;
            float topCy = cs.topCy * (w / cs.w);
            curDipPx = (h - topCy) * (1f - curQ);
        }

        // painter's algorithm: far blocks first (z desc); the piece sits in
        // front of the block it stands on and behind strictly nearer blocks
        boolean pieceDrawn = false;
        for (int i = vis.size() - 1; i >= 0; i--) {
            Blocks.Block blk = vis.get(i);
            if (!pieceDrawn && blk.center.z < p.pos.z - 1e-4f) {
                drawPieceAndShadow(curDipPx);
                pieceDrawn = true;
            }
            float q = (blk == cur) ? curQ : 1f;
            if (blk.settleT > 0) q *= 1f - 0.05f * (blk.settleT / SETTLE_T);
            drawBlockSprite(blk, q);
            if (blk == game.next() && game.state() != Game.State.OVER) {
                drawMarker(blk);
            }
        }
        if (!pieceDrawn) drawPieceAndShadow(curDipPx);

        // small expanding rings (special bonus)
        for (Ring rg : rings) {
            float t = rg.age / Config.RING_T;
            float r = Config.RING_R0 + (Config.RING_R1 - Config.RING_R0) * t;
            float cx = camera.screenX(rg.wx, rg.wz);
            float cy = camera.screenY(rg.wx, rg.wz);
            drawAnnulus(cx, cy, camera.spanX(r), camera.spanY(r),
                    Math.max(3f, camera.spanX(r) * 0.14f),
                    0.55f, 0.45f, 0.30f, 0.34f * (1f - t));
        }
    }

    /**
     * Draws one block sprite anchored at its top-face center. The squash
     * compresses the sprite around its BOTTOM edge, so the top-face center
     * (and the piece standing on it) rides DOWN by (1-q) * body height.
     */
    private void drawBlockSprite(Blocks.Block blk, float q) {
        Sprites.BlockSprite s = sprites.blocks[Sprites.idOf(blk.kind, blk.style)];
        if (s.tex < 0) return;
        float cx = camera.screenX(blk.center.x, blk.center.z);
        float drop = dropOf(blk);
        float cy = camera.screenY(blk.center.x, blk.center.z) - camera.rise(drop);
        float w = 2 * blk.halfW * camera.scale();
        float h = w * s.h / (float) s.w;
        float topCy = s.topCy * (w / s.w);
        float body = h - topCy;               // sprite px below the anchor
        float h2 = h * q;
        float anchorY = cy + body * (1f - q); // anchor rides down while squashing
        float left = cx - w * 0.5f;
        float top = anchorY - topCy * q;
        drawTexQuad(s.tex, left, top, w, h2, 1, 1, 1, 1f);
    }

    private void drawPieceAndShadow(float curDipPx) {
        Piece p = game.piece();
        if (p.dead && game.state() == Game.State.OVER && p.pos.y < Config.FALL_KILL_Y - 1f) {
            return;   // piece is gone below; skip drawing
        }

        // blob shadow under the piece, on whichever block it is over
        Blocks.Block cur = game.cur();
        Blocks.Block next = game.next();
        Blocks.Block over = null;
        if (cur.dist2(p.pos.x, p.pos.z) < (cur.halfW + 0.6f) * (cur.halfW + 0.6f)) over = cur;
        else if (next != null
                && next.dist2(p.pos.x, p.pos.z) < (next.halfW + 0.6f) * (next.halfW + 0.6f)) over = next;
        if (over != null && p.pos.y > -0.1f) {
            float h = Math.max(0, p.pos.y);
            float r = 0.42f * (1f - Math.min(0.5f, h * 0.09f));
            float a = Config.SHADOW_ALPHA * (1f - Math.min(0.8f, h * 0.16f));
            float sx = camera.screenX(p.pos.x, p.pos.z);
            float sy = camera.screenY(p.pos.x, p.pos.z) + (over == cur ? curDipPx : 0f);
            float rx = camera.spanX(r);
            float ry = camera.spanY(r);
            drawTexQuad(sprites.softDiscTex, sx - rx, sy - ry, rx * 2, ry * 2,
                    Config.SHADOW_RGB[0], Config.SHADOW_RGB[1], Config.SHADOW_RGB[2], a);
        }

        // the piece sprite: squash & stretch around the feet anchor, tilting
        // while wobbling on an edge or tumbling into the fall
        float fx = camera.screenX(p.pos.x, p.pos.z);
        float fy = camera.screenY(p.pos.x, p.pos.z, p.pos.y) + curDipPx;
        float w = Config.PIECE_W_UNITS * camera.scale() * p.widenXZ;
        float h = Config.PIECE_H_UNITS * camera.scale() * p.squashY;
        float tilt = p.wobble * 150f + clamp(p.tumbleDeg, -Config.PIECE_TILT_MAX, Config.PIECE_TILT_MAX);
        drawTexQuadRotated(sprites.pieceTex, fx, fy, w, h, tilt);
    }

    /**
     * Perfect-zone marker: a thin pulsing ellipse ring + dot at the TARGET's
     * top-face center, exactly the radius that scores the 2/4/6... combo.
     */
    private void drawMarker(Blocks.Block blk) {
        float pr = game.targetPerfectR();
        float cx = camera.screenX(blk.center.x, blk.center.z);
        float cy = camera.screenY(blk.center.x, blk.center.z) - camera.rise(dropOf(blk));
        float pulse = 0.80f + 0.20f * (float) Math.sin(game.totalTime() * 5.5f);
        float[] ink = Config.MARKER_INK;
        drawAnnulus(cx, cy, camera.spanX(pr) * 1.12f, camera.spanY(pr) * 1.12f,
                Math.max(2.5f, camera.spanX(pr) * 0.10f),
                ink[0], ink[1], ink[2], Config.MARKER_ALPHA * pulse);
        // center dot via the soft disc, tinted
        float dotR = Math.max(3f, camera.spanX(pr) * 0.16f);
        drawTexQuad(sprites.softDiscTex, cx - dotR, cy - dotR * Config.RHO, dotR * 2, dotR * 2 * Config.RHO,
                ink[0], ink[1], ink[2], Config.MARKER_ALPHA * 1.15f * pulse);
    }

    /** Current drop-in height of a spawning block (world units). */
    private float dropOf(Blocks.Block blk) {
        if (blk.spawnT <= 0) return 0f;
        float t = blk.spawnT / Config.SPAWN_T;
        return t * t * Config.SPAWN_DROP_H;
    }

    // ---------------------------------------------------------------- textured quads

    /** Axis-aligned textured quad in PIXEL coordinates (top-left + size). */
    private void drawTexQuad(int tex, float leftPx, float topPx, float wPx, float hPx,
                             float r, float g, float b, float a) {
        GLES20.glUseProgram(texProg.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(texProg.uTex, 0);
        FloatBuffer buf = dynBuf.get(48);
        emitTexQuadVerts(buf, leftPx, topPx, wPx, hPx, r, g, b, a);
        buf.flip();
        uploadAndDrawTex(buf, 6);
    }

    /** Textured quad rotated around its BOTTOM-CENTER anchor (the piece). */
    private void drawTexQuadRotated(int tex, float axPx, float ayPx, float wPx, float hPx, float tiltDeg) {
        GLES20.glUseProgram(texProg.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(texProg.uTex, 0);
        float rad = (float) Math.toRadians(tiltDeg);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        // corner offsets from the anchor (feet): (-w/2,-h) (w/2,-h) (w/2,0) (-w/2,0)
        // v=0 at the sprite TOP corners (image top row), v=1 at the feet.
        float[] ox = {-wPx * 0.5f, wPx * 0.5f, wPx * 0.5f, -wPx * 0.5f};
        float[] oy = {-hPx, -hPx, 0f, 0f};
        float[] u = {0f, 1f, 1f, 0f};
        float[] v = {0f, 0f, 1f, 1f};
        FloatBuffer buf = dynBuf.get(48);
        emitQuad(buf, axPx, ayPx, ox, oy, c, s, u, v, 1, 1, 1, 1f);
        buf.flip();
        uploadAndDrawTex(buf, 6);
    }

    private void emitTexQuadVerts(FloatBuffer buf, float leftPx, float topPx, float wPx, float hPx,
                                  float r, float g, float b, float a) {
        float x0 = camera.ndcX(leftPx), x1 = camera.ndcX(leftPx + wPx);
        float y0 = camera.ndcY(topPx), y1 = camera.ndcY(topPx + hPx);
        // V is 0 at the image TOP row (rows are uploaded top-first, so texture
        // row 0 = image row 0) and 1 at the bottom: the quad's top edge must
        // sample v=0 or every sprite renders vertically flipped.
        buf.put(x0); buf.put(y0); buf.put(0); buf.put(0); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        buf.put(x1); buf.put(y0); buf.put(1); buf.put(0); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        buf.put(x1); buf.put(y1); buf.put(1); buf.put(1); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        buf.put(x0); buf.put(y0); buf.put(0); buf.put(0); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        buf.put(x1); buf.put(y1); buf.put(1); buf.put(1); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        buf.put(x0); buf.put(y1); buf.put(0); buf.put(1); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
    }

    /** Emits a 6-vert quad from rotated corner offsets around an anchor. */
    private void emitQuad(FloatBuffer buf, float axPx, float ayPx,
                          float[] ox, float[] oy, float c, float s,
                          float[] u, float[] v, float r, float g, float b, float a) {
        float[] px = new float[4], py = new float[4];
        for (int i = 0; i < 4; i++) {
            px[i] = axPx + ox[i] * c - oy[i] * s;
            py[i] = ayPx + ox[i] * s + oy[i] * c;
        }
        int[] idx = {0, 1, 2, 0, 2, 3};
        for (int k = 0; k < 6; k++) {
            int i = idx[k];
            buf.put(camera.ndcX(px[i])); buf.put(camera.ndcY(py[i]));
            buf.put(u[i]); buf.put(v[i]);
            buf.put(r); buf.put(g); buf.put(b); buf.put(a);
        }
    }

    private void uploadAndDrawTex(FloatBuffer b, int verts) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dynVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * 32, b, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glEnableVertexAttribArray(texProg.aPos);
        GLES20.glVertexAttribPointer(texProg.aPos, 2, GLES20.GL_FLOAT, false, 32, 0);
        GLES20.glEnableVertexAttribArray(texProg.aUV);
        GLES20.glVertexAttribPointer(texProg.aUV, 2, GLES20.GL_FLOAT, false, 32, 8);
        GLES20.glEnableVertexAttribArray(texProg.aColor);
        GLES20.glVertexAttribPointer(texProg.aColor, 4, GLES20.GL_FLOAT, false, 32, 16);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        GLES20.glDisableVertexAttribArray(texProg.aColor);
        GLES20.glDisableVertexAttribArray(texProg.aUV);
        GLES20.glDisableVertexAttribArray(texProg.aPos);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    /** Thin ellipse ring (annulus) in PIXEL coordinates via the flat program. */
    private void drawAnnulus(float cxPx, float cyPx, float rx, float ry, float thickPx,
                             float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        final int seg = 20;
        FloatBuffer buf = dynBuf.get(seg * 6 * 6);
        int verts = 0;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float ox0 = (float) Math.cos(a0), oy0 = (float) Math.sin(a0);
            float ox1 = (float) Math.cos(a1), oy1 = (float) Math.sin(a1);
            // outer / inner points (px)
            float x0o = camera.ndcX(cxPx + ox0 * rx), y0o = camera.ndcY(cyPx + oy0 * ry);
            float x1o = camera.ndcX(cxPx + ox1 * rx), y1o = camera.ndcY(cyPx + oy1 * ry);
            float x0i = camera.ndcX(cxPx + ox0 * (rx - thickPx)), y0i = camera.ndcY(cyPx + oy0 * (ry - thickPx * Config.RHO));
            float x1i = camera.ndcX(cxPx + ox1 * (rx - thickPx)), y1i = camera.ndcY(cyPx + oy1 * (ry - thickPx * Config.RHO));
            buf.put(x0o); buf.put(y0o); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            buf.put(x1o); buf.put(y1o); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            buf.put(x1i); buf.put(y1i); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            buf.put(x0o); buf.put(y0o); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            buf.put(x1i); buf.put(y1i); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            buf.put(x0i); buf.put(y0i); buf.put(r); buf.put(g); buf.put(b); buf.put(a);
            verts += 6;
        }
        buf.flip();
        GLES20.glUseProgram(spriteProg.id);
        uploadAndDrawSprites(buf, verts);
    }

    // ---------------------------------------------------------------- particles (screen space)

    private void drawParticles() {
        if (particles.isEmpty()) return;
        GLES20.glUseProgram(spriteProg.id);
        int max = particles.size() * 6;
        FloatBuffer b = dynBuf.get(max * 6);
        int verts = 0;
        for (Particle p : particles) {
            float t = p.age / Config.PARTICLE_T;
            float alpha = 0.85f * (1f - t);
            float sx = camera.screenX(p.x, p.z);
            float sy = camera.screenY(p.x, p.z, p.y);
            float halfPx = Math.max(1.5f, p.size * camera.scale()) * (1f - t * 0.45f);
            float dx = halfPx / camera.viewW() * 2f;
            float dy = halfPx / camera.viewH() * 2f;
            float cxn = camera.ndcX(sx), cyn = camera.ndcY(sy);
            float r, g, bl;
            if (p.gold) { r = 0.97f; g = 0.80f; bl = 0.36f; }
            else { r = 0.97f; g = 0.96f; bl = 0.91f; }
            b.put(cxn - dx); b.put(cyn - dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            b.put(cxn + dx); b.put(cyn - dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            b.put(cxn + dx); b.put(cyn + dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            b.put(cxn - dx); b.put(cyn - dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            b.put(cxn + dx); b.put(cyn + dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            b.put(cxn - dx); b.put(cyn + dy); b.put(r); b.put(g); b.put(bl); b.put(alpha);
            verts += 6;
        }
        b.flip();
        uploadAndDrawSprites(b, verts);
    }

    // ---------------------------------------------------------------- text

    private void drawText() {
        GLES20.glUseProgram(fontProg.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(fontProg.uTex, 0);

        FloatBuffer b = dynBuf.get(2048);

        if (game.state() != Game.State.OVER) {
            String s = String.valueOf(game.score());
            float h = camera.viewH() * 0.085f;
            float topPx = camera.viewH() * 0.115f;   // clears notches/status areas
            float cyNdc = camera.ndcY(topPx + h * 0.5f);
            // soft shadow pass, then the crisp number on top (separate draw
            // calls: uColor applies to the WHOLE draw, not per quad)
            bindFont(scoreFont);
            GLES20.glUniform4f(fontProg.uColor,
                    Config.SCORE_INK[0], Config.SCORE_INK[1], Config.SCORE_INK[2], 0.16f);
            drawTextBuffer(b, scoreFont.emit(b, s, 0f, cyNdc - 0.016f, h, camera.viewW(), camera.viewH()));
            GLES20.glUniform4f(fontProg.uColor,
                    Config.SCORE_INK[0], Config.SCORE_INK[1], Config.SCORE_INK[2], 0.95f);
            drawTextBuffer(b, scoreFont.emit(b, s, 0f, cyNdc, h, camera.viewW(), camera.viewH()));

            // combo streak indicator under the score (x2, x4, ...)
            if (game.combo() >= 2) {
                bindFont(popupFont);
                GLES20.glUniform4f(fontProg.uColor, 0.45f, 0.36f, 0.30f, 0.62f);
                float ch = camera.viewH() * 0.028f;
                float ccy = camera.ndcY(topPx + h + ch * 0.8f);
                drawTextBuffer(b, popupFont.emit(b, "x" + game.combo(), 0f, ccy,
                        ch, camera.viewW(), camera.viewH()));
            }
        }

        // floating "+N" popups anchored to world points (own draw call each:
        // every popup has its own color/alpha)
        bindFont(popupFont);
        for (Popup p : popups) {
            float t = p.age / Config.POPUP_T;
            float alpha = t < 0.6f ? 1f : (1f - (t - 0.6f) / 0.4f);
            float rise = t * 0.9f;
            float sxPx = camera.screenX(p.wx, p.wz);
            float syPx = camera.screenY(p.wx, p.wz, p.wy + rise);
            float sxNdc = camera.ndcX(sxPx);
            float syNdc = camera.ndcY(syPx);
            if (p.gold) {
                GLES20.glUniform4f(fontProg.uColor, Config.GOLD[0], Config.GOLD[1], Config.GOLD[2], alpha);
            } else {
                GLES20.glUniform4f(fontProg.uColor, 1f, 1f, 0.97f, alpha);
            }
            drawTextBuffer(b, popupFont.emit(b, p.text, sxNdc, syNdc,
                    camera.viewH() * 0.05f, camera.viewW(), camera.viewH()));
        }
    }

    private void bindFont(FontAtlas font) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, font.textureId());
    }

    /** Uploads and draws one batch of font quads with the CURRENT uColor. */
    private void drawTextBuffer(FloatBuffer b, int verts) {
        if (verts <= 0) return;
        b.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dynVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * 16, b, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glEnableVertexAttribArray(fontProg.aPos);
        GLES20.glVertexAttribPointer(fontProg.aPos, 2, GLES20.GL_FLOAT, false, 16, 0);
        GLES20.glEnableVertexAttribArray(fontProg.aUV);
        GLES20.glVertexAttribPointer(fontProg.aUV, 2, GLES20.GL_FLOAT, false, 16, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        GLES20.glDisableVertexAttribArray(fontProg.aUV);
        GLES20.glDisableVertexAttribArray(fontProg.aPos);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        b.clear();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
