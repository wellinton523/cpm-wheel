package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * First-person hand/item animations (original implementation). Applied to the hand PoseStack from the loader's
 * RenderHandEvent, before vanilla/CPM draw the arm, so CPM's first-person hand, our hand-animation and glow hooks
 * all still run. Settings come from a snapshot (load()), never read from disk per frame.
 */
public final class HandAnim {
    private HandAnim() {}
    private static final Logger LOG = LoggerFactory.getLogger("cpmwheel");

    // ---- settings snapshot ----
    public static final String[] FEATURES = {"idle", "bob", "inertia", "jump", "sneak", "swap", "poses", "swing", "eat", "swim"};
    static volatile boolean master = true;
    static final boolean[] on = new boolean[FEATURES.length];
    static { java.util.Arrays.fill(on, true); }
    static volatile float gI = 1, bobI = 1, inerI = 1, poseI = 1, swingI = 1, idleI = 1;
    /** features switched off because another mod handles them */
    static final boolean[] blocked = new boolean[FEATURES.length];
    static volatile boolean allBlocked = false;
    public static String conflictText = "";

    public static void load(WheelConfig c) {
        master = c.handAnim;
        for (int i = 0; i < FEATURES.length; i++) on[i] = !c.handAnimOff.contains(FEATURES[i]);
        gI = c.haIntensity / 100f; bobI = c.haBob / 100f; inerI = c.haInertia / 100f;
        poseI = c.haPose / 100f; swingI = c.haSwing / 100f; idleI = c.haIdle / 100f;
    }
    static int fi(String f) { for (int i = 0; i < FEATURES.length; i++) if (FEATURES[i].equals(f)) return i; return -1; }
    static boolean f(int i) { return on[i] && !blocked[i]; }

    /** called once at client setup with the loader's "is mod loaded" check */
    public static void detectConflicts(java.util.function.Predicate<String> loaded) {
        StringBuilder sb = new StringBuilder();
        if (any(loaded, "hold_my_items", "holdmyitems")) { allBlocked = true; note(sb, "Hold My Items: all"); }
        if (any(loaded, "firstperson", "firstpersonmod")) { allBlocked = true; note(sb, "First Person Model: all"); }
        if (any(loaded, "realcamera")) { block("bob", "inertia", "jump", "sneak", "swim", "idle"); note(sb, "Real Camera: idle/bob/inertia/jump/sneak/swim"); }
        if (any(loaded, "bettercombat")) { block("swing", "poses"); note(sb, "Better Combat: poses/swing"); }
        if (any(loaded, "eating_animation", "eatinganimation")) { block("eat"); note(sb, "Eating Animation: eat"); }
        conflictText = sb.toString();
        if (!conflictText.isEmpty()) LOG.info("[cpmwheel] hand animations: disabled because of other mods -> " + conflictText);
    }
    private static boolean any(java.util.function.Predicate<String> l, String... ids) { for (String s : ids) if (l.test(s)) return true; return false; }
    private static void block(String... f) { for (String s : f) blocked[fi(s)] = true; }
    private static void note(StringBuilder sb, String s) { if (sb.length() > 0) sb.append("; "); sb.append(s); }

    // ---- per-tick state (client thread) ----
    private static int lastSlot = -1; private static Item lastMain = null;
    private static boolean wasGround = true; private static float fallPeak = 0;
    private static float springPos, springVel; // jump/land
    private static int swingCount; private static boolean wasSwinging;
    private static long swapStart = -1;

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) { lastSlot = -1; lastMain = null; return; }
        int slot = p.getInventory().selected; Item main = p.getMainHandItem().getItem();
        if (lastSlot != -1 && (slot != lastSlot || main != lastMain)) swapStart = System.nanoTime();
        lastSlot = slot; lastMain = main;
        boolean g = p.onGround();
        if (!g) fallPeak = Math.max(fallPeak, p.fallDistance);
        if (wasGround && !g && p.getDeltaMovement().y > 0.1) springVel += 0.9f;            // jump: hands lift
        if (!wasGround && g) { springVel -= Math.min(2.2f, 0.8f + fallPeak * 0.25f); fallPeak = 0; } // land: dip
        wasGround = g;
        boolean sw = p.swinging;
        if (sw && !wasSwinging) swingCount++;
        wasSwinging = sw;
    }

    // ---- per-frame state (render thread = client thread) ----
    private static long lastNs = 0, frame = 0;
    private static float sYaw = Float.NaN, sPitch, sneakW, swimW, climbW;
    private static final float[][] pose = new float[2][6];
    private static PoseStack.Pose savedFor; private static long savedFrame = -1;
    private static final Matrix4f savedPose = new Matrix4f(); private static final Matrix3f savedNormal = new Matrix3f();
    private static boolean loggedFirst;

    /** loader calls this at the start of every rendered frame */
    public static void frameStart() {
        frame++;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Mth.clamp((now - lastNs) / 1e9f, 0f, 0.1f);
        lastNs = now;
        Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player;
        if (p == null) { sYaw = Float.NaN; return; }
        // smoothed camera for inertia
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (Float.isNaN(sYaw)) { sYaw = yaw; sPitch = pitch; }
        float k = 1 - (float) Math.exp(-dt * 10);
        sYaw += Mth.wrapDegrees(yaw - sYaw) * k; sPitch += (pitch - sPitch) * k;
        float k2 = 1 - (float) Math.exp(-dt * 8);
        sneakW += ((p.isCrouching() ? 1 : 0) - sneakW) * k2;
        swimW += ((p.isSwimming() || (p.isInWater() && !p.onGround()) ? 1 : 0) - swimW) * k2;
        climbW += ((p.onClimbable() && !p.onGround() ? 1 : 0) - climbW) * k2;
        // jump/land spring (substeps for stability)
        for (float t = dt; t > 0; t -= 0.01f) {
            float h = Math.min(0.01f, t);
            springVel += (-90f * springPos - 11f * springVel) * h;
            springPos += springVel * h;
        }
        springPos = Mth.clamp(springPos, -0.25f, 0.25f);
        // per-hand category pose blend
        for (int hi = 0; hi < 2; hi++) {
            float[] tgt = target(p, hi == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            float kp = 1 - (float) Math.exp(-dt * 7);
            for (int j = 0; j < 6; j++) pose[hi][j] += (tgt[j] - pose[hi][j]) * kp;
        }
    }

    private static final float[] ZERO = new float[6];
    /** {tx, ty, tz, rx, ry, rz} for the held item category (rz/ry/tx mirrored by arm side later) */
    private static float[] target(LocalPlayer p, InteractionHand hand) {
        if (!f(fi("poses"))) return ZERO;
        if (p.isUsingItem() && p.getUsedItemHand() == hand) return ZERO; // let vanilla bow/shield/eat poses win
        ItemStack s = p.getItemInHand(hand); Item it = s.getItem();
        if (s.isEmpty()) return hand == InteractionHand.MAIN_HAND ? new float[]{0.02f, -0.03f, 0, 6, 0, 4} : ZERO;
        if (it instanceof SwordItem) return new float[]{0, 0.01f, 0, -4, 0, -6};
        if (it instanceof AxeItem) return new float[]{0, -0.02f, 0.01f, -8, 0, -10};
        if (it instanceof DiggerItem) return new float[]{0, -0.01f, 0, -4, 0, -4};
        if (it instanceof BowItem) return new float[]{-0.02f, 0, 0, 0, 6, -4};
        if (it instanceof CrossbowItem) return new float[]{0, -0.02f, 0, 4, 0, 0};
        if (it instanceof TridentItem) return new float[]{0, 0.03f, 0, -10, 0, 0};
        if (it instanceof ShieldItem) return new float[]{0.03f, 0, 0, 0, -8, 0};
        if (it instanceof BlockItem) return new float[]{0, -0.02f, 0, 6, 10, 0};
        UseAnim ua = s.getUseAnimation();
        if (ua == UseAnim.EAT || ua == UseAnim.DRINK) return new float[]{0, -0.01f, 0, 4, 0, 2};
        return ZERO;
    }

    /** apply to the hand PoseStack; called from the loader's RenderHandEvent for each hand */
    public static void apply(PoseStack ps, InteractionHand hand, ItemStack stack, float pt, float swing) {
        if (!master || allBlocked) return;
        Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player;
        if (p == null || Float.isNaN(sYaw)) return;
        try {
            // undo what we did to the other hand this frame (vanilla does not push between hands)
            PoseStack.Pose last = ps.last();
            if (savedFor == last && savedFrame == frame) { last.pose().set(savedPose); last.normal().set(savedNormal); }
            else { savedPose.set(last.pose()); savedNormal.set(last.normal()); savedFor = last; savedFrame = frame; }
            if (!loggedFirst) { loggedFirst = true; LOG.info("[cpmwheel] hand animations active (first frame)"); }

            boolean right = (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT);
            float side = right ? 1 : -1, G = gI;
            float t = p.tickCount + pt;
            float tx = 0, ty = 0, tz = 0, rx = 0, ry = 0, rz = 0;

            if (f(0)) { float I = G * idleI; ty += Mth.sin(t * 0.08f) * 0.006f * I; rz += Mth.sin(t * 0.05f) * 0.6f * I * side; rx += Mth.cos(t * 0.06f) * 0.4f * I; }
            if (f(1)) {
                float walk = Mth.lerp(pt, p.walkDistO, p.walkDist) * (float) Math.PI;
                float b = Mth.lerp(pt, p.oBob, p.bob) * (p.isSprinting() ? 1.4f : 1f) * G * bobI;
                tx += Mth.sin(walk) * b * 0.08f; ty -= Math.abs(Mth.cos(walk)) * b * 0.10f; rz += Mth.sin(walk) * b * 3f;
            }
            if (f(2)) {
                float I = G * inerI;
                float dy = Mth.clamp(Mth.wrapDegrees(p.getViewYRot(pt) - sYaw), -40, 40), dp = Mth.clamp(p.getViewXRot(pt) - sPitch, -40, 40);
                ry += dy * 0.15f * I; tx += dy * 0.0015f * I; rx += dp * 0.12f * I; ty -= dp * 0.0012f * I;
            }
            if (f(3)) ty += springPos * 0.35f * G;
            if (f(4)) { ty -= 0.06f * sneakW * G; rx += 4f * sneakW * G; }
            if (f(5) && swapStart > 0 && hand == InteractionHand.MAIN_HAND) {
                float ph = (System.nanoTime() - swapStart) / 1e9f / 0.3f;
                if (ph < 1) { float e = Mth.sin(ph * (float) Math.PI) * G; rx -= 25f * e; rz += 10f * e * side; ty -= 0.05f * e; }
            }
            float[] pz = pose[hand == InteractionHand.MAIN_HAND ? 0 : 1];
            float P = G * poseI;
            tx += pz[0] * side * P; ty += pz[1] * P; tz += pz[2] * P; rx += pz[3] * P; ry += pz[4] * side * P; rz += pz[5] * side * P;
            if (f(7) && swing > 0 && hand == InteractionHand.MAIN_HAND) {
                float s = Mth.sin(swing * (float) Math.PI) * G * swingI; Item it = stack.getItem();
                if (stack.isEmpty()) tz -= 0.05f * s;
                else if (it instanceof AxeItem) rx -= 20f * s;                          // heavy overhead chop
                else if (it instanceof SwordItem || it instanceof TridentItem) {
                    if ((swingCount & 1) == 0) rz -= 15f * s * side; else { rz += 12f * s * side; rx -= 10f * s; } // alternating slashes
                } else if (it instanceof DiggerItem) rx -= 8f * s;
                else rz -= 6f * s * side;
            }
            if (f(8) && p.isUsingItem() && p.getUsedItemHand() == hand) {
                UseAnim ua = p.getUseItem().getUseAnimation(); float u = p.getTicksUsingItem() + pt;
                if (ua == UseAnim.EAT) { rz += Mth.sin(u * 0.9f) * 4f * side * G; ty += Mth.sin(u * 1.2f) * 0.01f * G; }
                else if (ua == UseAnim.DRINK) { rx -= Math.min(1f, u / 8f) * 8f * G; }
            }
            if (f(9)) {
                if (swimW > 0.01f) { ty -= 0.04f * swimW * G; rx += Mth.sin(t * 0.15f) * 6f * swimW * G; rz += Mth.sin(t * 0.15f) * 4f * side * swimW * G; }
                if (climbW > 0.01f) ty += Mth.sin(t * 0.4f + (right ? 0 : (float) Math.PI)) * 0.04f * climbW * G;
            }
            if (tx != 0 || ty != 0 || tz != 0) ps.translate(tx, ty, tz);
            if (ry != 0) ps.mulPose(Axis.YP.rotationDegrees(ry));
            if (rx != 0) ps.mulPose(Axis.XP.rotationDegrees(rx));
            if (rz != 0) ps.mulPose(Axis.ZP.rotationDegrees(rz));
        } catch (Throwable th) {
            if (!loggedErr) { loggedErr = true; LOG.warn("[cpmwheel] hand animation error (disabled for this session)", th); }
            master = false;
        }
    }
    private static boolean loggedErr;
}
