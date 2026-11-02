package io.github.maharjanronish.jumpjump.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete game logic: a pure-Java state machine with zero platform
 * dependencies, so the exact same rules run in desktop tests and on Android.
 *
 * Rules replicated from the original WeChat "Jump Jump":
 *   - hold to charge, release to jump; distance grows linearly with hold time
 *   - +1 for a normal landing
 *   - dead-center landing: 2, 4, 6, 8, ... points (+2 per consecutive), cap 32
 *   - stay ~2 s on manhole / Rubik / store / music box: +5 / +10 / +15 / +30
 *   - blocks shrink and gaps widen as the score climbs
 *   - tiny hops within the same block are allowed (adjustment, no points)
 *   - missing the block ends the run
 */
public final class Game {

    public enum State { READY, CHARGING, FLYING, LANDED, SPECIAL_WAIT, SPECIAL_ANIM, FALLING, OVER }

    public interface Listener {
        void onEvent(GameEvent e);
    }

    private final Blocks blocks;
    private final Piece piece = new Piece();
    private final List<GameEvent> events = new ArrayList<>(16);

    private State state = State.READY;
    private int score;
    private int combo;                 // consecutive perfect landings
    private float totalTime;

    // charging
    private float chargeT;
    // flight
    private float pieceS;              // arc coordinate of the piece base
    private float fromS, jumpD, flightT, flightTTotal, arcH;
    // landing settle
    private float landT;
    // special stay
    private float waitT, animT;
    private boolean pendingSpecial;
    // falling
    private float vy, vAlongU, teeterT, tumbleRate, overDelay;
    private GameEvent.FallReason fallReason;
    private boolean bounceDone;

    private Blocks.Block cur, next;
    /** Smoothed camera focus (world). */
    private final Vec3 camFocus = new Vec3();

    public Game(long seed) {
        blocks = new Blocks(seed);
        reset(seed);
    }

    // ------------------------------------------------------------------ lifecycle

    public void reset(long seed) {
        blocks.reset();
        score = 0;
        combo = 0;
        events.clear();
        cur = blocks.get(0);
        next = blocks.get(1);
        pieceS = cur.s;
        piece.reset(new Vec3(0, 0, 0));
        camFocus.set(cur.center);
        state = State.READY;
        chargeT = 0; flightT = 0; landT = 0; waitT = 0; animT = 0;
        pendingSpecial = false; overDelay = 0;
        ensureAhead();
    }

    private void ensureAhead() {
        List<Blocks.Block> c = blocks.chain();
        int idx = c.indexOf(cur);
        // keep at least KEEP_AHEAD + 1 blocks queued after the current one
        while (idx < 0 || c.size() - idx < Config.KEEP_AHEAD + 2) {
            blocks.spawnNext(score);
            idx = c.indexOf(cur);
        }
    }

    // ------------------------------------------------------------------ input

    /** Finger down. Returns false if the press is ignored in this state. */
    public boolean press() {
        switch (state) {
            case READY:
            case LANDED:
                state = State.CHARGING;
                chargeT = 0;
                return true;
            case SPECIAL_WAIT:
                // Jumping away forfeits the stay bonus (as in the original).
                state = State.CHARGING;
                chargeT = 0;
                return true;
            default:
                return false;
        }
    }

    /** Finger up: performs the jump. Returns false if ignored. */
    public boolean release() {
        if (state != State.CHARGING) return false;
        float hold = Math.max(chargeT, Config.CHARGE_MIN_T);
        float power = Math.min(hold, Config.CHARGE_MAX_T) / Config.CHARGE_MAX_T;
        jumpD = Config.JUMP_MIN_DIST + power * (Config.JUMP_MAX_DIST - Config.JUMP_MIN_DIST);
        fromS = pieceS;
        flightT = 0;
        flightTTotal = Config.FLIGHT_T_BASE + Config.FLIGHT_T_PER_D * jumpD;
        arcH = Config.ARC_H_BASE + Config.ARC_H_PER_D * jumpD;
        state = State.FLYING;
        piece.airborne = true;
        emit(GameEvent.jump(jumpD, piece.pos.x, piece.pos.y, piece.pos.z));
        return true;
    }

    // ------------------------------------------------------------------ main update

    public void update(float dt) {
        dt = Math.min(dt, 0.05f);
        totalTime += dt;

        // camera focus: during flight it drifts toward the target so the arc
        // is followed smoothly, then settles on the new current block
        float goalX = cur.center.x, goalZ = cur.center.z;
        if (state == State.FLYING) {
            float k = Math.min(1f, flightT / flightTTotal);
            goalX += Config.U_X * jumpD * 0.45f * k;
            goalZ += Config.U_Z * jumpD * 0.45f * k;
        }
        float a = 1f - (float) Math.exp(-Config.CAM_LERP_RATE * dt);
        camFocus.x += (goalX - camFocus.x) * a;
        camFocus.z += (goalZ - camFocus.z) * a;

        // block settle animations
        for (Blocks.Block b : visible()) {
            if (b.settleT > 0) b.settleT = Math.max(0, b.settleT - dt);
        }

        switch (state) {
            case READY: {
                piece.squashY = 1f + 0.018f * (float) Math.sin(totalTime * 2.6f);
                piece.widenXZ = 1f;
                piece.wobble *= Math.max(0, 1 - dt * 5);
                break;
            }
            case CHARGING: {
                chargeT += dt;
                float power = power();
                piece.squashY = 1f - Config.PIECE_CHARGE_SQUASH * power;
                piece.widenXZ = 1f + Config.PIECE_CHARGE_WIDEN * power;
                break;
            }
            case FLYING: {
                flightT += dt;
                float k = Math.min(1f, flightT / flightTTotal);
                float s = fromS + jumpD * k;
                setPieceAt(s, arcH * 4 * k * (1 - k));
                piece.squashY = 1f + 0.10f * (float) Math.sin(Math.PI * k);
                piece.widenXZ = 1f;
                if (k >= 1f) resolveLanding();
                break;
            }
            case LANDED: {
                landT += dt;
                float k2 = Math.min(1f, landT / Config.PIECE_LAND_T);
                float e = (1 - k2) * (1 - k2);
                piece.squashY = 1f - Config.PIECE_LAND_SQUASH * e;
                piece.widenXZ = 1f + Config.PIECE_LAND_SQUASH * 0.7f * e;
                piece.wobble *= Math.max(0, 1 - dt * 4);
                if (k2 >= 1f) {
                    if (pendingSpecial) { state = State.SPECIAL_WAIT; waitT = 0; }
                    else state = State.READY;
                }
                break;
            }
            case SPECIAL_WAIT: {
                waitT += dt;
                piece.squashY = 1f;
                if (waitT >= Config.SPECIAL_STAY_T) triggerSpecial();
                break;
            }
            case SPECIAL_ANIM: {
                animT += dt;
                cur.animT = Math.min(1f, animT / Config.SPECIAL_ANIM_T);
                updateSpecialDecor(dt);
                piece.squashY = 1f;
                if (animT >= Config.SPECIAL_ANIM_T) state = State.READY;
                break;
            }
            case FALLING: {
                if (teeterT > 0) {
                    teeterT -= dt;
                    piece.wobble = (float) (Math.sin(teeterT * 16f) * 0.16 * (teeterT / Config.WOBBLE_TIME));
                    piece.tumbleDeg += dt * 22f;
                } else {
                    vy -= Config.GRAVITY * dt;
                    piece.pos.y += vy * dt;
                    pieceS += vAlongU * dt;
                    setPieceXY(pieceS);
                    piece.tumbleDeg += tumbleRate * dt;
                    // a short fall may clip the target block's wall and bounce back
                    if (!bounceDone && fallReason == GameEvent.FallReason.SHORT_GAP
                            && vAlongU > 0 && pieceS >= next.nearEdgeS()) {
                        vAlongU = -vAlongU * 0.35f;
                        bounceDone = true;
                    }
                    if (piece.pos.y < Config.FALL_KILL_Y) {
                        overDelay += dt;
                        if (overDelay >= Config.OVER_DELAY && state == State.FALLING) {
                            state = State.OVER;
                            emit(GameEvent.gameOver(score));
                        }
                    }
                }
                break;
            }
            case OVER:
                break;
        }
    }

    private void setPieceAt(float s, float y) {
        pieceS = s;
        piece.pos.x = Config.U_X * s;
        piece.pos.z = Config.U_Z * s;
        piece.pos.y = y;
    }

    private void setPieceXY(float s) {
        piece.pos.x = Config.U_X * s;
        piece.pos.z = Config.U_Z * s;
    }

    // ------------------------------------------------------------------ landing

    private void resolveLanding() {
        float landS = fromS + jumpD;
        piece.airborne = false;

        // 1) tiny hop landing back on the current block (adjustment hop)
        float oc = landS - cur.s;
        if (Math.abs(oc) <= cur.halfW - 0.10f) {
            setPieceAt(landS, 0);
            state = State.LANDED;
            landT = 0;
            pendingSpecial = false;
            emit(GameEvent.minihop(piece.pos.x, 0, piece.pos.z));
            return;
        }

        float o = landS - next.s;
        float halfW = next.halfW;
        float perfectR = Math.min(Config.PERFECT_R_ABS, halfW * Config.PERFECT_R_FRAC);

        if (Math.abs(o) <= perfectR) {
            combo++;
            int gained = Math.min(2 * combo, Config.COMBO_CAP);
            score += gained;
            setPieceAt(landS, 0);
            advance(gained, true);
        } else if (Math.abs(o) <= halfW - Config.EDGE_INSET) {
            combo = 0;
            score += Config.SCORE_NORMAL;
            setPieceAt(landS, 0);
            if (Math.abs(o) > halfW * Config.WOBBLE_FRAC) piece.wobble = 0.12f;
            advance(Config.SCORE_NORMAL, false);
        } else if (Math.abs(o) <= halfW + Config.EDGE_TOLERANCE) {
            setPieceAt(landS, 0);
            startFall(GameEvent.FallReason.EDGE_TIP, 0.35f);
        } else if (o < 0) {
            // undershoot
            setPieceAt(landS, 0);
            if (Math.abs(landS - next.nearEdgeS()) <= Config.WALL_HIT_RANGE) {
                startFall(GameEvent.FallReason.SHORT_WALL, -2.2f);
            } else {
                startFall(GameEvent.FallReason.SHORT_GAP, 1.4f);
            }
        } else {
            // overshoot: keeps drifting forward while dropping
            setPieceAt(landS, 0);
            startFall(GameEvent.FallReason.OVERSHOOT, 2.4f);
        }
    }

    private void advance(int gained, boolean perfect) {
        cur = next;
        cur.settleT = 0.18f;
        ensureAhead();
        next = blocks.chain().get(blocks.chain().indexOf(cur) + 1);
        state = State.LANDED;
        landT = 0;
        pendingSpecial = cur.kind.isSpecial() && !cur.bonusGiven;
        if (perfect) emit(GameEvent.perfect(gained, combo, piece.pos.x, piece.headY(), piece.pos.z));
        else emit(GameEvent.land(gained, piece.pos.x, 0, piece.pos.z));
    }

    private void startFall(GameEvent.FallReason reason, float drift) {
        state = State.FALLING;
        fallReason = reason;
        piece.dead = true;
        piece.airborne = true;
        vy = reason == GameEvent.FallReason.OVERSHOOT ? -0.5f : 0.5f;
        vAlongU = drift;
        teeterT = reason == GameEvent.FallReason.EDGE_TIP ? Config.WOBBLE_TIME : 0;
        tumbleRate = reason == GameEvent.FallReason.SHORT_WALL ? -300f : 260f;
        overDelay = 0;
        bounceDone = false;
        emit(GameEvent.fall(reason, piece.pos.x, piece.pos.y, piece.pos.z));
    }

    private void triggerSpecial() {
        int pts = cur.kind.bonus();
        score += pts;
        cur.markBonusGiven();
        cur.activatedT = 0.001f;
        state = State.SPECIAL_ANIM;
        animT = 0;
        emit(GameEvent.specialBonus(cur.kind, pts, cur.center.x, 1.2f, cur.center.z));
    }

    private void updateSpecialDecor(float dt) {
        if (cur.kind == Blocks.Kind.RUBIK) {
            cur.spin = cur.animT * ((float) Math.PI / 2f);
        } else if (cur.kind == Blocks.Kind.MUSICBOX && cur.activatedT > 0) {
            cur.activatedT += dt;
            if (cur.activatedT < 3.4f) cur.spin += dt * 2.6f;
        }
    }

    private void emit(GameEvent e) {
        events.add(e);
    }

    // ------------------------------------------------------------------ queries

    public List<GameEvent> drainEvents() {
        List<GameEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /** Blocks in the draw window: one behind .. three ahead. */
    public List<Blocks.Block> visible() {
        List<Blocks.Block> all = blocks.chain();
        int ci = all.indexOf(cur);
        int from = Math.max(0, ci - Config.KEEP_BEHIND);
        int to = Math.min(all.size(), ci + Config.KEEP_AHEAD + 2);
        return all.subList(from, to);
    }

    public State state() { return state; }
    public int score() { return score; }
    public int combo() { return combo; }
    public Piece piece() { return piece; }
    public Blocks.Block cur() { return cur; }
    public Blocks.Block next() { return next; }
    public Vec3 camFocus() { return camFocus; }
    /** Center distance to the next block (drives camera look-ahead / zoom). */
    public float camDist() { return next != null ? next.s - cur.s : 4f; }
    /** Charge power 0..1 while charging. */
    public float power() {
        return state == State.CHARGING ? Math.min(chargeT, Config.CHARGE_MAX_T) / Config.CHARGE_MAX_T : 0f;
    }
    public float totalTime() { return totalTime; }

    /** Exact hold time (s) needed to land dead-center on the next block. Used by tests/bots. */
    public float perfectHoldTime() {
        float d = next.s - pieceS;
        float power = (d - Config.JUMP_MIN_DIST) / (Config.JUMP_MAX_DIST - Config.JUMP_MIN_DIST);
        return Math.max(Config.CHARGE_MIN_T, Math.min(1f, power) * Config.CHARGE_MAX_T);
    }
}
