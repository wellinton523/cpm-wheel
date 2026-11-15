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

    /** input sources: angles in degrees, speeds as 10° per block/s; "prev" = the previous link's angle */
    public static final String[] INPUTS = {"headYaw", "headPitch", "bodyYaw", "headRel", "walk", "fwd", "side", "vert", "prev"};
    /** how links sharing one CPM layer are combined */
    public static final String[] MODES = {"sum", "avg", "max"};

    /** 1.7.1: one weighted input of a link (Live2D style: the link is driven by the weighted sum of its inputs) */
    public static class Input {
        public String src = "headYaw";
        public int weight = 100;           // % (0..300)
        public boolean invert = false;
        public Input() {}
        public Input(String src, int weight, boolean invert) { this.src = src; this.weight = weight; this.invert = invert; }
    }

    /** one link (also the stored config object) */
    public static class Link {
        public String anim = "";           // CPM value layer
        /** 1.7.0 single input (read from old configs, moved into inputs by migrateLink) */
        public String input = "";
        /** 1.7.1 weighted inputs */
        public List<Input> inputs = new ArrayList<>();
        public int len = 100;              // % (10..400): longer = softer, slower swing
        public int weight = 100;           // % (10..500): heavier = more inertia
        public int damp = 30;              // % of critical damping (0..100): low = bouncy
        public int gain = 100;             // % input strength (0..300)
        public int min = -45, max = 45;    // output angle mapped to value 0 .. maxValue
        public boolean invert = false;
        public boolean lag = true;         // true = "atraso" (a − x), false = "segue" (a)
        /** 1.7.2 "Desativar quando" for this link only (the chain's list also applies) */
        public List<TriggerLogic.Cond> off = new ArrayList<>();
        public String offCombine = "or";
        public Link() {}
        public Link(String input, boolean lag) { inputs.add(new Input(input, 100, false)); this.lag = lag; }
        public Link add(String src, int weight, boolean invert) { inputs.add(new Input(src, weight, invert)); return this; }
    }

    /** one chain ("Cabelo", "Cauda"…) */
    public static class Chain {
        public String id = "", name = "";
        public boolean on = true;
        public List<Link> links = new ArrayList<>();
        /** 1.7.2 "Desativar quando": while these hold, the chain fades to rest and stops writing */
        public List<TriggerLogic.Cond> off = new ArrayList<>();
        public String offCombine = "or";
        public int fadeMs = 300;
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
     * 1.7.1 signed movement relative to the BODY facing (Minecraft yaw: 0 = +Z, 90 = −X):
     * {forward (+ forward, − backward), side (+ right, − left)} in the same units as vx / vz.
     */
    public static float[] bodyRelative(double vx, double vz, float bodyYawDeg) {
        double r = Math.toRadians(bodyYawDeg);
        double fx = -Math.sin(r), fz = Math.cos(r);   // forward
        double rx = -Math.cos(r), rz = -Math.sin(r);  // right
        return new float[]{(float) (vx * fx + vz * fz), (float) (vx * rx + vz * rz)};
    }

    // ---------- 1.7.2 suspend ("Desativar quando") ----------
    /** physics weight w (1 = full physics, 0 = rest) moves toward 0 while suspended and back to 1, over fadeMs */
    public static float fadeStep(float w, boolean suspended, int fadeMs, float dt) {
        float target = suspended ? 0 : 1;
        if (fadeMs <= 0) return target;
        float d = dt * 1000f / fadeMs;
        return w < target ? Math.min(target, w + d) : Math.max(target, w - d);
    }
    /** value written with weight w: rest + (v − rest)·w */
    public static int faded(int v, int rest, float w) { return Math.round(rest + (v - rest) * clamp(w, 0, 1)); }
    /** suspended and fully faded: stop writing this link (an animation may own the layer now) */
    public static boolean released(float w, boolean suspended) { return suspended && w <= 0f; }

    /** config 20 -> 21: the single input becomes a one-entry list at 100 % */
    public static void migrateLink(Link l) {
        if (l.inputs == null) l.inputs = new ArrayList<>();
        if (l.inputs.isEmpty() && l.input != null && !l.input.isBlank())
            l.inputs.add(new Input(java.util.Arrays.asList(INPUTS).contains(l.input) ? l.input : "headYaw", 100, false));
        l.input = "";
    }

    /** weighted sum of the link's inputs (before the link's own intensity); prevA = the previous link's angle */
    public static float drive(Link l, java.util.Map<String, Float> src, float prevA) {
        float x = 0;
        if (l.inputs == null) return 0;
        for (Input in : l.inputs) {
            if (in == null) continue;
            float v = "prev".equals(in.src) ? prevA : src == null ? 0 : src.getOrDefault(in.src, 0f);
            x += v * clamp(in.weight, 0, 300) / 100f * (in.invert ? -1 : 1);
        }
        return x;
    }

    /** steps a whole chain with named input sources; out[i] = output angle */
    public static float[] stepChain(List<Link> links, State[] st, java.util.Map<String, Float> src, float dt, float[] out) {
        for (int i = 0; i < links.size(); i++) {
            Link p = links.get(i);
            float x = drive(p, src, i == 0 ? 0 : st[i - 1].a) * clamp(p.gain, 0, 300) / 100f;
            step(st[i], x, p, dt);
            out[i] = output(st[i], x, p);
        }
        return out;
    }

    /**
     * 1.7.1 several links on one layer: combine their offsets from the rest value.
     * sum = rest + Σ offsets, avg = rest + mean, max = the offset farthest from rest; clamped to 0..maxValue.
     */
    public static int combine(int[] values, int[] rests, String mode, int maxValue) {
        if (values.length == 0) return 0;
        int rest = rests[0];
        float r;
        switch (mode == null ? "sum" : mode) {
            case "avg" -> { float t = 0; for (int i = 0; i < values.length; i++) t += values[i] - rests[i]; r = rest + t / values.length; }
            case "max" -> { int best = 0; for (int i = 0; i < values.length; i++) { int o = values[i] - rests[i]; if (Math.abs(o) > Math.abs(best)) best = o; } r = rest + best; }
            default -> { float t = 0; for (int i = 0; i < values.length; i++) t += values[i] - rests[i]; r = rest + t; }
        }
        return Math.round(clamp(r, 0, Math.max(0, maxValue)));
    }

    /**
     * Steps a whole chain. ext[i] = the body input of link i in degrees (ignored for "prev");
     * out[i] = the output angle. Returns out.
     */
    public static float[] stepChain(List<Link> links, State[] st, float[] ext, float dt, float[] out) {
        for (int i = 0; i < links.size(); i++) {
            Link p = links.get(i);
            float g = clamp(p.gain, 0, 300) / 100f;
            java.util.Map<String, Float> m = new java.util.HashMap<>();
            for (String k : INPUTS) m.put(k, ext[i]);  // test helper: every source reads ext[i]
            float x = drive(p, m, i == 0 ? 0 : st[i - 1].a) * g;
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
