package io.github.maharjanronish.jumpjump.core;

/** Events the game emits for the presentation layer (sound, particles, UI). */
public final class GameEvent {

    public enum Type {
        JUMP,                    // jump started (dist)
        MINIHOP,                 // hop within the same block
        LAND,                    // landed safely; gained points (perfect?)
        PERFECT,                 // dead-center landing (gained)
        SPECIAL_BONUS,           // stay bonus fired (kind, points)
        FALL,                    // started falling (reason)
        GAME_OVER;               // final score ready for the UI panel
    }

    public enum FallReason { SHORT_WALL, SHORT_GAP, OVERSHOOT, EDGE_TIP }

    public final Type type;
    public final int intArg;         // points / fall-reason ordinal
    public final int intArg2;        // block kind ordinal / combo count
    public final float x, y, z;      // world anchor (piece base / head)

    private GameEvent(Type type, int a, int b, float x, float y, float z) {
        this.type = type;
        this.intArg = a;
        this.intArg2 = b;
        this.x = x; this.y = y; this.z = z;
    }

    static GameEvent jump(float dist, float x, float y, float z) {
        return new GameEvent(Type.JUMP, 0, 0, x, y, z);
    }
    static GameEvent minihop(float x, float y, float z) {
        return new GameEvent(Type.MINIHOP, 0, 0, x, y, z);
    }
    static GameEvent land(int gained, float x, float y, float z) {
        return new GameEvent(Type.LAND, gained, 0, x, y, z);
    }
    static GameEvent perfect(int gained, int combo, float x, float y, float z) {
        return new GameEvent(Type.PERFECT, gained, combo, x, y, z);
    }
    static GameEvent specialBonus(Blocks.Kind kind, int pts, float x, float y, float z) {
        return new GameEvent(Type.SPECIAL_BONUS, pts, kind.ordinal(), x, y, z);
    }
    static GameEvent fall(FallReason r, float x, float y, float z) {
        return new GameEvent(Type.FALL, r.ordinal(), 0, x, y, z);
    }
    static GameEvent gameOver(int score) {
        return new GameEvent(Type.GAME_OVER, score, 0, 0, 0, 0);
    }

    public Blocks.Kind kind() { return Blocks.Kind.values()[intArg2]; }
    public FallReason reason() { return FallReason.values()[intArg]; }
}
