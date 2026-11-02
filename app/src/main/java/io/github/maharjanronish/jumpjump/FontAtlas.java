package io.github.maharjanronish.jumpjump;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Runtime-generated bitmap font for digits and '+' - zero asset files.
 * White glyphs, tinted per draw call (score ink, ivory popups, gold bonuses).
 */
public final class FontAtlas {

    public static final String GLYPHS = "0123456789+";

    private int texture = -1;
    private final float[] uvs = new float[GLYPHS.length() * 4]; // u0,v0,u1,v1
    private final float[] widths = new float[GLYPHS.length()];
    private float rowH;

    public void create() {
        int size = 128;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        paint.setTextSize(size * 0.72f);
        paint.setColor(0xFFFFFFFF);

        // measure
        float maxW = 0;
        for (int i = 0; i < GLYPHS.length(); i++) {
            float w = paint.measureText(String.valueOf(GLYPHS.charAt(i)));
            widths[i] = w;
            maxW = Math.max(maxW, w);
        }
        rowH = size;

        // lay out on one horizontal strip
        int stripW = (int) Math.ceil(maxW * GLYPHS.length()) + GLYPHS.length() * 8;
        Bitmap bmp = Bitmap.createBitmap(stripW, size, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(bmp);
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = -fm.ascent;
        float x = 0;
        for (int i = 0; i < GLYPHS.length(); i++) {
            String g = String.valueOf(GLYPHS.charAt(i));
            c.drawText(g, x + 4, y, paint);
            float w = widths[i];
            uvs[i * 4] = (x + 4) / stripW;
            uvs[i * 4 + 1] = 0f;
            uvs[i * 4 + 2] = (x + 4 + w) / stripW;
            uvs[i * 4 + 3] = 1f;
            x += w + 8;
        }

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        texture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA, bmp, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        bmp.recycle();
    }

    public int textureId() { return texture; }

    /** Width of a string in units of "glyph height". */
    public float textWidth(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            int idx = GLYPHS.indexOf(s.charAt(i));
            if (idx >= 0) w += widths[idx] / rowH;
        }
        return w;
    }

    /**
     * Appends quads (NDC x/y + UV) for the text centered horizontally at
     * (cx, cy), with the given pixel height. Returns the vertex count added.
     */
    public int emit(FloatBuffer buf, String s, float cxNdc, float cyNdc, float hPx,
                    float viewW, float viewH) {
        float hNdc = 2f * hPx / viewH;
        float wScale = hPx / rowH; // px per glyph-height unit
        float totalW = 0;
        for (int i = 0; i < s.length(); i++) {
            int idx = GLYPHS.indexOf(s.charAt(i));
            if (idx >= 0) totalW += widths[idx];
        }
        float startX = cxNdc - (totalW * wScale) / viewW; // px -> NDC x
        float x = startX;
        int quads = 0;
        for (int i = 0; i < s.length(); i++) {
            int idx = GLYPHS.indexOf(s.charAt(i));
            if (idx < 0) continue;
            float wPx = widths[idx];
            float x0 = x, x1 = x + 2f * wPx * wScale / viewW;
            float y0 = cyNdc - hNdc * 0.5f, y1 = cyNdc + hNdc * 0.5f;
            float u0 = uvs[idx * 4], v0 = uvs[idx * 4 + 1];
            float u1 = uvs[idx * 4 + 2], v1 = uvs[idx * 4 + 3];
            buf.put(x0); buf.put(y0); buf.put(u0); buf.put(v1);
            buf.put(x1); buf.put(y0); buf.put(u1); buf.put(v1);
            buf.put(x1); buf.put(y1); buf.put(u1); buf.put(v0);
            buf.put(x0); buf.put(y0); buf.put(u0); buf.put(v1);
            buf.put(x1); buf.put(y1); buf.put(u1); buf.put(v0);
            buf.put(x0); buf.put(y1); buf.put(u0); buf.put(v0);
            x = x1;
            quads++;
        }
        return quads * 6;
    }
}
