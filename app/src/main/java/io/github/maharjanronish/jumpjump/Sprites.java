package io.github.maharjanronish.jumpjump;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.opengl.GLES20;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import io.github.maharjanronish.jumpjump.core.Blocks;
import io.github.maharjanronish.jumpjump.core.Config;

/**
 * All textures in one place:
 *  - the 10 pre-rendered isometric block sprites (assets/blocks/*.png)
 *  - the chess piece (procedurally drawn, like the original)
 *  - a soft radial disc (blob shadow, marker dot)
 *
 * Block sprites are the user-supplied concept art; every sprite carries its
 * anchor: the pixel row of the TOP-FACE CENTER (the landing surface), which
 * the renderer places exactly at the block's logical world center.
 *
 * Textures are uploaded with STRAIGHT alpha (Bitmap.getPixels -> RGBA bytes)
 * so blending behaves identically on every GPU.
 */
final class Sprites {

    /** One loaded block sprite. */
    static final class BlockSprite {
        int tex = -1;
        int w, h;
        /** Pixel row of the top-face (landing) center, from the sprite top. */
        float topCy;
    }

    /** Asset files in fixed order; index = sprite id. */
    private static final String[] FILES = {
            "01_plain_stone_slab.png",   // 0 plain
            "02_grass_layer_platform.png",// 1 plain
            "03_wooden_platform.png",    // 2 plain
            "04_dark_striped_platform.png",// 3 plain
            "10_face_tile_platform.png", // 4 plain
            "05_round_cylinder.png",     // 5 cylinder
            "06_manhole_platform.png",   // 6 manhole  +5
            "07_rubiks_cube.png",        // 7 rubik    +10
            "09_small_shop_platform.png",// 8 store    +15
            "08_record_player_platform.png", // 9 music box +30
    };

    /** Measured anchors (pixel row of the top-face center at 256 px width). */
    private static final float[] TOP_CY = {
            84.05f, 69.39f, 62.42f, 63.53f, 64.90f,
            62.71f, 67.62f, 64.70f, 72.00f, 60.13f,
    };

    /** Sprite id for a block kind + style. */
    static int idOf(Blocks.Kind kind, int style) {
        switch (kind) {
            case CYLINDER: return 5;
            case MANHOLE:  return 6;
            case RUBIK:    return 7;
            case STORE:    return 8;
            case MUSICBOX: return 9;
            default:       return style % 5;   // plain styles 0..4
        }
    }

    final BlockSprite[] blocks = new BlockSprite[FILES.length];
    int pieceTex = -1;
    int softDiscTex = -1;

    /** Loads everything; call once on the GL thread. */
    void create(Context ctx) {
        for (int i = 0; i < FILES.length; i++) {
            BlockSprite s = new BlockSprite();
            Bitmap bmp = decode(ctx, "blocks/" + FILES[i]);
            if (bmp != null) {
                s.w = bmp.getWidth();
                s.h = bmp.getHeight();
                s.topCy = TOP_CY[i] * (s.w / 256f);
                s.tex = uploadStraight(bmp);
                bmp.recycle();
            }
            blocks[i] = s;
        }
        pieceTex = createPieceTexture();
        softDiscTex = createSoftDisc();
    }

    private static Bitmap decode(Context ctx, String assetPath) {
        try (InputStream in = ctx.getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- piece

    /**
     * The black-and-ivory chess piece, drawn with Canvas (no asset file).
     * 220 x 370 px at 200 px per world unit: 1.10 x 1.85 units, feet at the
     * bottom edge, anchor = bottom-center. Proportions matched to the
     * original: base ~45% of height, ivory head ~31%, collar ring visible.
     */
    private static int createPieceTexture() {
        float u = 200f;
        int w = Math.round(Config.PIECE_W_UNITS * u);
        int h = Math.round(Config.PIECE_H_UNITS * u);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = w * 0.5f;
        float baseR = 0.42f * u;      // base half-width (45% of height)
        float neckR = 0.17f * u;      // shoulder half-width
        float headR = 0.29f * u;      // ivory head radius (31% of height)
        float headCy = 0.33f * u;     // head center
        float bodyTop = 0.62f * u;    // shoulder line
        float bodyLen = h - bodyTop;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // body: tapered silhouette with a soft waist, feet at the bottom
        Path body = new Path();
        body.moveTo(cx - baseR, h);
        body.quadTo(cx - baseR * 0.96f, h - bodyLen * 0.42f,
                cx - 0.30f * u, bodyTop + bodyLen * 0.16f);
        body.quadTo(cx - neckR * 1.25f, bodyTop + bodyLen * 0.05f,
                cx - neckR, bodyTop);
        body.lineTo(cx + neckR, bodyTop);
        body.quadTo(cx + neckR * 1.25f, bodyTop + bodyLen * 0.05f,
                cx + 0.30f * u, bodyTop + bodyLen * 0.16f);
        body.quadTo(cx + baseR * 0.96f, h - bodyLen * 0.42f,
                cx + baseR, h);
        body.close();
        p.setColor(0xFF343238);
        c.drawPath(body, p);

        // left-side shade for volume
        Path shade = new Path();
        shade.moveTo(cx - baseR * 0.9f, h);
        shade.quadTo(cx - baseR * 0.85f, h - bodyLen * 0.42f,
                cx - 0.24f * u, bodyTop + bodyLen * 0.18f);
        shade.lineTo(cx - 0.10f * u, bodyTop + bodyLen * 0.18f);
        shade.quadTo(cx - 0.14f * u, h - bodyLen * 0.42f,
                cx - 0.14f * u, h);
        shade.close();
        p.setColor(0xFF26242A);
        c.drawPath(shade, p);

        // collar ring below the head (drawn before the head so it peeks out)
        p.setColor(0xFF3C3A40);
        c.drawOval(cx - 0.34f * u, bodyTop - 0.07f * u,
                cx + 0.34f * u, bodyTop + 0.09f * u, p);

        // ivory head with a soft highlight
        p.setColor(0xFFF6F1E4);
        c.drawOval(cx - headR, headCy - headR, cx + headR, headCy + headR, p);
        p.setColor(0xFFFFFCF4);
        c.drawOval(cx - headR * 0.62f, headCy - headR * 0.62f,
                cx + headR * 0.10f, headCy + headR * 0.10f, p);

        int tex = uploadStraight(bmp);
        bmp.recycle();
        return tex;
    }

    // ---------------------------------------------------------------- soft disc

    /** 64 x 64 white radial disc (opaque core, transparent edge). */
    private static int createSoftDisc() {
        int n = 64;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f,
                new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.64f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(n / 2f, n / 2f, n / 2f, p);
        int tex = uploadStraight(bmp);
        bmp.recycle();
        return tex;
    }

    // ---------------------------------------------------------------- upload

    /**
     * Uploads a bitmap as GL_RGBA with STRAIGHT alpha (Bitmap.getPixels
     * returns un-premultiplied ARGB). This avoids driver-dependent
     * premultiplication behavior of GLUtils.texImage2D.
     */
    private static int uploadStraight(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = pixels[y * w + x];
                bb.put((byte) ((c >> 16) & 0xFF));   // R
                bb.put((byte) ((c >> 8) & 0xFF));    // G
                bb.put((byte) (c & 0xFF));           // B
                bb.put((byte) ((c >> 24) & 0xFF));   // A
            }
        }
        bb.flip();

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int tex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return tex;
    }
}
