import com.welli.cpmwheel.Live2dMath;
import com.welli.cpmwheel.LookLogic;
import com.welli.cpmwheel.LookLogic.Cand;
import java.util.List;
import java.util.Random;

/** 1.8.3 offline check: screen-area / nearest target choice, stickiness, eye smoothing, smooth blink.
 *  javac -d /tmp/lk src/main/java/com/welli/cpmwheel/Live2dMath.java src/main/java/com/welli/cpmwheel/TriggerLogic.java src/main/java/com/welli/cpmwheel/LookLogic.java tools/LookLogicCheck.java && java -cp /tmp/lk LookLogicCheck */
public class LookLogicCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }
    static boolean near(float a, float b, float e) { return Math.abs(a - b) <= e; }

    public static void main(String[] args) {
        LookLogic.Opts o = new LookLogic.Opts(); o.radius = 16; o.viewFov = 70; o.aspect = 16f / 9f;
        // projection: yaw 0 looks toward +Z, +yaw turns right toward -X
        float[] c = LookLogic.project(0, 0, 5, 0, 0, 70, 16f / 9f);
        ok("straight ahead projects to the centre", c != null && near(c[0], 0, 1e-3f) && near(c[1], 0, 1e-3f));
        float[] r = LookLogic.project(-1, 0, 5, 0, 0, 70, 16f / 9f);
        ok("target to the right (-X when facing +Z) is right of centre", r != null && r[0] > 0);
        float[] u = LookLogic.project(0, 1, 5, 0, 0, 70, 16f / 9f);
        ok("target above is above the centre", u != null && u[1] > 0);
        ok("behind = not on screen", LookLogic.project(0, 0, -5, 0, 0, 70, 1.7f) == null);
        ok("yaw 90 faces -X", near(LookLogic.project(-5, 0, 0, 90, 0, 70, 1.7f)[0], 0, 1e-3f));
        float[] dn = LookLogic.project(0, -5 * Math.tan(Math.toRadians(30)), 5, 0, 30, 70, 1.7f);
        ok("looking down 30°: a point 30° below is at the centre", dn != null && Math.abs(dn[1]) < 0.01f && Math.abs(dn[0]) < 0.01f);
        // screen-area mode chooses closest to the centre, not nearest in distance
        Cand nearSide = new Cand(1, -3, 0, 3);      // 2 blocks close but 45° to the right: outside a 20 % circle
        Cand farCentre = new Cand(2, 0.2, 0, 12);    // far but almost in the crosshair
        ok("screen: the one at the crosshair wins over a closer one off to the side", LookLogic.pick(List.of(nearSide, farCentre), o, 0, 0, -1) == 2);
        o.mode = "nearest";
        ok("nearest mode (old): the closer one wins", LookLogic.pick(List.of(nearSide, farCentre), o, 0, 0, -1) == 1);
        o.mode = "screen";
        ok("screen: outside the area = no target", LookLogic.pick(List.of(nearSide), o, 0, 0, -1) == -1);
        ok("max range still applies", LookLogic.pick(List.of(new Cand(3, 0, 0, 30)), o, 0, 0, -1) == -1);
        Cand rej = new Cand(4, 0, 0, 5); rej.ok = false;
        ok("rejected by filters (invisible/vanish/freecam/LOS) is never picked", LookLogic.pick(List.of(rej), o, 0, 0, -1) == -1);
        // ties: same screen spot, distance breaks the tie
        ok("same screen spot: the closer one wins", LookLogic.pick(List.of(new Cand(5, 0, 0, 10), new Cand(6, 0, 0, 4)), o, 0, 0, -1) == 6);
        // rectangle + offset
        o.shape = "rect"; o.areaW = 20; o.areaH = 10;
        ok("rect: inside", LookLogic.areaDist(5, 2, o, 0) <= 1 && LookLogic.areaDist(11, 0, o, 0) > 1 && LookLogic.areaDist(0, 6, o, 0) > 1);
        o.offX = 10; ok("rect with offset: centre moved", LookLogic.areaDist(10, 0, o, 0) == 0 && LookLogic.areaDist(-1, 0, o, 0) > 1);
        o.offX = 0; o.shape = "circle"; o.areaR = 20;
        ok("circle radius is in % of the height (round on screen)", near(LookLogic.areaDist(0, 20, o, 0), 1, 1e-4f) && near(LookLogic.areaDist(20 / o.aspect, 0, o, 0), 1, 1e-4f));
        // stickiness: two targets close to the centre, current keeps going unless the other is clearly better
        Cand a = new Cand(7, -0.30, 0, 10), b = new Cand(8, 0.25, 0, 10);
        int first = LookLogic.pick(List.of(a, b), o, 0, 0, -1);
        ok("picks the more centred one first", first == 8);
        ok("sticky: keeps the current one when the other is only slightly better", LookLogic.pick(List.of(a, b), o, 0, 0, 7) == 7);
        o.sticky = false;
        ok("without stickiness it switches", LookLogic.pick(List.of(a, b), o, 0, 0, 7) == 8);
        o.sticky = true;
        ok("sticky: switches when the other is clearly better", LookLogic.pick(List.of(new Cand(7, -1.5, 0, 10), new Cand(8, 0, 0, 10)), o, 0, 0, 7) == 8);
        {   double edge = 10 * Math.tan(Math.toRadians(35)) * 0.20 * 2 * 1.1;   // 10 % past the 20 % circle edge (horizontal = vertical units)
            Cand past = new Cand(7, -edge, 0, 10);
            ok("sticky: current kept a bit past the edge, new one not picked there", LookLogic.pick(List.of(past), o, 0, 0, 7) == 7 && LookLogic.pick(List.of(past), o, 0, 0, -1) == -1); }
        // smoothing
        LookLogic.Smooth s = new LookLogic.Smooth(); s.respMs = 150; s.maxSpeed = 0; s.returnMs = 500; s.switchMs = 0;
        LookLogic.Eye e = new LookLogic.Eye(); float max = 0;
        for (int i = 0; i < 40; i++) { float[] v = LookLogic.step(e, true, 30, -10, false, s, 0.05f, null); max = Math.max(max, v[0]); }
        ok("spring: reaches the target", near(e.y, 30, 0.05f) && near(e.p, -10, 0.05f));
        ok("spring: no overshoot", max <= 30.0001f);
        LookLogic.Eye e1 = new LookLogic.Eye(), e2 = new LookLogic.Eye();
        for (int i = 0; i < 6; i++) LookLogic.step(e1, true, 30, 0, false, s, 0.05f, null);
        for (int i = 0; i < 30; i++) LookLogic.step(e2, true, 30, 0, false, s, 0.01f, null);
        ok("frame-rate independent (0.3 s at 20 Hz vs 100 Hz: " + e1.y + " / " + e2.y + ")", near(e1.y, e2.y, 1.0f));
        s.maxSpeed = 100; LookLogic.Eye e3 = new LookLogic.Eye();
        LookLogic.step(e3, true, 60, 0, false, s, 0.05f, null);
        ok("max angular speed 100°/s -> at most 5° per 0.05 s", e3.y <= 5.0001f);
        s.maxSpeed = 0;
        // return to centre after the delay
        for (int i = 0; i < 8; i++) LookLogic.step(e, false, 0, 0, false, s, 0.05f, null);
        ok("holds the last spot during the return delay", near(e.y, 30, 0.5f));
        for (int i = 0; i < 40; i++) LookLogic.step(e, false, 0, 0, false, s, 0.05f, null);
        ok("then returns to the centre", Math.abs(e.y) < 0.1f);
        // switch smoothing: slower right after a change
        s.switchMs = 600; LookLogic.Eye f1 = new LookLogic.Eye(), f2 = new LookLogic.Eye();
        LookLogic.step(f1, true, 30, 0, true, s, 0.05f, null); LookLogic.step(f2, true, 30, 0, false, s, 0.05f, null);
        ok("target switch moves more softly", f1.y < f2.y);
        s.switchMs = 0;
        s.mode = "lerp"; LookLogic.Eye g = new LookLogic.Eye();
        for (int i = 0; i < 40; i++) LookLogic.step(g, true, 20, 0, false, s, 0.05f, null);
        ok("lerp reaches the target", near(g.y, 20, 0.05f));
        s.mode = "off"; LookLogic.Eye h = new LookLogic.Eye(); LookLogic.step(h, true, 20, 0, false, s, 0.05f, null);
        ok("off = instant", h.y == 20);
        s.mode = "spring"; s.saccade = 100; LookLogic.Eye k = new LookLogic.Eye(); Random rr = new Random(3); float mx = 0;
        for (int i = 0; i < 200; i++) { LookLogic.step(k, true, 0, 0, false, s, 0.05f, rr); mx = Math.max(mx, Math.abs(k.y)); }
        ok("saccades stay small (≤ 4°) " + mx, mx > 0.1f && mx <= 4.0001f);
        LookLogic.Eye bad = new LookLogic.Eye(); LookLogic.step(bad, true, Float.NaN, 0, false, s, 0.05f, rr);
        ok("NaN never sticks", Float.isFinite(bad.y));

        // smooth blink
        Live2dMath.Gen bl = new Live2dMath.Gen(); bl.kind = "blink"; bl.closeMs = 100; bl.holdMs = 50; bl.openMs = 150; bl.curve = "smooth";
        ok("blink: starts open", Live2dMath.blinkLevel(bl, 0) == 0);
        ok("blink: half closed at mid close", near(Live2dMath.blinkLevel(bl, 50), 0.5f, 1e-3f));
        ok("blink: closed during hold", Live2dMath.blinkLevel(bl, 120) == 1);
        ok("blink: opening", near(Live2dMath.blinkLevel(bl, 225), 0.5f, 1e-3f));
        ok("blink: over after close+hold+open", Live2dMath.blinkLevel(bl, 301) < 0);
        ok("smooth curve eases in/out", Live2dMath.ease("smooth", 0.1f) < 0.1f && Live2dMath.ease("smooth", 0.9f) > 0.9f);
        ok("curves end at 0 and 1", Live2dMath.ease("sine", 0) == 0 && near(Live2dMath.ease("sine", 1), 1, 1e-6f) && Live2dMath.ease("quick", 1) == 1 && Live2dMath.ease("linear", 0.5f) == 0.5f);
        Live2dMath.Gen old = new Live2dMath.Gen(); old.kind = "blink"; old.pulseMs = 200;
        int[] t = Live2dMath.blinkTimes(old);
        ok("old configs: 30/40/30 % of the old closed time", t[0] == 60 && t[1] == 80 && t[2] == 60);
        bl.periodMs = 1000; bl.rand = 0; bl.doubleChance = 100; Live2dMath.GenState gs = new Live2dMath.GenState();
        int starts = 0; float prev = 0; java.util.List<Integer> at = new java.util.ArrayList<>();
        for (int i = 0; i < 20 * 10; i++) { float lv = Live2dMath.stepGen(bl, gs, 0.05f, new Random(i)); if (lv > 0 && prev == 0) { starts++; at.add(i); } prev = lv; }
        boolean pairs = at.size() >= 4 && at.get(1) - at.get(0) <= 12 && at.get(2) - at.get(1) > 12;
        ok("double blink at 100 %: pairs, never triples (" + at + ")", pairs);
        bl.doubleChance = 0; gs = new Live2dMath.GenState(); at.clear(); prev = 0;
        for (int i = 0; i < 20 * 10; i++) { float lv = Live2dMath.stepGen(bl, gs, 0.05f, new Random(i)); if (lv > 0 && prev == 0) at.add(i); prev = lv; }
        boolean regular = true; for (int i = 1; i < at.size(); i++) regular &= at.get(i) - at.get(i - 1) > 12;
        ok("double blink at 0 %: single blinks", regular && at.size() >= 6);

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }
}
