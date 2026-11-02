package io.github.maharjanronish.jumpjump.core;

/**
 * The player's "little person" (the black-and-ivory chess piece).
 * Pure state; the renderer builds the visual from this.
 */
public final class Piece {

    /** World position of the base (feet). */
    public final Vec3 pos = new Vec3();
    /** Squash & stretch: applied as scaleY / scaleXZ around the base. */
    public float squashY = 1f;
    public float widenXZ = 1f;
    /** Lean while wobbling on an edge (radians, visual). */
    public float wobble;
    /** Tumble rotation while falling (degrees around X, visual). */
    public float tumbleDeg;
    /** True while airborne. */
    public boolean airborne;
    /** True while game-over fall is playing. */
    public boolean dead;

    void reset(Vec3 start) {
        pos.set(start);
        squashY = 1f;
        widenXZ = 1f;
        wobble = 0f;
        tumbleDeg = 0f;
        airborne = false;
        dead = false;
    }

    /** Center of the head (world) - used for popups anchoring. */
    public float headY() { return pos.y + Config.PIECE_H_UNITS * squashY; }
}
