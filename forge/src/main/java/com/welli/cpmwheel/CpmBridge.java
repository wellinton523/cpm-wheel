package com.welli.cpmwheel;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Talks to Customizable Player Models without a compile-time dependency.
 * - Playing: official IClientAPI (registered through IMC with a dynamic proxy of ICPMPlugin),
 *   falling back to CPM's AnimationEngine.applyCommand (same path as /cpmclient animate).
 * - Listing: CPM's API has no listing method, so we read the local player's ModelDefinition
 *   the same way the /cpmclient animate command builds its tab-completion list.
 */
public final class CpmBridge {
    private static Object clientApi;

    private CpmBridge() {}

    public static Object createPluginProxy() {
        try {
            Class<?> plugin = Class.forName("com.tom.cpm.api.ICPMPlugin");
            return Proxy.newProxyInstance(CpmBridge.class.getClassLoader(), new Class<?>[]{plugin}, (p, m, a) -> {
                switch (m.getName()) {
                    case "initClient": clientApi = a[0]; CpmWheel.LOG.info("[CPM Wheel] CPM client API received"); return null;
                    case "initCommon": return null;
                    case "getOwnerModId": return CpmWheel.MODID;
                    case "hashCode": return System.identityHashCode(p);
                    case "equals": return p == a[0];
                    case "toString": return "CpmWheelPlugin";
                }
                return null;
            });
        } catch (Throwable t) {
            CpmWheel.LOG.warn("[CPM Wheel] could not create CPM plugin", t);
            return null;
        }
    }

    public static boolean cpmPresent() {
        try { Class.forName("com.tom.cpm.shared.MinecraftClientAccess"); return true; } catch (Throwable t) { return false; }
    }

    private static Object call(Object o, String name, Object... args) throws Exception {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            Object r = tryCall(c, o, name, args);
            if (r != NONE) return r;
            for (Class<?> i : allInterfaces(c)) { r = tryCall(i, o, name, args); if (r != NONE) return r; }
        }
        throw new NoSuchMethodException(name);
    }

    private static final Object NONE = new Object();

    private static Object tryCall(Class<?> c, Object o, String name, Object[] args) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                return m.invoke(o, args);
            }
        }
        return NONE;
    }

    private static Set<Class<?>> allInterfaces(Class<?> c) {
        Set<Class<?>> s = new LinkedHashSet<>();
        Deque<Class<?>> q = new ArrayDeque<>(Arrays.asList(c.getInterfaces()));
        while (!q.isEmpty()) { Class<?> i = q.poll(); if (s.add(i)) q.addAll(Arrays.asList(i.getInterfaces())); }
        return s;
    }

    private static Object access() throws Exception {
        Class<?> mca = Class.forName("com.tom.cpm.shared.MinecraftClientAccess");
        return mca.getMethod("get").invoke(null);
    }

    private static Object registry() throws Exception {
        Object player = call(access(), "getCurrentClientPlayer");
        Object def = call(player, "getModelDefinition");
        return def == null ? null : call(def, "getAnimations");
    }

    /** Model profile id for per-model config, or "default". */
    public static String modelKey() {
        try {
            Object reg = registry();
            Object id = reg == null ? null : call(reg, "getProfileId");
            if (id != null && !id.toString().isBlank()) return id.toString();
        } catch (Throwable ignored) {}
        return "default";
    }

    /** Animation name -> max value (1 = on/off). */
    public static LinkedHashMap<String, Integer> listAnimations() { return listAnimations(false, null); }

    /** 1.7.0: value layers (max > 1) for "Física Live2D", including command-controlled ones; cmd collects the command-controlled names */
    public static LinkedHashMap<String, Integer> listValueLayers(Set<String> cmd) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        for (var e : listAnimations(true, cmd).entrySet()) if (e.getValue() > 1) out.put(e.getKey(), e.getValue());
        return out;
    }

    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, Integer> listAnimations(boolean includeCmd, Set<String> cmdNames) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        try {
            Object reg = registry();
            if (reg == null) return out;
            try {
                Map<String, Object> map = (Map<String, Object>) call(reg, "getCommandActionsMap");
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    boolean cmdOnly = false; int max = 1;
                    try { cmdOnly = (Boolean) call(e.getValue(), "isCommandControlled"); } catch (Throwable ignored) {}
                    try { max = (Integer) call(e.getValue(), "getMaxValue"); } catch (Throwable ignored) {}
                    if (!cmdOnly || includeCmd) out.put(e.getKey(), Math.max(1, max));
                    if (cmdOnly && cmdNames != null) cmdNames.add(e.getKey());
                }
            } catch (NoSuchMethodException old) {
                // older CPM: custom poses + named gestures
                try { for (Object k : ((Map<String, ?>) call(reg, "getCustomPoses")).keySet()) out.put(k.toString(), 1); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            CpmWheel.LOG.debug("[CPM Wheel] list failed", t);
        }
        return out;
    }

    private static Map<String, Integer> maxCache = new HashMap<>();
    private static long maxCacheAt = 0;

    /** CPM max value of an animation (1 = on/off toggle, >1 = value/layer animation), 1 if unknown. */
    public static int maxValue(String name) {
        if (name == null || name.isBlank()) return 1;
        long now = System.currentTimeMillis();
        if (now - maxCacheAt > 1000) { maxCache = listAnimations(true, null); maxCacheAt = now; }
        return maxCache.getOrDefault(name, 1);
    }

    public static boolean play(String name, int value) {
        try {
            if (clientApi != null) {
                Object r = value < 0 ? call(clientApi, "playAnimation", name) : call(clientApi, "playAnimation", name, value);
                if (Boolean.TRUE.equals(r)) return true;
            }
        } catch (Throwable t) { CpmWheel.LOG.debug("[CPM Wheel] api play failed", t); }
        try {
            Object eng = call(call(access(), "getPlayerRenderManager"), "getAnimationEngine");
            return Boolean.TRUE.equals(call(eng, "applyCommand", name, value, (Boolean) null));
        } catch (Throwable t) { CpmWheel.LOG.debug("[CPM Wheel] engine play failed", t); }
        return false;
    }

    public static int playing(String name) {
        try { if (clientApi != null) return (Integer) call(clientApi, "getAnimationPlaying", name); } catch (Throwable ignored) {}
        try {
            Object act = ((Map<?, ?>) call(registry(), "getCommandActionsMap")).get(name);
            if (act != null) return (Integer) call(act, "getValue");
        } catch (Throwable ignored) {}
        return -1;
    }

    // ---------- 1.7.0 local prediction for "Física Live2D" ----------
    // CPM renders the local player from persistentState.gestureData, which normally only changes when the server echoes the
    // gesture packet back (so without CPM on the server, value layers never move for yourself). We write it directly.
    private static boolean predictFailed = false;
    private static java.lang.reflect.Field fEngGesture, fPersistent, fPGesture, fPPrev, fPTime;

    private static java.lang.reflect.Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { java.lang.reflect.Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
    private static Object engine() throws Exception { return call(call(access(), "getPlayerRenderManager"), "getAnimationEngine"); }

    /** byte index and max value of a value layer in CPM's gesture data, or null */
    public static int[] layerParam(String name) {
        try {
            Object act = ((Map<?, ?>) call(registry(), "getCommandActionsMap")).get(name);
            if (act == null) return null;
            java.lang.reflect.Field f = field(act.getClass(), "parameter");
            if (f == null) return null;
            int max = 255;
            try { max = (Integer) call(act, "getMaxValue"); } catch (Throwable ignored) {}
            return new int[]{f.getInt(act), Math.max(1, max)};
        } catch (Throwable t) { return null; }
    }

    /**
     * Makes the local player's model show these values right away (fail-safe, does nothing if CPM's internals differ).
     * @param bytes  parameter index -> raw byte (0..255) to force; null = only copy the engine's own data (synced values)
     * @param copyEngine also copy everything the engine holds (values sent with "Sincronizar no multiplayer")
     */
    public static void predict(Map<Integer, Integer> bytes, boolean copyEngine) {
        if (predictFailed) return;
        try {
            Object eng = engine();
            Object player = call(access(), "getCurrentClientPlayer");
            if (eng == null || player == null) return;
            if (fPersistent == null) {
                fEngGesture = field(eng.getClass(), "gestureData");
                fPersistent = field(player.getClass(), "persistentState");
                Object ps0 = fPersistent == null ? null : fPersistent.get(player);
                if (ps0 != null) { fPGesture = field(ps0.getClass(), "gestureData"); fPPrev = field(ps0.getClass(), "prevGestureData"); fPTime = field(ps0.getClass(), "lastGestureReceiveTime"); }
                if (fEngGesture == null || fPersistent == null || fPGesture == null || fPPrev == null || fPTime == null) {
                    predictFailed = true; CpmWheel.LOG.info("[CPM Wheel] local prediction not available with this CPM version"); return;
                }
            }
            Object ps = fPersistent.get(player);
            if (ps == null) return;
            byte[] cur = (byte[]) fPGesture.get(ps);
            if (cur == null) return;
            byte[] next = cur.clone();
            if (copyEngine) {
                byte[] eg = (byte[]) fEngGesture.get(eng);
                if (eg == null || eg.length != cur.length) return;
                // keep pose / gesture bytes (0, 1) as the server has them, copy the value parameters
                for (int i = 2; i < eg.length; i++) next[i] = eg[i];
            }
            if (bytes != null) for (var e : bytes.entrySet()) if (e.getKey() >= 2 && e.getKey() < next.length) next[e.getKey()] = (byte) (int) e.getValue();
            if (java.util.Arrays.equals(next, cur)) return;
            fPPrev.set(ps, cur);
            fPGesture.set(ps, next);
            fPTime.setLong(ps, (Long) call(eng, "getTime"));
        } catch (Throwable t) {
            predictFailed = true;
            CpmWheel.LOG.warn("[CPM Wheel] local prediction disabled after an error", t);
        }
    }

    /** Turn off everything that is currently on. */
    public static void resetAll() {
        for (String n : listAnimations().keySet()) {
            int v = playing(n);
            if (v != 0) play(n, 0);
        }
    }
}
