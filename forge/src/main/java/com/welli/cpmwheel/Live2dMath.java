package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.0 "Física Live2D": pure chain math (no Minecraft classes; checked offline by tools/Live2dMathCheck.java).
 *
 * Every link is a damped spring that follows its input angle x with inertia: a'' = (K·(x − a) − C·a') / W.
 *   K comes from the length (longer = softer and slower), W from the weight, C is a fraction of critical damping.
 * The first link of a chain follows a body input (head yaw, head pitch, body yaw, walk speed, vertical speed);
 * the next links usually use "prev" = the previous link's angle, so the tips lag behind the center.
 * Output: "atraso" (lag) = a − x, the bend relative to what drives it (for a hair bone parented to the head / center),
 *         "segue" (follow) = a itself. The output angle is mapped linearly from [min, max] to the CPM value 0..maxValue.
 */
public final class Live2dMath {
    private Live2dMath() {}

    public static final String[] INPUTS = {"headYaw", "headPitch", "bodyYaw", "walk", "vert", "prev"};

    /** one link (also the stored config object) */
    public static class Link {
        public String anim = "";           // CPM value layer
        public String input = "headYaw";
        public int len = 100;              // % (10..400): longer = softer, slower swing
        public int weight = 100;           // % (10..500): heavier = more inertia
        public int damp = 30;              // % of critical damping (0..100): low = bouncy
        public int gain = 100;             // % input strength (0..300)
        public int min = -45, max = 45;    // output angle mapped to value 0 .. maxValue
        public boolean invert = false;
        public boolean lag = true;         // true = "atraso" (a − x), false = "segue" (a)
        public Link() {}
        public Link(String input, boolean lag) { this.input = input; this.lag = lag; }
    }

    /** one chain ("Cabelo", "Cauda"…) */
    public static class Chain {
        public String id = "", name = "";
        public boolean on = true;
        public List<Link> links = new ArrayList<>();
    }

    public static final class State { public float a, v; public boolean init; }

    /** spring constant for a length in %: 100 % ≈ 2 Hz */
    public static float stiffness(int len) {
        float l = clamp(len, 10, 400) / 100f;
        return 160f / (l * l);
    }

    /** advance one link toward input x by dt seconds */
    public static void step(State s, float x, Link p, float dt) {
        if (!s.init || Float.isNaN(s.a) || Float.isNaN(s.v) || Float.isInfinite(s.a)) { s.a = x; s.v = 0; s.init = true; }
        float K = stiffness(p.len), W = Math.max(0.1f, clamp(p.weight, 10, 500) / 100f);
        float C = clamp(p.damp, 0, 100) / 100f * 2f * (float) Math.sqrt(K * W);
        for (float t = dt; t > 1e-6f; t -= 0.005f) {
            float h = Math.min(0.005f, t);
            float acc = (K * (x - s.a) - C * s.v) / W;
            s.v += acc * h;
            s.a += s.v * h;
        }
        if (s.a - x > 180) { s.a = x + 180; s.v = 0; }
        if (s.a - x < -180) { s.a = x - 180; s.v = 0; }
    }

    /** the angle a link outputs */
    public static float output(State s, float x, Link p) {
        float o = p.lag ? s.a - x : s.a;
        return p.invert ? -o : o;
    }

    /** angle -> CPM value (0..maxValue), linear from [min, max]; min > max works reversed */
    public static int toValue(float angle, int min, int max, int maxValue) {
        if (maxValue <= 0) return 0;
        if (min == max) return Math.round(maxValue / 2f);
        float f = (angle - min) / (float) (max - min);
        f = clamp(f, 0, 1);
        return Math.round(f * maxValue);
    }

    /**
     * Steps a whole chain. ext[i] = the body input of link i in degrees (ignored for "prev");
     * out[i] = the output angle. Returns out.
     */
    public static float[] stepChain(List<Link> links, State[] st, float[] ext, float dt, float[] out) {
        for (int i = 0; i < links.size(); i++) {
            Link p = links.get(i);
            float g = clamp(p.gain, 0, 300) / 100f;
            float x = "prev".equals(p.input) ? (i == 0 ? 0 : st[i - 1].a) * g : ext[i] * g;
            step(st[i], x, p, dt);
            out[i] = output(st[i], x, p);
        }
        return out;
    }

    /** keeps a continuous angle (no jump from 179 to -180) */
    public static float unwrap(float prevUnwrapped, float raw) {
        float d = raw - prevUnwrapped;
        d = (float) (d - 360.0 * Math.floor((d + 180.0) / 360.0));
        return prevUnwrapped + d;
    }

    static float clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
}
