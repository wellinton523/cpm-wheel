import com.welli.cpmwheel.PendulumMath;

/**
 * Offline check of the hanging-physics math (1.5.9). No Minecraft needed:
 *   javac -d /tmp/pm src/main/java/com/welli/cpmwheel/PendulumMath.java tools/PendulumMathCheck.java && java -cp /tmp/pm PendulumMathCheck
 * The game runs the same PendulumMath.step for the main hand (index 0) and the off hand (index 1) with the same settings,
 * and applies the result in camera space with the same ZP(aZ)·XP(aX), so "both hands" = same numbers in, same motion out.
 */
public class PendulumMathCheck {
    static int fails = 0;
    static void check(String name, boolean ok) { System.out.println((ok ? "OK   " : "FAIL ") + name); if (!ok) fails++; }

    static PendulumMath.State run(int[] p, float side, boolean inv, java.util.function.Consumer<PendulumMath.In> in, float secs) {
        PendulumMath.State s = new PendulumMath.State();
        PendulumMath.In i = new PendulumMath.In();
        in.accept(i);
        for (float t = 0; t < secs; t += 1 / 60f) PendulumMath.step(s, p, i, 1 / 60f, side, inv);
        return s;
    }
    static int[] lantern() { return new int[]{1, 0, 80, 45, 35, 100, 45, 0, 100, 1, 0}; }

    public static void main(String[] a) {
        int[] L = lantern();
        int[] L0 = L.clone(); L0[PendulumMath.GRAVITY] = 0;
        // hands: the math has no hand input; run it twice exactly like the two per-hand states in the game
        PendulumMath.State m = run(L, 1, false, i -> i.yawRate = 180, 0.2f), o = run(L, 1, false, i -> i.yawRate = 180, 0.2f);
        check("main and off hand: same settings -> identical angles", m.aZ == o.aZ && m.aX == o.aX);
        // turning right: a hanging lantern lags to the LEFT on screen (both hands)
        float[] p = PendulumMath.massPos(m.aZ, m.aX, 1);
        check("turn right -> hanging lantern bottom moves left (x<0): x=" + p[0], p[0] < -0.02f);
        PendulumMath.State l2 = run(L, 1, false, i -> i.yawRate = -180, 0.2f);
        check("turn left -> bottom moves right", PendulumMath.massPos(l2.aZ, l2.aX, 1)[0] > 0.02f);
        // upright item (inverted pendulum): the TOP lags left when turning right
        PendulumMath.State up = run(L0, -1, false, i -> i.yawRate = 180, 0.2f);
        check("turn right -> upright item top moves left", PendulumMath.massPos(up.aZ, up.aX, -1)[0] < -0.02f);
        // walking forward (accelerating): the bottom swings back toward the camera (+z)
        int[] R = L0.clone(); R[PendulumMath.REACT] = 100;
        PendulumMath.State fw = run(R, 1, false, i -> i.fwdAcc = 8, 0.15f);
        check("accelerate forward -> bottom swings back (z>0)", PendulumMath.massPos(fw.aZ, fw.aX, 1)[2] > 0.02f);
        PendulumMath.State st = run(R, 1, false, i -> i.fwdAcc = -8, 0.15f);
        check("stop -> bottom swings forward (z<0)", PendulumMath.massPos(st.aZ, st.aX, 1)[2] < -0.02f);
        PendulumMath.State sr = run(R, 1, false, i -> i.strafeAcc = 8, 0.15f);
        check("strafe right -> bottom lags left", PendulumMath.massPos(sr.aZ, sr.aX, 1)[0] < -0.02f);
        // gravity: looking down 80° -> hangs straight down in the world: camera-space world-down = (0, -cos80, -sin80)
        PendulumMath.State dn = run(L, 1, false, i -> i.pitch = 80, 3f);
        float[] d = PendulumMath.massPos(dn.aZ, dn.aX, 1);
        double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d[1] * -Math.cos(Math.toRadians(80)) + d[2] * -Math.sin(Math.toRadians(80))))));
        check("look down 80°, gravity 100% + raised limit -> points to the ground (err " + Math.round(ang) + "°)", ang < 3);
        PendulumMath.State dn2 = run(L.clone(), 1, false, i -> i.pitch = 80, 3f);
        int[] Lnr = L.clone(); Lnr[PendulumMath.RAISE] = 0;
        PendulumMath.State dn3 = run(Lnr, 1, false, i -> i.pitch = 80, 3f);
        check("without raised limit it stops at the max angle (45°): " + Math.round(dn3.aX), Math.abs(dn3.aX - 45) < 0.6f);
        PendulumMath.State upl = run(L, 1, false, i -> i.pitch = -60, 3f);
        check("look up -> hangs back toward the player (z>0)", PendulumMath.massPos(upl.aZ, upl.aX, 1)[2] > 0.3f);
        PendulumMath.State g0 = run(L0, 1, false, i -> i.pitch = 80, 3f);
        check("gravity 0% -> stays fixed to the hand", Math.abs(g0.aX) < 0.5f && dn2.aX > 70);
        // invert escape hatch flips the sway, not the gravity rest
        PendulumMath.State iv = run(L, 1, true, i -> i.yawRate = 180, 0.2f);
        check("invert -> opposite sideways swing", Math.signum(iv.aZ) == -Math.signum(m.aZ) && iv.aZ != 0);
        // 1.5.8 bug reproduced: sideways angle multiplied by the arm side f (+1 right arm) in the hand frame
        float old = 180 * 0.9f / 45f; // static deflection sign for a right turn
        float oldRightBottomX = PendulumMath.massPos(old * 1 * 0.8f, 0, 1)[0], oldLeftBottomX = PendulumMath.massPos(old * -1 * 0.8f, 0, 1)[0];
        check("1.5.8 formula: right arm moved the opposite way of the left arm (the reported inversion)", Math.signum(oldRightBottomX) != Math.signum(oldLeftBottomX) && oldRightBottomX > 0);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
