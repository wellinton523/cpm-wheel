// javac -d /tmp/wm src/main/java/com/welli/cpmwheel/WorldMath.java src/main/java/com/welli/cpmwheel/Live2dMath.java src/main/java/com/welli/cpmwheel/TriggerLogic.java tools/WorldMathCheck.java && java -cp /tmp/wm WorldMathCheck
import com.welli.cpmwheel.Live2dMath;
import com.welli.cpmwheel.Live2dMath.Link;
import com.welli.cpmwheel.Live2dMath.State;
import com.welli.cpmwheel.WorldMath;
import java.util.List;

/** 1.9.2 offline check of "Movimento real no mundo" (anchor velocity -> chain inertia) */
public class WorldMathCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }
    static boolean near(float a, float b, float e) { return Math.abs(a - b) <= e; }
    static State[] st(int k) { State[] s = new State[k]; for (int i = 0; i < k; i++) s[i] = new State(); return s; }

    /** runs ticks of a constant velocity profile; returns the peak |output| of link i and the last output */
    static float[] run(List<Link> links, State[] s, float[] vel, int ticks, int i) {
        float[] out = new float[links.size()]; float peak = 0, signed = 0;
        if (!s[0].init) { float[][] z = new float[links.size()][]; for (int k = 0; k < z.length; k++) z[k] = new float[3]; WorldMath.stepInertia(links, s, z, 1f, 0.05f, out); }   // start at rest
        for (int t = 0; t < ticks; t++) {
            float[][] v = new float[links.size()][]; for (int k = 0; k < v.length; k++) v[k] = vel;
            WorldMath.stepInertia(links, s, v, 1f, 0.05f, out);
            if (Math.abs(out[i]) > peak) { peak = Math.abs(out[i]); signed = out[i]; }
        }
        return new float[]{peak, out[i], signed};
    }

    public static void main(String[] a) {
        // ---- axes / covered ----
        Link side = new Link("side", true);
        float[] ax = WorldMath.axes(side);
        ok("side input -> right axis 1", near(ax[1], 1, 1e-4f) && ax[0] == 0 && ax[2] == 0);
        Link hair = new Link("headYaw", true).add("bodyYaw", 50, true).add("fwd", 20, true);
        ax = WorldMath.axes(hair);
        ok("hair preset: right = head turn ×0.5 (strongest angle), fwd = its fwd input -0.2", near(ax[1], 0.5f, 1e-4f) && near(ax[0], -0.2f, 1e-4f) && ax[2] == 0);
        Link tail = new Link("bodyYaw", true).add("side", 30, true);
        ok("tail preset: a speed input on the axis wins (side −0.3)", near(WorldMath.axes(tail)[1], -0.3f, 1e-4f));
        Link fall = new Link("fall", true);
        ok("fall -> up axis negative", WorldMath.axes(fall)[2] < 0);
        ok("prev-only link has no own axes", WorldMath.axes(new Link("prev", true))[1] == 0);
        ok("covered: side -> bit 2", WorldMath.covered(side) == 2);
        ok("covered: hair = fwd + body + head", WorldMath.covered(hair) == (1 | 8 | 16));
        ok("covered: weight 0 doesn't count", WorldMath.covered(new Link().add("side", 0, false)) == 0);

        // ---- linkVel: nothing counted twice ----
        float[][] comp = {{1, 2, 3}, {10, 20, 30}, {100, 200, 300}, {1000, 2000, 3000}};
        float[] v = WorldMath.linkVel(comp, 0);
        ok("uncovered link gets everything", near(v[0], 1111, 1e-3f) && near(v[1], 2222, 1e-3f) && near(v[2], 3333, 1e-3f));
        v = WorldMath.linkVel(comp, 2);
        ok("side input: player speed not added on the right axis", near(v[1], 2220, 1e-3f) && near(v[0], 1111, 1e-3f));
        v = WorldMath.linkVel(comp, 8 | 16);
        ok("turn inputs: body turn and head rotation not added", near(v[0], 1001, 1e-3f));

        // ---- inertia ----
        List<Link> one = List.of(new Link("side", true));
        State[] s = st(1);
        float[] r = run(one, s, new float[]{0, 4.3f, 0}, 100, 0);
        ok("start strafing right: swings (peak > 5°)", r[0] > 5);
        ok("…to the same side as the side input's own lag (negative)", r[2] < 0);
        ok("steady motion settles back to rest", Math.abs(r[1]) < 0.5f);
        r = run(one, s, new float[]{0, 0, 0}, 100, 0);
        ok("stopping swings the other way, then rest", r[2] > 0 && Math.abs(r[1]) < 0.5f);
        // same as the side input's impulse (hold 0) in the normal chain
        Link imp = new Link("side", true); imp.inputs.get(0).hold = 0;
        State[] sm = st(1); float[] om = new float[1];
        java.util.Map<String, Float> src = new java.util.HashMap<>();
        src.put("side", 0f); Live2dMath.stepChain(List.of(imp), sm, src, 0.05f, om);
        src.put("side", 43f); float pk = 0;
        for (int t = 0; t < 60; t++) { Live2dMath.stepChain(List.of(imp), sm, src, 0.05f, om); pk = Math.max(pk, Math.abs(om[0])); }
        State[] s2 = st(1); run(one, s2, new float[]{0, 0, 0}, 1, 0); float pk2 = run(one, s2, new float[]{0, 4.3f, 0}, 60, 0)[0];
        ok("matches a pure-impulse side input of the same speed", near(pk, pk2, 0.5f));
        // segue link: still pure inertia (no held displacement while moving)
        Link seg = new Link("side", false);
        State[] s3 = st(1);
        r = run(List.of(seg), s3, new float[]{0, 4.3f, 0}, 100, 0);
        ok("\"segue\" link: swings then rests at 0", r[0] > 5 && Math.abs(r[1]) < 0.5f);
        // tips whip
        Link b = new Link("prev", true); b.len = 80;
        List<Link> two = List.of(new Link("side", true), b);
        State[] s4 = st(2);
        r = run(two, s4, new float[]{0, 4.3f, 0}, 100, 1);
        ok("prev link follows (whip)", r[0] > 1 && Math.abs(r[1]) < 0.5f);
        // gain / strength / invert
        Link g0 = new Link("side", true); g0.gain = 0;
        ok("gain 0 -> no inertia", run(List.of(g0), st(1), new float[]{0, 4.3f, 0}, 40, 0)[0] < 1e-4f);
        Link inv = new Link("side", true); inv.invert = true;
        ok("invert flips", run(List.of(inv), st(1), new float[]{0, 4.3f, 0}, 40, 0)[2] > 0);
        float[] o = new float[1]; State[] s5 = st(1);
        WorldMath.stepInertia(one, s5, new float[][]{{0, 0, 0}}, 0f, 0.05f, o);
        WorldMath.stepInertia(one, s5, new float[][]{{0, 50, 0}}, 0f, 0.05f, o);
        ok("strength 0 -> nothing", o[0] == 0);
        // restart at rest (epoch): init false -> output 0 even at speed
        State[] s6 = st(1); s6[0].init = false;
        WorldMath.stepInertia(one, s6, new float[][]{{0, 20, 0}}, 1f, 0.05f, o);
        ok("restart at the current speed: no impulse", Math.abs(o[0]) < 1e-3f);
        WorldMath.stepInertia(one, s6, new float[][]{{0, Float.NaN, 0}}, 1f, 0.05f, o);
        ok("NaN never escapes", !Float.isNaN(o[0]));

        // ---- velocity tracker ----
        WorldMath.Vel vt = new WorldMath.Vel();
        ok("first sample primes at the real speed", vt.add(0, 0, 8, 0.05f, 30, 14) && near(vt.v[2], 8, 1e-4f));
        vt.add(0, 0, 8, 0.05f, 30, 14);
        vt.add(0, 0, 200, 0.05f, 30, 14);
        ok("absurd speed: clamped, no reset, change limited", vt.primed && vt.v[2] <= 8 + 14.01f && vt.v[2] > 8);
        WorldMath.Vel v2 = new WorldMath.Vel(); v2.add(0, 0, 0, 0.05f, 30, 14); v2.add(0, 0, 29, 0.05f, 30, 14);
        ok("huge change per tick is clamped", v2.v[2] <= 14.01f);
        WorldMath.Vel vn = new WorldMath.Vel(); vn.add(1, 1, 1, 0.05f, 30, 14);
        ok("NaN resets", !vn.add(Float.NaN, 0, 0, 0.05f, 30, 14) && !vn.primed);

        // ---- model space ----
        float[] w = WorldMath.modelToWorld(new float[]{5, 0, 0}, 0, false);
        ok("yaw 0: model +x (left) -> world +X", w[0] > 0.2f && Math.abs(w[2]) < 1e-4f);
        w = WorldMath.modelToWorld(new float[]{0, 0, -4}, 0, false);
        ok("yaw 0: model front (−z) -> world +Z (facing)", w[2] > 0.2f);
        w = WorldMath.modelToWorld(new float[]{0, -8, 0}, 0, false);
        ok("top of the head ≈ 1.88 blocks", near(w[1], 1.875f, 0.01f));
        w = WorldMath.modelToWorld(new float[]{0, 24, 0}, 37, false);
        ok("feet at 0", near(w[1], 0, 0.01f));
        w = WorldMath.modelToWorld(new float[]{0, 0, -4}, 90, false);
        ok("yaw 90 faces −X", w[0] < -0.2f);
        float[] q = WorldMath.rotZYX(new float[]{0, -6, 0}, (float) Math.toRadians(40), 0, 0);
        ok("looking down moves the top of the head forward (−z)", q[2] < -3);
        float[] bv = WorldMath.toBody(0, 0, 3, 0);
        ok("toBody: +Z at yaw 0 = forward", near(bv[0], 3, 1e-4f) && near(bv[1], 0, 1e-4f));
        bv = WorldMath.toBody(-3, 0, 0, 0);
        ok("toBody: −X at yaw 0 = right", near(bv[1], 3, 1e-4f));
        float[] tv = WorldMath.turnVel(new float[]{0, 10, 3}, 0, 30, false, 0.05f);
        ok("turning right: a point at the lower back moves left", tv[1] < -0.3f);
        tv = WorldMath.turnVel(new float[]{0, -6, 0}, 0, 30, false, 0.05f);
        ok("turning: a point on the turn axis doesn't move", Math.abs(tv[0]) < 1e-3f && Math.abs(tv[1]) < 1e-3f);
        WorldMath.PoseIn pi = new WorldMath.PoseIn(); pi.crouch = true;
        ok("sneaking: head 4.2 px lower, body leans 0.5 rad", near(WorldMath.vanillaPose(WorldMath.HEAD, pi, false)[1], 4.2f, 1e-4f) && near(WorldMath.vanillaPose(WorldMath.BODY, pi, false)[3], 0.5f, 1e-4f));
        pi.crouch = false; pi.limbPos = 0; pi.limbAmt = 1;
        ok("walking: arms swing opposite", WorldMath.vanillaPose(WorldMath.RARM, pi, false)[3] < 0 && WorldMath.vanillaPose(WorldMath.LARM, pi, false)[3] > 0);
        pi.headPitch = 30;
        ok("noHead drops the head rotation", WorldMath.vanillaPose(WorldMath.HEAD, pi, true)[3] == 0 && WorldMath.vanillaPose(WorldMath.HEAD, pi, false)[3] > 0.5f);

        // ---- parts / mirror / guess ----
        ok("mirror part arm", WorldMath.mirrorPart("larm").equals("rarm") && WorldMath.mirrorPart("auto").equals("auto"));
        Live2dMath.Chain ch = new Live2dMath.Chain(); ch.name = "Asa"; ch.links.add(new Link("side", true)); ch.worldPart = "lleg"; ch.worldPct = 150;
        Live2dMath.Chain m = Live2dMath.mirror(ch, "m", " (esp.)", List.of());
        ok("mirror keeps strength, swaps leg, flips side axis", m.worldPct == 150 && m.worldPart.equals("rleg") && WorldMath.axes(m.links.get(0))[1] < 0);
        ok("copy keeps world settings", Live2dMath.copy(ch, "c").worldPct == 150 && Live2dMath.copy(ch, "c").worldPart.equals("lleg"));
        ok("new chain: on at 100 %, auto", new Live2dMath.Chain().worldPct == 100 && new Live2dMath.Chain().worldPart.equals("auto"));
        Live2dMath.Chain g = new Live2dMath.Chain(); g.name = "Cabelo";
        ok("guess: Cabelo -> head", WorldMath.guess(g) == WorldMath.HEAD);
        g.name = "Cauda"; ok("guess: Cauda -> body", WorldMath.guess(g) == WorldMath.BODY);
        g.name = "Manga direita"; ok("guess: Manga direita -> right arm", WorldMath.guess(g) == WorldMath.RARM);
        g.name = "x"; Link lk = new Link("headPitch", true); lk.anim = "thing_l"; g.links.add(lk);
        ok("guess: head inputs -> head", WorldMath.guess(g) == WorldMath.HEAD);

        System.out.println((n - fails) + "/" + n + " passed");
        if (fails > 0) System.exit(1);
    }
}
