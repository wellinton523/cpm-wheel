package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
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
    public static final String[] FEATURES = {"idle", "bob", "inertia", "jump", "sneak", "swap", "poses", "swing", "eat", "swim", "arm", "lantern"};
    static volatile boolean master = true;
    static final boolean[] on = new boolean[FEATURES.length];
    static { java.util.Arrays.fill(on, true); }
    static volatile float gI = 1, bobI = 1, inerI = 1, poseI = 1, swingI = 1, idleI = 1;
    /** features switched off because another mod handles them (recomputed in load() from detected mods + "ignore" overrides) */
    static final boolean[] blocked = new boolean[FEATURES.length];
    static volatile boolean allBlocked = false;
    public static String conflictText = "";
    /** other mods we know about: key, display name, mod ids */
    public static final String[][] MODS = {
            {"hmi", "Hold My Items", "hold_my_items", "holdmyitems"},
            {"fpm", "First Person Model", "firstperson", "firstpersonmod"},
            {"realcamera", "Real Camera", "realcamera"},
            {"bettercombat", "Better Combat", "bettercombat"},
            {"eating", "Eating Animation", "eating_animation", "eatinganimation"}};
    public static final boolean[] modLoaded = new boolean[MODS.length];
    private static java.util.List<String> ignored = java.util.List.of();
    /** Better Combat present and not ignored: our swing variations pause while a BC attack runs */
    static volatile boolean bcCompat = false;
    static volatile boolean failed = false;

    public static void load(WheelConfig c) {
        master = c.handAnim;
        for (int i = 0; i < FEATURES.length; i++) on[i] = !c.handAnimOff.contains(FEATURES[i]);
        gI = c.haIntensity / 100f; bobI = c.haBob / 100f; inerI = c.haInertia / 100f;
        poseI = c.haPose / 100f; swingI = c.haSwing / 100f; idleI = c.haIdle / 100f;
        int[][] na = new int[CATS.length][7];
        for (int i = 0; i < CATS.length; i++) { int[] v = c.haAdj.get(CATS[i]); if (v != null && v.length == 7) na[i] = v.clone(); }
        loadItems(c);
        adj = na; vanillaItem = c.haVanillaItem; twoHandBlock = c.haTwoHandBlock;
        boolean[] fl = new boolean[CATS.length]; for (int i = 0; i < CATS.length; i++) fl[i] = c.haFlat.contains(CATS[i]); flatCats = fl;
        int[][] am = new int[2][7];
        int[] m = c.haArm.get("main"), o = c.haArm.get("off");
        if (m != null && m.length == 7) am[0] = m.clone(); if (o != null && o.length == 7) am[1] = o.clone();
        arm = am;
        if (!c.haIgnore.equals(ignored)) { ignored = java.util.List.copyOf(c.haIgnore); recompute(true); }
    }
    static int fi(String f) { for (int i = 0; i < FEATURES.length; i++) if (FEATURES[i].equals(f)) return i; return -1; }
    static boolean f(int i) { return on[i] && !blocked[i]; }
    public static boolean active(String mod) { for (int i = 0; i < MODS.length; i++) if (MODS[i][0].equals(mod)) return modLoaded[i] && !ignored.contains(mod); return false; }

    /** called once at client setup with the loader's "is mod loaded" check */
    public static void detectConflicts(java.util.function.Predicate<String> loaded) {
        for (int i = 0; i < MODS.length; i++) for (int j = 2; j < MODS[i].length; j++) if (loaded.test(MODS[i][j])) modLoaded[i] = true;
        LOG.info("[cpmwheel] hand animations: stage 1 registered (events on game bus, client dist)");
        recompute(true);
    }

    private static void recompute(boolean log) {
        java.util.Arrays.fill(blocked, false);
        boolean all = false; bcCompat = false;
        StringBuilder sb = new StringBuilder();
        if (active("hmi")) { all = true; note(sb, "Hold My Items: all"); }
        if (active("fpm")) { all = true; note(sb, "First Person Model: all"); }
        if (active("realcamera")) { block("arm", "bob", "inertia", "jump", "sneak", "swim", "idle"); note(sb, "Real Camera: arm/idle/bob/inertia/jump/sneak/swim"); }
        if (active("bettercombat")) { bcCompat = true; note(sb, "Better Combat: swing variations pause during BC attacks"); }
        if (active("eating")) { block("eat"); note(sb, "Eating Animation: eat"); }
        allBlocked = all;
        conflictText = sb.toString();
        if (log) LOG.info("[cpmwheel] hand animations: compat -> " + (conflictText.isEmpty() ? "none" : conflictText) + " (ignored: " + ignored + ")");
    }
    private static void block(String... f) { for (String s : f) blocked[fi(s)] = true; }
    private static void note(StringBuilder sb, String s) { if (sb.length() > 0) sb.append("; "); sb.append(s); }

    // ---- Better Combat attack detection (reflection on the client-side BC interface, else assume attacking) ----
    private static java.lang.reflect.Method bcSwing; private static boolean bcLooked;
    static boolean bcAttacking() {
        if (!bcCompat) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!bcLooked) {
            bcLooked = true;
            for (String n : new String[]{"isWeaponSwingInProgress", "getSwingProgress"}) {
                try { bcSwing = mc.getClass().getMethod(n); break; } catch (Throwable ignored) {}
            }
            LOG.info("[cpmwheel] hand animations: Better Combat API " + (bcSwing != null ? "found (" + bcSwing.getName() + ")" : "not found -> our swing variations stay off"));
        }
        if (bcSwing == null) return true;
        try {
            Object r = bcSwing.invoke(mc);
            if (r instanceof Boolean b) return b;
            if (r instanceof Number n) { float v = n.floatValue(); return v > 0 && v < 1; }
        } catch (Throwable ignored) {}
        return true;
    }

    // ---- pipeline status (shown on the settings screen) ----
    static volatile long lastFrameMs, lastHandMs, lastApplyMs;
    private static boolean logFrame, logHand;
    /** translation key + arg of the status line */
    public static String[] status() {
        long now = System.currentTimeMillis();
        if (failed) return new String[]{"cpmwheel.ha.st.error", ""};
        if (!master) return new String[]{"cpmwheel.ha.st.off", ""};
        if (allBlocked) return new String[]{"cpmwheel.ha.st.blocked", conflictText};
        if (Minecraft.getInstance().player == null) return new String[]{"cpmwheel.ha.st.noworld", ""};
        if (now - lastFrameMs > 3000) return new String[]{"cpmwheel.ha.st.noframe", ""};
        if (now - lastHandMs > 3000) return new String[]{"cpmwheel.ha.st.nohand", ""};
        return new String[]{"cpmwheel.ha.st.ok", ""};
    }

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
    private static float swimPhase, climbPhase;
    private static float pYaw, pPitch, lanZ, lanX, lanVZ, lanVX; // lantern pendulum
    private static final float[][] pose = new float[2][6];
    private static PoseStack.Pose savedFor; private static long savedFrame = -1;
    private static final Matrix4f savedPose = new Matrix4f(); private static final Matrix3f savedNormal = new Matrix3f();
    private static boolean loggedFirst;

    /** loader calls this at the start of every rendered frame */
    public static void frameStart() {
        frame++;
        lastFrameMs = System.currentTimeMillis();
        if (!logFrame) { logFrame = true; LOG.info("[cpmwheel] hand animations: stage 2 frame event received"); }
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
        // lantern pendulum: driven by turning, looking up/down and walking
        if (dt > 0) {
            float yr = Mth.clamp(Mth.wrapDegrees(yaw - pYaw) / dt, -720, 720), pr = Mth.clamp((pitch - pPitch) / dt, -720, 720);
            float walkAcc = (float) (p.getDeltaMovement().horizontalDistance() * 40);
            for (float t = dt; t > 0; t -= 0.01f) {
                float h = Math.min(0.01f, t);
                lanVZ += (-45f * lanZ - 3.5f * lanVZ + yr * 0.9f) * h;
                lanVX += (-45f * lanX - 3.5f * lanVX - pr * 0.6f + walkAcc * Mth.sin(p.tickCount * 0.6f)) * h;
                lanZ += lanVZ * h; lanX += lanVX * h;
            }
            lanZ = Mth.clamp(lanZ, -45, 45); lanX = Mth.clamp(lanX, -45, 45);
        }
        pYaw = yaw; pPitch = pitch;
        // stroke phases: tied to swim speed / climb speed
        net.minecraft.world.phys.Vec3 mv = p.getDeltaMovement();
        swimPhase = (swimPhase + dt * (0.45f + (float) mv.length() * 6f)) % 1f;
        climbPhase = (climbPhase + dt * (0.5f + (float) Math.abs(mv.y) * 12f)) % 1f;
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
    public static boolean apply(PoseStack ps, InteractionHand hand, ItemStack stack, float pt, float swing, float equip, MultiBufferSource buf, int light) {
        lastHandMs = System.currentTimeMillis();
        if (!logHand) { logHand = true; LOG.info("[cpmwheel] hand animations: stage 3 hand event received (master=" + master + ", allBlocked=" + allBlocked + ", failed=" + failed + ")"); }
        Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player;
        if (p == null) return false;
        try {
            // undo what we did to the other hand this frame (vanilla does not push between hands)
            PoseStack.Pose last = ps.last();
            if (savedFor == last && savedFrame == frame) { last.pose().set(savedPose); last.normal().set(savedNormal); }
            else { savedPose.set(last.pose()); savedNormal.set(last.normal()); savedFor = last; savedFrame = frame; }
            // arm position adjust: always applies, even with the other hand animations off
            armAdjust(ps, hand == InteractionHand.MAIN_HAND, (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT) ? 1 : -1, stack.isEmpty() ? null : stack.getItem());
            if (!master || allBlocked || failed || Float.isNaN(sYaw)) return false;
            if (!loggedFirst) { loggedFirst = true; LOG.info("[cpmwheel] hand animations: stage 4 transforms applied (first frame)"); }

            boolean right = (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT);
            float side = right ? 1 : -1, G = gI;
            float t = p.tickCount + pt;
            float tx = 0, ty = 0, tz = 0, rx = 0, ry = 0, rz = 0;

            if (f(0)) { float I = G * idleI; ty += Mth.sin(t * 0.08f) * 0.010f * I; rz += Mth.sin(t * 0.05f) * 0.6f * I * side; rx += Mth.cos(t * 0.06f) * 0.4f * I; }
            if (f(1)) {
                float walk = Mth.lerp(pt, p.walkDistO, p.walkDist) * (float) Math.PI;
                float b = Mth.lerp(pt, p.oBob, p.bob) * (p.isSprinting() ? 1.4f : 1f) * G * bobI;
                tx += Mth.sin(walk) * b * 0.08f; ty -= Math.abs(Mth.cos(walk)) * b * 0.13f; rz += Mth.sin(walk) * b * 4f; rx += Math.abs(Mth.cos(walk)) * b * 2f;
            }
            if (f(2)) {
                float I = G * inerI;
                float dy = Mth.clamp(Mth.wrapDegrees(p.getViewYRot(pt) - sYaw), -40, 40), dp = Mth.clamp(p.getViewXRot(pt) - sPitch, -40, 40);
                ry += dy * 0.25f * I; tx += dy * 0.002f * I; rx += dp * 0.2f * I; ty -= dp * 0.0015f * I;
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
            if (f(7) && swing > 0 && hand == InteractionHand.MAIN_HAND && !bcAttacking()) {
                float s = Mth.sin(swing * (float) Math.PI) * G * swingI; Item it = stack.getItem();
                if (stack.isEmpty()) tz -= 0.05f * s;
                else if (it instanceof AxeItem) { rx -= 30f * s; ty -= 0.04f * s; }                          // heavy overhead chop
                else if (it instanceof SwordItem || it instanceof TridentItem) {
                    if ((swingCount & 1) == 0) { rz -= 28f * s * side; ry += 10f * s * side; } else { rz += 24f * s * side; rx -= 14f * s; ry -= 8f * s * side; } // alternating slashes
                } else if (it instanceof DiggerItem) { rx -= 14f * s; tz -= 0.03f * s; }
                else rz -= 6f * s * side;
            }
            if (f(8) && p.isUsingItem() && p.getUsedItemHand() == hand) {
                UseAnim ua = p.getUseItem().getUseAnimation(); float u = p.getTicksUsingItem() + pt;
                if (ua == UseAnim.EAT) { rz += Mth.sin(u * 0.9f) * 4f * side * G; ty += Mth.sin(u * 1.2f) * 0.01f * G; }
                else if (ua == UseAnim.DRINK) { rx -= Math.min(1f, u / 8f) * 8f * G; }
            }
            if (f(9)) {
                // both arms: the empty off-hand arm is not drawn by vanilla, so the main-hand pass draws it too
                if ((swimW > 0.05f || climbW > 0.05f) && hand == InteractionHand.MAIN_HAND && p.getOffhandItem().isEmpty() && buf != null && !p.isInvisible()) {
                    ps.pushPose(); restoreSaved(ps); armAdjust(ps, false, -side); stroke(ps, -side, G); armChain(ps, -side, 0, 0); drawArm(ps, p, buf, light, !right); ps.popPose();
                }
                stroke(ps, side, G);
            }
            if (tx != 0 || ty != 0 || tz != 0) ps.translate(tx, ty, tz);
            if (ry != 0) ps.mulPose(Axis.YP.rotationDegrees(ry));
            if (rx != 0) ps.mulPose(Axis.XP.rotationDegrees(rx));
            if (rz != 0) ps.mulPose(Axis.ZP.rotationDegrees(rz));
            return heldExtras(ps, p, hand, stack, swing, equip, buf, light, right);
        } catch (Throwable th) {
            if (!loggedErr) { loggedErr = true; LOG.warn("[cpmwheel] hand animation error (disabled for this session)", th); }
            failed = true;
        }
        return false;
    }
    private static boolean loggedErr, logArm;

    // ---- arm visible with items: we draw arm + item ourselves, item seated in the arm's fist ----
    public static final String[] CATS = {"sword", "axe", "tool", "bow", "crossbow", "trident", "shield", "block", "food", "lantern", "torch", "other", "flatblock"};
    /** built-in per-category base {x, y, z (1/100 block), scale %, rotX, rotY, rotZ (deg)}; user offsets from config are added */
    private static final int[][] BASE = {
            {0, 0, 0, 90, 0, 0, 0},      // sword   (third-person grip, blade tilted up/forward)
            {0, 0, 0, 90, 0, 0, 0},      // axe
            {0, 0, 0, 90, 0, 0, 0},      // tool
            {0, 0, 0, 90, 0, 0, 0},      // bow     (third-person bow pose = vertical in the fist)
            {0, 0, 0, 90, 0, 0, 0},      // crossbow
            {0, 0, 0, 90, 0, 0, 0},      // trident
            {0, 0, 0, 80, 0, 0, 0},      // shield
            {0, 4, 0, 100, 0, 0, 0},     // block   (upright, small, just above the fist)
            {0, 0, 0, 90, 0, 0, 0},      // food
            {0, 0, 0, 45, 0, 0, 0},      // lantern (block model, hanging)
            {0, 0, 0, 55, 0, 0, 0},      // torch   (block model, upright)
            {0, 0, 0, 90, 0, 0, 0},      // other
            {0, 1, 0, 80, 0, 0, 0}};     // flatblock (flowers, doors, saplings, rails, panes...): held like an item, grip in the fist
    static volatile int[][] adj = new int[CATS.length][7];
    static volatile boolean vanillaItem = false, twoHandBlock = false;
    static volatile boolean[] flatCats = new boolean[13];
    /** arm adjust {x,y,z (1/100 block), rx,ry,rz (deg), scale % offset} for main [0] and off hand [1] */
    static volatile int[][] arm = new int[2][7];

    /** view-space arm adjust around the vanilla hand area so rotations/scale stay local; mirrored for the left arm */
    static void armAdjust(PoseStack ps, boolean main, float side) { armAdjust(ps, main, side, null); }
    /** global arm adjust + per-item/per-mod arm override for the held item (priority item > mod) */
    static void armAdjust(PoseStack ps, boolean main, float side, Item held) {
        int[] a = arm[main ? 0 : 1];
        int[] o = held != null && itemOverrides ? pick(ITEM_ARM, MOD_ARM, held, main ? 0 : 1) : null;
        if (o != null) { a = a.clone(); for (int q = 0; q < 7; q++) a[q] += o[q]; }
        boolean any = false; for (int v : a) if (v != 0) { any = true; break; }
        if (!any) return;
        float px = side * 0.6f, py = -0.55f, pz = -0.9f;
        ps.translate(px + side * a[0] / 100f, py + a[1] / 100f, pz + a[2] / 100f);
        if (a[4] != 0) ps.mulPose(Axis.YP.rotationDegrees(side * a[4]));
        if (a[3] != 0) ps.mulPose(Axis.XP.rotationDegrees(a[3]));
        if (a[5] != 0) ps.mulPose(Axis.ZP.rotationDegrees(side * a[5]));
        float sc = Math.max(0.2f, 1 + a[6] / 100f);
        if (sc != 1) ps.scale(sc, sc, sc);
        ps.translate(-px, -py, -pz);
    }
    private static void restoreSaved(PoseStack ps) { ps.last().pose().set(savedPose); ps.last().normal().set(savedNormal); }

    /** block items whose item model is a flat sprite (not gui3d): cached per item */
    private static final java.util.Map<Item, Boolean> FLAT = new java.util.IdentityHashMap<>();
    static boolean flatModel(ItemStack s) {
        Boolean r = FLAT.get(s.getItem());
        if (r == null) {
            try { r = !Minecraft.getInstance().getItemRenderer().getModel(s, null, null, 0).isGui3d(); }
            catch (Throwable t) { r = false; }
            if (FLAT.size() > 4096) FLAT.clear();
            FLAT.put(s.getItem(), r);
        }
        return r;
    }

    // ---- per-item overrides {x,y,z,scale,rx,ry,rz} per hand, added on top of the category values ----
    static volatile boolean itemOverrides = true;
    static volatile java.util.Map<Item, int[][]> ITEM_ADJ = java.util.Map.of();
    static volatile java.util.Map<String, int[][]> MOD_ADJ = java.util.Map.of();
    static volatile java.util.Map<Item, int[][]> ITEM_ARM = java.util.Map.of();
    static volatile java.util.Map<String, int[][]> MOD_ARM = java.util.Map.of();
    static int[] pick(java.util.Map<Item, int[][]> items, java.util.Map<String, int[][]> mods, Item it, int hi) {
        int[][] io = items.get(it);
        int[] o = io == null ? null : io[hi];
        if (o == null && !mods.isEmpty()) { int[][] mo = mods.get(BuiltInRegistries.ITEM.getKey(it).getNamespace()); o = mo == null ? null : mo[hi]; }
        return o;
    }
    static void loadItems(WheelConfig c) {
        java.util.Map<Item, int[][]> m = new java.util.IdentityHashMap<>();
        java.util.Map<String, int[][]> mods = new java.util.HashMap<>(), amods = new java.util.HashMap<>();
        java.util.Map<Item, int[][]> am = new java.util.IdentityHashMap<>();
        for (var e : c.haItem.entrySet()) {
            String k = e.getKey(); int bar = k.lastIndexOf('|');
            if (bar < 0 || e.getValue() == null || e.getValue().length != 7) continue;
            if (k.startsWith("arm/")) {
                String ak = k.substring(4); int ab = ak.lastIndexOf('|');
                int hi = "off".equals(ak.substring(ab + 1)) ? 1 : 0;
                if (ak.startsWith("mod:")) { amods.computeIfAbsent(ak.substring(4, ab), x -> new int[2][])[hi] = e.getValue().clone(); continue; }
                net.minecraft.resources.ResourceLocation arl = net.minecraft.resources.ResourceLocation.tryParse(ak.substring(0, ab));
                if (arl != null && BuiltInRegistries.ITEM.containsKey(arl))
                    am.computeIfAbsent(BuiltInRegistries.ITEM.get(arl), x -> new int[2][])[hi] = e.getValue().clone();
                continue;
            }
            if (k.startsWith("mod:")) {
                mods.computeIfAbsent(k.substring(4, bar), x -> new int[2][])["off".equals(k.substring(bar + 1)) ? 1 : 0] = e.getValue().clone();
                continue;
            }
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(k.substring(0, bar));
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            Item it = BuiltInRegistries.ITEM.get(rl);
            m.computeIfAbsent(it, x -> new int[2][])["off".equals(k.substring(bar + 1)) ? 1 : 0] = e.getValue().clone();
        }
        ITEM_ADJ = m; MOD_ADJ = mods; ITEM_ARM = am; MOD_ARM = amods; itemOverrides = c.haItemOn;
    }

    static int cat(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof SwordItem) return 0;
        if (it instanceof AxeItem) return 1;
        if (it instanceof DiggerItem) return 2;
        if (it instanceof BowItem) return 3;
        if (it instanceof CrossbowItem) return 4;
        if (it instanceof TridentItem) return 5;
        if (it instanceof ShieldItem) return 6;
        if (it instanceof BlockItem bi) {
            if (bi.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) return 9;
            if (BuiltInRegistries.ITEM.getKey(it).getPath().contains("torch")) return 10;
            return flatModel(s) ? 12 : 7;
        }
        UseAnim ua = s.getUseAnimation();
        if (ua == UseAnim.EAT || ua == UseAnim.DRINK) return 8;
        return 11;
    }

    private static final org.joml.Matrix4f TMP = new org.joml.Matrix4f();
    private static final org.joml.Vector3f FIST = new org.joml.Vector3f();

    /** vanilla empty-hand arm chain (on screen, natural position) */
    private static void armChain(PoseStack ps, float f, float swing, float equip) {
        float g = Mth.sqrt(swing);
        float h = -0.3f * Mth.sin(g * (float) Math.PI), i = 0.4f * Mth.sin(g * (float) Math.PI * 2), j = -0.4f * Mth.sin(swing * (float) Math.PI);
        ps.translate(f * (h + 0.64f), i - 0.6f + equip * -0.6f, j - 0.72f);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45f));
        float k = Mth.sin(swing * swing * (float) Math.PI), l = Mth.sin(g * (float) Math.PI);
        ps.mulPose(Axis.YP.rotationDegrees(f * l * 70f));
        ps.mulPose(Axis.ZP.rotationDegrees(f * k * -20f));
        ps.translate(f * -1f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120f));
        ps.mulPose(Axis.XP.rotationDegrees(200f));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135f));
        ps.translate(f * 5.6f, 0f, 0f);
    }

    /** fist position (where third-person items are held) relative to the current frame, for this arm pose */
    private static org.joml.Vector3f fist(float f, float swing, float equip) {
        PoseStack t = new PoseStack();
        armChain(t, f, swing, equip);
        // arm ModelPart pivot (+-5, 2, 0) px, then vanilla's held-item offset (1/16, 2/16, -10/16) in the arm frame
        t.translate(f * -5f / 16f, 2f / 16f, 0);
        t.mulPose(Axis.XP.rotationDegrees(-90f));
        t.mulPose(Axis.YP.rotationDegrees(180f));
        t.translate(f / 16f, 0.125f, -0.625f);
        t.last().pose().getNormalizedRotation(GRIP);
        // blade direction in third person = arm-frame forward; tilt the whole grip so it points up/forward like a held tool
        org.joml.Vector3f blade = GRIP.transform(new org.joml.Vector3f(0, 1, 0));
        org.joml.Vector3f want = new org.joml.Vector3f(-0.2f * f, 0.8f, -0.55f).normalize();
        new org.joml.Quaternionf().rotationTo(blade, want).mul(GRIP, GRIP);
        return t.last().pose().transformPosition(0, 0, 0, FIST);
    }
    private static final org.joml.Quaternionf GRIP = new org.joml.Quaternionf();

    /** returns true when we drew the hand ourselves (caller cancels the vanilla hand) */
    private static boolean heldExtras(PoseStack ps, LocalPlayer p, InteractionHand hand, ItemStack stack, float swing, float equip,
                                      MultiBufferSource buf, int light, boolean right) {
        if (stack.isEmpty() || buf == null || !f(10) || p.isInvisible()) return false;
        Item it = stack.getItem();
        if (it instanceof MapItem) return false;                                       // vanilla draws arms with maps
        if (p.isUsingItem() && p.getUsedItemHand() == hand) return false;              // bow draw, eating, shield block: vanilla
        if (it instanceof CrossbowItem && CrossbowItem.isCharged(stack)) return false; // aiming pose: vanilla
        float f = right ? 1 : -1;
        if (vanillaItem) {                                                             // our arm, vanilla item position
            ps.pushPose(); armChain(ps, f, swing, equip); drawArm(ps, p, buf, light, right); ps.popPose();
            return false;
        }
        int c = cat(stack);
        boolean two = c == 7 && twoHandBlock && hand == InteractionHand.MAIN_HAND && p.getOffhandItem().isEmpty();
        ps.pushPose(); armChain(ps, f, swing, equip); drawArm(ps, p, buf, light, right); ps.popPose();
        if (two) { ps.pushPose(); armAdjust(ps, false, -f); armChain(ps, -f, 0, equip); drawArm(ps, p, buf, light, !right); ps.popPose(); }

        org.joml.Vector3f fp = fist(f, swing, equip);
        int[] b = BASE[c], a = adj[c].clone();
        if (itemOverrides) {
            int hi = hand == InteractionHand.MAIN_HAND ? 0 : 1;
            int[][] io = ITEM_ADJ.get(it);
            int[] o = io == null ? null : io[hi];                    // priority: item > mod > category
            if (o == null && !MOD_ADJ.isEmpty()) {
                int[][] mo = MOD_ADJ.get(BuiltInRegistries.ITEM.getKey(it).getNamespace());
                o = mo == null ? null : mo[hi];
            }
            if (o != null) for (int q = 0; q < 7; q++) a[q] += o[q];
        }
        ps.pushPose();
        ps.translate(fp.x + f * (b[0] + a[0]) / 100f, fp.y + (b[1] + a[1]) / 100f, fp.z + (b[2] + a[2]) / 100f);
        if (two) ps.translate(-f * 0.45f, 0.05f, 0);                                   // between both fists
        float sc = Math.max(0.05f, (b[3] + a[3]) / 100f);
        // attack: quick chop of the item around the fist, plus our user rotations
        float sw = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        if (sw != 0) ps.mulPose(Axis.XP.rotationDegrees(-45f * sw));
        if (b[5] + a[5] != 0) ps.mulPose(Axis.YP.rotationDegrees(f * (b[5] + a[5])));
        if (b[4] + a[4] != 0) ps.mulPose(Axis.XP.rotationDegrees(b[4] + a[4]));
        if (b[6] + a[6] != 0) ps.mulPose(Axis.ZP.rotationDegrees(f * (b[6] + a[6])));
        boolean flat = flatCats[c];
        if ((c == 9 || c == 10) && !flat) {
            net.minecraft.world.level.block.state.BlockState st = ((BlockItem) it).getBlock().defaultBlockState();
            if (c == 9) {
                if (st.hasProperty(net.minecraft.world.level.block.LanternBlock.HANGING)) st = st.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
                if (f(11)) { ps.mulPose(Axis.ZP.rotationDegrees(lanZ * f * 0.8f)); ps.mulPose(Axis.XP.rotationDegrees(lanX * 0.8f)); }
                ps.scale(sc, sc, sc);
                ps.translate(-0.5f, -1.0f, -0.5f);   // chain top at the fist, lantern hangs below
            } else {
                if (f(11)) ps.mulPose(Axis.ZP.rotationDegrees(lanZ * f * 0.15f));
                ps.mulPose(Axis.XP.rotationDegrees(-10f));
                ps.scale(sc, sc, sc);
                ps.translate(-0.5f, -0.25f, -0.5f);  // grip a quarter up the stick
            }
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } else if (c == 7 && !flat) {                         // blocks: upright, small, vanilla first-person block pose
            ps.scale(sc, sc, sc);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, stack,
                    right ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, ps, buf, light);
        } else {                                     // everything else: held like third person, grip in the fist
            if (c == 9 && f(11)) { ps.mulPose(Axis.ZP.rotationDegrees(lanZ * f * 0.8f)); ps.mulPose(Axis.XP.rotationDegrees(lanX * 0.8f)); }
            if (c == 10 && f(11)) ps.mulPose(Axis.ZP.rotationDegrees(lanZ * f * 0.15f));
            ps.mulPose(new org.joml.Quaternionf(GRIP));
            ps.scale(sc, sc, sc);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, stack,
                    right ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    !right, ps, buf, light);
        }
        ps.popPose();
        if (!logItem) { logItem = true; LOG.info("[cpmwheel] hand animations: stage 6 item drawn in fist (cat=" + CATS[c] + ", fist=" + fp + ")"); }
        return true;
    }
    private static boolean logItem;

    /** swim breaststroke / climb hand-over-hand offset for one arm (side = +1 right, -1 left) */
    private static void stroke(PoseStack ps, float side, float G) {
        float x = 0, y = 0, z = 0, ry = 0, rx = 0;
        if (swimW > 0.01f) {        // reach forward together, then sweep out to the sides and back
            float a = swimPhase * (float) Math.PI * 2;
            float reach = (1 + Mth.cos(a)) / 2, sweep = Math.max(0, Mth.sin(a));
            x += side * 0.28f * sweep * swimW; y += (0.12f * reach - 0.06f) * swimW; z += (-0.28f * reach + 0.12f * sweep) * swimW;
            ry += -side * 30f * sweep * swimW; rx += -20f * reach * swimW;
        }
        if (climbW > 0.01f) {       // alternate: one hand reaches up while the other pulls down
            float a = (climbPhase + (side > 0 ? 0 : 0.5f)) * (float) Math.PI * 2;
            float up = (1 + Mth.sin(a)) / 2;
            y += (0.32f * up - 0.12f) * climbW; z += -0.12f * up * climbW; rx += -35f * up * climbW;
        }
        x *= G; y *= G; z *= G; ry *= G; rx *= G;
        if (x != 0 || y != 0 || z != 0) ps.translate(x, y, z);
        if (ry != 0) ps.mulPose(Axis.YP.rotationDegrees(ry));
        if (rx != 0) ps.mulPose(Axis.XP.rotationDegrees(rx));
    }

    private static void drawArm(PoseStack ps, LocalPlayer p, MultiBufferSource buf, int light, boolean right) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer pr)) return;
        AbstractClientPlayer ap = p;
        if (!logArm) { logArm = true; LOG.info("[cpmwheel] hand animations: stage 5 arm-with-item drawn via " + pr.getClass().getName()); }
        if (right) pr.renderRightHand(ps, buf, light, ap); else pr.renderLeftHand(ps, buf, light, ap);
    }
}
