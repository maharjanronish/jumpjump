package io.github.maharjanronish.jumpjump;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Shader compilation + the four tiny programs used by the renderer. */
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

    /** Lit geometry: blocks, piece, decorations. */
    static final class MeshProgram {
        final int id = link(
                "attribute vec3 aPos; attribute vec3 aNormal;"
                + "uniform mat4 uMVP; uniform mat4 uM;"
                + "varying vec3 vN; varying vec3 vWorld;"
                + "void main(){"
                + "  gl_Position = uMVP * vec4(aPos, 1.0);"
                + "  vN = mat3(uM[0].xyz, uM[1].xyz, uM[2].xyz) * aNormal;"
                + "  vWorld = (uM * vec4(aPos, 1.0)).xyz;"
                + "}",
                "precision mediump float;"
                + "uniform vec4 uColor; uniform vec3 uLight;"
                + "varying vec3 vN; varying vec3 vWorld;"
                + "void main(){"
                + "  vec3 n = normalize(vN);"
                + "  float d = max(dot(n, uLight), 0.0);"
                + "  float f = max(dot(n, normalize(vec3(0.55, 0.2, 0.35))), 0.0) * 0.18;"
                + "  float grad = clamp((vWorld.y + 2.6) / 3.2, 0.0, 1.0);"
                + "  vec3 c = uColor.rgb * (0.55 + 0.45 * d + f) * (0.88 + 0.12 * grad);"
                + "  gl_FragColor = vec4(c, uColor.a);"
                + "}");
        final int aPos = GLES20.glGetAttribLocation(id, "aPos");
        final int aNormal = GLES20.glGetAttribLocation(id, "aNormal");
        final int uMVP = GLES20.glGetUniformLocation(id, "uMVP");
        final int uM = GLES20.glGetUniformLocation(id, "uM");
        final int uColor = GLES20.glGetUniformLocation(id, "uColor");
        final int uLight = GLES20.glGetUniformLocation(id, "uLight");
    }

    /** Unlit world-space triangles with per-draw color (shadows, gauges, rings). */
    static final class Flat3DProgram {
        final int id = link(
                "attribute vec3 aPos; uniform mat4 uMVP;"
                + "void main(){ gl_Position = uMVP * vec4(aPos, 1.0); }",
                "precision mediump float; uniform vec4 uColor;"
                + "void main(){ gl_FragColor = uColor; }");
        final int aPos = GLES20.glGetAttribLocation(id, "aPos");
        final int uMVP = GLES20.glGetUniformLocation(id, "uMVP");
        final int uColor = GLES20.glGetUniformLocation(id, "uColor");
    }

    /** Screen-space triangles with per-vertex color (background, dust). */
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

    /** Small dynamic vertex buffer helper (positions+colors / positions+uv). */
    static final class DynBuf {
        private FloatBuffer buf;
        private float[] tmp = new float[64];

        FloatBuffer get(int floats) {
            int bytes = floats * 4;
            if (buf == null || buf.capacity() < bytes) {
                int cap = Math.max(1024, bytes * 2);
                buf = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder()).asFloatBuffer();
            }
            buf.clear();
            return buf;
        }

        void put(FloatBuffer b, float... v) {
            if (v.length > tmp.length) tmp = new float[v.length];
            b.put(v, 0, v.length);
        }
    }
}
