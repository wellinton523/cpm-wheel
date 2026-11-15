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
 *         1.7.3: each input has a "Tranco ↔ Mantido" mix h (0..100 %). The held part xh moves the spring's equilibrium,
 *         so a sustained input keeps the link displaced: target = x − 2·xh, lag output = a − (x − xh) → settles at −xh
 *         (the same side as the swing, e.g. wings stay back while walking). h = 0 is the 1.7.0–1.7.2 behaviour
 *         (pure impulse: a steady input settles back to rest).
 *         "segue" (follow) = a itself. The output angle is mapped linearly from [min, max] to the CPM value 0..maxValue.
 */
public final class Live2dMath {
    private Live2dMath() {}

    /** input sources: angles in degrees, speeds as 10° per block/s; "prev" = the previous link's angle */
    public static final String[] INPUTS = {"headYaw", "headPitch", "bodyYaw", "headRel", "walk", "fwd", "side", "vert",
            // 1.8.0: falling speed, gravity / look (pitch), wind, damage / landing impulses, look-at, voice
            "fall", "grav", "windF", "windS", "hit", "land", "lookYaw", "lookPitch", "voice", "layer", "prev"};
    /** 1.8.0: sources whose sign flips when a chain is mirrored (left <-> right) */
    public static final String[] MIRROR = {"headYaw", "bodyYaw", "headRel", "side", "windS", "lookYaw"};
    /** how links sharing one CPM layer are combined */
    public static final String[] MODES = {"sum", "avg", "max"};

    /** 1.7.1: one weighted input of a link (Live2D style: the link is driven by the weighted sum of its inputs) */
    public static class Input {
        public String src = "headYaw";
        public int weight = 100;           // % (0..300)
        public boolean invert = false;
        /** 1.7.3 "Tranco ↔ Mantido": 0 = only the swing on changes (old), 100 = the input holds the link displaced */
        public int hold = 0;
        /** 1.8.0 source "layer" (Camada/animação em movimento): the CPM animation / value layer read, and how:
         *  "rate" = how fast its value changes, "value" = its value, "osc" = while active, a push oscillating at refHz10/10 Hz */
        public String ref = "";
        public String refMode = "rate";
        public int refHz10 = 40;
        public Input() {}
        public Input(String src, int weight, boolean invert) { this.src = src; this.weight = weight; this.invert = invert; this.hold = defaultHold(src); }
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
        /** 1.8.0 soft angle limit (off by default): the output approaches [limLo, limHi] smoothly instead of passing it */
        public boolean softLim = false;
        public int limLo = -45, limHi = 45;
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

    /** 1.7.3: velocity inputs default to mostly sustained, angles (head / body) stay impulse-only like before */
    public static final int HOLD_SPEED = 75;
    public static boolean isSpeed(String src) { return "walk".equals(src) || "fwd".equals(src) || "side".equals(src) || "vert".equals(src)
            || "fall".equals(src) || "windF".equals(src) || "windS".equals(src); }
    /** 1.8.0: gravity, look-at and voice keep their value (100 %); impulses are already one-shot (0 %) */
    public static int defaultHold(String src) {
        if ("grav".equals(src) || "lookYaw".equals(src) || "lookPitch".equals(src) || "voice".equals(src)) return 100;
        return isSpeed(src) ? HOLD_SPEED : 0;
    }
    /** config 22 -> 23: existing velocity inputs gain the sustained behaviour */
    public static void migrateHold(Link l) {
        if (l == null || l.inputs == null) return;
        for (Input in : l.inputs) if (in != null && in.hold == 0) in.hold = defaultHold(in.src);
    }

    /** 1.7.3 speed smoothing: exponential low-pass with time constant tau (s), then a small dead zone around 0 */
    public static float smooth(float prev, float raw, float dt, float tau) {
        if (Float.isNaN(prev) || Float.isInfinite(prev)) prev = 0;
        if (Float.isNaN(raw) || Float.isInfinite(raw)) raw = 0;
        float k = tau <= 0 ? 1 : 1 - (float) Math.exp(-dt / tau);
        return prev + (raw - prev) * k;
    }
    public static float deadZone(float v, float dz) { return Math.abs(v) < dz ? 0 : v; }

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
            float v = "prev".equals(in.src) ? prevA : src == null ? 0 : src.getOrDefault(key(in), 0f);
            x += v * clamp(in.weight, 0, 300) / 100f * (in.invert ? -1 : 1);
        }
        return x;
    }

    /** 1.7.3: the held part of the drive (Σ contribution · hold %); "prev" is never held */
    public static float held(Link l, java.util.Map<String, Float> src) {
        float x = 0;
        if (l.inputs == null) return 0;
        for (Input in : l.inputs) {
            if (in == null || "prev".equals(in.src)) continue;
            float v = src == null ? 0 : src.getOrDefault(key(in), 0f);
            x += v * clamp(in.weight, 0, 300) / 100f * (in.invert ? -1 : 1) * clamp(in.hold, 0, 100) / 100f;
        }
        return x;
    }

    /** 1.7.3: one link step with drive x and held part xh (both after the link's intensity); returns the output angle */
    public static float stepLink(State s, float x, float xh, Link p, float dt) {
        if (!p.lag) { step(s, x, p, dt); return output(s, x, p); }  // "segue" already keeps a held input
        step(s, x - 2 * xh, p, dt);
        return output(s, x - xh, p);
    }

    /** steps a whole chain with named input sources; out[i] = output angle */
    public static float[] stepChain(List<Link> links, State[] st, java.util.Map<String, Float> src, float dt, float[] out) {
        for (int i = 0; i < links.size(); i++) {
            Link p = links.get(i);
            float g = clamp(p.gain, 0, 300) / 100f;
            float x = drive(p, src, i == 0 ? 0 : st[i - 1].a) * g;
            out[i] = limit(p, stepLink(st[i], x, held(p, src) * g, p, dt));
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
            out[i] = stepLink(st[i], x, held(p, m) * g, p, dt);
        }
        return out;
    }

    /** keeps a continuous angle (no jump from 179 to -180) */
    public static float unwrap(float prevUnwrapped, float raw) {
        float d = raw - prevUnwrapped;
        d = (float) (d - 360.0 * Math.floor((d + 180.0) / 360.0));
        return prevUnwrapped + d;
    }

    // ---------- 1.8.0 soft limit ----------
    /** identity inside [lo + k, hi − k]; beyond it approaches lo / hi exponentially (continuous, slope 1 at the knee) */
    public static float softClamp(float x, float lo, float hi) {
        if (Float.isNaN(x)) return (lo + hi) / 2;
        if (lo > hi) { float t = lo; lo = hi; hi = t; }
        float k = Math.min(10f, (hi - lo) / 4f);
        if (k <= 0) return lo;
        if (x > hi - k) return hi - k * (float) Math.exp(-(x - (hi - k)) / k);
        if (x < lo + k) return lo + k * (float) Math.exp(-((lo + k) - x) / k);
        return x;
    }
    public static float limit(Link l, float out) { return l != null && l.softLim ? softClamp(out, l.limLo, l.limHi) : out; }

    // ---------- 1.8.0 one-shot impulses (damage, landing) ----------
    public static final class Impulse { public float v; }
    /** a new hit replaces a smaller one (no endless stacking) */
    public static void kick(Impulse i, float peak) { if (peak > i.v) i.v = Math.min(120f, peak); }
    /** returns the current value, then decays with time constant tau (s) */
    public static float decay(Impulse i, float dt, float tau) {
        float out = i.v;
        i.v *= (float) Math.exp(-dt / Math.max(0.01f, tau));
        if (i.v < 0.05f) i.v = 0;
        return out;
    }
    /** damage in HP (half hearts) -> degrees */
    public static float hitPeak(float damage) { return Math.min(90f, Math.max(0f, damage) * 8f); }
    /** landing speed in blocks/s -> degrees (normal jumps ≈ 0) */
    public static float landPeak(float fallSpeed) { return Math.min(90f, Math.max(0f, fallSpeed - 5f) * 6f); }

    // ---------- 1.8.0 wind ----------
    /** biome id -> wind factor (open / high places windy, forests / caves calm) */
    public static float biomeWind(String id) {
        if (id == null) return 1f;
        String b = id.toLowerCase(java.util.Locale.ROOT);
        if (b.contains("deep_dark") || b.contains("cave")) return 0.3f;
        if (b.contains("peak") || b.contains("windswept") || b.contains("slope") || b.contains("mountain") || b.contains("stony_shore")) return 1.8f;
        if (b.contains("ocean") || b.contains("beach") || b.contains("plains") || b.contains("savanna") || b.contains("desert") || b.contains("badlands") || b.contains("tundra") || b.contains("snowy")) return 1.3f;
        if (b.contains("forest") || b.contains("jungle") || b.contains("swamp") || b.contains("grove") || b.contains("taiga")) return 0.7f;
        return 1f;
    }
    /** base wind (degrees): 12° × biome, rain ×1.8, thunder ×3, higher = stronger (up to ×2.5 at y ≥ 208), roofed ×0.15 */
    public static float windStrength(boolean rain, boolean thunder, float biome, double y, boolean sky) {
        float w = 12f * Math.max(0f, biome);
        if (thunder) w *= 3f; else if (rain) w *= 1.8f;
        w *= 1f + clamp((float) (y - 64) / 96f, 0f, 1.5f);
        if (!sky) w *= 0.15f;
        return w;
    }
    /** gust multiplier (≈0.3..1.7): slow swell + faster flutter + occasional strong gusts; deterministic in t (s) */
    public static float gust(double t, int seed) {
        double s = seed * 0.6180339887;
        double g = 0.75 + 0.2 * Math.sin(0.31 * t + s) + 0.1 * Math.sin(1.7 * t + 2 * s) + 0.05 * Math.sin(4.3 * t + 3 * s);
        double burst = Math.sin(0.13 * t + 5 * s);
        if (burst > 0.8) g += (burst - 0.8) * 3.5;  // gust: up to +0.7
        return (float) clamp((float) g, 0.2f, 1.8f);
    }
    /** direction the wind blows toward (Minecraft yaw, degrees), slowly turning */
    public static float windDir(double t, int seed) { return (float) (seed * 47 % 360 + 40 * Math.sin(0.02 * t + seed)); }
    /** wind relative to the body: {front (+ = from the front, like walking forward), side (+ = from the right, like strafing right)} */
    public static float[] windRel(float strength, float dirDeg, float bodyYaw) {
        double r = Math.toRadians(dirDeg);
        float[] rel = bodyRelative(-Math.sin(r) * strength, Math.cos(r) * strength, bodyYaw);
        return new float[]{-rel[0], -rel[1]};
    }

    // ---------- 1.8.0 look-at ----------
    /** angles from the head to a target offset (dx, dy, dz): {yaw (+ = right), pitch (+ = below), 1 if visible else 0}; clamped ±maxYaw / ±45 */
    public static float[] lookAngles(double dx, double dy, double dz, float headYaw, float headPitch, float maxYaw) {
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1e-6 && Math.abs(dy) < 1e-6) return new float[]{0, 0, 0};
        float yawT = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitchT = (float) -Math.toDegrees(Math.atan2(dy, h));
        float ry = unwrap(0, yawT - headYaw);
        if (Math.abs(ry) > 120) return new float[]{0, 0, 0};       // behind: no tracking
        float rp = pitchT - headPitch;
        return new float[]{clamp(ry, -maxYaw, maxYaw), clamp(rp, -45, 45), 1};
    }

    // ---------- 1.8.0 breathing / blinking generators ----------
    public static class Gen {
        public String id = "", name = "";
        public boolean on = true;
        /** "breath" (smooth oscillation) or "blink" (short pulse at random intervals) */
        public String kind = "breath";
        public String anim = "";
        /** breath: one cycle; blink: mean interval between blinks (ms) */
        public int periodMs = 3500;
        /** value range in % of the layer max (lo = rest) */
        public int lo = 0, hi = 100;
        /** random variation of period / amplitude (%) */
        public int rand = 15;
        /** blink: how long the eye is closed (ms) */
        public int pulseMs = 150;
    }
    public static final class GenState { public double phase; public float amp = 1, mul = 1, t, next = -1, pulse = -1; }
    /** advances a generator, returns its level 0..1 */
    public static float stepGen(Gen g, GenState s, float dt, java.util.Random r) {
        float rnd = clamp(g.rand, 0, 100) / 100f;
        float period = Math.max(200f, g.periodMs);
        if ("blink".equals(g.kind)) {
            if (s.next < 0) s.next = Math.max(200f, period * (1 + rnd * (2 * r.nextFloat() - 1)));
            float ms = dt * 1000f;
            if (s.pulse < 0) {
                s.t += ms;
                if (s.t < s.next) return 0;
                s.pulse = 0; s.t = 0;
            }
            s.pulse += ms;
            float len = Math.max(50f, g.pulseMs), p = s.pulse / len;
            if (p >= 1) { s.pulse = -1; s.next = Math.max(200f, period * (1 + rnd * (2 * r.nextFloat() - 1))); return 0; }
            return p < 0.3f ? p / 0.3f : p < 0.7f ? 1f : (1 - p) / 0.3f;
        }
        s.phase += dt * 1000.0 / (period * s.mul) * 2 * Math.PI;
        if (s.phase >= 2 * Math.PI) {
            s.phase -= 2 * Math.PI;
            s.mul = 1 + rnd * (2 * r.nextFloat() - 1) * 0.5f;
            s.amp = 1 - rnd * r.nextFloat() * 0.5f;
        }
        return (float) (0.5 - 0.5 * Math.cos(s.phase)) * s.amp;
    }
    /** level -> CPM value inside [lo, hi] % of maxValue */
    public static int genValue(Gen g, float level, int maxValue) {
        float lo = clamp(g.lo, 0, 100), hi = clamp(g.hi, 0, 100);
        return Math.round((lo + (hi - lo) * clamp(level, 0, 1)) / 100f * Math.max(0, maxValue));
    }

    // ---------- 1.8.0 mirror + copy ----------
    public static Input copy(Input i) { Input n = new Input(); n.src = i.src; n.weight = i.weight; n.invert = i.invert; n.hold = i.hold;
        n.ref = i.ref; n.refMode = i.refMode; n.refHz10 = i.refHz10; return n; }

    // ---------- 1.8.0 "Camada/animação (movimento)" ----------
    public static final String[] REF_MODES = {"rate", "value", "osc"};
    /** map key of an input's value: the source name, or one key per watched layer + mode */
    public static String key(Input in) {
        return "layer".equals(in.src) ? "layer|" + in.ref + "|" + in.refMode + "|" + in.refHz10 : in.src;
    }
    public static final class LayerSig { public float prev = -1; public double phase; }
    /** value (≤ 0 / -1 = off) of a CPM animation with max value -> degrees: rate ×30 per (fraction/s) clamped ±90, value ×45, osc 45·sin */
    public static float layerSignal(LayerSig s, int value, int max, float dt, String mode, float hz) {
        float n = value <= 0 ? 0 : Math.min(1f, value / (float) Math.max(1, max));
        float prev = s.prev < 0 ? n : s.prev;
        s.prev = n;
        if ("value".equals(mode)) return n * 45f;
        if ("osc".equals(mode)) {
            if (n <= 0) { s.phase = 0; return 0; }
            s.phase += dt * Math.max(0.1f, hz) * 2 * Math.PI;
            if (s.phase > 2 * Math.PI * 1000) s.phase -= 2 * Math.PI * 1000;
            return (float) (n * 45f * Math.sin(s.phase));
        }
        return clamp((n - prev) / Math.max(1e-3f, dt) * 30f, -90f, 90f);
    }
    public static TriggerLogic.Cond copy(TriggerLogic.Cond c) { TriggerLogic.Cond n = new TriggerLogic.Cond(c.type, c.value, c.num); n.not = c.not; return n; }
    public static Link copy(Link l) {
        Link n = new Link();
        n.anim = l.anim; n.len = l.len; n.weight = l.weight; n.damp = l.damp; n.gain = l.gain; n.min = l.min; n.max = l.max;
        n.invert = l.invert; n.lag = l.lag; n.offCombine = l.offCombine; n.softLim = l.softLim; n.limLo = l.limLo; n.limHi = l.limHi;
        if (l.inputs != null) for (Input i : l.inputs) if (i != null) n.inputs.add(copy(i));
        if (l.off != null) for (TriggerLogic.Cond c : l.off) if (c != null) n.off.add(copy(c));
        return n;
    }
    public static Chain copy(Chain c, String newId) {
        Chain n = new Chain();
        n.id = newId; n.name = c.name; n.on = c.on; n.offCombine = c.offCombine; n.fadeMs = c.fadeMs;
        for (Link l : c.links) if (l != null) n.links.add(copy(l));
        if (c.off != null) for (TriggerLogic.Cond q : c.off) if (q != null) n.off.add(copy(q));
        return n;
    }
    /** copy with left/right flipped: lateral and yaw inputs inverted, layers renamed left<->right when such a layer exists (else blank) */
    public static Chain mirror(Chain c, String newId, String suffix, java.util.Collection<String> layers) {
        Chain n = copy(c, newId);
        n.name = (c.name == null ? "" : c.name) + suffix;
        List<String> ms = java.util.Arrays.asList(MIRROR);
        for (Link l : n.links) {
            for (Input i : l.inputs) if (ms.contains(i.src)) i.invert = !i.invert;
            String m = mirrorName(l.anim);
            l.anim = m != null && layers != null && layers.contains(m) ? m : "";
        }
        return n;
    }
    private static final String[][] SIDES = {{"left", "right"}, {"esquerda", "direita"}, {"esquerdo", "direito"}, {"esq", "dir"}, {"l", "r"}, {"e", "d"}};
    /** "asa_esq" -> "asa_dir", "WingLeft" not split (only _ - . space separated tokens and Left/Right words); null if nothing to swap */
    public static String mirrorName(String anim) {
        if (anim == null || anim.isBlank()) return null;
        StringBuilder out = new StringBuilder(); boolean changed = false;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+").matcher(anim);
        while (m.find()) {
            String tok = m.group(), low = tok.toLowerCase(java.util.Locale.ROOT), rep = null;
            for (String[] p : SIDES) { if (low.equals(p[0])) rep = p[1]; else if (low.equals(p[1])) rep = p[0]; if (rep != null) break; }
            if (rep == null) {   // CamelCase words: WingLeft / AsaDireita
                for (String[] p : SIDES) {
                    if (p[0].length() < 3) continue;
                    String a = cap(p[0]), b = cap(p[1]);
                    if (tok.endsWith(a)) { rep = tok.substring(0, tok.length() - a.length()) + b; break; }
                    if (tok.endsWith(b)) { rep = tok.substring(0, tok.length() - b.length()) + a; break; }
                }
                if (rep != null) { out.append(rep); changed = true; continue; }
                out.append(tok); continue;
            }
            if (tok.equals(tok.toUpperCase(java.util.Locale.ROOT)) && !tok.equals(low)) rep = rep.toUpperCase(java.util.Locale.ROOT);
            else if (Character.isUpperCase(tok.charAt(0))) rep = cap(rep);
            out.append(rep); changed = true;
        }
        return changed ? out.toString() : null;
    }
    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---------- 1.8.0 presets as text ----------
    public static final String PRESET_PREFIX = "CPMW-L2D1:";
    public static String wrapPreset(String json) {
        return PRESET_PREFIX + java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** clipboard text -> JSON (prefixed Base64 or plain JSON), null if it isn't a preset */
    public static String unwrapPreset(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith(PRESET_PREFIX)) {
            try { return new String(java.util.Base64.getDecoder().decode(t.substring(PRESET_PREFIX.length()).replaceAll("\\s", "")), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException e) { return null; }
        }
        return t.startsWith("{") && t.endsWith("}") ? t : null;
    }

    static float clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
}
