import com.welli.cpmwheel.Live2dMath;
import com.welli.cpmwheel.Live2dMath.Link;
import com.welli.cpmwheel.Live2dMath.State;
import java.util.List;

/** 1.7.0 offline check of the "Física Live2D" chain math and value mapping:
 *  javac -d /tmp/l2 src/main/java/com/welli/cpmwheel/Live2dMath.java src/main/java/com/welli/cpmwheel/TriggerLogic.java tools/Live2dMathCheck.java && java -cp /tmp/l2 Live2dMathCheck */
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

        // ---------- 1.7.2 suspend: fade out / release / fade in ----------
        float w = 1;
        int steps = 0;
        while (w > 0 && steps < 100) { w = Live2dMath.fadeStep(w, true, 300, 0.05f); steps++; }
        ok("fade out 0.3 s = 6 ticks", steps == 6 && w == 0);
        ok("halfway value during fade", Live2dMath.faded(228, 128, 0.5f) == 178);
        ok("faded value at w=1 is the physics value", Live2dMath.faded(200, 128, 1f) == 200);
        ok("faded value at w=0 is rest", Live2dMath.faded(10, 128, 0f) == 128);
        // writes during the fade go monotonically to rest, then the link is released
        float fw = 1; int last = 228; boolean mono = true, releasedEarly = false;
        for (int i = 0; i < 6; i++) {
            fw = Live2dMath.fadeStep(fw, true, 300, 0.05f);
            int v = Live2dMath.faded(228, 128, fw);
            if (v > last) mono = false; last = v;
            if (i < 5 && Live2dMath.released(fw, true)) releasedEarly = true;
        }
        ok("fade-out writes go down to rest", mono && last == 128);
        ok("released only after the fade", !releasedEarly && Live2dMath.released(fw, true));
        ok("not released when resuming at w=0", !Live2dMath.released(0f, false));
        steps = 0;
        while (fw < 1 && steps < 100) { fw = Live2dMath.fadeStep(fw, false, 300, 0.05f); steps++; }
        ok("fade in 0.3 s = 6 ticks", steps == 6 && fw == 1);
        ok("fade 0 = instant", Live2dMath.fadeStep(1, true, 0, 0.05f) == 0 && Live2dMath.fadeStep(0, false, 0, 0.05f) == 1);
        ok("resume mid-fade turns back", Live2dMath.fadeStep(0.5f, false, 300, 0.05f) > 0.5f);
        ok("long fade 1 s: 20 ticks", Math.abs(Live2dMath.fadeStep(1, true, 1000, 0.05f) - 0.95f) < 1e-5f);
        Live2dMath.Chain nc = new Live2dMath.Chain();
        ok("config 22 defaults: no suspend conditions, OR, fade 0.3 s", nc.off.isEmpty() && "or".equals(nc.offCombine) && nc.fadeMs == 300 && new Link().off.isEmpty());
        // shared layer: a released link drops out of the combine, the other keeps writing alone
        ok("shared: only the active link remains", Live2dMath.combine(new int[]{150}, new int[]{128}, "sum", 255) == 150);


        // ---------- 1.7.3 "Tranco ↔ Mantido" ----------
        ok("defaults: speeds 75 %, angles / prev 0 %", Live2dMath.defaultHold("fwd") == 75 && Live2dMath.defaultHold("walk") == 75 && Live2dMath.defaultHold("side") == 75
                && Live2dMath.defaultHold("vert") == 75 && Live2dMath.defaultHold("headYaw") == 0 && Live2dMath.defaultHold("bodyYaw") == 0 && Live2dMath.defaultHold("prev") == 0);
        ok("new speed input gets 75 %, Gson-style empty input 0 %", new Live2dMath.Input("fwd", 20, true).hold == 75 && new Live2dMath.Input().hold == 0);
        {   // migration 22 -> 23: old velocity inputs (hold 0) gain 75 %, angle inputs and edited values unchanged
            Link ml = new Link(); Live2dMath.Input a = new Live2dMath.Input(); a.src = "fwd"; Live2dMath.Input b = new Live2dMath.Input(); b.src = "headYaw";
            Live2dMath.Input c = new Live2dMath.Input(); c.src = "side"; c.hold = 40;
            ml.inputs.add(a); ml.inputs.add(b); ml.inputs.add(c);
            Live2dMath.migrateHold(ml);
            ok("migration: fwd 0 -> 75, headYaw stays 0, edited side 40 kept", a.hold == 75 && b.hold == 0 && c.hold == 40);
        }
        // wings: walking forward (fwd 43 = 4.3 blocks/s), input fwd 20 % inverted, lag
        float walkOld = settleWalk(0), walkNew = settleWalk(75), walkFull = settleWalk(100);
        ok("mix 0 %: steady walking settles back to rest (old behaviour)", near(walkOld, 0, 0.3f));
        ok("mix 75 %: steady walking holds an offset (0.75 · 8.6 = 6.45°)", near(walkNew, 6.45f, 0.3f));
        ok("mix 100 %: holds the full input (8.6°)", near(walkFull, 8.6f, 0.3f));
        ok("held offset is on the same side as the start swing", firstSwing(75) > 0 && walkNew > 0 && firstSwing(0) > 0);
        {   // mix 0 % = exactly the 1.7.2 math
            Link a0 = new Link("fwd", true).add("headYaw", 50, false); for (Live2dMath.Input in : a0.inputs) in.hold = 0; a0.damp = 25;
            State x0 = new State(), y0 = new State(); boolean same = true;
            java.util.Map<String, Float> m = new java.util.HashMap<>();
            for (int i = 0; i < 120; i++) {
                m.put("fwd", i < 60 ? 43f : 0f); m.put("headYaw", (float) (30 * Math.sin(i * 0.2)));
                float[] o = Live2dMath.stepChain(List.of(a0), new State[]{x0}, m, 0.05f, new float[1]);
                float x = Live2dMath.drive(a0, m, 0); Live2dMath.step(y0, x, a0, 0.05f);
                if (!near(o[0], Live2dMath.output(y0, x, a0), 1e-4f)) same = false;
            }
            ok("mix 0 %: identical to 1.7.2 (a − x)", same);
        }
        {   // stop: returns to rest, crossing it (a swing), then settles
            Link wg = wing(75); State ws = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
            m.put("fwd", 0f); Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o);
            m.put("fwd", 43f); for (int i = 0; i < 200; i++) Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o);
            m.put("fwd", 0f); float minO = 1e9f;
            for (int i = 0; i < 40; i++) { Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o); minO = Math.min(minO, o[0]); }
            ok("stopping swings past rest (overshoot below 0)", minO < -0.5f);
            for (int i = 0; i < 300; i++) Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o);
            ok("after stopping it settles at rest", near(o[0], 0, 0.3f));
        }
        {   // prev never holds; follow ignores the mix
            Link pl = new Link("prev", true); java.util.Map<String, Float> m = new java.util.HashMap<>();
            ok("prev input is never held", Live2dMath.held(pl, m) == 0);
            Link fl = new Link("fwd", false); State fs = new State(); m.put("fwd", 0f); float[] o = new float[1];
            Live2dMath.stepChain(List.of(fl), new State[]{fs}, m, 0.05f, o); m.put("fwd", 40f);
            for (int i = 0; i < 300; i++) Live2dMath.stepChain(List.of(fl), new State[]{fs}, m, 0.05f, o);
            ok("follow output keeps the input (40)", near(o[0], 40, 0.3f));
        }
        // speed smoothing: ground/air hops and tiny spikes don't flicker
        {
            float sv = 0; for (int i = 0; i < 40; i++) sv = Live2dMath.smooth(sv, 43, 0.05f, 0.15f);
            ok("smoothing converges to the steady speed", near(sv, 43, 0.1f));
            float lo = 1e9f, hi = -1e9f;
            for (int i = 0; i < 100; i++) { sv = Live2dMath.smooth(sv, (i % 12 < 6) ? 43 : 56, 0.05f, 0.15f); if (i > 20) { lo = Math.min(lo, sv); hi = Math.max(hi, sv); } }
            ok("hopping 43/56 stays between, never near 0", lo > 40 && hi < 58);
            float sp = 43; boolean drop = false;
            for (int i = 0; i < 40; i++) { sp = Live2dMath.smooth(sp, i == 10 ? 0 : 43, 0.05f, 0.15f); if (Live2dMath.deadZone(sp, 0.5f) == 0) drop = true; }
            ok("one zero tick does not turn walking off", !drop && sp > 42);
            ok("dead zone removes tiny drift", Live2dMath.deadZone(0.3f, 0.5f) == 0 && Live2dMath.deadZone(-0.3f, 0.5f) == 0 && Live2dMath.deadZone(5, 0.5f) == 5);
            ok("smooth is NaN-safe", Float.isFinite(Live2dMath.smooth(Float.NaN, Float.NaN, 0.05f, 0.15f)));
            // the held output with a noisy, smoothed speed stays steady (no on/off flicker)
            Link wg = wing(75); State ws = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
            float s2 = 0; m.put("fwd", 0f); Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o);
            float omin = 1e9f, omax = -1e9f; java.util.Random rnd = new java.util.Random(7);
            for (int i = 0; i < 400; i++) {
                float raw = 43 + (rnd.nextFloat() - 0.5f) * 8 + ((i % 15 < 7) ? 0 : 10) + (rnd.nextInt(25) == 0 ? -43 : 0);
                s2 = Live2dMath.smooth(s2, raw, 0.05f, 0.15f); m.put("fwd", Live2dMath.deadZone(s2, 0.5f));
                Live2dMath.stepChain(List.of(wg), new State[]{ws}, m, 0.05f, o);
                if (i > 100) { omin = Math.min(omin, o[0]); omax = Math.max(omax, o[0]); }
            }
            ok("noisy walking: offset stays clearly away from rest (" + omin + ".." + omax + ")", omin > 3f);
        }

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }

    static Link wing(int hold) { Link w = new Link("fwd", true); w.inputs.get(0).weight = 20; w.inputs.get(0).invert = true; w.inputs.get(0).hold = hold; w.damp = 25; return w; }
    /** output after 10 s of steady forward walking */
    static float settleWalk(int hold) {
        Link w = wing(hold); State s = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
        m.put("fwd", 0f); Live2dMath.stepChain(List.of(w), new State[]{s}, m, 0.05f, o);
        m.put("fwd", 43f); for (int i = 0; i < 200; i++) Live2dMath.stepChain(List.of(w), new State[]{s}, m, 0.05f, o);
        return o[0];
    }
    /** largest output in the first 0.5 s after starting to walk */
    static float firstSwing(int hold) {
        Link w = wing(hold); State s = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
        m.put("fwd", 0f); Live2dMath.stepChain(List.of(w), new State[]{s}, m, 0.05f, o);
        m.put("fwd", 43f); float mx = 0; for (int i = 0; i < 10; i++) { Live2dMath.stepChain(List.of(w), new State[]{s}, m, 0.05f, o); mx = Math.max(mx, o[0]); }
        return mx;
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
