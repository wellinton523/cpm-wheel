package com.welli.cpmwheel;

import java.lang.reflect.Method;

/** Glow parts on the first-person hand under shaders: swap CPM's "eyes" render type for an emissive entity type. */
public final class HandGlow {
    private HandGlow() {}
    public static final String[] MODES = {"off", "emissive", "auto"};
    /** from config (refreshed every 2 s / settings), never read from disk per frame */
    public static volatile String mode = "auto";
    /** true between CPM's renderHand and renderHandPost (first-person hand only) */
    public static volatile boolean inHand = false;

    private static Method inUse;
    private static Object api;
    private static boolean irisChecked, irisMissing;
    private static long lastCheck;
    private static boolean lastResult;

    /** Iris shader pack active? (reflection, re-checked at most every 500 ms; false if Iris is absent) */
    static boolean shadersOn() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500) return lastResult;
        lastCheck = now;
        if (!irisChecked) {
            irisChecked = true;
            for (String cn : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> c = Class.forName(cn);
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                    break;
                } catch (Throwable ignored) {}
            }
            irisMissing = inUse == null;
        }
        if (irisMissing) return lastResult = false;
        try { lastResult = Boolean.TRUE.equals(inUse.invoke(api)); } catch (Throwable t) { lastResult = false; }
        return lastResult;
    }

    public static boolean active() {
        if (!inHand) return false;
        String m = mode;
        return "emissive".equals(m) || ("auto".equals(m) && shadersOn());
    }
}
