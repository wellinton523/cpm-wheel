package com.welli.cpmwheel;

import java.util.Arrays;
import java.util.Map;

/**
 * 1.5.9 "Igual nas duas mãos" for one target ("ns:id", "grp:<id>", "mod:<ns>", "cat:<type>"):
 * one set of values (item adjust, arm adjust, hanging physics) edited once and written to both hands.
 * The stored numbers are arm-relative (the renderer already mirrors X position, Y and Z rotation by arm side),
 * so an identical copy IS the correct mirror for the other hand. Physics runs in camera space and is identical.
 * The per-hand "Inverter balanço" flag is kept per hand. Turning it off leaves two independent copies.
 */
public final class SameHands {
    private SameHands() {}
    private static final String[] H = {"main", "off"};

    public static boolean on(WheelConfig c, String base) { return c.haSame != null && c.haSame.contains(base); }

    /** do the two hands currently have different own values (so turning it on must ask which to keep)? */
    public static boolean differ(WheelConfig c, String base) {
        for (String kp : new String[]{"", "arm/"})
            if (!Arrays.equals(c.haItem.get(kp + base + "|main"), c.haItem.get(kp + base + "|off"))) return true;
        int[] a = c.haPhys.get(base + "|main"), b = c.haPhys.get(base + "|off");
        if (a == null || b == null) return a != b;
        a = a.clone(); b = b.clone(); a[PendulumMath.INVERT] = 0; b[PendulumMath.INVERT] = 0;
        return !Arrays.equals(a, b);
    }

    /** turn on, keeping the values of hand "from" (0 main, 1 off) for both */
    public static void enable(WheelConfig c, String base, int from) {
        copy(c, base, from);
        c.haSame.add(base);
        HandAnim.load(c);
    }
    public static void disable(WheelConfig c, String base) { c.haSame.remove(base); }

    /** after an edit of the main hand while on: mirror it to the off hand */
    public static void sync(WheelConfig c, String base) { if (on(c, base)) { copy(c, base, 0); HandAnim.load(c); } }

    static void copy(WheelConfig c, String base, int from) {
        String fs = "|" + H[from], ts = "|" + H[1 - from];
        for (String kp : new String[]{"", "arm/"}) put(c.haItem, kp + base + ts, c.haItem.get(kp + base + fs));
        int[] src = c.haPhys.get(base + fs), dst = c.haPhys.get(base + ts);
        if (src == null) c.haPhys.remove(base + ts);
        else {
            int[] n = src.clone();
            n[PendulumMath.INVERT] = dst != null ? dst[PendulumMath.INVERT] : 0; // invert stays per hand
            c.haPhys.put(base + ts, n);
        }
    }
    private static void put(Map<String, int[]> m, String k, int[] v) { if (v == null) m.remove(k); else m.put(k, v.clone()); }
}
