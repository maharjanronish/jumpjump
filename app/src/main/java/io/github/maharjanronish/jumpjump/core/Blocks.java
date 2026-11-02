package io.github.maharjanronish.jumpjump.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Block model + level generation.
 *
 * Blocks form a straight chain along the progression axis U. Each block stores
 * its center (top surface at y = 0) and its half extent along U. Lateral size
 * equals the along-U size (square or round footprint).
 */
public final class Blocks {

    public enum Kind {
        NORMAL, CYLINDER,      // plain pastel shapes
        MANHOLE,               // +5 stay bonus
        RUBIK,                 // +10 stay bonus
        STORE,                 // +15 stay bonus
        MUSICBOX,              // +30 stay bonus
        CLOCK;                 // decorative: shows the real local time

        public boolean isSpecial() {
            return this == MANHOLE || this == RUBIK || this == STORE || this == MUSICBOX;
        }

        public int bonus() {
            switch (this) {
                case MANHOLE:  return 5;
                case RUBIK:    return 10;
                case STORE:    return 15;
                case MUSICBOX: return 30;
                default:       return 0;
            }
        }
    }

    public static final class Block {
        public final Kind kind;
        /** Center of the top surface. */
        public final Vec3 center = new Vec3();
        /** Half width along U (for cylinders: radius). */
        public final float halfW;
        /** Pastel base color index (renderer resolves to RGB). */
        public final int colorIndex;
        /** Arc-length position along U from the world origin (monotonic). */
        public float s;
        /** 0..1 progress of the special-block animation (door open, spin...). */
        public float animT;
        /** Continuous spin angle (radians) for decorations that rotate. */
        public float spin;
        /** Landing settle animation (seconds remaining). */
        public float settleT;
        /** True once the stay bonus of a special block has been awarded. */
        public boolean bonusGiven;
        /** Seconds since the special block was activated (drives decor spin). */
        public float activatedT;

        Block(Kind kind, Vec3 center, float halfW, int colorIndex, float s) {
            this.kind = kind;
            this.center.set(center);
            this.halfW = halfW;
            this.colorIndex = colorIndex;
            this.s = s;
        }

        public void markBonusGiven() { bonusGiven = true; }

        public float nearEdgeS() { return s - halfW; }
        public float farEdgeS()  { return s + halfW; }
    }

    // ---------------------------------------------------------------- generation

    private final Random rng;
    private final List<Block> chain = new ArrayList<>(32);
    private float lastS;
    private Kind lastKind = Kind.NORMAL;
    private int index;

    public Blocks(long seed) {
        rng = new Random(seed);
    }

    public void reset() {
        chain.clear();
        lastS = 0;
        lastKind = Kind.NORMAL;
        index = 0;
        // First block sits at the origin under the piece; big and friendly.
        chain.add(new Block(Kind.NORMAL, new Vec3(0, 0, 0), 1.9f,
                rng.nextInt(Config.BLOCK_PASTELS.length), 0f));
        for (int i = 0; i < Config.KEEP_AHEAD; i++) spawnNext(0);
    }

    public List<Block> chain() { return chain; }

    public Block get(int i) { return chain.get(i); }

    /** Difficulty curve: widths shrink and gaps widen as the score climbs. */
    private static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0f, Math.min(1f, t)); }

    static float[] widthRange(int score) {
        float t = Math.min(1f, score / 120f);
        return new float[]{
                lerp(1.30f, 0.90f, t) * 2f,   // min width 2.60 -> 1.80
                lerp(1.90f, 1.40f, t) * 2f    // max width 3.80 -> 2.80
        };
    }

    static float[] gapRange(int score) {
        float t = Math.min(1f, score / 140f);
        return new float[]{
                lerp(3.4f, 4.0f, t),
                lerp(5.4f, 7.3f, t)
        };
    }

    /** Generates the next block (called by Game when advancing). */
    void spawnNext(int score) {
        index++;
        float[] widths = widthRange(score);
        float[] gaps = gapRange(score);

        float halfW = (lerp(widths[0], widths[1], rng.nextFloat())) * 0.5f;

        Kind kind = pickKind(score, halfW);

        // Center distance: never overlap, never out of reach.
        float cdMin = Math.max(gaps[0], chain.get(chain.size() - 1).halfW + halfW + 0.55f);
        float cdMax = Math.min(gaps[1], Config.JUMP_MAX_DIST * 0.90f);
        if (cdMax < cdMin) cdMax = cdMin;
        float cd = lerp(cdMin, cdMax, rng.nextFloat());

        // Intro blocks: friendly distances, but never overlapping.
        if (index <= Config.INTRO_BLOCKS) {
            float minCd = chain.get(chain.size() - 1).halfW + halfW + 0.55f;
            cd = Math.max(lerp(3.4f, 4.6f, rng.nextFloat()), minCd);
            halfW = Math.max(halfW, 1.30f);
        }

        push(kind, halfW, cd);
    }

    private Kind pickKind(int score, float halfW) {
        if (index <= Config.INTRO_BLOCKS) return Kind.NORMAL;

        // Cylinders only for plain blocks (never special) and never right after a special.
        if (lastKind == Kind.NORMAL) {
            float pCyl = lerp(Config.P_CYL_START, Config.P_CYL_MAX,
                    Math.min(1f, score / Config.CYL_SCORE_AT_MAX));
            if (rng.nextFloat() < pCyl) return Kind.CYLINDER;
        }

        // Specials never repeat back to back.
        if (!lastKind.isSpecial() && lastKind != Kind.CLOCK && rng.nextFloat() < Config.P_SPECIAL) {
            float w = rng.nextFloat();
            float acc = Config.W_MANHOLE;
            if (w < acc) return Kind.MANHOLE;
            acc += Config.W_RUBIK;
            if (w < acc) return Kind.RUBIK;
            acc += Config.W_STORE;
            if (w < acc) return Kind.STORE;
            acc += Config.W_MUSICBOX;
            if (w < acc) return Kind.MUSICBOX;
            return Kind.CLOCK;
        }
        return Kind.NORMAL;
    }

    private void push(Kind kind, float halfW, float cd) {
        Block prev = chain.get(chain.size() - 1);
        lastS = prev.s + cd;
        Vec3 c = new Vec3(
                prev.center.x + Config.U_X * cd,
                0,
                prev.center.z + Config.U_Z * cd);
        // Avoid same pastel twice in a row.
        int ci;
        do { ci = rng.nextInt(Config.BLOCK_PASTELS.length); } while (ci == prev.colorIndex);
        // Special blocks get fixed signature colors.
        switch (kind) {
            case MANHOLE:  ci = 7; break;   // grey blue
            case RUBIK:    ci = 1; break;   // mint
            case STORE:    ci = 2; break;   // cream
            case MUSICBOX: ci = 2; break;   // cream
            case CLOCK:    ci = 7; break;   // grey blue
            default: break;
        }
        chain.add(new Block(kind, c, halfW, ci, lastS));
        lastKind = kind;
        // Keep memory flat: drop blocks far behind.
        while (chain.size() > 64) chain.remove(0);
    }

    float lastS() { return lastS; }
}
