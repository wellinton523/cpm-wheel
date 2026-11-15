package com.welli.cpmwheel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 1.7.0 "Física Live2D" runtime: every client tick (START, before CPM sends its gesture data) steps the chains of
 * the current model and writes the CPM value layers, only when the integer value changes.
 * "Sincronizar no multiplayer" on: through CPM's command (sent to the server like any wheel animation) + local prediction;
 * off: only the local copy CPM renders from (nobody else sees it, nothing is sent).
 */
public final class Live2d {
    private Live2d() {}

    private static final Map<String, Live2dMath.State[]> STATES = new HashMap<>();
    /** anim -> last value written */
    private static final Map<String, Integer> WRITTEN = new LinkedHashMap<>();
    /** anim -> rest value (used when the physics lets go) */
    private static final Map<String, Integer> REST = new HashMap<>();
    /** local-only mode: parameter byte -> raw byte to keep in the local copy */
    private static final Map<Integer, Integer> LOCAL = new HashMap<>();
    /** live preview: chainId#i -> {angle, value, max} */
    private static final Map<String, float[]> LIVE = new HashMap<>();
    /** 1.7.1: anim -> number of links using it / value written after combining */
    private static final Map<String, Integer> SHARED = new HashMap<>(), COMBINED = new HashMap<>();
    public static int shared(String anim) { return SHARED.getOrDefault(anim, 0); }
    public static Integer combined(String anim) { return COMBINED.get(anim); }
    private static float yawU, bodyU; private static boolean inputsInit = false;
    private static double lastY; private static boolean failed = false;
    private static WheelConfig cfg; private static long cfgAt = 0;

    public static float[] live(String chainId, int i) { return LIVE.get(chainId + "#" + i); }
    public static boolean failed() { return failed; }

    /** "Zerar física": restart from rest and rewrite every value */
    public static void reset() { STATES.clear(); WRITTEN.clear(); LOCAL.clear(); LIVE.clear(); SHARED.clear(); COMBINED.clear(); inputsInit = false; }

    /** config changed in a screen */
    public static void use(WheelConfig c) { cfg = c; cfgAt = System.currentTimeMillis(); }

    public static void tickStart(Minecraft mc) {
        if (failed) return;
        try {
            LocalPlayer p = mc.player;
            long now = System.currentTimeMillis();
            if (p == null) { if (!WRITTEN.isEmpty() || !STATES.isEmpty()) { WRITTEN.clear(); LOCAL.clear(); STATES.clear(); inputsInit = false; } return; }
            if (cfg == null || now - cfgAt > 1000) { cfg = WheelConfig.get(); cfgAt = now; }
            WheelConfig c = cfg;
            if (!c.l2dOn || c.l2dChains.isEmpty()) { release(c, Set.of()); return; }
            if (mc.isPaused()) return;
            // inputs (degrees; speeds as 10° per block/s)
            float yaw = p.getYHeadRot(), body = p.yBodyRot;
            if (!inputsInit) { yawU = yaw; bodyU = body; lastY = p.getY(); inputsInit = true; }
            yawU = Live2dMath.unwrap(yawU, yaw); bodyU = Live2dMath.unwrap(bodyU, body);
            var mv = p.getDeltaMovement();
            float walk = (float) Math.sqrt(mv.x * mv.x + mv.z * mv.z) * 20f * 10f;
            float vert = (float) (p.getY() - lastY) * 20f * 10f; lastY = p.getY();
            if (p.onGround() && Math.abs(vert) < 0.5f) vert = 0;
            float[] rel = Live2dMath.bodyRelative(mv.x, mv.z, body); // signed, relative to the body facing
            float fwd = rel[0] * 20f * 10f, side = rel[1] * 20f * 10f;   // + forward / + right
            float headRel = Live2dMath.unwrap(0, yaw - body);
            Map<String, Float> in = new HashMap<>();
            in.put("headYaw", yawU); in.put("headPitch", p.getXRot()); in.put("bodyYaw", bodyU); in.put("headRel", headRel);
            in.put("walk", walk); in.put("fwd", fwd); in.put("side", side); in.put("vert", vert);

            Set<String> driven = new HashSet<>(), paused = new HashSet<>(), seen = new HashSet<>();
            boolean anyChange = false;
            // 1.7.1: every link adds its value to its layer; layers are combined and written once per tick
            Map<String, java.util.List<int[]>> per = new LinkedHashMap<>(); // anim -> {value, rest, max}
            for (Live2dMath.Chain ch : c.l2dChains) {
                if (!ch.on || ch.links.isEmpty()) continue;
                seen.add(ch.id);
                Live2dMath.State[] st = STATES.get(ch.id);
                if (st == null || st.length != ch.links.size()) {
                    st = new Live2dMath.State[ch.links.size()];
                    for (int i = 0; i < st.length; i++) st[i] = new Live2dMath.State();
                    STATES.put(ch.id, st);
                }
                float[] out = new float[st.length];
                Live2dMath.stepChain(ch.links, st, in, 0.05f, out);
                for (int i = 0; i < st.length; i++) {
                    Live2dMath.Link l = ch.links.get(i);
                    if (l.anim.isBlank()) { LIVE.put(ch.id + "#" + i, new float[]{out[i], -1, 0}); continue; }
                    int max = CpmBridge.maxValue(l.anim);
                    int v = Live2dMath.toValue(out[i], l.min, l.max, max);
                    LIVE.put(ch.id + "#" + i, new float[]{out[i], v, max});
                    per.computeIfAbsent(l.anim, k -> new java.util.ArrayList<>()).add(new int[]{v, Live2dMath.toValue(0, l.min, l.max, max), max});
                }
            }
            SHARED.clear(); COMBINED.clear();
            for (var e : per.entrySet()) {
                String anim = e.getKey(); var list = e.getValue();
                SHARED.put(anim, list.size());
                if (c.l2dPauseTrig && EffectTriggers.held(anim)) { paused.add(anim); WRITTEN.remove(anim); continue; }
                int[] vals = new int[list.size()], rests = new int[list.size()];
                for (int i = 0; i < vals.length; i++) { vals[i] = list.get(i)[0]; rests[i] = list.get(i)[1]; }
                int max = list.get(0)[2];
                int v = Live2dMath.combine(vals, rests, c.l2dModes.getOrDefault(anim, "sum"), max);
                COMBINED.put(anim, v);
                driven.add(anim);
                REST.put(anim, rests[0]);
                Integer old = WRITTEN.get(anim);
                if (old == null || old != v) { write(c, anim, v, max); WRITTEN.put(anim, v); anyChange = true; }
            }
            STATES.keySet().removeIf(k -> !seen.contains(k));
            LIVE.keySet().removeIf(k -> !seen.contains(k.substring(0, k.lastIndexOf('#'))));
            release(c, paused, driven);
            // local view: synced values come from the engine; local-only bytes are re-applied every tick (a server echo may overwrite them)
            if (anyChange || !LOCAL.isEmpty()) CpmBridge.predict(LOCAL.isEmpty() ? null : LOCAL, c.l2dSync && anyChange);
        } catch (Throwable t) {
            failed = true;
            CpmWheel.LOG.warn("[CPM Wheel] Física Live2D disabled after an error", t);
        }
    }

    private static void write(WheelConfig c, String anim, int v, int max) {
        if (c.l2dSync) {
            CpmBridge.play(anim, v);
            int[] pr = CpmBridge.layerParam(anim);
            if (pr != null) LOCAL.remove(pr[0]);
        } else {
            int[] pr = CpmBridge.layerParam(anim);
            if (pr != null) LOCAL.put(pr[0], Math.round(v * 255f / pr[1]));
            else CpmBridge.play(anim, v); // unknown layout: fall back to the normal command
        }
    }

    private static void release(WheelConfig c, Set<String> keep) { release(c, keep, Set.of()); }
    /** layers no longer driven go back to their rest value once */
    private static void release(WheelConfig c, Set<String> paused, Set<String> driven) {
        if (WRITTEN.isEmpty()) { if (driven.isEmpty()) LOCAL.clear(); return; }
        boolean changed = false;
        for (var it = WRITTEN.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (driven.contains(e.getKey()) || paused.contains(e.getKey())) continue;
            int rest = REST.getOrDefault(e.getKey(), 0);
            int[] pr = CpmBridge.layerParam(e.getKey());
            if (pr != null) LOCAL.remove(pr[0]);
            if (c.l2dSync || pr == null) CpmBridge.play(e.getKey(), rest);
            else LOCAL.put(pr[0], Math.round(rest * 255f / pr[1]));
            it.remove(); changed = true;
        }
        if (changed) {
            CpmBridge.predict(LOCAL.isEmpty() ? null : LOCAL, true);
            if (driven.isEmpty()) LOCAL.clear();
        }
    }
}
