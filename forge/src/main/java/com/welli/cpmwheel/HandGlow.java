package com.welli.cpmwheel;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Glow parts on the first-person hand under shaders: swap CPM's "eyes" render type for entity-translucent + full-bright. */
public final class HandGlow {
    private HandGlow() {}
    private static final Logger LOG = LoggerFactory.getLogger("cpmwheel");
    public static final String[] MODES = {"off", "emissive", "auto"};
    /** from config (refreshed every 2 s / settings), never read from disk per frame */
    public static volatile String mode = "auto";
    /** true between CPM's renderHand and renderHandPost (first-person hand only) */
    public static volatile boolean inHand = false;

    /** render types we handed out for glow parts (identity; RenderType instances are cached per texture, so this stays small) */
    private static final Set<Object> GLOW = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    public static void markGlow(Object rt) {
        if (rt == null) return;
        if (GLOW.size() > 256) GLOW.clear();
        if (GLOW.add(rt)) logOnce("swap", "hand glow: replaced CPM glow render type with " + rt + " (mode=" + mode + ", irisShaders=" + shadersOn() + ")");
    }
    public static boolean isGlow(Object rt) { return rt != null && inHand && !WorldArm.inWorldPass && GLOW.contains(rt); }

    /** one-time debug lines */
    public static void logOnce(String key, String msg) { if (LOGGED.add(key)) LOG.info("[cpmwheel] " + msg); }
    public static void hookFired(String name) { logOnce("hook:" + name, "mixin hook active: " + name); }
    public static void fail(String where, Throwable t) { if (LOGGED.add("fail:" + where)) LOG.warn("[cpmwheel] hand glow error in " + where, t); }

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
            logOnce("iris", "hand glow: Iris API " + (irisMissing ? "not found" : "found"));
        }
        if (irisMissing) return lastResult = false;
        try { lastResult = Boolean.TRUE.equals(inUse.invoke(api)); } catch (Throwable t) { lastResult = false; }
        return lastResult;
    }

    public static boolean active() {
        if (!inHand || WorldArm.inWorldPass) return false; // 1.5.7: in the world pass CPM's own emissive type works
        String m = mode;
        return "emissive".equals(m) || ("auto".equals(m) && shadersOn());
    }
}
