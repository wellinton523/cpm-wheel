package com.welli.cpmwheel;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;

/**
 * 1.9.2 "Movimento real no mundo": where each chain's anchor really is and how it moves (see WorldMath).
 *
 * Real pose: while the world is drawn (third person / mirror views), right after the local player is rendered, the
 * post-animation transforms CPM used for that frame are read (part roots: vanilla pose + CPM animations; the anchor bone's
 * ancestors) and the anchor's model-space velocity is measured between frames with their real timestamps.
 * Without a fresh render (first person, CPM internals unreadable) the vanilla pose is rebuilt from the player state
 * (head, walk swing, sneaking, sitting) with the last known anchor point. The player's own motion and the body turn always
 * come from the tick positions. Everything is fail-safe: errors only turn the extra motion off.
 */
public final class PartTrack {
    private PartTrack() {}

    /** one anchor per chain */
    static final class Anchor {
        int part = WorldMath.BODY; Object cube; String anim = ""; String mode = ""; boolean detected;
        float[] local;                       // anchor point in the part root's frame (pixels): captured, else default
        long checkedTick = -1000; Object def;
        // real (render) samples, model space
        float[] lastFull, lastNoHead; long lastNs;
        final WorldMath.Vel realFull = new WorldMath.Vel(), realNoHead = new WorldMath.Vel();
        long realAt;
        // approximation (tick), model space
        float[] apFull, apNoHead;
        // smoothed body-frame components
        final WorldMath.Vel turn = new WorldMath.Vel(), head = new WorldMath.Vel(), rest = new WorldMath.Vel();
        float[][] comp = new float[4][3];
        int src;                             // 1 = approximated pose, 2 = real pose
        long usedTick;
        int ep;                              // bumped when the components restart (no impulse from a reset)
    }
    private static final Map<String, Anchor> ANCH = new HashMap<>();
    private static final WorldMath.Vel ROOT = new WorldMath.Vel();
    private static double lx, ly, lz; private static float lyaw; private static boolean init = false;
    private static Object lastLevel, lastPlayer; private static long tick = 0; private static int lastTc = Integer.MIN_VALUE;
    /** bumped on every reset (teleport, respawn, dimension change): the chains restart their inertia at rest */
    public static int epoch = 0;
    private static float kick = 0;
    /** world-pass flag (set by the level render stages) so inventory / GUI renders of the player are ignored */
    public static volatile boolean inWorld = false;
    private static final WorldMath.PoseIn POSE = new WorldMath.PoseIn();
    private static float[] A = new float[3];

    public static void reset() { ANCH.clear(); ROOT.reset(); init = false; epoch++; }
    public static void simKick() { kick = 6f; }

    /** per tick, before the chains step (only while chains run with the world motion on) */
    public static void tick(LocalPlayer p) {
        tick++;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        float yaw = p.yBodyRot;
        boolean jump = !init || p != lastPlayer || p.level() != lastLevel || p.isSleeping() || p.isDeadOrDying();
        double dx = x - lx, dy = y - ly, dz = z - lz;
        if (!jump && dx * dx + dy * dy + dz * dz > 100.0) jump = true;   // teleport / respawn (10+ blocks in a tick): restart at rest
        boolean gap = p.tickCount != lastTc + 1;   // paused / world motion turned back on: skip this sample (no impulse, no snap)
        lastTc = p.tickCount;
        if (gap && !jump) { lx = x; ly = y; lz = z; lyaw = yaw; for (Anchor a : ANCH.values()) { a.apFull = null; a.lastFull = null; } return; }
        if (jump) {
            ROOT.reset(); for (Anchor a : ANCH.values()) resetAnchor(a);
            epoch++; init = true; lastPlayer = p; lastLevel = p.level();
            lx = x; ly = y; lz = z; lyaw = yaw; A = new float[3];
            return;
        }
        lx = x; ly = y; lz = z;
        float dyaw = Live2dMath.unwrap(0, yaw - lyaw);
        float yaw0 = yaw - dyaw; lyaw = yaw;
        boolean was = ROOT.primed;
        if (ROOT.add((float) dx * 20f, (float) dy * 20f, (float) dz * 20f, 0.05f, WorldMath.MAX_SPEED, WorldMath.MAX_DV))
            A = WorldMath.toBody(ROOT.v[0], ROOT.v[1], ROOT.v[2], yaw);
        else A = new float[3];
        if (!was) epoch++;   // (re)started: the chains take the current speed as rest
        POSE.headYawRel = Live2dMath.unwrap(0, p.getYHeadRot() - yaw); POSE.headPitch = p.getXRot();
        POSE.limbPos = p.walkAnimation.position(); POSE.limbAmt = Math.min(1f, p.walkAnimation.speed());
        POSE.crouch = p.isCrouching(); POSE.riding = p.isPassenger();
        long now = System.nanoTime();
        float k = kick; kick = 0;
        for (var it = ANCH.entrySet().iterator(); it.hasNext(); ) {
            Anchor a = it.next().getValue();
            if (tick - a.usedTick > 40) { it.remove(); continue; }   // chain gone
            float[] local = a.local != null ? a.local : WorldMath.defaultOffset(a.part);
            float[] pf = WorldMath.apply(WorldMath.vanillaPose(a.part, POSE, false), local);
            float[] pn = a.part == WorldMath.HEAD ? WorldMath.apply(WorldMath.vanillaPose(a.part, POSE, true), local) : pf;
            float[] vF = null, vN = null;
            boolean real = a.realFull.primed && now - a.realAt < 150_000_000L;
            if (real) { vF = a.realFull.v; vN = a.realNoHead.v; a.src = 2; }
            else if (a.apFull != null) { vF = diff(pf, a.apFull, 20f); vN = diff(pn, a.apNoHead, 20f); a.src = 1; }
            a.apFull = pf; a.apNoHead = pn;
            boolean p0 = a.turn.primed && a.rest.primed && a.head.primed;
            float[] t = WorldMath.turnVel(pf, yaw0, yaw, POSE.crouch, 0.05f);
            a.turn.add(t[0], t[1], t[2], 0.05f, WorldMath.MAX_SPEED, WorldMath.MAX_DV);
            if (vF != null) {
                float[] rb = modelVelToBody(vN, yaw, POSE.crouch);
                float[] fb = modelVelToBody(vF, yaw, POSE.crouch);
                a.rest.add(rb[0], rb[1], rb[2], 0.05f, WorldMath.MAX_POSE_SPEED, WorldMath.MAX_DV);
                a.head.add(fb[0] - rb[0], fb[1] - rb[1], fb[2] - rb[2], 0.05f, WorldMath.MAX_POSE_SPEED, WorldMath.MAX_DV);
            }
            if (p0 != (a.turn.primed && a.rest.primed && a.head.primed)) a.ep++;
            a.comp = new float[][]{A.clone(), a.turn.v.clone(), a.head.v.clone(), new float[]{a.rest.v[0] + k, a.rest.v[1] + k, a.rest.v[2]}};
        }
    }
    private static void resetAnchor(Anchor a) {
        a.ep++; a.realFull.reset(); a.realNoHead.reset(); a.lastFull = null; a.apFull = null; a.turn.reset(); a.head.reset(); a.rest.reset(); a.comp = new float[4][3];
    }
    private static float[] diff(float[] a, float[] b, float perSec) {
        return new float[]{(a[0] - b[0]) * perSec, (a[1] - b[1]) * perSec, (a[2] - b[2]) * perSec};
    }
    /** model-space velocity (pixels/s) -> body frame (blocks/s) */
    private static float[] modelVelToBody(float[] v, float yaw, boolean crouch) {
        float[] o = WorldMath.modelToWorld(new float[]{0, 0, 0}, yaw, crouch), w = WorldMath.modelToWorld(v, yaw, crouch);
        return WorldMath.toBody(w[0] - o[0], w[1] - o[1], w[2] - o[2], yaw);
    }

    /**
     * The chain's anchor and its velocity components {A, B, H, R} (body frame, blocks/s); registers / refreshes the anchor.
     * part = "auto" or a WorldMath part name.
     */
    public static float[][] components(Live2dMath.Chain ch) {
        Anchor a = ANCH.computeIfAbsent(ch.id, k -> new Anchor());
        a.usedTick = tick;
        String anim = "";
        for (Live2dMath.Link l : ch.links) if (l != null && l.anim != null && !l.anim.isBlank()) { anim = l.anim; break; }
        String mode = ch.worldPart == null ? "auto" : ch.worldPart;
        Object def = CpmBridge.definition();
        if (!anim.equals(a.anim) || !mode.equals(a.mode) || def != a.def || tick - a.checkedTick > 200) {
            boolean changed = !anim.equals(a.anim) || !mode.equals(a.mode) || def != a.def;
            a.anim = anim; a.mode = mode; a.def = def; a.checkedTick = tick;
            Object cube = CpmBridge.layerCube(anim);
            int cp = cube == null ? -1 : CpmBridge.cubePart(cube);
            int fixed = WorldMath.partIndex(mode);
            int part = fixed >= 0 ? fixed : cp >= 0 ? cp : WorldMath.guess(ch);
            a.detected = fixed < 0 && cp >= 0;
            if (cp != part) cube = null;            // a bone of another part: use the chosen part's default point
            if (changed || part != a.part || cube != a.cube) { a.part = part; a.cube = cube; a.local = null; resetAnchor(a); }
        }
        return a.comp;
    }
    /** changes whenever the chain's motion restarted (teleport, new anchor…): its inertia restarts at rest */
    public static int epochOf(String chainId) { Anchor a = ANCH.get(chainId); return epoch * 4099 + (a == null ? 0 : a.ep); }
    /** {part index, source (0 none / 1 approximated / 2 real), detected (1/0)} of a chain, for the screens */
    public static int[] info(String chainId) {
        Anchor a = ANCH.get(chainId);
        return a == null ? null : new int[]{a.part, a.src, a.detected ? 1 : 0};
    }

    // ---------- render-time capture (main thread, world pass only) ----------
    /** right after the local player was drawn in the world: read the real pose CPM used for this frame */
    public static void capture(Object entity, PlayerModel<?> model) {
        if (!inWorld || ANCH.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (entity == null || entity != mc.player || model == null) return;
            long now = System.nanoTime();
            boolean[] need = new boolean[6];
            for (Anchor a : ANCH.values()) need[a.part] = true;
            float[][] roots = CpmBridge.rootPoses(CpmBridge.definition(), need);
            ModelPart[] mp = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
            float hx = model.head.xRot, hy = model.head.yRot;
            for (Anchor a : ANCH.values()) {
                float[] root = roots[a.part];
                if (root == null) { ModelPart m = mp[a.part]; root = new float[]{m.x, m.y, m.z, m.xRot, m.yRot, m.zRot}; }
                if (a.cube != null) { float[] l = CpmBridge.cubeAnchor(a.cube); if (l != null) a.local = l; }
                float[] local = a.local != null ? a.local : WorldMath.defaultOffset(a.part);
                float[] pf = WorldMath.apply(root, local);
                float[] pn = pf;
                if (a.part == WorldMath.HEAD) pn = WorldMath.apply(new float[]{root[0], root[1], root[2], root[3] - hx, root[4] - hy, root[5]}, local);
                float dt = (now - a.lastNs) / 1e9f;
                if (a.lastFull != null && dt >= 0.004f && dt <= 0.2f) {
                    float[] vf = diff(pf, a.lastFull, 1f / dt), vn = diff(pn, a.lastNoHead, 1f / dt);
                    float lim = WorldMath.MAX_POSE_SPEED * 16f / 0.9375f;
                    a.realFull.add(vf[0], vf[1], vf[2], dt, lim, lim);
                    a.realNoHead.add(vn[0], vn[1], vn[2], dt, lim, lim);
                    a.realAt = now;
                } else if (a.lastFull != null && dt < 0.004f) continue;   // same frame (several passes): keep the older sample
                else { a.realFull.reset(); a.realNoHead.reset(); }
                a.lastFull = pf; a.lastNoHead = pn; a.lastNs = now;
            }
        } catch (Throwable t) {
            CpmWheel.LOG.debug("[CPM Wheel] world motion capture failed", t);
        }
    }
}
