package io.github.maharjanronish.jumpjump;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import io.github.maharjanronish.jumpjump.core.Blocks;
import io.github.maharjanronish.jumpjump.core.Camera;
import io.github.maharjanronish.jumpjump.core.Config;
import io.github.maharjanronish.jumpjump.core.Game;
import io.github.maharjanronish.jumpjump.core.GameEvent;
import io.github.maharjanronish.jumpjump.core.Mat4;
import io.github.maharjanronish.jumpjump.core.Piece;
import io.github.maharjanronish.jumpjump.core.Vec3;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * All rendering for the game. Frame budget on a 2015-class phone:
 * ~25 draw calls, < 10k triangles, RGB565 framebuffer - comfortably 60 fps.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {

    public interface Ui {
        /** Called from the GL thread when the run ends. */
        void onGameOver(int score);
        /** Called from the GL thread after the very first jump (hide hint). */
        void onFirstJump();
    }

    private final Game game;
    private final Ui ui;
    private final Sfx sfx;
    private final Camera camera = new Camera();

    private ShaderUtil.MeshProgram meshProg;
    private ShaderUtil.Flat3DProgram flatProg;
    private ShaderUtil.SpriteProgram spriteProg;
    private ShaderUtil.FontProgram fontProg;

    private Meshes.Mesh cube, cylPiece, cylBlock, cylHorn, sphere;
    private Meshes.FlatMesh disc, ring;

    private final FontAtlas font = new FontAtlas();

    private float aspect = 0.46f;
    private int viewW = 1080, viewH = 2340;

    // scratch matrices
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] local = new float[16];
    private final float[] mvp = new float[16];

    // transient effects
    private static final class Popup {
        float wx, wy, wz; String text; float age; boolean gold;
    }
    private static final class Ring {
        float wx, wz; float age; float[] color;
    }
    private static final class Dust {
        float wx, wz, vx, vz, age; float size;
    }
    private final List<Popup> popups = new ArrayList<>(8);
    private final List<Ring> rings = new ArrayList<>(8);
    private final List<Dust> dust = new ArrayList<>(16);
    private final Random fxRandom = new Random();
    private final ShaderUtil.DynBuf dynBuf = new ShaderUtil.DynBuf();
    private final int[] tmpIds = new int[1];

    private long lastFrameNanos;
    private boolean firstJumpSeen;

    public GameRenderer(Game game, Sfx sfx, Ui ui) {
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
        dust.clear();
        lastFrameNanos = System.nanoTime();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        meshProg = new ShaderUtil.MeshProgram();
        flatProg = new ShaderUtil.Flat3DProgram();
        spriteProg = new ShaderUtil.SpriteProgram();
        fontProg = new ShaderUtil.FontProgram();
        font.create();

        cube = Meshes.cube();
        cylPiece = Meshes.cyl(22, 1.0f, 0.607f);   // piece body taper
        cylBlock = Meshes.cyl(26, 1.0f, 1.0f);      // round blocks / vinyl base
        cylHorn = Meshes.cyl(16, 0.35f, 1.0f);      // gramophone horn
        sphere = Meshes.sphere(10, 14);
        disc = Meshes.disc(22);
        ring = Meshes.ring(26);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        aspect = viewW / (float) viewH;
        GLES20.glViewport(0, 0, viewW, viewH);
    }

    @Override public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameNanos) * 1e-9f);
        lastFrameNanos = now;

        game.update(dt);
        handleEvents(dt);
        updateEffects(dt);

        camera.update(game, aspect);
        System.arraycopy(camera.viewProj, 0, vp, 0, 16);

        GLES20.glClearColor(Config.BG_BOTTOM[0], Config.BG_BOTTOM[1], Config.BG_BOTTOM[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        drawBackground();
        drawWorld();
        drawBlendedWorld();
        drawDust();
        drawText(dt);
    }

    // ---------------------------------------------------------------- events

    private void handleEvents(float dt) {
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
                    break;
                case LAND:
                    sfx.play(Sfx.LAND);
                    spawnDust(e.x, e.z, 5);
                    addPopup(e.x, e.y + 1.1f, e.z, "+" + e.intArg, false);
                    break;
                case PERFECT:
                    sfx.play(Sfx.PERFECT, Math.min(e.intArg2, 8));
                    spawnDust(e.x, e.z, 8);
                    addRing(e.x, e.z, new float[]{1f, 1f, 1f, 0.55f});
                    addPopup(e.x, e.y + 1.1f, e.z, "+" + e.intArg, false);
                    break;
                case SPECIAL_BONUS:
                    sfx.play(Sfx.special(e.kind()));
                    addPopup(e.x, e.y + 0.9f, e.z, "+" + e.intArg, true);
                    if (e.kind() == Blocks.Kind.MANHOLE) {
                        addRing(e.x, e.z, new float[]{0.55f, 0.75f, 0.95f, 0.5f});
                        addRing(e.x, e.z, new float[]{0.55f, 0.75f, 0.95f, 0.35f});
                    }
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

    private void addRing(float x, float z, float[] color) {
        Ring r = new Ring();
        r.wx = x; r.wz = z; r.age = 0; r.color = color;
        rings.add(r);
    }

    private void spawnDust(float x, float z, int n) {
        for (int i = 0; i < n; i++) {
            Dust d = new Dust();
            d.wx = x; d.wz = z;
            double a = fxRandom.nextDouble() * Math.PI * 2;
            float sp = 0.8f + fxRandom.nextFloat() * 1.6f;
            d.vx = (float) Math.cos(a) * sp;
            d.vz = (float) Math.sin(a) * sp;
            d.age = 0;
            d.size = 0.5f + fxRandom.nextFloat();
            dust.add(d);
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
        for (int i = dust.size() - 1; i >= 0; i--) {
            Dust d = dust.get(i);
            d.age += dt;
            d.wx += d.vx * dt; d.wz += d.vz * dt;
            d.vx *= (1 - dt * 2.4f); d.vz *= (1 - dt * 2.4f);
            if (d.age > Config.DUST_T) dust.remove(i);
        }
    }

    // ---------------------------------------------------------------- background

    private void drawBackground() {
        GLES20.glUseProgram(spriteProg.id);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        FloatBuffer b = dynBuf.get(36);
        float[] t = Config.BG_TOP, bo = Config.BG_BOTTOM;
        // two triangles: top edge uses BG_TOP, bottom edge BG_BOTTOM
        b.put(-1); b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.put(-1); b.put(1);  b.put(t[0]); b.put(t[1]); b.put(t[2]); b.put(1);
        b.put(1);  b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.put(-1); b.put(-1); b.put(bo[0]); b.put(bo[1]); b.put(bo[2]); b.put(1);
        b.flip();
        uploadAndDrawSprites(b, 6);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void uploadAndDrawSprites(FloatBuffer b, int verts) {
        GLES20.glGenBuffers(1, tmpIds, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tmpIds[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * 24, b, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glEnableVertexAttribArray(spriteProg.aPos);
        GLES20.glVertexAttribPointer(spriteProg.aPos, 2, GLES20.GL_FLOAT, false, 24, 0);
        GLES20.glEnableVertexAttribArray(spriteProg.aColor);
        GLES20.glVertexAttribPointer(spriteProg.aColor, 4, GLES20.GL_FLOAT, false, 24, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        GLES20.glDisableVertexAttribArray(spriteProg.aColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDeleteBuffers(1, tmpIds, 0);
    }

    // ---------------------------------------------------------------- world

    private void drawWorld() {
        GLES20.glUseProgram(meshProg.id);
        GLES20.glUniform3f(meshProg.uLight, -0.55f, 0.85f, -0.25f);
        for (Blocks.Block blk : game.visible()) {
            drawBlock(blk);
        }
        drawPiece();
    }

    private void drawBlock(Blocks.Block blk) {
        float w = blk.halfW;
        float settle = blk.settleT > 0 ? blk.settleT / 0.18f : 0f; // 1 -> 0
        float sy = Config.BLOCK_BODY_DEPTH * (1f - 0.045f * settle);
        float sxz = 2f * w * (1f + 0.03f * settle);

        float[] base = blockBaseColor(blk);
        float rotY = blk.kind == Blocks.Kind.RUBIK ? (float) Math.toDegrees(blk.spin) : 0f;

        if (blk.kind == Blocks.Kind.CYLINDER) {
            Mat4.model(model, blk.center.x, -Config.BLOCK_BODY_DEPTH, blk.center.z, rotY, w, sy, w);
            drawMesh(cylBlock, model, base, 1f);
        } else {
            Mat4.model(model, blk.center.x, -Config.BLOCK_BODY_DEPTH * 0.5f, blk.center.z, rotY, sxz, sy, sxz);
            drawMesh(cube, model, base, 1f);
        }

        switch (blk.kind) {
            case RUBIK:    drawRubik(blk, w, rotY); break;
            case STORE:    drawStore(blk, w); break;
            case MUSICBOX: drawMusicBox(blk, w); break;
            case CLOCK:    drawClock(blk, w); break;
            default: break; // MANHOE decor is flat (blended pass)
        }
    }

    private float[] blockBaseColor(Blocks.Block blk) {
        switch (blk.kind) {
            case RUBIK:    return new float[]{0.16f, 0.15f, 0.17f};
            case MUSICBOX: return Config.MUSIC_WOOD;
            default:       return Config.BLOCK_PASTELS[blk.colorIndex % Config.BLOCK_PASTELS.length];
        }
    }

    private void drawRubik(Blocks.Block blk, float w, float rotYDeg) {
        // 3x3 stickers on the top face + the camera-facing front face;
        // sticker offsets rotate with the block's solve animation
        float rad = (float) Math.toRadians(rotYDeg);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        float[][] top = {
                Config.RUBIK_WHITE, Config.RUBIK_RED, Config.RUBIK_BLUE,
                Config.RUBIK_ORANGE, Config.RUBIK_YELLOW, Config.RUBIK_GREEN,
                Config.RUBIK_RED, Config.RUBIK_WHITE, Config.RUBIK_BLUE,
        };
        float cell = (2f * w - 0.10f) / 3f;
        float yTop = 0.02f;
        for (int i = 0; i < 9; i++) {
            float cx = (i % 3 - 1) * (cell + 0.04f);
            float cz = (i / 3 - 1) * (cell + 0.04f);
            float rx = cx * c + cz * s;
            float rz = -cx * s + cz * c;
            Mat4.model(local, blk.center.x + rx, yTop, blk.center.z + rz, rotYDeg, cell, 0.035f, cell);
            drawMesh(cube, local, top[i], 1f);
        }
        // front stickers (facing the camera side)
        float[][] front = {
                Config.RUBIK_GREEN, Config.RUBIK_YELLOW, Config.RUBIK_ORANGE,
                Config.RUBIK_BLUE, Config.RUBIK_WHITE, Config.RUBIK_RED,
                Config.RUBIK_YELLOW, Config.RUBIK_GREEN, Config.RUBIK_ORANGE,
        };
        for (int i = 0; i < 9; i++) {
            float cx = (i % 3 - 1) * (cell + 0.04f);
            float cy = -(i / 3) * (cell + 0.04f) - 0.30f;
            float rx = cx * c;
            float rz = -cx * s - w - 0.02f;
            Mat4.model(local, blk.center.x + rx, cy, blk.center.z + rz, rotYDeg, cell, cell, 0.035f);
            drawMesh(cube, local, front[i], 1f);
        }
    }

    private void drawStore(Blocks.Block blk, float w) {
        float x = blk.center.x, z = blk.center.z;
        float open = blk.animT; // 0 closed -> 1 open
        // lit interior (behind the door)
        Mat4.model(local, x, -1.05f, z - w + 0.01f, 0, w * 0.95f, 1.15f, 0.02f);
        drawMesh(cube, local, Config.STORE_WINDOW, 1f);
        // door slides right as it "opens"
        float doorW = w * 0.46f;
        Mat4.model(local, x - doorW * 0.5f + open * doorW, -1.08f, z - w - 0.03f, 0, doorW, 1.10f, 0.05f);
        drawMesh(cube, local, Config.STORE_DOOR, 1f);
        // two windows beside the door
        Mat4.model(local, x - w * 0.60f, -0.95f, z - w - 0.02f, 0, w * 0.28f, 0.55f, 0.04f);
        drawMesh(cube, local, Config.STORE_WINDOW, 1f);
        Mat4.model(local, x + w * 0.62f, -0.95f, z - w - 0.02f, 0, w * 0.28f, 0.55f, 0.04f);
        drawMesh(cube, local, Config.STORE_WINDOW, 1f);
        // awning with white stripes
        Mat4.model(local, x, -0.34f, z - w - 0.07f, 0, w * 1.95f, 0.30f, 0.16f);
        drawMesh(cube, local, Config.STORE_AWNING, 1f);
        for (int i = -1; i <= 1; i++) {
            Mat4.model(local, x + i * w * 0.62f, -0.34f, z - w - 0.155f, 0, w * 0.22f, 0.31f, 0.02f);
            drawMesh(cube, local, Config.RUBIK_WHITE, 1f);
        }
        // sign band
        Mat4.model(local, x, -0.05f, z - w - 0.02f, 0, w * 1.7f, 0.26f, 0.04f);
        drawMesh(cube, local, Config.STORE_SIGN, 1f);
    }

    private void drawMusicBox(Blocks.Block blk, float w) {
        float x = blk.center.x, z = blk.center.z;
        float spin = blk.spin;
        float deg = (float) Math.toDegrees(spin);
        // vinyl record (dark disc reads as a record from above)
        Mat4.model(local, x, 0.028f, z, deg, w * 0.62f, 0.05f, w * 0.62f);
        drawMesh(cylBlock, local, Config.MUSIC_VINYL, 1f);
        // red label spins visibly off-center
        float lx = 0.22f * w, lz = 0.10f * w;
        float rx = (float) (Math.cos(spin) * lx - Math.sin(spin) * lz);
        float rz = (float) (Math.sin(spin) * lx + Math.cos(spin) * lz);
        Mat4.model(local, x + rx, 0.052f, z + rz, 0, w * 0.17f, 0.05f, w * 0.17f);
        drawMesh(cylBlock, local, Config.MUSIC_LABEL, 1f);
        // brass horn at the back corner, tilted
        Mat4.modelPY(local, x - w * 0.55f, 0.30f, z + w * 0.42f, -28f, 35f, w * 0.30f, w * 0.85f, w * 0.30f);
        drawMesh(cylHorn, local, Config.MUSIC_BRASS, 1f);
    }

    private void drawClock(Blocks.Block blk, float w) {
        float x = blk.center.x, z = blk.center.z;
        Calendar now = Calendar.getInstance();
        float minute = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        float hour = (now.get(Calendar.HOUR) % 12) + minute / 60f;
        float aMin = (float) (Math.PI * 2 * minute / 60f);
        float aHour = (float) (Math.PI * 2 * hour / 12f);
        drawHand(blk, w, aMin, 0.62f * w, 0.030f * w);
        drawHand(blk, w, aHour, 0.40f * w, 0.040f * w);
        // center pin
        Mat4.model(local, x, 0.030f, z, 0, 0.06f * w, 0.045f, 0.06f * w);
        drawMesh(cylBlock, local, Config.CLOCK_HAND, 1f);
    }

    private void drawHand(Blocks.Block blk, float w, float angle, float len, float thick) {
        float dx = (float) Math.sin(angle), dz = (float) Math.cos(angle);
        Mat4.model(local,
                blk.center.x + dx * len * 0.5f, 0.026f, blk.center.z + dz * len * 0.5f,
                (float) Math.toDegrees(angle) - 90f,
                len, 0.012f, thick);
        drawMesh(cube, local, Config.CLOCK_HAND, 1f);
    }

    // ---------------------------------------------------------------- the piece

    private void drawPiece() {
        Piece p = game.piece();
        if (p.dead && game.state() == Game.State.OVER) {
            // piece is gone below; skip drawing
            if (p.pos.y < Config.FALL_KILL_Y - 1f) return;
        }
        float pitch = (float) Math.toDegrees(p.wobble * 3f) + p.tumbleDeg;
        Mat4.modelPY(model,
                p.pos.x, p.pos.y, p.pos.z,
                pitch, 0f,
                p.widenXZ, p.squashY, p.widenXZ);

        // body: tapered cylinder, black
        Mat4.model(local, 0, 0, 0, 0, Config.PIECE_BASE_R, 1.28f, Config.PIECE_BASE_R);
        Mat4.mul(local, model, local);
        drawMesh(cylPiece, local, Config.PIECE_BODY, 1f);

        // head: ivory sphere
        float headY = 1.52f;
        Mat4.model(local, 0, headY, 0, 0, Config.PIECE_HEAD_R, Config.PIECE_HEAD_R, Config.PIECE_HEAD_R);
        Mat4.mul(local, model, local);
        drawMesh(sphere, local, Config.PIECE_HEAD, 1f);
    }

    // ---------------------------------------------------------------- blended world-space

    private void drawBlendedWorld() {
        GLES20.glUseProgram(flatProg.id);
        GLES20.glDepthMask(false);

        // blob shadow under the piece
        Piece p = game.piece();
        Blocks.Block cur = game.cur();
        float overBlock = Math.abs((p.pos.x * Config.U_X + p.pos.z * Config.U_Z) - cur.s);
        if (overBlock < cur.halfW + 0.6f && p.pos.y > -0.1f) {
            float h = Math.max(0, p.pos.y);
            float r = 0.46f * (1f - Math.min(0.5f, h * 0.09f));
            float alpha = Config.SHADOW_ALPHA * (1f - Math.min(0.8f, h * 0.16f));
            Mat4.model(model, p.pos.x, 0.02f, p.pos.z, 0, r, 1, r);
            drawFlat(disc, model, Config.SHADOW[0], Config.SHADOW[1], Config.SHADOW[2], alpha);
        }

        // charge gauge disc
        float power = game.power();
        if (power > 0f) {
            float r = 0.30f + 1.05f * power;
            Mat4.model(model, p.pos.x, 0.035f, p.pos.z, 0, r, 1, r);
            drawFlat(disc, model, Config.GAUGE[0], Config.GAUGE[1], Config.GAUGE[2], Config.GAUGE_ALPHA);
        }

        // expanding rings
        for (Ring rg : rings) {
            float t = rg.age / Config.RING_T;
            float r = 0.35f + 2.1f * t;
            float a = rg.color[3] * (1f - t);
            Mat4.model(model, rg.wx, 0.05f, rg.wz, 0, r, 1, r);
            drawFlat(ring, model, rg.color[0], rg.color[1], rg.color[2], a);
        }

        // manhole cover (flat dark disc, drawn blended to sit on the block)
        for (Blocks.Block blk : game.visible()) {
            if (blk.kind == Blocks.Kind.MANHOLE) {
                Mat4.model(model, blk.center.x, 0.012f, blk.center.z, 0, blk.halfW * 0.62f, 1, blk.halfW * 0.62f);
                drawFlat(disc, model, Config.MANHOLE_COVER[0], Config.MANHOLE_COVER[1], Config.MANHOLE_COVER[2], 1f);
                Mat4.model(model, blk.center.x, 0.014f, blk.center.z, 0, blk.halfW * 0.45f, 1, blk.halfW * 0.45f);
                drawFlat(disc, model, Config.MANHOLE_RING[0], Config.MANHOLE_RING[1], Config.MANHOLE_RING[2], 1f);
            }
        }

        GLES20.glDepthMask(true);
    }

    // ---------------------------------------------------------------- dust (screen space)

    private void drawDust() {
        if (dust.isEmpty()) return;
        GLES20.glUseProgram(spriteProg.id);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        int max = dust.size() * 6;
        FloatBuffer b = dynBuf.get(max * 6);
        int verts = 0;
        float px = viewH * 0.004f;
        for (Dust d : dust) {
            float t = d.age / Config.DUST_T;
            float a = 0.55f * (1f - t);
            float size = px * d.size * (1f - t * 0.5f);
            float sx = camera.screenX(d.wx, 0.18f, d.wz) * 2f - 1f;
            float sy = camera.screenY(d.wx, 0.18f, d.wz) * 2f - 1f;
            float dx = 2f * size / viewW;
            float dy = 2f * size / viewH;
            float r = 0.96f, g = 0.95f, bl = 0.90f;
            b.put(sx - dx); b.put(sy - dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            b.put(sx + dx); b.put(sy - dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            b.put(sx + dx); b.put(sy + dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            b.put(sx - dx); b.put(sy - dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            b.put(sx + dx); b.put(sy + dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            b.put(sx - dx); b.put(sy + dy); b.put(r); b.put(g); b.put(bl); b.put(a);
            verts += 6;
        }
        b.flip();
        uploadAndDrawSprites(b, verts);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    // ---------------------------------------------------------------- text

    private void drawText(float dt) {
        GLES20.glUseProgram(fontProg.id);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, font.textureId());
        GLES20.glUniform1i(fontProg.uTex, 0);

        FloatBuffer b = dynBuf.get(2048);
        int verts = 0;

        // big score, top center
        if (game.state() != Game.State.OVER) {
            GLES20.glUniform4f(fontProg.uColor,
                    Config.SCORE_INK[0], Config.SCORE_INK[1], Config.SCORE_INK[2], 0.95f);
            verts += font.emit(b, String.valueOf(game.score()), 0f, 0.86f,
                    viewH * 0.062f, viewW, viewH);
        }

        // floating "+N" popups anchored to world points
        for (Popup p : popups) {
            float t = p.age / Config.POPUP_T;
            float alpha = t < 0.6f ? 1f : (1f - (t - 0.6f) / 0.4f);
            float rise = t * 0.9f;
            float syNdc = camera.screenY(p.wx, p.wy + rise, p.wz) * 2f - 1f;
            float sxNdc = camera.screenX(p.wx, p.wy + rise, p.wz) * 2f - 1f;
            if (p.gold) {
                GLES20.glUniform4f(fontProg.uColor, Config.GOLD[0], Config.GOLD[1], Config.GOLD[2], alpha);
            } else {
                GLES20.glUniform4f(fontProg.uColor, 1f, 1f, 0.97f, alpha);
            }
            verts += font.emit(b, p.text, sxNdc, syNdc, viewH * 0.05f, viewW, viewH);
        }

        if (verts > 0) {
            b.flip();
            GLES20.glGenBuffers(1, tmpIds, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tmpIds[0]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * 16, b, GLES20.GL_DYNAMIC_DRAW);
            GLES20.glEnableVertexAttribArray(fontProg.aPos);
            GLES20.glVertexAttribPointer(fontProg.aPos, 2, GLES20.GL_FLOAT, false, 16, 0);
            GLES20.glEnableVertexAttribArray(fontProg.aUV);
            GLES20.glVertexAttribPointer(fontProg.aUV, 2, GLES20.GL_FLOAT, false, 16, 8);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
            GLES20.glDisableVertexAttribArray(fontProg.aUV);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glDeleteBuffers(1, tmpIds, 0);
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    // ---------------------------------------------------------------- low-level draws

    private void drawMesh(Meshes.Mesh m, float[] mdl, float[] color, float alpha) {
        Mat4.mul(mvp, vp, mdl);
        GLES20.glUniformMatrix4fv(meshProg.uMVP, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(meshProg.uM, 1, false, mdl, 0);
        GLES20.glUniform4f(meshProg.uColor, color[0], color[1], color[2], alpha);
        m.draw(meshProg.aPos, meshProg.aNormal);
    }

    private void drawFlat(Meshes.FlatMesh m, float[] mdl, float r, float g, float b, float a) {
        Mat4.mul(mvp, vp, mdl);
        GLES20.glUniformMatrix4fv(flatProg.uMVP, 1, false, mvp, 0);
        GLES20.glUniform4f(flatProg.uColor, r, g, b, a);
        m.draw(flatProg.aPos);
    }
}
