package com.welli.cpmwheel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.5.7 "Better Combat method": draw the first-person arm (and the item in its fist) during the WORLD entity pass,
 * into the level's entity buffer, like Player Animator / Better Combat do with the 3rd-person model. With an Iris
 * shader pack the hand pass has no emissive program for CPM's glow ("eyes") render type, the entity pass does
 * (gbuffers_spidereyes / entities), so the glow is emissive there. The pose stack is built so the arm lands
 * exactly where the hand pass would draw it (hand fov + camera bob), optionally pulled toward the camera (depth %)
 * so it clips less into walls. Hands this pass drew are skipped in the normal hand pass the same frame;
 * anything it can't draw (maps, bow/crossbow use, eating, vanilla item position...) stays in the hand pass.
 */
public final class WorldArm {
    private WorldArm() {}
    private static final Logger LOG = LoggerFactory.getLogger("cpmwheel");
    public static final String[] MODES = {"on", "auto", "off"};
    /** from config (refreshed every 2 s / on settings change) */
    public static volatile String mode = "on";
    public static volatile int depth = 40;
    /** frame (HandAnim.frame) in which each hand was drawn here: [0] main, [1] off */
    private static final long[] drawn = {-1, -1};
    /** true while we draw: CPM glow keeps its own emissive render type (no hand-glow swap) */
    public static volatile boolean inWorldPass = false;
    private static boolean failed, logOk, logSkipPa;
    private static String lastSkip = "";

    public static void load(WheelConfig c) {
        mode = c.haWorldArm == null ? "on" : c.haWorldArm;
        depth = Math.max(20, Math.min(100, c.haWorldDepth <= 0 ? 40 : c.haWorldDepth));
    }

    static boolean wanted() {
        String m = mode;
        return "on".equals(m) || ("auto".equals(m) && HandGlow.shadersOn());
    }

    /** RenderHandEvent: true = this hand was already drawn in the world pass this frame (cancel the hand pass copy) */
    public static boolean suppress(InteractionHand hand) {
        return drawn[hand == InteractionHand.MAIN_HAND ? 0 : 1] == HandAnim.frame;
    }

    /** status for the settings tooltip / log */
    public static String status() { return failed ? "error" : lastSkip.isEmpty() ? "ok" : lastSkip; }

    private static void skip(String why) {
        if (!why.equals(lastSkip)) { lastSkip = why; if (!why.isEmpty()) LOG.info("[cpmwheel] world-pass arm: using the normal hand pass (" + why + ")"); }
    }

    /** Player Animator (Better Combat) drawing its own 3rd-person-model arms this frame? Then we draw nothing (no double arm). */
    private static java.lang.reflect.Method paGet, paMode; private static boolean paLooked;
    static boolean playerAnimatorFirstPerson(LocalPlayer p) {
        try {
            if (!paLooked) {
                paLooked = true;
                try { paGet = p.getClass().getMethod("playerAnimator_getAnimation"); } catch (Throwable ignored) {}
            }
            if (paGet == null) return false;
            Object anim = paGet.invoke(p);
            if (anim == null) return false;
            if (paMode == null) paMode = anim.getClass().getMethod("getFirstPersonMode");
            Object m = paMode.invoke(anim);
            return m instanceof Enum<?> e && e.name().equals("THIRD_PERSON_MODEL");
        } catch (Throwable t) { return false; }
    }

    /** NeoForge RenderLevelStageEvent AFTER_ENTITIES (still the entity phase: Iris has not switched to block entities) */
    public static void afterEntities(PoseStack levelPose, Camera camera, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || failed) return;
        if (!wanted()) { skip("off".equals(mode) ? "" : "auto: no shader pack"); return; }
        try {
            GameRenderer gr = mc.gameRenderer;
            if (!mc.options.getCameraType().isFirstPerson() || camera.isDetached() || camera.getEntity() != p || p.isSleeping()
                    || mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                    || !VanillaAccess.renderHand(gr) || VanillaAccess.panoramic(gr) || p.isScoping()) return;
            if (playerAnimatorFirstPerson(p)) {
                if (!logSkipPa) { logSkipPa = true; LOG.info("[cpmwheel] world-pass arm: Better Combat / Player Animator is drawing the arms -> paused while it animates"); }
                return;
            }
            if (HandAnim.allBlocked || HandAnim.blocked[HandAnim.fi("arm")]) { skip("another first-person mod is active"); return; }
            if (HandAnim.failed) { skip("hand animations disabled after an error"); return; }
            var ia = gr.itemInHandRenderer;
            ItemStack main = VanillaAccess.mainItem(ia), off = VanillaAccess.offItem(ia);
            if (main == null) main = p.getMainHandItem();
            if (off == null) off = p.getOffhandItem();
            // vanilla hides/forces hands while a bow / crossbow is drawn or charged: leave all of that to the hand pass
            boolean bowLike = main.is(Items.BOW) || off.is(Items.BOW) || main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW);
            if (bowLike && (p.isUsingItem() || CrossbowItem.isCharged(main))) { skip(""); return; }
            boolean doMain = HandAnim.worldOk(p, InteractionHand.MAIN_HAND, main);
            boolean doOff = off.isEmpty() || HandAnim.worldOk(p, InteractionHand.OFF_HAND, off);
            if (!doMain && !doOff) { skip(""); return; }
            skip("");

            // ---- pose stack: hand-pass clip space expressed in the entity pass ----
            Matrix4f world = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
            if (levelPose != null) world.mul(levelPose.last().pose());
            Matrix4f hand = gr.getProjectionMatrix(VanillaAccess.fov(gr, camera, pt));
            float k = depth / 100f;
            hand.scale(k, k, k);
            PoseStack bob = new PoseStack();
            VanillaAccess.bobHurt(gr, bob, pt);
            if (mc.options.bobView().get()) VanillaAccess.bobView(gr, bob, pt);
            hand.mul(bob.last().pose());
            Matrix4f m = world.invert(new Matrix4f()).mul(hand);
            // both projections share near/far, so this is affine up to float noise: drop the projective row
            m.m03(0).m13(0).m23(0).m33(1);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            Matrix3f n = new Matrix3f(m).invert().transpose();
            float det = n.determinant();
            if (det > 1e-12f) n.scale((float) (1.0 / Math.cbrt(det)));
            ps.last().normal().set(n);

            // ---- vanilla ItemInHandRenderer.renderHandsWithItems, our part only ----
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, pt);
            float attack = p.getAttackAnim(pt);
            InteractionHand swingHand = p.swingingArm == null ? InteractionHand.MAIN_HAND : p.swingingArm;
            float xb = Mth.lerp(pt, p.xBobO, p.xBob), yb = Mth.lerp(pt, p.yBobO, p.yBob);
            ps.mulPose(Axis.XP.rotationDegrees((p.getViewXRot(pt) - xb) * 0.1F));
            ps.mulPose(Axis.YP.rotationDegrees((p.getViewYRot(pt) - yb) * 0.1F));
            inWorldPass = true;
            try {
                long fr = HandAnim.frame;
                if (doMain) {
                    float sw = swingHand == InteractionHand.MAIN_HAND ? attack : 0;
                    float eq = 1 - Mth.lerp(pt, VanillaAccess.oMainH(ia), VanillaAccess.mainH(ia));
                    if (hand(ps, p, InteractionHand.MAIN_HAND, main, pt, sw, eq, buf, light)) drawn[0] = fr;
                }
                if (doOff) {
                    if (off.isEmpty()) drawn[1] = fr; // vanilla draws nothing for an empty off hand
                    else {
                        float sw = swingHand == InteractionHand.OFF_HAND ? attack : 0;
                        float eq = 1 - Mth.lerp(pt, VanillaAccess.oOffH(ia), VanillaAccess.offH(ia));
                        if (hand(ps, p, InteractionHand.OFF_HAND, off, pt, sw, eq, buf, light)) drawn[1] = fr;
                    }
                }
            } finally { inWorldPass = false; }
            // vanilla buffer: flush now, still in the entity phase. Iris' fully-buffered source draws it with the other entities.
            if (buf.getClass() == MultiBufferSource.BufferSource.class) buf.endBatch();
            if (!logOk) { logOk = true; LOG.info("[cpmwheel] world-pass arm: active (entity pass, depth " + depth + "%, buffer " + buf.getClass().getName() + ")"); }
        } catch (Throwable t) {
            failed = true; inWorldPass = false;
            LOG.warn("[cpmwheel] world-pass arm error -> back to the normal hand pass for this session", t);
        }
    }

    /** one hand: our hand animation + either our arm-with-item or (empty main hand) vanilla's empty arm */
    private static boolean hand(PoseStack ps, LocalPlayer p, InteractionHand h, ItemStack stack, float pt, float swing, float equip, MultiBufferSource buf, int light) {
        boolean drew = HandAnim.apply(ps, h, stack, pt, swing, equip, buf, light);
        if (drew) return true;
        if (!stack.isEmpty()) return false; // not ours after all: the hand pass draws it this frame
        if (h == InteractionHand.MAIN_HAND && !p.isInvisible()) {
            boolean right = p.getMainArm() == HumanoidArm.RIGHT;
            ps.pushPose();
            HandAnim.emptyArm(ps, p, buf, light, right, swing, equip);
            ps.popPose();
        }
        return true;
    }
}
