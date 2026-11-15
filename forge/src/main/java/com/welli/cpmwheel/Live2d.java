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
    private static float yawU, bodyU; private static boolean inputsInit = false;
    private static double lastY; private static boolean failed = false;
    private static WheelConfig cfg; private static long cfgAt = 0;

    public static float[] live(String chainId, int i) { return LIVE.get(chainId + "#" + i); }
    public static boolean failed() { return failed; }

    /** "Zerar física": restart from rest and rewrite every value */
    public static void reset() { STATES.clear(); WRITTEN.clear(); LOCAL.clear(); LIVE.clear(); inputsInit = false; }

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
            Map<String, Float> in = Map.of("headYaw", yawU, "headPitch", p.getXRot(), "bodyYaw", bodyU, "walk", walk, "vert", vert, "prev", 0f);

            Set<String> driven = new HashSet<>(), paused = new HashSet<>(), seen = new HashSet<>();
            boolean anyChange = false;
            for (Live2dMath.Chain ch : c.l2dChains) {
                if (!ch.on || ch.links.isEmpty()) continue;
                seen.add(ch.id);
                Live2dMath.State[] st = STATES.get(ch.id);
                if (st == null || st.length != ch.links.size()) {
                    st = new Live2dMath.State[ch.links.size()];
                    for (int i = 0; i < st.length; i++) st[i] = new Live2dMath.State();
                    STATES.put(ch.id, st);
                }
                float[] ext = new float[st.length], out = new float[st.length];
                for (int i = 0; i < st.length; i++) ext[i] = in.getOrDefault(ch.links.get(i).input, 0f);
                Live2dMath.stepChain(ch.links, st, ext, 0.05f, out);
                for (int i = 0; i < st.length; i++) {
                    Live2dMath.Link l = ch.links.get(i);
                    if (l.anim.isBlank()) { LIVE.put(ch.id + "#" + i, new float[]{out[i], -1, 0}); continue; }
                    int max = CpmBridge.maxValue(l.anim);
                    int v = Live2dMath.toValue(out[i], l.min, l.max, max);
                    LIVE.put(ch.id + "#" + i, new float[]{out[i], v, max});
                    if (driven.contains(l.anim)) continue; // the first link using a layer wins
                    if (c.l2dPauseTrig && EffectTriggers.held(l.anim)) { paused.add(l.anim); WRITTEN.remove(l.anim); continue; }
                    driven.add(l.anim);
                    REST.put(l.anim, Live2dMath.toValue(0, l.min, l.max, max));
                    Integer old = WRITTEN.get(l.anim);
                    if (old == null || old != v) { write(c, l.anim, v, max); WRITTEN.put(l.anim, v); anyChange = true; }
                }
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
