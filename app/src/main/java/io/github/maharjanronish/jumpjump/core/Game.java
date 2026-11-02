package io.github.maharjanronish.jumpjump.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete game logic: a pure-Java state machine with zero platform
 * dependencies, so the exact same rules run in desktop tests and on Android.
 *
 * Rules replicated from the original WeChat "Jump Jump":
 *  - hold to charge, release to jump; distance grows linearly with hold time
 *  - the jump follows the LANE from the current block to the next one
 *    (runs of 3-6 blocks per direction, switching sides like a level
 *    switch); a piece that landed near an edge can therefore miss
 *    sideways too
 *  - +1 for a normal landing
 *  - dead-center landing: 2, 4, 6, 8, ... points (+2 per consecutive), cap 32
 *  - stay ~2 s on manhole / Rubik / store / record player: +5 / +10 / +15 / +30
 *  - blocks shrink and gaps widen as the score climbs
 *  - tiny hops within the same block are allowed (adjustment, no points)
 *  - missing the block ends the run
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
    // flight (parallel-lane model: origin + lane direction + distance)
    private float p0x, p0z;            // flight origin (world XZ)
    private float jumpD;               // flight distance
    private float segDx, segDz;        // active lane direction (cur -> next)
    private float flightT, flightTTotal, arcH;
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
        segDx = cur.toNextX;
        segDz = cur.toNextZ;
        p0x = 0;
        p0z = 0;
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
        // keep exactly the blocks that are drawn visible; the newest one
        // drops in on screen right when it is needed
        while (idx < 0 || c.size() - idx < Config.KEEP_AHEAD + 1) {
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
        p0x = piece.pos.x;
        p0z = piece.pos.z;
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

        // camera focus: rides a little ahead of the piece toward the next
        // block. The fixed camera then pans smoothly (official feel): the
        // composition re-centers as the piece travels the lane.
        {
            Vec3 n = next.center;
            float goalX = piece.pos.x + (n.x - piece.pos.x) * Config.FOCUS_AHEAD;
            float goalZ = piece.pos.z + (n.z - piece.pos.z) * Config.FOCUS_AHEAD;
            float a = 1f - (float) Math.exp(-Config.CAM_LERP_RATE * dt);
            camFocus.x += (goalX - camFocus.x) * a;
            camFocus.z += (goalZ - camFocus.z) * a;
        }

        // block settle + drop-in animations
        for (Blocks.Block b : visible()) {
            if (b.settleT > 0) b.settleT = Math.max(0, b.settleT - dt);
            if (b.spawnT > 0) {
                b.spawnT -= dt;
                if (b.spawnT <= 0) { b.spawnT = 0; b.settleT = 0.16f; }
            }
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
                float d = jumpD * k;
                piece.pos.x = p0x + segDx * d;
                piece.pos.z = p0z + segDz * d;
                piece.pos.y = arcH * 4 * k * (1 - k);
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
                    piece.pos.x += vAlongU * segDx * dt;
                    piece.pos.z += vAlongU * segDz * dt;
                    piece.tumbleDeg += tumbleRate * dt;
                    // a short fall may clip the target block's wall and bounce back
                    if (!bounceDone && fallReason == GameEvent.FallReason.SHORT_GAP
                            && vAlongU > 0) {
                        float rx = piece.pos.x - next.center.x, rz = piece.pos.z - next.center.z;
                        float along = rx * segDx + rz * segDz;
                        if (along >= -next.halfW && along <= next.halfW
                                && next.contains(piece.pos.x, piece.pos.z, 0.4f)) {
                            vAlongU = -vAlongU * 0.35f;
                            bounceDone = true;
                        }
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

    // ------------------------------------------------------------------ landing

    private void resolveLanding() {
        piece.airborne = false;
        float lx = p0x + segDx * jumpD;
        float lz = p0z + segDz * jumpD;

        // 1) tiny hop landing back on the current block (adjustment hop)
        if (cur.contains(lx, lz, -0.10f)) {
            piece.pos.set(lx, 0, lz);
            state = State.LANDED;
            landT = 0;
            pendingSpecial = false;
            emit(GameEvent.minihop(lx, 0, lz));
            return;
        }

        // 2) where did we land relative to the target block?
        float dx = lx - next.center.x;
        float dz = lz - next.center.z;
        float rad2 = dx * dx + dz * dz;
        float along = dx * segDx + dz * segDz;      // >0: past center, <0: short
        float lateral = -dx * segDz + dz * segDx;  // sideways offset in the lane frame
        float halfW = next.halfW;
        float perfectR = Math.min(Config.PERFECT_R_ABS, halfW * Config.PERFECT_R_FRAC);

        if (rad2 <= perfectR * perfectR) {
            // dead-center: combo 2, 4, 6, 8 ...
            combo++;
            int gained = Math.min(2 * combo, Config.COMBO_CAP);
            score += gained;
            piece.pos.set(lx, 0, lz);
            advance(gained, true);
        } else if (next.contains(lx, lz, -Config.EDGE_INSET)) {
            combo = 0;
            score += Config.SCORE_NORMAL;
            piece.pos.set(lx, 0, lz);
            if (rad2 > (halfW * Config.WOBBLE_FRAC) * (halfW * Config.WOBBLE_FRAC)) {
                piece.wobble = 0.12f;
            }
            advance(Config.SCORE_NORMAL, false);
        } else if (next.contains(lx, lz, Config.EDGE_TOLERANCE)) {
            // right at the edge: teeter and fall
            piece.pos.set(lx, 0, lz);
            startFall(GameEvent.FallReason.EDGE_TIP, 0.35f);
        } else if (along < 0) {
            // undershoot
            piece.pos.set(lx, 0, lz);
            if (Math.abs(lateral) <= halfW + 0.35f && along >= -(halfW + Config.WALL_HIT_RANGE)) {
                // smacked into the target's front wall
                startFall(GameEvent.FallReason.SHORT_WALL, -2.2f);
            } else {
                startFall(GameEvent.FallReason.SHORT_GAP, 1.4f);
            }
        } else {
            // overshoot (or far sideways): keeps drifting forward while dropping
            piece.pos.set(lx, 0, lz);
            startFall(GameEvent.FallReason.OVERSHOOT, 2.4f);
        }
    }

    private void advance(int gained, boolean perfect) {
        cur = next;
        cur.settleT = 0.18f;
        ensureAhead();
        next = blocks.chain().get(blocks.chain().indexOf(cur) + 1);
        segDx = cur.toNextX;
        segDz = cur.toNextZ;
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
        state = State.SPECIAL_ANIM;
        animT = 0;
        emit(GameEvent.specialBonus(cur.kind, pts, cur.center.x, 1.2f, cur.center.z));
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

    /** Blocks in the draw window: one behind .. two ahead. */
    public List<Blocks.Block> visible() {
        List<Blocks.Block> all = blocks.chain();
        int ci = all.indexOf(cur);
        int from = Math.max(0, ci - Config.KEEP_BEHIND);
        int to = Math.min(all.size(), ci + Config.KEEP_AHEAD + 1);
        return all.subList(from, to);
    }

    public State state() { return state; }
    public int score() { return score; }
    public int combo() { return combo; }
    public Piece piece() { return piece; }
    public Blocks.Block cur() { return cur; }
    public Blocks.Block next() { return next; }
    public Vec3 camFocus() { return camFocus; }
    /** Current direction phase (advances on each lane switch -> bg tint). */
    public int blocksPhase() { return blocks.phase(); }
    /** Active lane direction (cur -> next), unit length in world XZ. */
    public float segDx() { return segDx; }
    public float segDz() { return segDz; }
    /** Center distance to the next block (drives camera look-ahead / zoom). */
    public float camDist() { return next != null ? next.s - cur.s : 4f; }
    /** Perfect-zone radius of the TARGET block (drives the center marker). */
    public float targetPerfectR() {
        return next == null ? Config.PERFECT_R_ABS
                : Math.min(Config.PERFECT_R_ABS, next.halfW * Config.PERFECT_R_FRAC);
    }
    /** Charge power 0..1 while charging. */
    public float power() {
        return state == State.CHARGING ? Math.min(chargeT, Config.CHARGE_MAX_T) / Config.CHARGE_MAX_T : 0f;
    }
    public float totalTime() { return totalTime; }

    /** Exact hold time (s) needed to land dead-center on the next block. Used by tests/bots. */
    public float perfectHoldTime() {
        float need = (next.center.x - piece.pos.x) * segDx
                + (next.center.z - piece.pos.z) * segDz;
        float power = (need - Config.JUMP_MIN_DIST) / (Config.JUMP_MAX_DIST - Config.JUMP_MIN_DIST);
        power = Math.max(0f, Math.min(1f, power));
        return Math.max(Config.CHARGE_MIN_T, power * Config.CHARGE_MAX_T);
    }
}
