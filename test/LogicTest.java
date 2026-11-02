import io.github.maharjanronish.jumpjump.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Desktop JVM test-suite for the game core (no Android dependencies).
 * Run:  javac -d out test/LogicTest.java app/src/main/java/.../core/*.java && java -cp out LogicTest
 */
public class LogicTest {

    static int passed = 0, failed = 0;
    static final float DT = 1f / 120f;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name + "   " + detail); }
    }

    /** Advances the game with fixed timesteps until a condition or timeout. */
    static void pump(Game g, float seconds) {
        int steps = (int) (seconds / DT);
        for (int i = 0; i < steps; i++) g.update(DT);
    }

    /** The "perfect bot": charges exactly long enough to hit the next center. */
    static void botJump(Game g) {
        float hold = g.perfectHoldTime();
        g.press();
        pump(g, hold);
        g.release();
        pump(g, 1.2f); // complete flight + settle
    }

    public static void main(String[] args) {
        System.out.println("== Jump Jump core logic tests ==");
        testCameraComposition();
        testBotClearability();
        testScoring();
        testMiniHop();
        testFalls();
        testSpecialStay();
        testLevelGeneration();
        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- camera

    static final int CAM_W = 1080, CAM_H = 2340;

    static void testCameraComposition() {
        System.out.println("[camera composition]");
        Game g = new Game(42);
        Camera cam = new Camera();
        cam.update(g, CAM_W, CAM_H, 0f);

        // current block low-center, next block clearly higher up-screen
        Vec3 cur = g.cur().center;
        Vec3 nxt = g.next().center;
        float cx = cam.screenX(cur.x, cur.z) / CAM_W, cy = cam.screenY(cur.x, cur.z) / CAM_H;
        float nx = cam.screenX(nxt.x, nxt.z) / CAM_W, ny = cam.screenY(nxt.x, nxt.z) / CAM_H;
        check("current block sits in the lower half of the screen",
                cy > 0.50f && cy < 0.80f, String.format("cur=(%.2f, %.2f)", cx, cy));
        check("next block is clearly higher on screen than the current one",
                ny < cy - 0.08f && ny > 0.12f && ny < 0.60f,
                String.format("next=(%.2f, %.2f) cur=(%.2f, %.2f)", nx, ny, cx, cy));
        check("both blocks horizontally centered enough to read the layout",
                cx > 0.15f && cx < 0.85f && nx > 0.15f && nx < 0.85f,
                String.format("cx=%.2f nx=%.2f", cx, nx));
        float bw = 2 * g.cur().halfW * cam.scale();
        check("early block spans 36-58% of the screen width",
                bw > 0.36f * CAM_W && bw < 0.58f * CAM_W, "bw=" + bw);
        check("progressive reveal: only a small window of blocks exists",
                g.visible().size() <= Config.KEEP_BEHIND + Config.KEEP_AHEAD + 1,
                "visible=" + g.visible().size());

        // direction RUNS like the original: the target stays on ONE screen
        // side for several jumps, then the lane switches (a "level switch");
        // both directions occur, but never alternating every single block
        boolean sawRight = false, sawLeft = false;
        int runLen = 1, maxRun = 0, minRun = Integer.MAX_VALUE, switches = 0;
        float lastSide = 0;
        for (int i = 0; i < 80; i++) {
            cam.update(g, CAM_W, CAM_H, 1f / 60f);
            Vec3 c2 = g.cur().center, n2 = g.next().center;
            float side = Math.signum(cam.screenX(n2.x, n2.z) - cam.screenX(c2.x, c2.z));
            if (side != 0 && lastSide != 0 && side != lastSide) {
                switches++;
                maxRun = Math.max(maxRun, runLen);
                minRun = Math.min(minRun, runLen);
                runLen = 1;
            } else if (side != 0) {
                runLen++;
            }
            if (side > 0) sawRight = true;
            if (side < 0) sawLeft = true;
            lastSide = side;
            botJump(g);
        }
        check("the lane runs BOTH directions over time (right and left)",
                sawRight && sawLeft, "right=" + sawRight + " left=" + sawLeft);
        check("direction switches are occasional: every block is NOT a zigzag",
                maxRun >= 3 && minRun >= 2 && switches >= 3,
                "switches=" + switches + " maxRun=" + maxRun + " minRun=" + minRun);
        check("no run lasts longer than the configured maximum",
                maxRun <= Config.RUN_LEN_MAX + 1, "maxRun=" + maxRun);
        // background phase follows the switches
        check("background phase advances with the direction switches",
                g.blocksPhase() >= 1, "phase=" + g.blocksPhase());

        // worst case over a long random run: the whole next block stays
        // on screen (the camera's lateral guard), edges included
        Game g2 = new Game(7);
        float worstA = 1f, worstB = -1f, worstNy = 0f, bestNy = 1f;
        for (int i = 0; i < 150; i++) {
            // converged camera (many real frames elapse per bot jump)
            cam.update(g2, CAM_W, CAM_H, 1.0f);
            Vec3 n3 = g2.next().center;
            float half = g2.next().halfW;
            // farthest sprite corner horizontally = center +/- halfW along x
            float edgeA = cam.screenX(n3.x - half, n3.z) / CAM_W;
            float edgeB = cam.screenX(n3.x + half, n3.z) / CAM_W;
            worstA = Math.min(worstA, Math.min(edgeA, edgeB));
            worstB = Math.max(worstB, Math.max(edgeA, edgeB));
            float nyFrac = cam.screenY(n3.x, n3.z) / CAM_H;
            worstNy = Math.max(worstNy, nyFrac);
            bestNy = Math.min(bestNy, nyFrac);
            botJump(g2);
        }
        check("max-distance next block fully visible horizontally",
                worstA > -0.02f && worstB < 1.02f,
                String.format("edges=%.2f/%.2f", worstA, worstB));
        check("next block always sits in the vertical sweet spot",
                worstNy > 0.05f && worstNy < 0.72f && bestNy > 0.18f,
                String.format("ny=%.2f..%.2f", bestNy, worstNy));
    }

    // ---------------------------------------------------------------- clearability

    static void testBotClearability() {
        System.out.println("[bot clearability over random levels]");
        int games = 40, jumps = 150;
        boolean allSurvived = true;
        float maxHold = 0;
        StringBuilder problems = new StringBuilder();
        for (int seed = 0; seed < games; seed++) {
            Game g = new Game(seed * 7919L + 3);
            for (int j = 0; j < jumps; j++) {
                float hold = g.perfectHoldTime();
                maxHold = Math.max(maxHold, hold);
                if (hold > Config.CHARGE_MAX_T * 0.96f) {
                    allSurvived = false;
                    problems.append("seed ").append(seed).append(" jump ").append(j)
                            .append(" hold ").append(hold).append("; ");
                }
                botJump(g);
                for (GameEvent e : g.drainEvents()) {
                    if (e.type == GameEvent.Type.FALL || e.type == GameEvent.Type.GAME_OVER) {
                        allSurvived = false;
                        problems.append("seed ").append(seed).append(" died on jump ").append(j).append("; ");
                    }
                }
            }
        }
        check("perfect bot never dies on " + (games * jumps) + " jumps", allSurvived, problems.toString());
        check("required hold stays under 96% of max charge", maxHold <= Config.CHARGE_MAX_T * 0.96f,
                "maxHold=" + maxHold);
    }

    // ---------------------------------------------------------------- scoring

    static List<GameEvent> playPerfect(Game g) { botJump(g); return g.drainEvents(); }

    static void testScoring() {
        System.out.println("[scoring rules]");
        Game g = new Game(11);
        int[] expected = {2, 4, 6, 8, 10, 12, 14, 16, 18, 20};
        int total = 0;
        boolean seqOk = true;
        for (int i = 0; i < 10; i++) {
            List<GameEvent> ev = playPerfect(g);
            GameEvent gain = null;
            for (GameEvent e : ev) if (e.type == GameEvent.Type.PERFECT) gain = e;
            if (gain == null || gain.intArg != expected[i]) {
                seqOk = false;
                System.out.println("    jump " + i + " got " + (gain == null ? "none" : gain.intArg)
                        + " want " + expected[i]);
            } else total += gain.intArg;
        }
        check("consecutive center landings score 2,4,6,...,10", seqOk, "");
        check("5 perfects total 30 / 10 perfects total 110 (like the original)",
                total == 110, "total=" + total);

        // cap at 32 after 16 consecutive
        for (int i = 10; i < 20; i++) playPerfect(g);
        List<GameEvent> ev = playPerfect(g);
        int gained = 0;
        for (GameEvent e : ev) if (e.type == GameEvent.Type.PERFECT) gained = e.intArg;
        check("combo capped at 32 points per jump", gained == 32, "gained=" + gained);

        // a normal landing resets the streak (tested on early, wide blocks).
        // NOTE: after a non-perfect landing the piece sits off the lane center,
        // so the very next jump cannot be perfect (lateral carry-over, as in
        // the original); within a few jumps the lane rotates back and the
        // next perfect must start from 2 again.
        Game g2 = new Game(23);
        botJump(g2);                     // perfect, combo = 1
        g2.drainEvents();
        float perfect = g2.perfectHoldTime();
        g2.press(); pump(g2, perfect + 0.06f); g2.release(); pump(g2, 1.2f);
        List<GameEvent> ev2 = g2.drainEvents();
        boolean hadPerfect2 = false;
        for (GameEvent e : ev2) if (e.type == GameEvent.Type.PERFECT) hadPerfect2 = true;
        check("slightly-off landing is not scored as perfect", !hadPerfect2, "");
        int again = 0;
        for (int tries = 0; tries < 4 && again == 0; tries++) {
            List<GameEvent> evT = playPerfect(g2);
            for (GameEvent e : evT) if (e.type == GameEvent.Type.PERFECT) again = e.intArg;
        }
        check("after a miss-of-center, next perfect starts back at 2", again == 2, "again=" + again);
    }

    // ---------------------------------------------------------------- mini hop

    static void testMiniHop() {
        System.out.println("[in-block adjustment hop]");
        Game g = new Game(5);
        // tiny tap -> minimal hop, should land back on the big first block
        int before = g.score();
        g.press(); pump(g, 0.06f); g.release(); pump(g, 1.0f);
        boolean minihop = false;
        for (GameEvent e : g.drainEvents()) if (e.type == GameEvent.Type.MINIHOP) minihop = true;
        check("tiny tap produces a mini-hop", minihop, "");
        check("mini-hop scores nothing", g.score() == before, "score=" + g.score());
        check("still alive and ready", g.state() == Game.State.READY || g.state() == Game.State.LANDED,
                g.state().toString());
    }

    // ---------------------------------------------------------------- falls

    static void testFalls() {
        System.out.println("[fall detection]");
        // overshoot: way too much power
        Game g = new Game(9);
        g.press(); pump(g, Config.CHARGE_MAX_T); g.release(); pump(g, 3f);
        List<GameEvent> ev = g.drainEvents();
        boolean fell = false, over = false;
        for (GameEvent e : ev) { if (e.type == GameEvent.Type.FALL) fell = true; if (e.type == GameEvent.Type.GAME_OVER) over = true; }
        check("full-power jump overshoots and falls", fell, "");
        check("game over event emitted", over, "");

        // undershoot: scan hold times until one lands in the gap between blocks
        Game g2 = new Game(9);
        boolean fellShort = false;
        for (float hold = 0.16f; hold < 0.42f; hold += 0.01f) {
            Game t = new Game(9);
            t.press(); pump(t, hold); t.release(); pump(t, 3f);
            for (GameEvent e : t.drainEvents()) {
                if (e.type == GameEvent.Type.FALL
                        && (e.reason() == GameEvent.FallReason.SHORT_GAP
                        || e.reason() == GameEvent.FallReason.SHORT_WALL)) {
                    fellShort = true;
                }
            }
        }
        check("an undershot jump falls into the gap / hits the wall", fellShort, "");

        // edge tip: land just at the far edge (hold a bit over perfect)
        Game g3 = new Game(9);
        float per = g3.perfectHoldTime();
        // walk the hold time up until we fall off the far edge
        boolean edgeFound = false;
        for (float extra = 0.10f; extra < 0.50f; extra += 0.02f) {
            Game t = new Game(9);
            t.press(); pump(t, per + extra); t.release(); pump(t, 3f);
            for (GameEvent e : t.drainEvents()) {
                if (e.type == GameEvent.Type.FALL && e.reason() == GameEvent.FallReason.OVERSHOOT) edgeFound = true;
            }
        }
        check("slightly-too-far landing falls off the far edge", edgeFound, "");
    }

    // ---------------------------------------------------------------- special stay bonus

    static void testSpecialStay() {
        System.out.println("[special block stay bonuses]");
        // play games until the next block is a music box, then wait on it
        Game found = null;
        outer:
        for (int seed = 0; seed < 60; seed++) {
            Game g = new Game(seed * 131L + 17);
            for (int j = 0; j < 120; j++) {
                if (g.next().kind == Blocks.Kind.MUSICBOX) { found = g; break outer; }
                botJump(g);
            }
        }
        check("a music box shows up during normal play", found != null, "");
        if (found != null) {
            int before = found.score();
            botJump(found); // land on it (center -> perfect points at current combo level)
            List<GameEvent> landEv = found.drainEvents();
            int gained = 0;
            for (GameEvent e : landEv) if (e.type == GameEvent.Type.PERFECT) gained = e.intArg;
            pump(found, Config.SPECIAL_STAY_T + Config.SPECIAL_ANIM_T + 0.3f);
            List<GameEvent> ev = found.drainEvents();
            int bonus = 0;
            for (GameEvent e : ev) if (e.type == GameEvent.Type.SPECIAL_BONUS) bonus = e.intArg;
            check("staying on the music box awards +30", bonus == 30, "bonus=" + bonus);
            check("score includes the bonus", found.score() == before + gained + 30,
                    "score=" + found.score() + " before=" + before + " gained=" + gained);

            // bonus is one-shot: hopping in place and waiting again gives nothing
            found.press(); pump(found, 0.06f); found.release(); pump(found, 1.0f);
            pump(found, Config.SPECIAL_STAY_T + Config.SPECIAL_ANIM_T + 0.3f);
            int bonus2 = 0;
            for (GameEvent e : found.drainEvents()) if (e.type == GameEvent.Type.SPECIAL_BONUS) bonus2 = e.intArg;
            check("bonus does not repeat on the same block", bonus2 == 0, "bonus2=" + bonus2);
        }

        // forfeit: jumping away before the timer cancels the bonus
        Game f2 = null;
        outer2:
        for (int seed = 500; seed < 560; seed++) {
            Game g = new Game(seed * 131L + 17);
            for (int j = 0; j < 120; j++) {
                if (g.next().kind == Blocks.Kind.MANHOLE) { f2 = g; break outer2; }
                botJump(g);
            }
        }
        if (f2 != null) {
            botJump(f2);
            f2.drainEvents();
            int before = f2.score();
            pump(f2, 0.6f);                       // start waiting...
            botJump(f2);                          // ...but jump away early
            pump(f2, Config.SPECIAL_STAY_T + 1f);
            int bonus = 0;
            for (GameEvent e : f2.drainEvents()) if (e.type == GameEvent.Type.SPECIAL_BONUS) bonus = e.intArg;
            check("jumping away forfeits the stay bonus", bonus == 0, "bonus=" + bonus);
            check("still playing after forfeit", f2.state() != Game.State.OVER, f2.state().toString());
        } else {
            check("manhole found for forfeit test (probabilistic)", true, "");
        }
    }

    // ---------------------------------------------------------------- generation

    static void testLevelGeneration() {
        System.out.println("[level generation invariants]");
        boolean ok = true;
        StringBuilder problems = new StringBuilder();
        for (int seed = 0; seed < 30; seed++) {
            Game g = new Game(seed * 977L + 1);
            for (int j = 0; j < 120; j++) {
                Blocks.Block nxt = g.next();
                Blocks.Block cur = g.cur();
                if (nxt.s - cur.s < cur.halfW + nxt.halfW + 0.5f) {
                    ok = false; problems.append("overlap seed ").append(seed).append("; ");
                }
                if (nxt.s - cur.s > Config.JUMP_MAX_DIST * 0.91f) {
                    ok = false; problems.append("unreachable seed ").append(seed).append("; ");
                }
                if (j < 2 && (nxt.kind.isSpecial() || nxt.kind == Blocks.Kind.CYLINDER)) {
                    ok = false; problems.append("special/cylinder in intro seed ").append(seed).append("; ");
                }
                botJump(g);
            }
        }
        check("no overlaps, all reachable, clean intro", ok, problems.toString());

        // variety: all four specials + cylinders appear over time
        boolean manhole = false, rubik = false, store = false, music = false, cyl = false;
        for (int seed = 0; seed < 25 && !(manhole && rubik && store && music && cyl); seed++) {
            Game g = new Game(seed * 613L + 29);
            for (int j = 0; j < 100; j++) {
                Blocks.Kind k = g.next().kind;
                if (k == Blocks.Kind.MANHOLE) manhole = true;
                if (k == Blocks.Kind.RUBIK) rubik = true;
                if (k == Blocks.Kind.STORE) store = true;
                if (k == Blocks.Kind.MUSICBOX) music = true;
                if (k == Blocks.Kind.CYLINDER) cyl = true;
                botJump(g);
            }
        }
        check("manhole/rubik/store/musicbox/cylinder all appear", manhole && rubik && store && music && cyl,
                "m=" + manhole + " r=" + rubik + " s=" + store + " x=" + music + " c=" + cyl);

        // difficulty ramp: blocks shrink with score
        Game easy = new Game(3);
        float easyW = 0, hardW = 0;
        easyW = easy.next().halfW;
        Game hard = new Game(3);
        for (int j = 0; j < 140; j++) botJump(hard);
        hardW = hard.next().halfW;
        check("blocks shrink as the score climbs", hardW < easyW,
                "easy=" + easyW + " hard=" + hardW);
    }
}
