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

    // ---------- 1.8.0 ----------
    private static final Live2dMath.Impulse HIT = new Live2dMath.Impulse(), LAND = new Live2dMath.Impulse();
    private static float prevHp = -1, lastVy = 0, lookY, lookP, voiceSm, windBiome = 1; private static int prevHurt, windTick; private static boolean wasGround = true;
    private static final Map<String, Live2dMath.LayerSig> LSIG = new HashMap<>();
    private static final Map<String, Live2dMath.GenState> GSTATE = new HashMap<>();
    private static final java.util.Random RNG = new java.util.Random();
    /** generator id -> {level, value, max} for the screens */
    private static final Map<String, float[]> LIVEG = new HashMap<>();
    public static float[] liveGen(String id) { return LIVEG.get(id); }
    /** "Prévia / simular": values added to the real inputs while the preview screen is open */
    public static final Map<String, Float> SIM = new HashMap<>();
    public static volatile boolean simOn = false;
    public static void simKick(float deg) { Live2dMath.kick(HIT, deg); }
    /** history for the preview graph: chainId#i -> ring of output angles / values */
    public static final int HN = 100;
    private static final Map<String, float[][]> HIST = new HashMap<>();
    private static int histIdx = 0;
    public static float[][] history(String chainId, int i) { return HIST.get(chainId + "#" + i); }
    public static int historyIndex() { return histIdx; }
    /** last input values (for the preview screen) */
    private static volatile Map<String, Float> lastIn = Map.of();
    public static Map<String, Float> inputs() { return lastIn; }
    private static WheelConfig cfg; private static long cfgAt = 0;

    public static float[] live(String chainId, int i) { return LIVE.get(chainId + "#" + i); }
    public static boolean failed() { return failed; }

    /** "Zerar física": restart from rest and rewrite every value */
    public static void reset() { STATES.clear(); WRITTEN.clear(); LOCAL.clear(); LIVE.clear(); SHARED.clear(); COMBINED.clear(); WEIGHTS.clear(); inputsInit = false;
        LSIG.clear(); GSTATE.clear(); LIVEG.clear(); HIST.clear(); HIT.v = 0; LAND.v = 0; }

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
            boolean gens = c.l2dGenOn && !c.l2dGens.isEmpty();
            if (!c.l2dOn || (c.l2dChains.isEmpty() && !gens)) { release(c, Set.of()); return; }
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
            // ---- 1.8.0 inputs ----
            in.put("fall", Math.max(0f, -vert));
            in.put("grav", -Math.max(-90f, Math.min(90f, p.getXRot())));   // + looking up, − looking down
            boolean ground = p.onGround();
            float vyRaw = (float) dy * 20f;   // blocks/s
            if (c.l2dImpactOn) {
                float hp = p.getHealth(), pct = c.l2dImpactPct / 100f;
                if (prevHp >= 0 && hp < prevHp - 0.01f) Live2dMath.kick(HIT, Live2dMath.hitPeak(prevHp - hp) * pct);
                else if (p.hurtTime > prevHurt && prevHurt == 0) Live2dMath.kick(HIT, Live2dMath.hitPeak(1) * pct);
                if (ground && !wasGround && lastVy < 0) Live2dMath.kick(LAND, Live2dMath.landPeak(-lastVy) * pct);
                prevHp = hp; prevHurt = p.hurtTime;
            } else { prevHp = -1; }
            wasGround = ground; lastVy = vyRaw;
            in.put("hit", Live2dMath.decay(HIT, 0.05f, 0.12f)); in.put("land", Live2dMath.decay(LAND, 0.05f, 0.12f));
            var level = p.level();
            if (c.l2dWindOn) {
                net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(p.getX(), p.getEyeY() + 0.5, p.getZ());
                if (windTick-- <= 0) { windTick = 20; windBiome = Live2dMath.biomeWind(level.getBiome(bp).unwrapKey().map(k -> k.location().toString()).orElse("")); }
                boolean sky = level.canSeeSky(bp);
                double t = level.getGameTime() / 20.0;
                float w = Live2dMath.windStrength(level.isRaining(), level.isThundering(), windBiome, p.getY(), sky) * Live2dMath.gust(t, 7) * c.l2dWindPct / 100f;
                float[] wr = Live2dMath.windRel(w, Live2dMath.windDir(t, 7), body);
                in.put("windF", wr[0]); in.put("windS", wr[1]);
            }
            if (c.l2dLookOn) {
                double r = c.l2dLookRadius, best = r * r; net.minecraft.world.entity.Entity tgt = null;
                if (c.l2dLookPlayers) { for (var o : level.players()) if (o != p && o.isAlive() && !o.isSpectator()) { double d = o.distanceToSqr(p); if (d < best) { best = d; tgt = o; } } }
                else for (var o : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive())) {
                    double d = o.distanceToSqr(p); if (d < best) { best = d; tgt = o; } }
                float ty = 0, tp = 0;
                if (tgt != null) {
                    var e = p.getEyePosition(); var o = tgt.getEyePosition();
                    float[] la = Live2dMath.lookAngles(o.x - e.x, o.y - e.y, o.z - e.z, yaw, p.getXRot(), 60);
                    if (la[2] > 0) { ty = la[0]; tp = la[1]; }
                }
                lookY = Live2dMath.smooth(lookY, ty, 0.05f, 0.12f); lookP = Live2dMath.smooth(lookP, tp, 0.05f, 0.12f);
                in.put("lookYaw", lookY); in.put("lookPitch", lookP);
            }
            if (c.l2dVoiceOn) {
                float lv = VoiceBridge.level(p.getUUID());
                if (lv < 0) lv = 0.35f + 0.65f * Math.abs((float) Math.sin(now / 1000.0 * 2 * Math.PI * 4.5)) * (0.7f + 0.3f * (float) Math.sin(now / 1000.0 * 7.3)); // talking: flap
                voiceSm = Live2dMath.smooth(voiceSm, lv, 0.05f, 0.05f);
                in.put("voice", voiceSm * 45f * c.l2dVoicePct / 100f);
            }
            if (c.l2dLayerOn) {
                Set<String> done = new HashSet<>();
                for (Live2dMath.Chain ch : c.l2dChains) if (ch.on) for (Live2dMath.Link l : ch.links) for (Live2dMath.Input ip : l.inputs) {
                    if (!"layer".equals(ip.src) || ip.ref.isBlank()) continue;
                    String k = Live2dMath.key(ip);
                    if (!done.add(k)) continue;
                    in.put(k, Live2dMath.layerSignal(LSIG.computeIfAbsent(k, q -> new Live2dMath.LayerSig()), CpmBridge.playing(ip.ref), CpmBridge.maxValue(ip.ref), 0.05f, ip.refMode, ip.refHz10 / 10f));
                }
            }
            if (simOn) for (var e : SIM.entrySet()) in.merge(e.getKey(), e.getValue(), Float::sum);
            lastIn = Map.copyOf(in);

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
                    float[][] h = HIST.computeIfAbsent(ch.id + "#" + i, k -> new float[2][HN]);
                    h[0][histIdx] = out[i]; h[1][histIdx] = max > 0 ? v / (float) max : 0;
                    var lst = per.computeIfAbsent(l.anim, k -> new java.util.ArrayList<>());
                    if (Live2dMath.released(wt[i], susp)) continue; // faded out: this link no longer writes the layer
                    lst.add(new int[]{v, rest, max});
                }
            }
            histIdx = (histIdx + 1) % HN;
            // 1.8.0 breathing / blinking generators (their layer joins the same per-layer combine)
            LIVEG.clear();
            if (gens) for (Live2dMath.Gen g : c.l2dGens) {
                if (!g.on || g.anim.isBlank()) continue;
                Live2dMath.GenState gs = GSTATE.computeIfAbsent(g.id + "|" + g.kind, k -> new Live2dMath.GenState());
                float lv = Live2dMath.stepGen(g, gs, 0.05f, RNG);
                int max = CpmBridge.maxValue(g.anim);
                int v = Live2dMath.genValue(g, lv, max);
                LIVEG.put(g.id, new float[]{lv, v, max});
                per.computeIfAbsent(g.anim, k -> new java.util.ArrayList<>()).add(new int[]{v, Live2dMath.genValue(g, 0, max), max});
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
            HIST.keySet().removeIf(k -> !seen.contains(k.substring(0, k.lastIndexOf('#'))));
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
