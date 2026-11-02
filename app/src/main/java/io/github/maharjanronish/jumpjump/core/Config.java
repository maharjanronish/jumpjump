package io.github.maharjanronish.jumpjump.core;

/**
 * Every tunable constant of the game, in one place.
 *
 * Design notes (matched to the original WeChat "Jump Jump" / 跳一跳):
 *  - The world is a straight line of blocks along a fixed diagonal direction U,
 *    seen from a high-angle (~38 deg on screen) perspective camera.
 *  - Charge time maps LINEARLY to jump distance (confirmed by the famous
 *    open-source auto-player bot wangshub/wechat_jump_game, ~1.393 ms per px).
 *  - Scoring: +1 per block, dead-center landing = 2,4,6,8... (+2 per consecutive,
 *    capped at 32), special blocks give a stay bonus after ~2 s.
 */
public final class Config {

    private Config() {}

    // ------------------------------------------------------------------ world
    /** Progression direction (unit). Screen movement appears as an up-right diagonal. */
    public static final float U_X = -0.47f, U_Y = 0f, U_Z = 0.88f;

    /** Height of block bodies below the shared top surface (y = 0). */
    public static final float BLOCK_BODY_DEPTH = 2.0f;
    /** Extra decorative top thickness that is safe to stand on visually. */
    public static final float TOP_SURFACE_Y = 0.04f;

    // ------------------------------------------------------------------ camera
    // Elevation ~44 deg, distance ~24 world units (≈ 8 early-block widths),
    // matching the proportions of the original and its open-source 3D clones.
    public static final float CAM_EYE_Y   = 16.7f;
    public static final float CAM_EYE_Z   = -17.3f;
    /** Vertical FOV (degrees). */
    public static final float CAM_FOV_Y   = 45f;
    public static final float CAM_NEAR   = 1.0f;
    public static final float CAM_FAR    = 70f;
    /** Camera looks this much ahead of the current block along U (scaled by gap). */
    public static final float LOOK_AHEAD_MIN = 1.4f;
    public static final float LOOK_AHEAD_FRACTION = 0.42f;
    public static final float LOOK_AHEAD_MAX = 3.4f;
    /** Subtle zoom-out when the next block is far (1 = none). */
    public static final float CAM_DIST_SCALE_MAX = 1.10f;
    /** Exponential focus lerp rate per second. */
    public static final float CAM_LERP_RATE = 6.0f;
    /** The look target sits this far ABOVE the block tops, seating the current
     *  block in the lower third of the screen like the original. */
    public static final float CAM_TARGET_RISE_Y = 4.0f;

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
    /** Safe inset from the block edge (fraction of half width). */
    public static final float EDGE_INSET     = 0.06f;
    /** |offset| beyond (halfW + this) counts as clean miss (no teeter). */
    public static final float EDGE_TOLERANCE = 0.18f;
    /** Within this distance of the near edge, the piece smashes into the wall. */
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
    public static final int    KEEP_BEHIND   = 1;
    public static final int    KEEP_AHEAD    = 3;
    /** First N blocks are plain, close and generous (on-boarding like original). */
    public static final int    INTRO_BLOCKS  = 3;

    // Special block spawn (per new block, after the intro, never twice in a row)
    public static final float P_SPECIAL      = 0.085f;
    public static final float W_MANHOLE      = 0.34f;
    public static final float W_RUBIK        = 0.26f;
    public static final float W_STORE        = 0.21f;
    public static final float W_MUSICBOX     = 0.15f;
    public static final float W_CLOCK        = 0.04f;   // decorative, no bonus

    /** Cylinders appear as score rises: probability ramp. */
    public static final float P_CYL_START   = 0.06f;
    public static final float P_CYL_MAX     = 0.26f;
    public static final float CYL_SCORE_AT_MAX = 90f;
    public static final float CYL_R_MIN     = 0.60f;
    public static final float CYL_R_MAX     = 0.95f;

    // ------------------------------------------------------------------ piece visuals
    public static final float PIECE_HEIGHT     = 1.80f;
    public static final float PIECE_BASE_R     = 0.36f;
    public static final float PIECE_TOP_R      = 0.22f;
    public static final float PIECE_HEAD_R     = 0.26f;
    public static final float PIECE_CHARGE_SQUASH  = 0.30f;
    public static final float PIECE_CHARGE_WIDEN   = 0.20f;
    public static final float PIECE_LAND_SQUASH    = 0.24f;
    public static final float PIECE_LAND_T         = 0.16f;

    // ------------------------------------------------------------------ palette
    // Background: soft vertical gradient, pale lavender like the original.
    public static final float[] BG_TOP    = {0.937f, 0.918f, 0.957f};   // #EFEAF4
    public static final float[] BG_BOTTOM = {0.973f, 0.961f, 0.980f};   // #F8F5FA
    public static final float[] SCORE_INK = {0.337f, 0.314f, 0.290f};   // #56504A

    // Rotating pastel block colors (top face shows these; shading darkens sides).
    public static final float[][] BLOCK_PASTELS = {
            {0.953f, 0.851f, 0.867f},  // blush      #F3D9DD
            {0.812f, 0.910f, 0.855f},  // mint       #CFE8DA
            {0.961f, 0.918f, 0.843f},  // cream      #F5EAD7
            {0.796f, 0.863f, 0.925f},  // powder blue#CBDCEC
            {0.890f, 0.839f, 0.925f},  // lilac      #E3D6EC
            {0.949f, 0.890f, 0.784f},  // butter     #F2E3C8
            {0.918f, 0.835f, 0.769f},  // sand       #EAD5C4
            {0.851f, 0.878f, 0.906f},  // grey blue  #D9E0E7
            {0.941f, 0.847f, 0.788f},  // peach      #F0D8C9
            {0.839f, 0.898f, 0.824f},  // sage       #D6E5D2
    };

    // Character: matte black body, ivory head (the iconic look).
    public static final float[] PIECE_BODY = {0.196f, 0.192f, 0.212f};  // #323136
    public static final float[] PIECE_HEAD = {0.965f, 0.945f, 0.894f};  // #F6F1E4
    public static final float[] SHADOW     = {0.29f, 0.27f, 0.33f};     // w/ alpha
    public static final float SHADOW_ALPHA = 0.30f;
    public static final float[] GAUGE      = {0.32f, 0.27f, 0.38f};     // charge disc
    public static final float GAUGE_ALPHA  = 0.36f;

    // Special block palettes (hex in comments).
    public static final float[] MANHOLE_COVER = {0.431f, 0.447f, 0.471f}; // #6E7278
    public static final float[] MANHOLE_RING  = {0.333f, 0.345f, 0.369f}; // #55585E
    public static final float[] RUBIK_WHITE   = {0.960f, 0.960f, 0.941f}; // #F5F5F0
    public static final float[] RUBIK_YELLOW  = {0.941f, 0.820f, 0.235f}; // #F0D13C
    public static final float[] RUBIK_RED     = {0.847f, 0.267f, 0.235f}; // #D8443C
    public static final float[] RUBIK_ORANGE  = {0.933f, 0.541f, 0.227f}; // #EE8A3A
    public static final float[] RUBIK_BLUE    = {0.235f, 0.431f, 0.847f}; // #3C6ED8
    public static final float[] RUBIK_GREEN   = {0.298f, 0.722f, 0.416f}; // #4CB86A
    public static final float[] STORE_AWNING = {0.306f, 0.620f, 0.561f};  // #4E9E8F
    public static final float[] STORE_DOOR   = {0.541f, 0.435f, 0.322f};  // #8A6F52
    public static final float[] STORE_WINDOW  = {1.000f, 0.914f, 0.722f}; // #FFE9B8 lit
    public static final float[] STORE_SIGN    = {0.243f, 0.431f, 0.557f}; // #3E6E8E
    public static final float[] MUSIC_WOOD    = {0.604f, 0.482f, 0.353f}; // #9A7B5A
    public static final float[] MUSIC_VINYL   = {0.180f, 0.169f, 0.200f}; // #2E2B33
    public static final float[] MUSIC_LABEL   = {0.847f, 0.267f, 0.235f}; // #D8443C
    public static final float[] MUSIC_BRASS   = {0.851f, 0.663f, 0.306f}; // #D9A94E
    public static final float[] CLOCK_FACE    = {0.965f, 0.960f, 0.945f}; // #F6F5F1
    public static final float[] CLOCK_HAND    = {0.25f, 0.24f, 0.26f};
    public static final float[] CREAM         = {0.961f, 0.918f, 0.843f}; // #F5EAD7
    public static final float[] GOLD          = {0.92f, 0.72f, 0.25f};    // bonus text

    // ------------------------------------------------------------------ particles / popups
    public static final float POPUP_T       = 0.85f;
    public static final float RING_T        = 0.5f;
    public static final float DUST_T        = 0.45f;
    public static final int   DUST_COUNT    = 7;
}
