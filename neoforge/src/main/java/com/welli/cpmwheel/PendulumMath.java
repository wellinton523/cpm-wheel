package com.welli.cpmwheel;

/**
 * 1.5.9 pure pendulum math for "Física de pendurado" (no Minecraft classes, checked offline by tools/PendulumMathCheck.java).
 *
 * Everything is in CAMERA space, at the item's pivot, so it is the same for both hands (no arm mirroring):
 *   aZ = rotation about the view axis (applied as ZP(aZ)): + = the part BELOW the pivot moves to the right on screen;
 *   aX = rotation about the screen X axis (applied as XP(aX), after ZP in the PoseStack): + = the part below the pivot moves away (forward).
 * side = +1 when the item's mass is below the pivot (lantern hanging from the fist, or pivot "topo"),
 *        -1 when it stands above it (items held upright in the fist: an inverted pendulum, the top lags).
 * Inputs (Minecraft conventions): yaw rate > 0 = turning right, pitch > 0 = looking down, accelerations in blocks/s²-ish.
 * Physics: things that do not rotate with the camera appear to move opposite to the turn (turning right -> lags left),
 * accelerating forward/right -> the mass lags back/left, gravity pulls the rest angle toward world-down (aX target = pitch).
 */
public final class PendulumMath {
    private PendulumMath() {}
    // settings indices
    public static final int ON = 0, PIVOT = 1, INTENSITY = 2, STIFF = 3, DAMP = 4, WEIGHT = 5, MAX = 6, REACT = 7, GRAVITY = 8, RAISE = 9, INVERT = 10, LEN = 11;

    public static final class State { public float aZ, aX, vZ, vX; }
    public static final class In {
        public float yawRate, pitchRate, pitch, strafeAcc, fwdAcc, vertAcc, walk;
    }

    /** angle the rest position points to (aX) for this camera pitch and gravity strength */
    public static float gravityTarget(int[] p, float pitch) {
        float g = p[GRAVITY] / 100f;
        return clamp(pitch * g, -90, 90);
    }

    public static void step(State s, int[] p, In in, float dt, float side, boolean invert) {
        float I = p[INTENSITY] / 100f, K = p[STIFF], C = p[DAMP] / 10f, W = Math.max(0.2f, p[WEIGHT] / 100f), M = p[MAX], R = p[REACT] / 100f;
        float g = p[GRAVITY] / 100f;
        float dyn = invert ? -1f : 1f;
        float tX = gravityTarget(p, in.pitch);
        // sway drives (angle space): turning / strafing / accelerating -> the mass lags behind (sign flips for an upright item);
        // pitch rate: the part of the item that does not follow gravity is dragged along with the hand, the world-fixed rest lags
        float dZ = dyn * side * (-in.yawRate * 0.9f - in.strafeAcc * 28f * R) * I;
        float dX = dyn * (in.pitchRate * 0.6f * (1f - g) + side * (-in.fwdAcc * 28f - in.vertAcc * 14f) * R + in.walk) * I;
        for (float t = dt; t > 0; t -= 0.01f) {
            float h = Math.min(0.01f, t);
            s.vZ += ((-K * s.aZ - C * s.vZ + dZ) / W) * h;
            s.vX += ((-K * (s.aX - tX) - C * s.vX + dX) / W) * h;
            s.aZ += s.vZ * h; s.aX += s.vX * h;
        }
        if (Math.abs(s.aZ) > M) { s.aZ = Math.copySign(M, s.aZ); s.vZ *= -0.3f; }
        // max angle: around the hand-fixed rest, or (raised limit) around the gravity rest so it can point straight down
        float lo = p[RAISE] == 1 ? Math.min(-M, tX - M) : -M, hi = p[RAISE] == 1 ? Math.max(M, tX + M) : M;
        lo = Math.max(lo, -100); hi = Math.min(hi, 100);
        if (s.aX < lo) { s.aX = lo; s.vX *= -0.3f; }
        if (s.aX > hi) { s.aX = hi; s.vX *= -0.3f; }
    }

    /** camera-space position of a point at distance 1 below (side=+1) or above (side=-1) the pivot after ZP(aZ)·XP(aX) */
    public static float[] massPos(float aZ, float aX, float side) {
        double z = Math.toRadians(aZ), x = Math.toRadians(aX);
        double vy = -side;                               // point below / above the pivot
        double y1 = vy * Math.cos(x), z1 = vy * Math.sin(x); // XP first (applied first to the vertex)
        double x2 = -y1 * Math.sin(z), y2 = y1 * Math.cos(z); // then ZP
        return new float[]{(float) x2, (float) y2, (float) z1};
    }

    static float clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
}
