package io.github.maharjanronish.jumpjump.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Block model + level generation.
 *
 * The chain runs 3-6 blocks in one direction, then SWITCHES to the other
 * side (a "level switch"), like the original game's long straights that
 * bend the other way. Each switch also advances the background phase.
 *
 * Visuals are pre-rendered isometric sprites: plain blocks cycle through
 * five platform styles; cylinders, manholes, Rubik's cubes, shops and
 * record players are their own sprites.
 */
public final class Blocks {

    public enum Kind {
        NORMAL, CYLINDER,      // plain shapes (NORMAL cycles 5 sprite styles)
        MANHOLE,               // +5 stay bonus
        RUBIK,                 // +10 stay bonus
        STORE,                 // +15 stay bonus
        MUSICBOX;              // +30 stay bonus

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
        /** Sprite style for plain blocks (0..4); specials/cylinders ignore it. */
        public final int style;
        /** Center of the top surface (world). */
        public final Vec3 center = new Vec3();
        /** Half width of the footprint (for cylinders: radius). */
        public final float halfW;
        /** Unit direction (world XZ) from this block to the next one. */
        public float toNextX, toNextZ;
        /** Arc-length center position (cumulative center-to-center distance). */
        public float s;
        /** Landing settle animation (seconds remaining). */
        public float settleT;
        /** Drop-in animation for newly appeared blocks (seconds remaining). */
        public float spawnT;
        /** True once the stay bonus of a special block has been awarded. */
        public boolean bonusGiven;

        Block(Kind kind, int style, Vec3 center, float halfW, float s) {
            this.kind = kind;
            this.style = style;
            this.center.set(center);
            this.halfW = halfW;
            this.s = s;
        }

        public void markBonusGiven() { bonusGiven = true; }

        /** True if the footprint is a circle (cylinder) rather than a square. */
        public boolean round() { return kind == Kind.CYLINDER; }

        /**
         * Point-in-footprint test in world XZ. Margin shrinks the footprint
         * when negative (safe landing) and grows it when positive (edge zone).
         */
        public boolean contains(float x, float z, float margin) {
            float dx = x - center.x, dz = z - center.z;
            float r = halfW + margin;
            if (round()) return dx * dx + dz * dz <= r * r;
            return Math.abs(dx) <= r && Math.abs(dz) <= r;
        }

        /** Squared distance from the footprint center (world XZ). */
        public float dist2(float x, float z) {
            float dx = x - center.x, dz = z - center.z;
            return dx * dx + dz * dz;
        }
    }

    // ---------------------------------------------------------------- generation

    private final Random rng;
    private final List<Block> chain = new ArrayList<>(32);
    private Kind lastKind = Kind.NORMAL;
    private int lastStyle = 0;
    private int index;
    /** +1 = the lane goes to the viewer's right; flips on a direction switch. */
    private int side = 1;
    /** Blocks left in the current same-direction run. */
    private int runLeft;
    /** Increments on every direction switch (drives the background phase). */
    private int phase;

    public Blocks(long seed) {
        rng = new Random(seed);
    }

    public void reset() {
        chain.clear();
        lastKind = Kind.NORMAL;
        lastStyle = 0;
        index = 0;
        side = 1;
        runLeft = runLen();
        phase = 0;
        // First block sits at the origin under the piece; big and friendly.
        chain.add(new Block(Kind.NORMAL, 0, new Vec3(0, 0, 0), 1.6f, 0f));
        for (int i = 0; i < Config.KEEP_AHEAD; i++) spawnNext(0);
        // The very first target must be immediately on screen (no drop-in);
        // the block after it drops in as a gentle intro flourish.
        chain.get(1).spawnT = 0f;
    }

    public List<Block> chain() { return chain; }

    public Block get(int i) { return chain.get(i); }

    /** Current direction phase; changes exactly when the lane switches. */
    public int phase() { return phase; }

    private int runLen() {
        return Config.RUN_LEN_MIN + rng.nextInt(Config.RUN_LEN_MAX - Config.RUN_LEN_MIN + 1);
    }

    /** Difficulty curve: widths shrink and gaps widen as the score climbs. */
    private static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0f, Math.min(1f, t)); }

    static float[] widthRange(int score) {
        float t = Math.min(1f, score / 120f);
        return new float[]{
                lerp(1.42f, 0.86f, t) * 2f,   // min width 2.84 -> 1.72
                lerp(1.86f, 1.28f, t) * 2f    // max width 3.72 -> 2.56
        };
    }

    /** AIR gap between the block footprints, measured along the lane. */
    static float[] airRange(int score) {
        float t = Math.min(1f, score / 130f);
        return new float[]{
                lerp(1.05f, 2.25f, t),
                lerp(1.75f, 3.15f, t)
        };
    }

    /** Generates the next block (called by Game when advancing). */
    void spawnNext(int score) {
        index++;
        float[] widths = widthRange(score);
        float[] air = airRange(score);

        float halfW = (lerp(widths[0], widths[1], rng.nextFloat())) * 0.5f;
        // Intro blocks stay generous.
        if (index <= Config.INTRO_BLOCKS) halfW = Math.max(halfW, 1.40f);

        Kind kind = pickKind(score);
        int style = pickStyle();

        // Center distance: block footprints PLUS a real air gap along the
        // lane (square tops stretch by 1/cos(laneAngle) along the lane, so
        // widths are converted to lane distance first). Never out of reach.
        Block prev = chain.get(chain.size() - 1);
        float reach = (prev.halfW + halfW) / Config.LANE_COS;
        float cdMin = reach + air[0];
        float cdMax = Math.min(reach + air[1], Config.JUMP_MAX_DIST * 0.92f);
        if (cdMax < cdMin) cdMax = cdMin;
        float cd = lerp(cdMin, cdMax, rng.nextFloat());

        // Intro blocks: friendly distances, but with a real gap to clear.
        if (index <= Config.INTRO_BLOCKS) {
            cd = reach + lerp(0.90f, 1.20f, rng.nextFloat());
        }

        push(kind, style, halfW, cd);
    }

    private Kind pickKind(int score) {
        if (index <= Config.INTRO_BLOCKS) return Kind.NORMAL;

        // Cylinders only for plain blocks (never special) and never right after a special.
        if (lastKind == Kind.NORMAL) {
            float pCyl = lerp(Config.P_CYL_START, Config.P_CYL_MAX,
                    Math.min(1f, score / Config.CYL_SCORE_AT_MAX));
            if (rng.nextFloat() < pCyl) return Kind.CYLINDER;
        }

        // Specials never repeat back to back.
        if (!lastKind.isSpecial() && rng.nextFloat() < Config.P_SPECIAL) {
            float w = rng.nextFloat();
            float acc = Config.W_MANHOLE;
            if (w < acc) return Kind.MANHOLE;
            acc += Config.W_RUBIK;
            if (w < acc) return Kind.RUBIK;
            acc += Config.W_STORE;
            if (w < acc) return Kind.STORE;
            return Kind.MUSICBOX;
        }
        return Kind.NORMAL;
    }

    /** Plain blocks rotate through the five platform sprites (no repeats). */
    private int pickStyle() {
        int s = rng.nextInt(5);
        if (s == lastStyle) s = (s + 1 + rng.nextInt(4)) % 5;
        return s;
    }

    private void push(Kind kind, int style, float halfW, float cd) {
        Block prev = chain.get(chain.size() - 1);

        // Direction RUNS: keep the lane for a few blocks, then switch side
        // (and advance the background phase) - the "level switch".
        if (runLeft <= 0) {
            side = -side;
            phase++;
            runLeft = runLen();
        } else {
            runLeft--;
        }

        float ang = (float) Math.toRadians(Config.LANE_ANGLE_BASE
                + (rng.nextFloat() * 2f - 1f) * Config.LANE_ANGLE_JITTER);
        // Screen-right is world -X for this camera, hence "-side".
        float dirX = -side * (float) Math.sin(ang);
        float dirZ = (float) Math.cos(ang);

        Vec3 c = new Vec3(prev.center.x + dirX * cd, 0, prev.center.z + dirZ * cd);
        prev.toNextX = dirX;
        prev.toNextZ = dirZ;

        Block b = new Block(kind, style, c, halfW, prev.s + cd);
        b.spawnT = Config.SPAWN_T;
        chain.add(b);
        lastKind = kind;
        lastStyle = style;
        // Keep memory flat: drop blocks far behind.
        while (chain.size() > 64) chain.remove(0);
    }
}
