package io.github.maharjanronish.jumpjump.core;

/**
 * Every tunable constant of the game, in one place.
 *
 * Design notes (matched to the original WeChat "Jump Jump" / 跳一跳):
 *  - The world is a flat top-down plane rendered as 2.5D isometric SPRITES
 *    (pre-rendered block art), exactly like the official game: a fixed
 *    camera angle, no per-lane rotation, smooth follow-pan.
 *  - The chain runs several blocks in ONE direction, then switches
 *    ("level switch"): every 3-6 blocks the lane flips left/right and the
 *    background tint glides to the next warm shade.
 *  - Charge time maps LINEARLY to jump distance (confirmed by the famous
 *    open-source auto-player bot wangshub/wechat_jump_game, ~1.393 ms/px).
 *  - Scoring: +1 per block, dead-center landing = 2,4,6,8... (+2 per
 *    consecutive, capped at 32), special blocks give a stay bonus after ~2 s.
 */
public final class Config {

    private Config() {}

    // ------------------------------------------------------------------ projection (2.5D iso)
    /** Vertical compression of the ground plane on screen (iso look). */
    public static final float RHO          = 0.62f;
    /** Screen rise factor for height above the plane (jumps). */
    public static final float RISE_K       = 0.80f;
    /** Px per world unit: fraction of the view WIDTH (fallback). */
    public static final float SCALE_W      = 0.145f;
    /** Px per world unit: never more than this fraction of view HEIGHT. */
    public static final float SCALE_H_CAP  = 0.068f;
    /** Screen row (0..1) where the camera focus is centered. */
    public static final float CAM_Y_FRAC   = 0.58f;
    /** Focus rides ahead of the piece toward the next block by this much. */
    public static final float FOCUS_AHEAD  = 0.30f;
    /** Exponential focus lerp rate per second. */
    public static final float CAM_LERP_RATE = 7.0f;

    // ------------------------------------------------------------------ lanes
    /** Angle of the lane off the straight-ahead axis (degrees).
     *  ~25 deg reads as ~38 deg on screen (RHO compresses z) like the
     *  official game's up-left / up-right jumps. */
    public static final float LANE_ANGLE_BASE   = 25f;
    public static final float LANE_ANGLE_JITTER = 6f;
    /** Cosine of the base lane angle (footprint widths -> lane distance). */
    public static final float LANE_COS = (float) Math.cos(Math.toRadians(25f));
    /** Direction switches only every RUN_LEN_MIN..RUN_LEN_MAX blocks. */
    public static final int   RUN_LEN_MIN = 3;
    public static final int   RUN_LEN_MAX = 6;

    // ------------------------------------------------------------------ jump physics
    /** Charge duration (seconds) that maps to the maximum jump distance. */
    public static final float CHARGE_MAX_T   = 1.15f;
    /** Minimum hold (s) counted; below this a small hop is still performed. */
    public static final float CHARGE_MIN_T   = 0.05f;
    public static final float JUMP_MIN_DIST  = 1.10f;
    public static final float JUMP_MAX_DIST  = 8.20f;
    /** Flight time and arc height grow mildly with distance (natural feel). */
    public static final float FLIGHT_T_BASE  = 0.46f;
    public static final float FLIGHT_T_PER_D = 0.030f;
    public static final float ARC_H_BASE     = 1.75f;
    public static final float ARC_H_PER_D    = 0.150f;
    /** Falling animation. */
    public static final float GRAVITY        = 26f;
    public static final float FALL_KILL_Y    = -6.5f;
    public static final float OVER_DELAY     = 0.45f;

    // ------------------------------------------------------------------ landing zones
    /** Safe inset from the block edge (world units). */
    public static final float EDGE_INSET     = 0.06f;
    /** |offset| beyond the footprint + this counts as a clean miss (no teeter). */
    public static final float EDGE_TOLERANCE = 0.18f;
    /** Within this distance of the near face, the piece smacks into the wall. */
    public static final float WALL_HIT_RANGE = 0.60f;
    /** Perfect (dead-center) radius: min(absolute, fraction of half width). */
    public static final float PERFECT_R_ABS  = 0.30f;
    public static final float PERFECT_R_FRAC = 0.16f;
    /** Landing offset (fraction of halfW) beyond which the piece wobbles. */
    public static final float WOBBLE_FRAC    = 0.62f;
    public static final float WOBBLE_TIME    = 0.55f;

    // ------------------------------------------------------------------ scoring
    public static final int  SCORE_NORMAL    = 1;
    /** consecutive perfect: 2,4,6,8... capped */
    public static final int  COMBO_CAP       = 32;
    /** Seconds of standing still on a special block before the bonus triggers. */
    public static final float SPECIAL_STAY_T = 1.9f;
    public static final float SPECIAL_ANIM_T = 0.85f;

    // ------------------------------------------------------------------ level generation
    /** Blocks kept visible behind / ahead of the current one (incl.). */
    public static final int    KEEP_BEHIND   = 1;
    public static final int    KEEP_AHEAD    = 1;
    /** First N blocks are plain, close and generous (on-boarding like original). */
    public static final int    INTRO_BLOCKS  = 2;
    /** The NEW TARGET drops in from above right after each landing (original feel). */
    public static final float SPAWN_DROP_H  = 4.5f;
    public static final float SPAWN_T       = 0.30f;

    // Special block spawn (per new block, after the intro, never twice in a row)
    public static final float P_SPECIAL      = 0.10f;
    public static final float W_MANHOLE      = 0.34f;
    public static final float W_RUBIK        = 0.26f;
    public static final float W_STORE        = 0.21f;
    public static final float W_MUSICBOX     = 0.19f;

    /** Cylinders appear as score rises: probability ramp. */
    public static final float P_CYL_START   = 0.10f;
    public static final float P_CYL_MAX     = 0.34f;
    public static final float CYL_SCORE_AT_MAX = 60f;
    public static final float CYL_R_MIN     = 0.60f;
    public static final float CYL_R_MAX     = 0.95f;

    // ------------------------------------------------------------------ charging visuals
    /** The current block squashes down together with the piece (original feel). */
    public static final float BLOCK_CHARGE_SQUASH = 0.15f;

    // ------------------------------------------------------------------ piece visuals
    /** Piece sprite size in world units (width x height). */
    public static final float PIECE_W_UNITS = 1.10f;
    public static final float PIECE_H_UNITS = 1.85f;
    public static final float PIECE_CHARGE_SQUASH  = 0.30f;
    public static final float PIECE_CHARGE_WIDEN   = 0.20f;
    public static final float PIECE_LAND_SQUASH    = 0.24f;
    public static final float PIECE_LAND_T         = 0.16f;
    /** Max screen tilt of the piece while wobbling / falling (degrees). */
    public static final float PIECE_TILT_MAX = 24f;

    // ------------------------------------------------------------------ palette
    /**
     * Warm light background gradients, one per "phase"; the phase advances
     * on every direction switch, and the colors glide over ~1 s.
     * Each row is {topR,topG,topB, bottomR,bottomG,bottomB} in 0..1.
     */
    public static final float[][] BG_PHASES = {
            {0.980f, 0.953f, 0.890f,  0.992f, 0.980f, 0.941f},  // cream
            {0.980f, 0.914f, 0.878f,  0.992f, 0.969f, 0.933f},  // blush
            {0.976f, 0.941f, 0.843f,  0.992f, 0.984f, 0.941f},  // butter
            {0.945f, 0.941f, 0.867f,  0.976f, 0.973f, 0.925f},  // sage
            {0.984f, 0.914f, 0.859f,  0.992f, 0.969f, 0.941f},  // peach
            {0.953f, 0.949f, 0.902f,  0.973f,  0.973f, 0.949f}, // warm grey
    };
    /** Background glide speed (exponential, per second). */
    public static final float BG_LERP_RATE = 2.2f;

    /** Ink for the big score / combo text (warm charcoal). */
    public static final float[] SCORE_INK = {0.29f, 0.26f, 0.22f};   // #4A4238
    /** Perfect-zone marker drawn at the center of the TARGET block. */
    public static final float[] MARKER_INK = {0.38f, 0.33f, 0.28f};
    public static final float MARKER_ALPHA = 0.36f;
    /** Piece blob shadow tint (RGB) + base alpha. */
    public static final float[] SHADOW_RGB  = {0.28f, 0.25f, 0.30f};
    public static final float SHADOW_ALPHA  = 0.30f;
    /** Gold for bonus popups. */
    public static final float[] GOLD = {0.92f, 0.72f, 0.25f};

    // ------------------------------------------------------------------ particles / popups
    public static final float POPUP_T       = 0.85f;
    /** Small expanding ring (special bonus only; subtle like the original). */
    public static final float RING_T        = 0.45f;
    public static final float RING_R0       = 0.15f;
    public static final float RING_R1       = 0.85f;
    /** Landing particles: world-space, gravity + one bounce (like the original). */
    public static final float PARTICLE_T    = 0.85f;
    public static final float PARTICLE_G    = 11.0f;
    public static final float PARTICLE_BOUNCE = 0.38f;
    public static final int   DUST_LAND     = 7;
    public static final int   DUST_MINIHOP  = 3;
    public static final int   DUST_PERFECT  = 9;
    public static final int   SPARK_PERFECT = 6;
    public static final int   DUST_SPECIAL  = 12;
}
