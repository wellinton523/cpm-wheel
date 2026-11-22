package com.welli.cpmwheel;

import java.util.List;
import java.util.Random;

/**
 * 1.8.3 "Olhar" (look-at) logic without Minecraft classes, so it can be checked offline:
 * target choice (screen area around the centre of the player's own view, or nearest), stickiness, and the eye smoothing
 * (critically damped spring / exponential / off, max angular speed, small saccades, return-to-centre delay).
 * Angles in degrees; dt in seconds. Every step uses dt, so the result does not depend on how often it runs.
 */
public final class LookLogic {
    private LookLogic() {}

    // ---------- candidates ----------
    /** one possible target, already measured from the real player's eyes (never from a free camera) */
    public static final class Cand {
        public int id; public double dx, dy, dz;
        /** filters done by the caller (false = rejected) */
        public boolean ok = true;
        public Cand() {}
        public Cand(int id, double dx, double dy, double dz) { this.id = id; this.dx = dx; this.dy = dy; this.dz = dz; }
        public double dist2() { return dx * dx + dy * dy + dz * dz; }
    }

    public static final class Opts {
        /** "screen" (area around the centre of the view, default) or "nearest" */
        public String mode = "screen";
        public double radius = 8;
        /** "nearest" mode: cone around the head direction (full angle, degrees) */
        public float fov = 160;
        /** screen area: "circle" (radius in % of the screen height) or "rect" (width / height in % of the screen); offset in % */
        public String shape = "circle";
        public float areaR = 20, areaW = 40, areaH = 30, offX = 0, offY = 0;
        /** player's view: vertical FOV (degrees) and width / height of the window */
        public float viewFov = 70, aspect = 16f / 9f;
        /** stickiness: the current target keeps going while inside the area grown by this %, and a new one must be this much better */
        public boolean sticky = true; public float stickPct = 25;
    }

    /** forward / right / up of a Minecraft head (yaw 0 = +Z, + yaw turns right toward -X; + pitch looks down) */
    static double[] basis(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double fx = -Math.sin(y) * Math.cos(p), fy = -Math.sin(p), fz = Math.cos(y) * Math.cos(p);
        double rx = -Math.cos(y), rz = -Math.sin(y);            // right = forward x up(0,1,0)
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;   // up = right x forward
        return new double[]{fx, fy, fz, rx, 0, rz, ux, uy, uz};
    }

    /** projects an offset from the eyes to the player's view: {x, y} in % of the screen from the centre (+x right, +y up), or null if behind */
    public static float[] project(double dx, double dy, double dz, float yaw, float pitch, float vfov, float aspect) {
        double[] b = basis(yaw, pitch);
        double z = dx * b[0] + dy * b[1] + dz * b[2];
        if (z <= 0.05) return null;
        double x = dx * b[3] + dy * b[4] + dz * b[5], y = dx * b[6] + dy * b[7] + dz * b[8];
        double t = Math.tan(Math.toRadians(Math.max(1, Math.min(170, vfov))) / 2);
        double nx = x / (z * t * Math.max(0.1, aspect)), ny = y / (z * t);
        return new float[]{(float) (nx * 50), (float) (ny * 50)};
    }

    /** how far a screen point is from the area centre, 0 = centre, 1 = edge (grow = extra % for stickiness); x, y in % of the screen */
    public static float areaDist(float x, float y, Opts o, float grow) {
        float g = 1 + Math.max(0, grow) / 100f;
        float cx = x - o.offX, cy = y - o.offY;
        if ("rect".equals(o.shape)) {
            float hw = Math.max(0.5f, o.areaW / 2f) * g, hh = Math.max(0.5f, o.areaH / 2f) * g;
            return Math.max(Math.abs(cx) / hw, Math.abs(cy) / hh);
        }
        // circle: radius in % of the screen height; x is in % of the width, so scale it to height units
        float r = Math.max(0.5f, o.areaR) * g;
        float xs = cx * o.aspect;
        return (float) Math.sqrt(xs * xs + cy * cy) / r;
    }

    /** angle (degrees) between the head direction and the offset */
    public static float angleOff(double dx, double dy, double dz, float yaw, float pitch) {
        double[] b = basis(yaw, pitch);
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return 0;
        double c = (dx * b[0] + dy * b[1] + dz * b[2]) / l;
        return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    /** score of a candidate (lower = better), or NaN if it can't be a target; grow = stickiness growth for the current one */
    public static double score(Cand c, Opts o, float yaw, float pitch, float grow) {
        if (c == null || !c.ok) return Double.NaN;
        double d2 = c.dist2();
        if (d2 > o.radius * o.radius || d2 < 1e-6) return Double.NaN;
        double dist = Math.sqrt(d2);
        if ("nearest".equals(o.mode)) {
            float ang = angleOff(c.dx, c.dy, c.dz, yaw, pitch);
            if (ang > Math.max(10, o.fov) / 2f * (1 + grow / 100f)) return Double.NaN;
            return dist;
        }
        float[] s = project(c.dx, c.dy, c.dz, yaw, pitch, o.viewFov, o.aspect);
        if (s == null) return Double.NaN;
        float a = areaDist(s[0], s[1], o, grow);
        if (a > 1) return Double.NaN;
        // closeness to the centre decides; distance only breaks near-ties (adds at most 0.02)
        return a + 0.02 * dist / Math.max(1, o.radius);
    }

    /** picks the target id (-1 = none); keeps the current one while it still qualifies with stickiness and nobody is clearly better */
    public static int pick(List<Cand> cands, Opts o, float yaw, float pitch, int current) {
        int best = -1; double bs = Double.MAX_VALUE, cur = Double.NaN;
        float grow = o.sticky ? o.stickPct : 0;
        for (Cand c : cands) {
            double s = score(c, o, yaw, pitch, 0);
            if (c.id == current && o.sticky) cur = score(c, o, yaw, pitch, grow);
            if (!Double.isNaN(s) && s < bs) { bs = s; best = c.id; }
        }
        if (o.sticky && current >= 0 && !Double.isNaN(cur)) {
            if (best < 0 || best == current) return current;
            double margin = Math.max(0, 1 - o.stickPct / 100.0);
            // the newcomer must be clearly better (by stickPct) than the current one
            if (bs >= cur * margin) return current;
        }
        return best;
    }

    // ---------- smoothing ----------
    public static final class Smooth {
        /** "spring" (critically damped), "lerp" (exponential) or "off" */
        public String mode = "spring";
        public int respMs = 150, maxSpeed = 720, saccade = 0, returnMs = 600, switchMs = 250;
    }
    public static final class Eye {
        public float y, p, vy, vp, holdY, holdP, lost = 9999, sacY, sacP, sacT, switchT = 9999;
        public int target = -1;
    }

    /** one step: target angles (ty, tp) when hasTarget; returns {yaw, pitch} */
    public static float[] step(Eye e, boolean hasTarget, float ty, float tp, boolean switched, Smooth s, float dt, Random r) {
        if (!(dt > 0)) dt = 0.05f;
        dt = Math.min(dt, 0.5f);
        if (switched) e.switchT = 0; else e.switchT += dt;
        float gy, gp;
        if (hasTarget) { e.holdY = ty; e.holdP = tp; e.lost = 0; gy = ty; gp = tp; }
        else { e.lost += dt; boolean hold = e.lost * 1000f < Math.max(0, s.returnMs); gy = hold ? e.holdY : 0; gp = hold ? e.holdP : 0; }
        // small random fixation shifts (saccades): up to 4° at 100 %, new spot every 0.4 .. 1.6 s
        if (s.saccade > 0 && r != null) {
            e.sacT -= dt;
            if (e.sacT <= 0) { float a = 4f * Math.min(100, s.saccade) / 100f; e.sacY = (r.nextFloat() * 2 - 1) * a; e.sacP = (r.nextFloat() * 2 - 1) * a * 0.6f; e.sacT = 0.4f + r.nextFloat() * 1.2f; }
            gy += e.sacY; gp += e.sacP;
        } else { e.sacY = e.sacP = 0; }
        float resp = Math.max(0, s.respMs);
        if (e.switchT * 1000f < Math.max(0, s.switchMs)) resp = Math.max(resp, s.switchMs);   // softer move right after a target change
        float maxV = s.maxSpeed <= 0 ? Float.MAX_VALUE : s.maxSpeed;
        if ("off".equals(s.mode) || resp <= 0) { e.y = limit(e.y, gy, maxV * dt); e.p = limit(e.p, gp, maxV * dt); e.vy = e.vp = 0; }
        else if ("lerp".equals(s.mode)) {
            float k = 1 - (float) Math.exp(-dt / (resp / 1000f / 3f));
            e.y = limit(e.y, e.y + (gy - e.y) * k, maxV * dt); e.p = limit(e.p, e.p + (gp - e.p) * k, maxV * dt); e.vy = e.vp = 0;
        } else {
            float[] a = damp(e.y, gy, e.vy, resp / 1000f, maxV, dt); e.y = a[0]; e.vy = a[1];
            float[] b = damp(e.p, gp, e.vp, resp / 1000f, maxV, dt); e.p = b[0]; e.vp = b[1];
        }
        if (!Float.isFinite(e.y)) { e.y = 0; e.vy = 0; } if (!Float.isFinite(e.p)) { e.p = 0; e.vp = 0; }
        return new float[]{e.y, e.p};
    }

    static float limit(float from, float to, float maxStep) { float d = to - from; return Math.abs(d) <= maxStep ? to : from + Math.signum(d) * maxStep; }

    /** critically damped spring ("smooth damp"): reaches the goal in about smoothTime, no overshoot; speed capped at maxV; {value, velocity} */
    public static float[] damp(float cur, float goal, float vel, float smoothTime, float maxV, float dt) {
        smoothTime = Math.max(0.0001f, smoothTime);
        float omega = 2f / smoothTime, x = omega * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = cur - goal, orig = goal;
        float maxChange = maxV * smoothTime;
        if (Math.abs(change) > maxChange) change = Math.signum(change) * maxChange;
        goal = cur - change;
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * exp;
        float out = goal + (change + temp) * exp;
        if ((orig - cur > 0) == (out > orig)) { out = orig; vel = 0; }   // no overshoot
        // hard speed cap per step
        float step = out - cur;
        if (Math.abs(step) > maxV * dt) { out = cur + Math.signum(step) * maxV * dt; vel = Math.signum(step) * maxV; }
        return new float[]{out, vel};
    }
}
