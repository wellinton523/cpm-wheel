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

    private static Method accessGet;
    private static Object access() throws Exception {
        if (accessGet == null) accessGet = Class.forName("com.tom.cpm.shared.MinecraftClientAccess").getMethod("get");
        return accessGet.invoke(null);
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

    // ---------- 1.9.2 model tree (real post-animation bone transforms, read-only, fail-safe) ----------
    private static boolean treeFailed = false;
    private static Object[] partEnums;
    /** per-frame reads: methods / fields looked up once per class */
    private static final Map<String, Method> MCACHE = new HashMap<>();
    private static final Map<String, java.lang.reflect.Field> FCACHE = new HashMap<>();
    private static Object callc(Object o, String name, Object... args) throws Exception {
        if (o == null) return null;
        String k = o.getClass().getName() + "#" + name + "/" + args.length;
        Method m = MCACHE.get(k);
        if (m == null) {
            outer:
            for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                List<Class<?>> cs = new ArrayList<>(); cs.add(c); cs.addAll(allInterfaces(c));
                for (Class<?> q : cs) for (Method x : q.getDeclaredMethods())
                    if (x.getName().equals(name) && x.getParameterCount() == args.length) { try { x.setAccessible(true); } catch (Throwable ignored) {} m = x; break outer; }
            }
            if (m == null) throw new NoSuchMethodException(name);
            MCACHE.put(k, m);
        }
        return m.invoke(o, args);
    }
    private static java.lang.reflect.Field fieldc(Class<?> c, String name) {
        String k = c.getName() + "#" + name;
        java.lang.reflect.Field f = FCACHE.get(k);
        if (f == null && !FCACHE.containsKey(k)) { f = field(c, name); FCACHE.put(k, f); }
        return f;
    }

    /** the local player's CPM model definition, or null */
    public static Object definition() {
        if (treeFailed) return null;
        try { Object player = callc(access(), "getCurrentClientPlayer"); return player == null ? null : callc(player, "getModelDefinition"); }
        catch (Throwable t) { return null; }
    }

    static boolean isA(Object o, String cls) {
        if (o == null) return false;
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(cls)) return true;
        return false;
    }
    private static final String CUBE = "com.tom.cpm.shared.model.RenderedCube", ROOT = "com.tom.cpm.shared.model.RootModelElement";

    static float[] vec(Object v) throws Exception {
        if (v == null) return null;
        java.lang.reflect.Field fx = fieldc(v.getClass(), "x"), fy = fieldc(v.getClass(), "y"), fz = fieldc(v.getClass(), "z");
        if (fx == null || fy == null || fz == null) return null;
        return new float[]{fx.getFloat(v), fy.getFloat(v), fz.getFloat(v)};
    }

    /** post-animation pose {x, y, z, xRot, yRot, zRot} (pixels / radians) of the 6 player part roots this frame; null entries = unknown */
    public static float[][] rootPoses(Object def, boolean[] need) {
        float[][] out = new float[6][];
        if (def == null || treeFailed) return out;
        try {
            if (partEnums == null) {
                Class<?> e = Class.forName("com.tom.cpm.shared.model.PlayerModelParts", true, def.getClass().getClassLoader());
                partEnums = e.getEnumConstants();
            }
            for (int i = 0; i < 6 && i < partEnums.length; i++) {
                if (need != null && !need[i]) continue;
                try {
                    Object pr = callc(def, "getModelElementFor", partEnums[i]);
                    Object root = pr == null ? null : callc(pr, "getMainRoot");
                    if (root == null) continue;
                    float[] pos = vec(callc(root, "getPos")), rot = vec(callc(root, "getRot"));
                    if (pos == null || rot == null) continue;
                    out[i] = new float[]{pos[0], pos[1], pos[2], rot[0], rot[1], rot[2]};
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            treeFailed = true;
            CpmWheel.LOG.info("[CPM Wheel] CPM model tree not readable with this CPM version; world motion uses the vanilla pose", t);
        }
        return out;
    }

    /**
     * point (pixels) where a cube hangs, in the frame of its part root: its pivot carried through every ancestor's current
     * (post-animation) position / rotation. {0,0,0} when the cube is the root itself; null if unreadable.
     */
    public static float[] cubeAnchor(Object cube) {
        try {
            if (isA(cube, ROOT)) return new float[]{0, 0, 0};
            float[] pt = vec(fieldc(cube.getClass(), "pos").get(cube));
            if (pt == null) return null;
            Object a = callc(cube, "getParent");
            for (int guard = 0; a != null && !isA(a, ROOT) && guard < 64; guard++) {
                float[] ap = vec(fieldc(a.getClass(), "pos").get(a)), ar = vec(fieldc(a.getClass(), "rotation").get(a));
                if (ap == null || ar == null) return null;
                pt = WorldMath.apply(new float[]{ap[0], ap[1], ap[2], ar[0], ar[1], ar[2]}, pt);
                a = callc(a, "getParent");
            }
            return a == null ? null : pt;
        } catch (Throwable t) { return null; }
    }

    /** index (WorldMath.PARTS) of the player part a cube belongs to, -1 if none (custom / armor roots) */
    public static int cubePart(Object cube) {
        try {
            Object a = cube;
            for (int guard = 0; a != null && !isA(a, ROOT) && guard < 64; guard++) a = call(a, "getParent");
            if (a == null) return -1;
            Object part = call(a, "getPart");
            String n = part == null ? "" : part.toString().toUpperCase(java.util.Locale.ROOT);
            switch (n) {
                case "HEAD": return 0; case "BODY": return 1; case "LEFT_ARM": return 2; case "RIGHT_ARM": return 3;
                case "LEFT_LEG": return 4; case "RIGHT_LEG": return 5; default: return -1;
            }
        } catch (Throwable t) { return -1; }
    }
    private static int depth(Object cube) {
        int d = 0;
        try { for (Object a = cube; a != null && !isA(a, ROOT) && d < 64; a = call(a, "getParent")) d++; } catch (Throwable ignored) {}
        return d;
    }

    /**
     * The bone a value layer swings: the cube closest to its part root among those its animation rotates (CPM's ValueTrigger /
     * LayerTrigger for the layer's parameter -> animations -> animated cubes). null if not found.
     */
    public static Object layerCube(String anim) {
        if (treeFailed || anim == null || anim.isBlank()) return null;
        try {
            int[] pr = layerParam(anim);
            Object reg = registry();
            if (pr == null || reg == null) return null;
            Object trig = call(reg, "getAnimations");
            if (!(trig instanceof Collection<?> all)) return null;
            List<Object> found = new ArrayList<>(), rot = new ArrayList<>();
            for (Object t : all) {
                if (t == null) continue;
                String cn = t.getClass().getSimpleName();
                if (!cn.endsWith("ValueTrigger") && !cn.endsWith("LayerTrigger")) continue;
                java.lang.reflect.Field fid = field(t.getClass(), "id"), fan = field(t.getClass(), "animations");
                if (fid == null || fan == null || fid.getInt(t) != pr[0]) continue;
                collectCubes(fan.get(t), found, rot);
            }
            List<Object> pool = rot.isEmpty() ? found : rot;
            Object best = null; int bd = Integer.MAX_VALUE;
            for (Object c : pool) { int d = depth(c); if (d < bd) { bd = d; best = c; } }
            return best;
        } catch (Throwable t) { return null; }
    }
    /** bounded walk through CPM's animation objects (never into the model / registry) collecting animated cubes */
    private static void collectCubes(Object start, List<Object> found, List<Object> rot) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object[]> q = new ArrayDeque<>();
        q.add(new Object[]{start, 0, Boolean.FALSE});
        int budget = 4000;
        while (!q.isEmpty() && budget-- > 0) {
            Object[] e = q.poll();
            Object o = e[0]; int d = (Integer) e[1]; boolean viaRot = (Boolean) e[2];
            if (o == null || !seen.add(o)) continue;
            if (isA(o, CUBE)) { if (!found.contains(o)) found.add(o); if (viaRot && !rot.contains(o)) rot.add(o); continue; }
            if (d > 6) continue;
            Class<?> c = o.getClass();
            if (c.isArray()) {
                if (!c.getComponentType().isPrimitive()) for (Object x : (Object[]) o) q.add(new Object[]{x, d + 1, viaRot});
                continue;
            }
            if (o instanceof Collection<?> col) { for (Object x : col) q.add(new Object[]{x, d + 1, viaRot}); continue; }
            String n = c.getName();
            boolean lambda = n.contains("$$Lambda");
            if (!lambda && !n.startsWith("com.tom.cpm.shared.animation") && !n.startsWith("com.tom.cpm.shared.parts.anim")) continue;
            if (n.endsWith("AnimationRegistry") || n.endsWith("ModelDefinition") || n.endsWith("AnimationEngine")) continue;
            boolean r = viaRot || c.getSimpleName().contains("Rot");
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    String tn = f.getType().getName();
                    if (tn.endsWith("ModelDefinition") || tn.endsWith("AnimationRegistry") || tn.startsWith("java.lang.String")) continue;
                    try { f.setAccessible(true); q.add(new Object[]{f.get(o), d + 1, r}); } catch (Throwable ignored) {}
                }
            }
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
