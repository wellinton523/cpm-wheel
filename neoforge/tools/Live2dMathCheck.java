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


        check180();

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }

    static void check180() {
        // ---------- 1.8.0 ----------
        ok("new inputs listed, prev stays last", java.util.Arrays.asList(Live2dMath.INPUTS).containsAll(List.of("fall", "grav", "windF", "windS", "hit", "land", "lookYaw", "lookPitch", "voice", "layer"))
                && Live2dMath.INPUTS[Live2dMath.INPUTS.length - 1].equals("prev"));
        ok("hold defaults: grav/look/voice 100, wind/fall 75, hit/land/layer 0", Live2dMath.defaultHold("grav") == 100 && Live2dMath.defaultHold("lookYaw") == 100
                && Live2dMath.defaultHold("voice") == 100 && Live2dMath.defaultHold("windF") == 75 && Live2dMath.defaultHold("fall") == 75
                && Live2dMath.defaultHold("hit") == 0 && Live2dMath.defaultHold("land") == 0 && Live2dMath.defaultHold("layer") == 0);
        // soft limit
        ok("soft clamp: identity inside", near(Live2dMath.softClamp(10, -45, 45), 10, 1e-4f) && near(Live2dMath.softClamp(-30, -45, 45), -30, 1e-4f));
        ok("soft clamp: never passes the limit", Live2dMath.softClamp(500, -45, 45) <= 45 && Live2dMath.softClamp(500, -45, 45) > 44.9f && Live2dMath.softClamp(-500, -45, 45) >= -45 && Live2dMath.softClamp(60, -45, 45) < 45);
        ok("soft clamp: continuous at the knee", near(Live2dMath.softClamp(35.001f, -45, 45), 35.001f, 0.01f) && Live2dMath.softClamp(40, -45, 45) < 40 && Live2dMath.softClamp(40, -45, 45) > 38);
        boolean mono = true; float pv = -1e9f;
        for (float x = -200; x <= 200; x += 0.5f) { float y = Live2dMath.softClamp(x, -45, 45); if (y < pv - 1e-4f) mono = false; pv = y; }
        ok("soft clamp: monotonic", mono);
        ok("soft clamp: reversed limits and NaN", near(Live2dMath.softClamp(0, 45, -45), 0, 1e-4f) && Float.isFinite(Live2dMath.softClamp(Float.NaN, -45, 45)));
        Link lim = new Link("headYaw", false); lim.damp = 100;
        ok("limit off by default = unchanged", near(Live2dMath.limit(lim, 80), 80, 1e-4f));
        lim.softLim = true; lim.limLo = -30; lim.limHi = 30;
        ok("limit on: 80° stays under 30°", Live2dMath.limit(lim, 80) < 30);
        {   State ls = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
            m.put("headYaw", 0f); Live2dMath.stepChain(List.of(lim), new State[]{ls}, m, 0.05f, o);
            m.put("headYaw", 90f); float mx = 0; for (int i = 0; i < 100; i++) { Live2dMath.stepChain(List.of(lim), new State[]{ls}, m, 0.05f, o); mx = Math.max(mx, o[0]); }
            ok("chain output respects the soft limit", mx < 30 && mx > 25); }
        // impulses
        Live2dMath.Impulse im = new Live2dMath.Impulse();
        ok("no impulse = 0", Live2dMath.decay(im, 0.05f, 0.12f) == 0);
        Live2dMath.kick(im, Live2dMath.hitPeak(4));
        ok("hit 4 HP = 32°", near(Live2dMath.decay(im, 0.05f, 0.12f), 32, 1e-3f));
        float after = 0; for (int i = 0; i < 20; i++) after = Live2dMath.decay(im, 0.05f, 0.12f);
        ok("impulse dies out within 1 s", after < 0.1f);
        Live2dMath.kick(im, 50); Live2dMath.kick(im, 10);
        ok("a smaller hit doesn't replace a bigger one", near(im.v, 50, 1e-3f));
        ok("landing: normal jump ≈ 0, 3 blocks small, high fall capped", Live2dMath.landPeak(4.5f) == 0 && Live2dMath.landPeak(7.7f) > 10 && Live2dMath.landPeak(7.7f) < 20 && Live2dMath.landPeak(60) == 90);
        ok("hit capped at 90°", Live2dMath.hitPeak(100) == 90 && Live2dMath.hitPeak(-3) == 0);
        {   Link hl = new Link("hit", true); hl.damp = 25; State hs = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1];
            Live2dMath.Impulse ii = new Live2dMath.Impulse();
            m.put("hit", 0f); Live2dMath.stepChain(List.of(hl), new State[]{hs}, m, 0.05f, o);
            Live2dMath.kick(ii, 40); float peak = 0;
            for (int i = 0; i < 200; i++) { m.put("hit", Live2dMath.decay(ii, 0.05f, 0.12f)); Live2dMath.stepChain(List.of(hl), new State[]{hs}, m, 0.05f, o); if (i < 20) peak = Math.max(peak, Math.abs(o[0])); }
            ok("hit input: a swing, then back to rest", peak > 10 && near(o[0], 0, 0.3f)); }
        // wind
        ok("biome wind: peaks windy, forest calm, cave very calm", Live2dMath.biomeWind("minecraft:jagged_peaks") > Live2dMath.biomeWind("minecraft:plains")
                && Live2dMath.biomeWind("minecraft:forest") < 1 && Live2dMath.biomeWind("minecraft:deep_dark") < Live2dMath.biomeWind("minecraft:forest") && Live2dMath.biomeWind(null) == 1);
        float wc = Live2dMath.windStrength(false, false, 1, 64, true), wr = Live2dMath.windStrength(true, false, 1, 64, true), wt = Live2dMath.windStrength(true, true, 1, 64, true);
        ok("wind: clear < rain < thunder", wc > 0 && wc < wr && wr < wt);
        ok("wind: higher = stronger, capped", Live2dMath.windStrength(false, false, 1, 160, true) > wc && near(Live2dMath.windStrength(false, false, 1, 1000, true), wc * 2.5f, 1e-3f));
        ok("wind: under a roof much weaker", Live2dMath.windStrength(false, false, 1, 64, false) < wc * 0.2f);
        float gmin = 9, gmax = -9; for (int i = 0; i < 20000; i++) { float gg = Live2dMath.gust(i * 0.05, 3); gmin = Math.min(gmin, gg); gmax = Math.max(gmax, gg); }
        ok("gusts vary and stay bounded (" + gmin + ".." + gmax + ")", gmin >= 0.2f && gmax <= 1.8f && gmax - gmin > 0.6f && gmax > 1.2f);
        float[] wf = Live2dMath.windRel(10, 180, 0);   // facing +Z (yaw 0), wind blowing toward yaw 180 = −Z = into the face
        ok("headwind is + front (like walking forward)", wf[0] > 9.9f && Math.abs(wf[1]) < 1e-3f);
        float[] ws = Live2dMath.windRel(10, 90, 0);    // blowing toward −X = toward the right side's opposite? (right of +Z is −X)
        ok("wind toward the right = from the left = − side", ws[1] < -9.9f);
        // look-at
        float[] la = Live2dMath.lookAngles(0, 0, 5, 0, 0, 60);
        ok("look: target straight ahead = 0,0", la[2] == 1 && near(la[0], 0, 1e-3f) && near(la[1], 0, 1e-3f));
        la = Live2dMath.lookAngles(-5, 0, 5, 0, 0, 60);  // facing +Z, −X is the right side
        ok("look: target front-right = +45° yaw", near(la[0], 45, 0.01f));
        la = Live2dMath.lookAngles(0, -5, 5, 0, 0, 60);
        ok("look: target below = + pitch", near(la[1], 45, 0.01f));
        ok("look: behind = no tracking", Live2dMath.lookAngles(0, 0, -5, 0, 0, 60)[2] == 0);
        ok("look: clamped to max yaw", near(Live2dMath.lookAngles(-5, 0, 0.5, 0, 0, 60)[0], 60, 1e-3f));
        ok("look: relative to head yaw", near(Live2dMath.lookAngles(-5, 0, 0, 90, 0, 60)[0], 0, 0.01f));
        // generators
        {   Live2dMath.Gen g = new Live2dMath.Gen(); g.periodMs = 2000; g.rand = 0;
            Live2dMath.GenState gs = new Live2dMath.GenState(); java.util.Random rr = new java.util.Random(1);
            float lo = 9, hi = -9, at1s = 0;
            for (int i = 0; i < 80; i++) { float lv = Live2dMath.stepGen(g, gs, 0.05f, rr); lo = Math.min(lo, lv); hi = Math.max(hi, lv); if (i == 19) at1s = lv; }
            ok("breath: 0..1 range", lo >= 0 && lo < 0.02f && hi <= 1 && hi > 0.98f);
            ok("breath: peak at half the period", near(at1s, 1, 0.01f));
            ok("breath value inside lo..hi %", Live2dMath.genValue(g, 0, 255) == 0 && Live2dMath.genValue(g, 1, 255) == 255);
            g.lo = 20; g.hi = 60;
            ok("breath 20..60 % of 200 = 40..120", Live2dMath.genValue(g, 0, 200) == 40 && Live2dMath.genValue(g, 1, 200) == 120 && Live2dMath.genValue(g, 2, 200) == 120);
            g.rand = 50; gs = new Live2dMath.GenState(); java.util.Set<Long> peaks = new java.util.HashSet<>();
            for (int i = 0; i < 2000; i++) { float lv = Live2dMath.stepGen(g, gs, 0.05f, rr); if (Float.isNaN(lv)) peaks.add(-1L); }
            ok("breath with random variation stays finite", !peaks.contains(-1L) && gs.mul >= 0.75f && gs.mul <= 1.25f && gs.amp >= 0.75f);
            Live2dMath.Gen b = new Live2dMath.Gen(); b.kind = "blink"; b.periodMs = 3000; b.rand = 40; b.pulseMs = 150;
            Live2dMath.GenState bs = new Live2dMath.GenState(); int blinks = 0, closedTicks = 0; float prevLv = 0; boolean full = false;
            for (int i = 0; i < 20 * 60; i++) { float lv = Live2dMath.stepGen(b, bs, 0.05f, rr); if (lv > 0 && prevLv == 0) blinks++; if (lv >= 1) { full = true; closedTicks++; } prevLv = lv; }
            ok("blink: ~20 blinks per minute at 3 s (" + blinks + ")", blinks >= 14 && blinks <= 28);
            ok("blink: fully closes, but briefly", full && closedTicks <= blinks * 3);
            b.rand = 0; bs = new Live2dMath.GenState(); java.util.List<Integer> gaps = new java.util.ArrayList<>(); int last = -1;
            for (int i = 0; i < 2000; i++) { float lv = Live2dMath.stepGen(b, bs, 0.05f, rr); if (lv > 0 && (i == 0 || last < i - 1)) { } if (lv > 0) { if (last >= 0 && i - last > 5) gaps.add(i - last); last = i; } }
            ok("blink without variation: regular intervals", !gaps.isEmpty() && java.util.Collections.max(gaps) - java.util.Collections.min(gaps) <= 1);
        }
        // mirror
        ok("mirror names", "asa_dir".equals(Live2dMath.mirrorName("asa_esq")) && "WingRight".equals(Live2dMath.mirrorName("WingLeft"))
                && "fis-asa-D".equals(Live2dMath.mirrorName("fis-asa-E")) && "ear_LEFT".equals(Live2dMath.mirrorName("ear_RIGHT"))
                && "AsaEsquerda".equals(Live2dMath.mirrorName("AsaDireita")) && Live2dMath.mirrorName("cabelo") == null && Live2dMath.mirrorName("") == null);
        {   Live2dMath.Chain mc = new Live2dMath.Chain(); mc.id = "a"; mc.name = "Asa E"; mc.fadeMs = 500;
            Link m0 = new Link("headYaw", true).add("side", 40, false).add("fwd", 20, true); m0.anim = "asa_esq"; m0.softLim = true; m0.limHi = 20;
            m0.off.add(new com.welli.cpmwheel.TriggerLogic.Cond("anim", "voar", 1));
            Link m1 = new Link("prev", true); m1.anim = "ponta_esq";
            mc.links.add(m0); mc.links.add(m1);
            Live2dMath.Chain mr = Live2dMath.mirror(mc, "b", " (espelho)", List.of("asa_esq", "asa_dir"));
            ok("mirror: yaw and side inverted, forward kept", mr.links.get(0).inputs.get(0).invert && mr.links.get(0).inputs.get(1).invert && mr.links.get(0).inputs.get(2).invert);
            ok("mirror: layer swapped when it exists, blank when not", "asa_dir".equals(mr.links.get(0).anim) && "".equals(mr.links.get(1).anim));
            ok("mirror: settings, limits and conditions copied", mr.fadeMs == 500 && mr.links.get(0).softLim && mr.links.get(0).limHi == 20 && mr.links.get(0).off.size() == 1 && "b".equals(mr.id) && "Asa E (espelho)".equals(mr.name));
            ok("mirror: deep copy (original untouched)", !mc.links.get(0).inputs.get(0).invert && mc.links.get(0).off.get(0) != mr.links.get(0).off.get(0) && "asa_esq".equals(mc.links.get(0).anim));
        }
        // presets as text
        String js = "{\"chains\":[{\"name\":\"Cabelo ç\"}]}";
        ok("preset text round trip (UTF-8)", js.equals(Live2dMath.unwrapPreset(Live2dMath.wrapPreset(js))) && Live2dMath.wrapPreset(js).startsWith("CPMW-L2D1:"));
        ok("preset: plain JSON accepted, garbage rejected", js.equals(Live2dMath.unwrapPreset("  " + js + "\n")) && Live2dMath.unwrapPreset("hello") == null
                && Live2dMath.unwrapPreset("CPMW-L2D1:%%%") == null && Live2dMath.unwrapPreset(null) == null);
        // layer motion input
        {   Live2dMath.LayerSig ls = new Live2dMath.LayerSig();
            ok("layer rate: first read = 0", Live2dMath.layerSignal(ls, 0, 255, 0.05f, "rate", 4) == 0);
            float r1 = Live2dMath.layerSignal(ls, 51, 255, 0.05f, "rate", 4);   // +0.2 in one tick = 4/s -> 120 -> clamped 90
            ok("layer rate: fast change clamps at 90", near(r1, 90, 1e-3f));
            float r2 = Live2dMath.layerSignal(ls, 56, 255, 0.05f, "rate", 4);   // +5/255 in a tick ≈ 0.39/s -> 11.8°
            ok("layer rate: slow change small", r2 > 10 && r2 < 13);
            ok("layer rate: steady = 0", Live2dMath.layerSignal(ls, 56, 255, 0.05f, "rate", 4) == 0);
            ok("layer rate: falling is negative", Live2dMath.layerSignal(ls, 40, 255, 0.05f, "rate", 4) < 0);
            Live2dMath.LayerSig lv = new Live2dMath.LayerSig();
            ok("layer value: max = 45°, off (-1) = 0", near(Live2dMath.layerSignal(lv, 255, 255, 0.05f, "value", 4), 45, 1e-3f) && Live2dMath.layerSignal(lv, -1, 255, 0.05f, "value", 4) == 0);
            Live2dMath.LayerSig lo2 = new Live2dMath.LayerSig(); float omin = 0, omax = 0; int zc = 0; float pz = 0;
            for (int i = 0; i < 40; i++) { float o = Live2dMath.layerSignal(lo2, 1, 1, 0.05f, "osc", 2); omin = Math.min(omin, o); omax = Math.max(omax, o); if (pz <= 0 && o > 0) zc++; pz = o; }
            ok("layer osc: flaps ±45 at 2 Hz while active (" + zc + " cycles in 2 s)", omax > 40 && omin < -40 && zc >= 3 && zc <= 5);
            ok("layer osc: stops when the animation stops", Live2dMath.layerSignal(lo2, 0, 1, 0.05f, "osc", 2) == 0 && lo2.phase == 0);
            // a flapping wing animation pushes the hair chain through a "layer" input
            Link hl = new Link("headYaw", true); hl.inputs.clear(); Live2dMath.Input li = new Live2dMath.Input("layer", 50, false); li.ref = "voar"; li.refMode = "osc"; li.refHz10 = 20; hl.inputs.add(li); hl.damp = 25;
            State hs = new State(); java.util.Map<String, Float> m = new java.util.HashMap<>(); float[] o = new float[1]; Live2dMath.LayerSig sg = new Live2dMath.LayerSig();
            float amp = 0;
            for (int i = 0; i < 100; i++) { m.put(Live2dMath.key(li), Live2dMath.layerSignal(sg, i < 60 ? 1 : 0, 1, 0.05f, li.refMode, li.refHz10 / 10f)); Live2dMath.stepChain(List.of(hl), new State[]{hs}, m, 0.05f, o); if (i > 20 && i < 60) amp = Math.max(amp, Math.abs(o[0])); }
            for (int i = 0; i < 200; i++) { m.put(Live2dMath.key(li), Live2dMath.layerSignal(sg, 0, 1, 0.05f, li.refMode, 2)); Live2dMath.stepChain(List.of(hl), new State[]{hs}, m, 0.05f, o); }
            ok("flapping animation moves the hair (" + amp + "°), rests after", amp > 5 && near(o[0], 0, 0.3f));
            ok("layer key depends on layer and mode", !Live2dMath.key(li).equals(Live2dMath.key(new Live2dMath.Input("layer", 50, false))) && "headYaw".equals(Live2dMath.key(new Live2dMath.Input("headYaw", 1, false))));
        }

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
