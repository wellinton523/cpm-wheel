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
    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, Integer> listAnimations() {
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
                    if (!cmdOnly) out.put(e.getKey(), Math.max(1, max));
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

    /** Turn off everything that is currently on. */
    public static void resetAll() {
        for (String n : listAnimations().keySet()) {
            int v = playing(n);
            if (v != 0) play(n, 0);
        }
    }
}
