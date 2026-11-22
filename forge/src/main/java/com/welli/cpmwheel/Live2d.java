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
    /** 1.8.1 Emotecraft: kick on emote start / stop + edge tracking */
    private static final Live2dMath.Impulse EMO = new Live2dMath.Impulse();
    private static final EmoteLogic.Edge EMO_EDGE = new EmoteLogic.Edge();
    private static float prevHp = -1, lastVy = 0, lookY, lookP, voiceSm, windBiome = 1; private static int prevHurt, windTick; private static boolean wasGround = true;
    private static final Map<String, Live2dMath.LayerSig> LSIG = new HashMap<>();
    private static final Map<String, Live2dMath.GenState> GSTATE = new HashMap<>();
    private static final java.util.Random RNG = new java.util.Random();
    /** 1.8.3 look-at eye state + smoothing settings */
    private static final LookLogic.Eye EYE = new LookLogic.Eye();
    private static final LookLogic.Smooth LOOK_S = new LookLogic.Smooth();
    /** 1.8.4: {eye yaw, eye pitch (smoothed), raw yaw, raw pitch, 1 = has target} for the eye-aim overlay (null = look-at off) */
    public static volatile float[] EYE_VIEW = null;
    public static volatile String eyeState = "off";
    // 1.9.0 eye extras
    private static final LookLogic.Glance GLANCE = new LookLogic.Glance();
    private static final LookLogic.Idle IDLE = new LookLogic.Idle();
    private static float idleYaw, idlePitch, hurtT = 99, snapLeft, blinkRate = 1, blinkSlow = 1, blinkFloor; private static int hurtPrev, snapId = Integer.MIN_VALUE;
    private static int ctxTick; private static boolean ctxSand;
    public static boolean idleNow() { return IDLE.idle; }
    /** current blink-rate multiplier (context x idle), for the settings screens */
    public static float blinkRateNow() { return blinkRate; }

    /** 1.9.0 context blink rate: bright daylight, eyes underwater, rain / sand, aiming a bow, crossbow, trident or spyglass */
    static float contextRate(Minecraft mc, LocalPlayer p, WheelConfig c) {
        var level = p.level();
        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        boolean water = p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        boolean sky = level.canSeeSky(bp);
        if (ctxTick-- <= 0) { ctxTick = 20; String b = level.getBiome(bp).unwrapKey().map(k -> k.location().getPath()).orElse(""); ctxSand = b.contains("desert") || b.contains("badlands"); }
        boolean rain = !water && sky && (level.isRainingAt(bp.above()) || (level.isRaining() && ctxSand));
        boolean bright = !water && !rain && sky && level.isDay() && !level.isRaining();
        boolean aim = false;
        if (p.isUsingItem()) {
            var it = p.getUseItem().getItem();
            aim = it instanceof net.minecraft.world.item.BowItem || it instanceof net.minecraft.world.item.CrossbowItem
                    || it instanceof net.minecraft.world.item.TridentItem || it instanceof net.minecraft.world.item.SpyglassItem;
        }
        if (!aim) for (var hand : net.minecraft.world.InteractionHand.values()) {
            var st = p.getItemInHand(hand);
            if (st.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(st)) { aim = true; break; }
        }
        return LookLogic.ctxRate(bright, water, rain, aim, c.l2dCtxBright, c.l2dCtxWater, c.l2dCtxRain, c.l2dCtxAim);
    }
    private static volatile long eyeAt = 0;
    /** latest eye-aim data, or null when look-at hasn't run for half a second (off, paused, no chains) */
    public static float[] eyeView() { float[] v = EYE_VIEW; return v != null && System.currentTimeMillis() - eyeAt < 500 ? v : null; }
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
    /** 1.8.3: config last used by the physics (for the look-at overlay) */
    public static WheelConfig config() { return cfg; }

    public static float[] live(String chainId, int i) { return LIVE.get(chainId + "#" + i); }
    public static boolean failed() { return failed; }

    /** "Zerar física": restart from rest and rewrite every value */
    public static void reset() { STATES.clear(); WRITTEN.clear(); LOCAL.clear(); LIVE.clear(); SHARED.clear(); COMBINED.clear(); WEIGHTS.clear(); inputsInit = false;
        LSIG.clear(); GSTATE.clear(); EYE.target = -1; EYE.y = EYE.p = EYE.vy = EYE.vp = 0; lookY = lookP = 0; LookTargets.clear(); GLANCE.primed = false; GLANCE.id = -1; IDLE.still = 0; IDLE.idle = false; hurtT = 99; snapLeft = 0; LIVEG.clear(); HIST.clear(); HIT.v = 0; LAND.v = 0; EMO.v = 0; EMO_EDGE.playing = false; EMO_EDGE.name = ""; }

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
            // 1.8.1 Emotecraft: "off" suspends every chain while an emote plays (with the chain fade); "kick" feeds the "emote" input
            boolean ecActive = EmoteBridge.active(c) && !"none".equals(c.ecL2d);
            boolean ecPlaying = ecActive && EmoteBridge.playing();
            boolean ecOff = ecPlaying && "off".equals(c.ecL2d);
            if (ecActive && "kick".equals(c.ecL2d)) { if (EMO_EDGE.step(ecPlaying, EmoteBridge.playingName()) != 0) Live2dMath.kick(EMO, c.ecKick); }
            else { EMO_EDGE.playing = false; EMO_EDGE.name = ""; }
            in.put("emote", Live2dMath.decay(EMO, 0.05f, 0.12f));
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
            // 1.9.0 idle (no input for N s) and damage edge, shared by the eyes and the blink generators
            boolean active = Math.abs(Live2dMath.unwrap(0, yaw - idleYaw)) > 0.5f || Math.abs(p.getXRot() - idlePitch) > 0.5f
                    || dx * dx + dy * dy + dz * dz > 1e-4 || p.swinging || p.isUsingItem() || p.hurtTime > 0;
            idleYaw = yaw; idlePitch = p.getXRot();
            boolean idle = c.l2dIdleOn && LookLogic.idle(IDLE, active, 0.05f, c.l2dIdleSec, c.l2dIdleWander, c.l2dIdleWanderMs, RNG);
            if (!c.l2dIdleOn) { IDLE.still = 0; IDLE.idle = false; }
            boolean hitNow = p.hurtTime > hurtPrev && hurtPrev == 0; hurtPrev = p.hurtTime;
            if (c.l2dHurtOn && hitNow) {
                hurtT = 0;
                net.minecraft.world.entity.Entity att = null;
                try { var src = p.getLastDamageSource(); if (src != null) att = src.getEntity(); } catch (Throwable ignored) {}
                if (att == null) att = p.getLastHurtByMob();
                snapId = att == null ? Integer.MIN_VALUE : att.getId(); snapLeft = c.l2dHurtSnap && att != null ? c.l2dHurtSnapMs / 1000f : 0;
            }
            hurtT += 0.05f;
            if (c.l2dLookOn) {
                // 1.8.3: target from the real player's view (never the camera) with privacy filters, then smoothed eyes
                LookTargets.Target tgt = LookTargets.find(mc, p, c), use = tgt;
                int mainId = tgt == null ? -1 : tgt.id;
                String extra = null;
                // 1.9.0 glance at a newly appeared eligible target, then back
                if (c.l2dGlanceOn) {
                    int gid = LookLogic.glance(GLANCE, LookTargets.ELIGIBLE.keySet(), mainId, c.l2dGlanceMs, c.l2dGlanceCdMs, c.l2dGlanceChance, 0.05f, RNG);
                    LookTargets.Target gt = gid == -1 ? null : LookTargets.ELIGIBLE.get(gid);
                    if (gt != null) { use = gt; extra = "glance"; }
                } else { GLANCE.primed = false; GLANCE.id = -1; }
                // 1.9.0 damage: quick snap toward the attacker, only if it passes every filter
                if (c.l2dHurtOn && snapLeft > 0) {
                    snapLeft -= 0.05f;
                    LookTargets.Target st = LookTargets.eligibleEntity(mc, p, c, level.getEntity(snapId));
                    if (st != null) { use = st; extra = "hurt"; } else snapLeft = 0;
                }
                boolean has = false; float ty = 0, tp = 0; int tid = -1;
                if (use != null) {
                    var e = p.getEyePosition(); var o = use.pos;
                    float[] la = Live2dMath.lookAngles(o.x - e.x, o.y - e.y, o.z - e.z, yaw, p.getXRot(), c.l2dLookMaxYaw);
                    if (la[2] > 0) { ty = la[0]; tp = la[1]; has = true; tid = use.id; }
                }
                if (!has && idle && c.l2dIdleWander > 0) { ty = IDLE.wy; tp = IDLE.wp; has = true; tid = -2; extra = "wander"; }   // 1.9.0 idle wander
                boolean switched = has && EYE.target >= 0 && tid != EYE.target;
                if (has) EYE.target = tid;
                LOOK_S.mode = c.l2dLookSmooth; LOOK_S.respMs = c.l2dLookRespMs; LOOK_S.maxSpeed = c.l2dLookMaxSpeed;
                LOOK_S.saccade = c.l2dLookSaccade; LOOK_S.returnMs = c.l2dLookReturnMs; LOOK_S.switchMs = c.l2dLookSwitchMs;
                if ("hurt".equals(extra) || "glance".equals(extra)) { LOOK_S.respMs = Math.min(LOOK_S.respMs, "hurt".equals(extra) ? 50 : 90); LOOK_S.switchMs = 0; switched = false; }
                float[] lk = LookLogic.step(EYE, has, ty, tp, switched, LOOK_S, 0.05f, RNG);
                lookY = lk[0]; lookP = lk[1];
                // 1.8.4 eye-aim overlay: smoothed angles (what the eyes show), raw aim, state
                EYE_VIEW = new float[]{lookY, lookP, has ? ty : 0, has ? tp : 0, has && tid != -2 ? 1 : 0};
                eyeState = extra != null ? extra : LookLogic.state(EYE, LOOK_S, has); eyeAt = System.currentTimeMillis();
                in.put("lookYaw", lookY); in.put("lookPitch", lookP);
            } else { EYE_VIEW = null; eyeState = "off"; if (EYE.target >= 0 || lookY != 0 || lookP != 0) { EYE.target = -1; EYE.y = EYE.p = EYE.vy = EYE.vp = 0; lookY = lookP = 0; LookTargets.clear(); } }
            // 1.9.0 blink modifiers: context rate, idle (slower, sleepy droop), damage squint
            float bRate = 1, bSlow = 1, bFloor = 0;
            if (c.l2dCtxOn) bRate *= contextRate(mc, p, c);
            if (idle) { bRate *= c.l2dIdleBlinkPct / 100f; bSlow = c.l2dIdleSlowPct / 100f; bFloor = c.l2dIdleDroop / 100f; }
            if (c.l2dHurtOn) bFloor = Math.max(bFloor, LookLogic.hurtLevel(hurtT, c.l2dHurtMs / 1000f) * c.l2dHurtPct / 100f);
            blinkRate = bRate; blinkSlow = bSlow; blinkFloor = bFloor;
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
                boolean chainOff = ecOff || (snap != null && TriggerLogic.eval(ch.off, ch.offCombine, snap));
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
                boolean isBlink = "blink".equals(g.kind);
                gs.rate = isBlink ? blinkRate : 1; gs.slow = isBlink ? blinkSlow : 1;
                float lv = Live2dMath.stepGen(g, gs, 0.05f, RNG);
                if (isBlink) lv = Math.max(lv, blinkFloor);   // 1.9.0 sleepy droop / damage squint
                int max = CpmBridge.maxValue(g.anim);
                int v = Live2dMath.genValue(g, lv, max);
                LIVEG.put(g.id, new float[]{lv, v, max});
                per.computeIfAbsent(g.anim, k -> new java.util.ArrayList<>()).add(new int[]{v, Live2dMath.genValue(g, 0, max), max});
                // 1.8.3: the same level on the extra layers (e.g. both eyelids blink together)
                if (g.more != null) for (String m : g.more) {
                    if (m == null || m.isBlank() || m.equals(g.anim)) continue;
                    int mm = CpmBridge.maxValue(m);
                    per.computeIfAbsent(m, k -> new java.util.ArrayList<>()).add(new int[]{Live2dMath.genValue(g, lv, mm), Live2dMath.genValue(g, 0, mm), mm});
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
