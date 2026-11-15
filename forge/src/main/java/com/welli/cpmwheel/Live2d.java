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
    /** 1.7.2: chainId -> physics weight per link (1 = on, 0 = faded to rest) */
    private static final Map<String, float[]> WEIGHTS = new HashMap<>();
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
    private static double lastX, lastY, lastZ; private static boolean failed = false;
    /** 1.7.3: smoothed speeds (walk, fwd, side, vert) */
    private static float sWalk, sFwd, sSide, sVert;
    /** speed smoothing time constant (s) and dead zone (in input units: 10° per block/s) */
    static final float SPEED_TAU = 0.15f, SPEED_DZ = 0.5f;
    private static WheelConfig cfg; private static long cfgAt = 0;

    public static float[] live(String chainId, int i) { return LIVE.get(chainId + "#" + i); }
    public static boolean failed() { return failed; }

    /** "Zerar física": restart from rest and rewrite every value */
    public static void reset() { STATES.clear(); WRITTEN.clear(); LOCAL.clear(); LIVE.clear(); SHARED.clear(); COMBINED.clear(); WEIGHTS.clear(); inputsInit = false; }

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
            if (!inputsInit) { yawU = yaw; bodyU = body; lastX = p.getX(); lastY = p.getY(); lastZ = p.getZ(); sWalk = sFwd = sSide = sVert = 0; inputsInit = true; }
            yawU = Live2dMath.unwrap(yawU, yaw); bodyU = Live2dMath.unwrap(bodyU, body);
            // 1.7.3: real displacement per tick (getDeltaMovement is the velocity AFTER friction: ×0.55 on the ground but
            // ×0.91 in the air, so walking speed jumped on every hop), smoothed so tick noise and spikes don't flicker
            double dx = p.getX() - lastX, dy = p.getY() - lastY, dz = p.getZ() - lastZ;
            lastX = p.getX(); lastY = p.getY(); lastZ = p.getZ();
            if (dx * dx + dy * dy + dz * dz > 4.0) { dx = dy = dz = 0; } // teleport: no swing
            float walk = (float) Math.sqrt(dx * dx + dz * dz) * 20f * 10f;
            float vert = (float) dy * 20f * 10f;
            if (p.onGround() && Math.abs(vert) < 0.5f) vert = 0;
            float[] rel = Live2dMath.bodyRelative(dx, dz, body); // signed, relative to the body facing
            sWalk = Live2dMath.smooth(sWalk, walk, 0.05f, SPEED_TAU); sVert = Live2dMath.smooth(sVert, vert, 0.05f, SPEED_TAU);
            sFwd = Live2dMath.smooth(sFwd, rel[0] * 20f * 10f, 0.05f, SPEED_TAU); sSide = Live2dMath.smooth(sSide, rel[1] * 20f * 10f, 0.05f, SPEED_TAU);
            walk = Live2dMath.deadZone(sWalk, SPEED_DZ); vert = Live2dMath.deadZone(sVert, SPEED_DZ);
            float fwd = Live2dMath.deadZone(sFwd, SPEED_DZ), side = Live2dMath.deadZone(sSide, SPEED_DZ);   // + forward / + right
            float headRel = Live2dMath.unwrap(0, yaw - body);
            Map<String, Float> in = new HashMap<>();
            in.put("headYaw", yawU); in.put("headPitch", p.getXRot()); in.put("bodyYaw", bodyU); in.put("headRel", headRel);
            in.put("walk", walk); in.put("fwd", fwd); in.put("side", side); in.put("vert", vert);

            // 1.7.2 "Desativar quando": one snapshot per tick, only when some chain / link has conditions
            TriggerLogic.Snapshot snap = null;
            for (Live2dMath.Chain ch : c.l2dChains) {
                if (!ch.on) continue;
                boolean need = !ch.off.isEmpty();
                for (Live2dMath.Link l : ch.links) need |= !l.off.isEmpty();
                if (need) { snap = EffectTriggers.peek(p, c); break; }
            }
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
                float[] wt = WEIGHTS.get(ch.id);
                if (wt == null || wt.length != st.length) { wt = new float[st.length]; java.util.Arrays.fill(wt, 1f); WEIGHTS.put(ch.id, wt); }
                boolean chainOff = snap != null && TriggerLogic.eval(ch.off, ch.offCombine, snap);
                for (int i = 0; i < st.length; i++) {
                    Live2dMath.Link l = ch.links.get(i);
                    boolean susp = chainOff || (snap != null && !l.off.isEmpty() && TriggerLogic.eval(l.off, l.offCombine, snap));
                    wt[i] = Live2dMath.fadeStep(wt[i], susp, ch.fadeMs, 0.05f);
                    if (l.anim.isBlank()) { LIVE.put(ch.id + "#" + i, new float[]{out[i], -1, 0, wt[i], susp ? 1 : 0}); continue; }
                    int max = CpmBridge.maxValue(l.anim);
                    int rest = Live2dMath.toValue(0, l.min, l.max, max);
                    int v = Live2dMath.faded(Live2dMath.toValue(out[i], l.min, l.max, max), rest, wt[i]);
                    LIVE.put(ch.id + "#" + i, new float[]{out[i], v, max, wt[i], susp ? 1 : 0});
                    var lst = per.computeIfAbsent(l.anim, k -> new java.util.ArrayList<>());
                    if (Live2dMath.released(wt[i], susp)) continue; // faded out: this link no longer writes the layer
                    lst.add(new int[]{v, rest, max});
                }
            }
            SHARED.clear(); COMBINED.clear();
            for (var e : per.entrySet()) {
                String anim = e.getKey(); var list = e.getValue();
                if (list.isEmpty()) { paused.add(anim); WRITTEN.remove(anim); dropLocal(anim); continue; } // every link suspended: hands the layer over (already at rest)
                SHARED.put(anim, list.size());
                if (c.l2dPauseTrig && EffectTriggers.held(anim)) { paused.add(anim); WRITTEN.remove(anim); dropLocal(anim); continue; }
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
            WEIGHTS.keySet().removeIf(k -> !seen.contains(k));
            LIVE.keySet().removeIf(k -> !seen.contains(k.substring(0, k.lastIndexOf('#'))));
            release(c, paused, driven);
            // local view: synced values come from the engine; local-only bytes are re-applied every tick (a server echo may overwrite them)
            if (anyChange || !LOCAL.isEmpty()) CpmBridge.predict(LOCAL.isEmpty() ? null : LOCAL, c.l2dSync && anyChange);
        } catch (Throwable t) {
            failed = true;
            CpmWheel.LOG.warn("[CPM Wheel] Física Live2D disabled after an error", t);
        }
    }

    /** stop forcing a local-only byte (the layer is handed over) */
    private static void dropLocal(String anim) {
        if (LOCAL.isEmpty()) return;
        int[] pr = CpmBridge.layerParam(anim);
        if (pr != null) LOCAL.remove(pr[0]);
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
