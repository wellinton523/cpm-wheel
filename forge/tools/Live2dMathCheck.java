import com.welli.cpmwheel.Live2dMath;
import com.welli.cpmwheel.Live2dMath.Link;
import com.welli.cpmwheel.Live2dMath.State;
import java.util.List;

/** 1.7.0 offline check of the "Física Live2D" chain math and value mapping:
 *  javac -d /tmp/l2 src/main/java/com/welli/cpmwheel/Live2dMath.java tools/Live2dMathCheck.java && java -cp /tmp/l2 Live2dMathCheck */
public class Live2dMathCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }
    static boolean near(float a, float b, float e) { return Math.abs(a - b) <= e; }

    public static void main(String[] args) {
        // value mapping
        ok("rest 0° -> middle 128 of 255", Live2dMath.toValue(0, -45, 45, 255) == 128);
        ok("-45° -> 0", Live2dMath.toValue(-45, -45, 45, 255) == 0);
        ok("45° -> 255", Live2dMath.toValue(45, -45, 45, 255) == 255);
        ok("beyond max clamps", Live2dMath.toValue(200, -45, 45, 255) == 255 && Live2dMath.toValue(-200, -45, 45, 255) == 0);
        ok("reversed range", Live2dMath.toValue(45, 45, -45, 255) == 0 && Live2dMath.toValue(-45, 45, -45, 255) == 255);
        ok("small layer max 10", Live2dMath.toValue(0, -30, 30, 10) == 5);
        ok("min == max -> middle", Live2dMath.toValue(7, 10, 10, 255) == 128);
        ok("asymmetric 0..90: 45 -> 128", Live2dMath.toValue(45, 0, 90, 255) == 128);

        // unwrap
        float u = Live2dMath.unwrap(170, -170);
        ok("unwrap 170 -> -170 = 190", near(u, 190, 0.01f));
        ok("unwrap -170 -> 170 = -190", near(Live2dMath.unwrap(-170, 170), -190, 0.01f));
        ok("unwrap many turns", near(Live2dMath.unwrap(720 + 10, 20), 740, 0.01f));

        // single link: settles to the input, lag output back to 0
        Link l = new Link("headYaw", true);
        State s = new State();
        Live2dMath.step(s, 0, l, 0.05f);
        ok("first step initialises at the input", s.init && s.a == 0 && s.v == 0);
        float minLag = 0;
        for (int i = 0; i < 4; i++) { Live2dMath.step(s, 60, l, 0.05f); minLag = Math.min(minLag, Live2dMath.output(s, 60, l)); }
        ok("turning right: lag output goes negative (hair lags)", minLag < -20);
        for (int i = 0; i < 200; i++) Live2dMath.step(s, 60, l, 0.05f);
        ok("settles on the input after 10 s", near(s.a, 60, 0.5f) && near(Live2dMath.output(s, 60, l), 0, 0.5f));
        Link f = new Link("walk", false);
        ok("follow output = angle", near(Live2dMath.output(s, 60, f), s.a, 0.001f));
        Link inv = new Link("headYaw", true); inv.invert = true;
        State si = new State(); si.init = true; si.a = 10;
        ok("invert flips", near(Live2dMath.output(si, 0, inv), -10, 0.001f));

        // overshoot: low damping overshoots, high damping does not
        ok("low damping overshoots", maxAngle(5) > 60.5f);
        ok("critical damping no overshoot", maxAngle(100) <= 60.01f);
        // longer = slower: time to reach half way
        ok("longer link is slower", reach(300) > reach(100) && reach(100) > reach(40));
        ok("heavier link is slower", reachW(300) > reachW(100));

        // chain: tips lag behind the center
        Link c0 = new Link("headYaw", true), c1 = new Link("prev", true), c2 = new Link("prev", true);
        List<Link> chain = List.of(c0, c1, c2);
        State[] st = {new State(), new State(), new State()};
        float[] out = new float[3];
        Live2dMath.stepChain(chain, st, new float[]{0, 0, 0}, 0.05f, out);
        int peak0 = -1, peak1 = -1, peak2 = -1; float m0 = 1e9f, m1 = 1e9f, m2 = 1e9f;
        for (int i = 0; i < 40; i++) {
            Live2dMath.stepChain(chain, st, new float[]{90, 90, 90}, 0.05f, out);
            if (st[0].a - 90 < m0) { m0 = st[0].a - 90; peak0 = i; }
            if (st[1].a - 90 < m1) { m1 = st[1].a - 90; peak1 = i; }
            if (st[2].a - 90 < m2) { m2 = st[2].a - 90; peak2 = i; }
        }
        float[] a50 = new float[3];
        State[] st2 = {new State(), new State(), new State()};
        Live2dMath.stepChain(chain, st2, new float[]{0, 0, 0}, 0.05f, out);
        for (int i = 0; i < 3; i++) Live2dMath.stepChain(chain, st2, new float[]{90, 90, 90}, 0.05f, out);
        ok("after 0.15 s: center moved more than tip 1, tip 1 more than tip 2", st2[0].a > st2[1].a && st2[1].a > st2[2].a && st2[2].a >= 0);
        ok("tip lag output negative while the center turns", out[1] < 0);
        for (int i = 0; i < 400; i++) Live2dMath.stepChain(chain, st, new float[]{90, 90, 90}, 0.05f, out);
        ok("whole chain settles (outputs ~0)", near(out[0], 0, 0.5f) && near(out[1], 0, 0.5f) && near(out[2], 0, 0.5f));
        ok("chain state stays finite", Float.isFinite(st[2].a) && Float.isFinite(st[2].v));
        // prev on the first link = 0 input, gain 0 = no motion
        Link g0 = new Link("headYaw", true); g0.gain = 0;
        State sg = new State();
        Live2dMath.stepChain(List.of(g0), new State[]{sg}, new float[]{50}, 0.05f, out);
        Live2dMath.stepChain(List.of(g0), new State[]{sg}, new float[]{90}, 0.05f, out);
        ok("gain 0: no motion", near(out[0], 0, 0.001f));
        // stability with extreme settings
        Link ex = new Link("headYaw", true); ex.len = 10; ex.weight = 10; ex.damp = 0;
        State se = new State();
        Live2dMath.step(se, 0, ex, 0.05f);
        for (int i = 0; i < 2000; i++) Live2dMath.step(se, (i % 20 < 10) ? 170 : -170, ex, 0.05f);
        ok("extreme settings stay finite and bounded", Float.isFinite(se.a) && Math.abs(se.a) < 400);
        // NaN recovery
        State sn = new State(); sn.init = true; sn.a = Float.NaN;
        Live2dMath.step(sn, 12, l, 0.05f);
        ok("NaN state recovers", near(sn.a, 12, 0.001f));
        // value of a lag link at rest = middle
        int rv = Live2dMath.toValue(Live2dMath.output(st[0], 90 * c0.gain / 100f, c0), c0.min, c0.max, 255);
        ok("rest maps to the middle frame (127/128)", rv == 127 || rv == 128);

        // ---------- 1.7.1 multiple inputs ----------
        java.util.Map<String, Float> src = new java.util.HashMap<>();
        src.put("headYaw", 40f); src.put("bodyYaw", 20f); src.put("fwd", 10f);
        Link mi = new Link("headYaw", true).add("bodyYaw", 50, true);
        ok("weighted sum: 40·100% − 20·50% = 30", near(Live2dMath.drive(mi, src, 0), 30, 0.001f));
        mi.add("fwd", 200, false);
        ok("third input adds 10·200% = 50", near(Live2dMath.drive(mi, src, 0), 50, 0.001f));
        Link pv = new Link("prev", true).add("headYaw", 25, false);
        ok("prev + head: 7 + 40·25% = 17", near(Live2dMath.drive(pv, src, 7), 17, 0.001f));
        Link none = new Link(); none.inputs.clear();
        ok("no inputs = 0", Live2dMath.drive(none, src, 5) == 0);
        ok("missing source counts 0", near(Live2dMath.drive(new Link("vert", true), src, 0), 0, 0.001f));
        Link w0 = new Link("headYaw", true); w0.inputs.get(0).weight = 0;
        ok("weight 0 ignores the input", Live2dMath.drive(w0, src, 0) == 0);
        // chain with named sources: gain multiplies the whole sum
        Link gl = new Link("headYaw", false).add("bodyYaw", 100, false); gl.gain = 50; gl.damp = 100;
        State gs = new State();
        float[] o1 = new float[1];
        Live2dMath.stepChain(List.of(gl), new State[]{gs}, src, 0.05f, o1);
        ok("gain 50% of (40+20) initialises at 30", near(gs.a, 30, 0.001f));
        // signed body-relative walking
        float[] r = Live2dMath.bodyRelative(0, 1, 0);          // yaw 0 faces +Z, moving +Z
        ok("forward is positive (yaw 0)", r[0] > 0.99f && Math.abs(r[1]) < 1e-4f);
        r = Live2dMath.bodyRelative(0, -1, 0);
        ok("backward is negative (yaw 0)", r[0] < -0.99f && Math.abs(r[1]) < 1e-4f);
        r = Live2dMath.bodyRelative(-1, 0, 90);                 // yaw 90 faces −X
        ok("forward is positive (yaw 90)", r[0] > 0.99f);
        r = Live2dMath.bodyRelative(1, 0, 90);
        ok("backward is negative (yaw 90)", r[0] < -0.99f);
        r = Live2dMath.bodyRelative(-1, 0, 0);                  // facing +Z, right side is −X
        ok("strafe right is positive", r[1] > 0.99f && Math.abs(r[0]) < 1e-4f);
        r = Live2dMath.bodyRelative(1, 0, 0);
        ok("strafe left is negative", r[1] < -0.99f);
        r = Live2dMath.bodyRelative(0, 1, 180);                 // facing −Z, moving +Z = backward
        ok("yaw 180: moving +Z is backward", r[0] < -0.99f);
        r = Live2dMath.bodyRelative(0.3, 0.4, 37);
        ok("speed is kept (|fwd,side| = |v|)", near((float) Math.hypot(r[0], r[1]), 0.5f, 1e-4f));
        // ---------- shared layers ----------
        ok("sum of offsets", Live2dMath.combine(new int[]{148, 138}, new int[]{128, 128}, "sum", 255) == 158);
        ok("avg of offsets", Live2dMath.combine(new int[]{148, 138}, new int[]{128, 128}, "avg", 255) == 143);
        ok("max picks the farthest (negative wins)", Live2dMath.combine(new int[]{148, 98}, new int[]{128, 128}, "max", 255) == 98);
        ok("opposite offsets cancel in sum", Live2dMath.combine(new int[]{158, 98}, new int[]{128, 128}, "sum", 255) == 128);
        ok("sum clamps high", Live2dMath.combine(new int[]{250, 250}, new int[]{128, 128}, "sum", 255) == 255);
        ok("sum clamps low", Live2dMath.combine(new int[]{10, 10}, new int[]{128, 128}, "sum", 255) == 0);
        ok("single link unchanged", Live2dMath.combine(new int[]{77}, new int[]{128}, "sum", 255) == 77);
        ok("different rests: offsets from each own rest", Live2dMath.combine(new int[]{70, 148}, new int[]{64, 128}, "sum", 255) == 90);
        ok("unknown mode = sum", Live2dMath.combine(new int[]{148, 138}, new int[]{128, 128}, "x", 255) == 158);
        // ---------- migration 20 -> 21 ----------
        Link old = new Link(); old.inputs.clear(); old.input = "bodyYaw";
        Live2dMath.migrateLink(old);
        ok("old single input -> one entry at 100%", old.inputs.size() == 1 && old.inputs.get(0).src.equals("bodyYaw") && old.inputs.get(0).weight == 100 && !old.inputs.get(0).invert && old.input.isEmpty());
        Live2dMath.migrateLink(old);
        ok("migration is idempotent", old.inputs.size() == 1);
        Link bad = new Link(); bad.inputs = null; bad.input = "nonsense";
        Live2dMath.migrateLink(bad);
        ok("unknown old input -> head yaw", bad.inputs.size() == 1 && bad.inputs.get(0).src.equals("headYaw"));
        Link keep = new Link("prev", true); keep.input = "walk";
        Live2dMath.migrateLink(keep);
        ok("existing list is kept", keep.inputs.size() == 1 && keep.inputs.get(0).src.equals("prev"));

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }

    static float maxAngle(int damp) {
        Link l = new Link("headYaw", false); l.damp = damp;
        State s = new State(); Live2dMath.step(s, 0, l, 0.05f);
        float m = 0;
        for (int i = 0; i < 200; i++) { Live2dMath.step(s, 60, l, 0.05f); m = Math.max(m, s.a); }
        return m;
    }
    static int reach(int len) {
        Link l = new Link("headYaw", false); l.len = len; l.damp = 100;
        State s = new State(); Live2dMath.step(s, 0, l, 0.05f);
        for (int i = 0; i < 1000; i++) { Live2dMath.step(s, 60, l, 0.01f); if (s.a >= 30) return i; }
        return 1000;
    }
    static int reachW(int w) {
        Link l = new Link("headYaw", false); l.weight = w; l.damp = 100;
        State s = new State(); Live2dMath.step(s, 0, l, 0.05f);
        for (int i = 0; i < 1000; i++) { Live2dMath.step(s, 60, l, 0.01f); if (s.a >= 30) return i; }
        return 1000;
    }
}
