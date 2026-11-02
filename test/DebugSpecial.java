import io.github.maharjanronish.jumpjump.core.*;

public class DebugSpecial {
    public static void main(String[] args) {
        Game g = null;
        // find a game whose next block is a music box
        outer:
        for (int seed = 0; seed < 60; seed++) {
            Game t = new Game(seed * 131L + 17);
            for (int j = 0; j < 120; j++) {
                if (t.next().kind == Blocks.Kind.MUSICBOX) { g = t; break outer; }
                botJump(t);
                t.drainEvents();
            }
        }
        System.out.println("found game, next kind = " + g.next().kind + ", score=" + g.score());
        int before = g.score();
        botJump(g);
        for (GameEvent e : g.drainEvents()) System.out.println("  after botJump: " + e.type + " arg=" + e.intArg);
        System.out.println("score after landing = " + g.score() + " (before " + before + ")");
        // step precisely
        float dt = 1 / 120f;
        for (int i = 0; i < (int) (3.2f / dt); i++) {
            g.update(dt);
            for (GameEvent e : g.drainEvents()) {
                System.out.println("  t=" + (i * dt) + " " + e.type + " arg=" + e.intArg + " score=" + g.score());
            }
        }
        System.out.println("final score = " + g.score() + " expected = " + (before + 2 + 30));
    }

    static void botJump(Game g) {
        float hold = g.perfectHoldTime();
        g.press();
        pump(g, hold);
        g.release();
        pump(g, 1.2f);
    }

    static void pump(Game g, float seconds) {
        float dt = 1 / 120f;
        int steps = (int) (seconds / dt);
        for (int i = 0; i < steps; i++) g.update(dt);
    }
}
