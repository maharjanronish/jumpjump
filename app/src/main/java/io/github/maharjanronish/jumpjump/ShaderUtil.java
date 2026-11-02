package io.github.maharjanronish.jumpjump;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Shader compilation + the three tiny programs used by the sprite renderer. */
public final class ShaderUtil {

    private ShaderUtil() {}

    static int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("shader compile error: " + GLES20.glGetShaderInfoLog(sh));
        }
        return sh;
    }

    static int link(String vs, String fs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("program link error: " + GLES20.glGetProgramInfoLog(p));
        }
        return p;
    }

    // ---------------------------------------------------------------- programs

    /**
     * Textured screen-space quads (block sprites, piece, shadow): NDC
     * position + UV + per-vertex RGBA tint. The texture holds STRAIGHT
     * alpha (uploaded via glTexImage2D from getPixels), blending is the
     * standard (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) set globally.
     */
    static final class TexProgram {
        final int id = link(
                "attribute vec2 aPos; attribute vec2 aUV; attribute vec4 aColor;"
                + "varying vec2 vUV; varying vec4 vColor;"
                + "void main(){ gl_Position = vec4(aPos, 0.0, 1.0); vUV = aUV; vColor = aColor; }",
                "precision mediump float;"
                + "uniform sampler2D uTex; varying vec2 vUV; varying vec4 vColor;"
                + "void main(){"
                + "  vec4 t = texture2D(uTex, vUV);"
                + "  gl_FragColor = vec4(t.rgb * vColor.rgb, t.a * vColor.a);"
                + "}");
        final int aPos = GLES20.glGetAttribLocation(id, "aPos");
        final int aUV = GLES20.glGetAttribLocation(id, "aUV");
        final int aColor = GLES20.glGetAttribLocation(id, "aColor");
        final int uTex = GLES20.glGetUniformLocation(id, "uTex");
    }

    /** Screen-space triangles with per-vertex color (background, dust, rings). */
    static final class SpriteProgram {
        final int id = link(
                "attribute vec2 aPos; attribute vec4 aColor; varying vec4 vColor;"
                + "void main(){ gl_Position = vec4(aPos, 0.0, 1.0); vColor = aColor; }",
                "precision mediump float; varying vec4 vColor;"
                + "void main(){ gl_FragColor = vColor; }");
        final int aPos = GLES20.glGetAttribLocation(id, "aPos");
        final int aColor = GLES20.glGetAttribLocation(id, "aColor");
    }

    /** Screen-space textured text quads (score number, +points popups). */
    static final class FontProgram {
        final int id = link(
                "attribute vec2 aPos; attribute vec2 aUV; uniform vec4 uColor; varying vec2 vUV;"
                + "void main(){ gl_Position = vec4(aPos, 0.0, 1.0); vUV = aUV; }",
                "precision mediump float; uniform sampler2D uTex; uniform vec4 uColor; varying vec2 vUV;"
                + "void main(){ gl_FragColor = vec4(uColor.rgb, uColor.a * texture2D(uTex, vUV).a); }");
        final int aPos = GLES20.glGetAttribLocation(id, "aPos");
        final int aUV = GLES20.glGetAttribLocation(id, "aUV");
        final int uColor = GLES20.glGetUniformLocation(id, "uColor");
        final int uTex = GLES20.glGetUniformLocation(id, "uTex");
    }

    /** Small dynamic vertex buffer helper (positions+colors / pos+uv+color). */
    static final class DynBuf {
        private FloatBuffer buf;

        FloatBuffer get(int floats) {
            int bytes = floats * 4;
            if (buf == null || buf.capacity() < bytes) {
                int cap = Math.max(1024, bytes * 2);
                buf = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder()).asFloatBuffer();
            }
            buf.clear();
            return buf;
        }
    }
}
