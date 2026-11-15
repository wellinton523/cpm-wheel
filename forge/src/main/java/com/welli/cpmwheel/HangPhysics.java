package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.5.8 "Física de pendurado": first-person hanging sway (spring + damper pendulum) for any item, group, mod or item type.
 * Settings per target and hand: int[8] {on, pivot (0 alça / 1 topo), intensity %, stiffness, damping x10, weight %, max angle, react %}
 * stored in cfg.haPhys as "ns:id|main", "grp:<id>|main", "mod:<ns>|main", "cat:<type>|main"; priority item > group > mod > type.
 * Lanterns use the "Lanterna" preset by default (same numbers as the old lantern swing).
 * Integrated every rendered frame with 10 ms sub-steps (smoother than per tick + partialTick).
 */
public final class HangPhysics {
    private HangPhysics() {}
    public static final String[] PRESETS = {"lantern", "backpack", "keychain"};
    public static final int[][] PRESET_VALUES = {
            {1, 0, 100, 45, 35, 100, 45, 0},   // Lanterna (old lantern behaviour)
            {1, 0, 70, 22, 45, 260, 25, 90},   // Mochila pesada: slow, heavy, small swing, reacts to jumps/stops
            {1, 1, 150, 70, 18, 40, 70, 130}}; // Chaveiro leve: fast, lively, big swing
    public static final int[] MIN = {0, 0, 0, 5, 5, 20, 5, 0}, MAX = {1, 1, 200, 150, 100, 400, 90, 200};
    public static int[] lantern() { return PRESET_VALUES[0].clone(); }

    static volatile java.util.Map<Item, int[][]> ITEM = java.util.Map.of();
    static volatile java.util.Map<String, int[][]> GROUP = java.util.Map.of(), MOD = java.util.Map.of();
    static volatile int[][][] CAT = new int[HandAnim.CATS.length][][];

    public static void load(WheelConfig c) {
        java.util.Map<Item, int[][]> m = new java.util.IdentityHashMap<>();
        java.util.Map<String, int[][]> g = new java.util.HashMap<>(), mo = new java.util.HashMap<>();
        int[][][] cat = new int[HandAnim.CATS.length][][];
        if (c.haPhys != null) for (var e : c.haPhys.entrySet()) {
            String k = e.getKey(); int bar = k.lastIndexOf('|');
            if (bar < 0 || e.getValue() == null || e.getValue().length != 8) continue;
            int hi = "off".equals(k.substring(bar + 1)) ? 1 : 0;
            String base = k.substring(0, bar);
            int[] v = e.getValue().clone();
            if (base.startsWith("grp:")) g.computeIfAbsent(base.substring(4), x -> new int[2][])[hi] = v;
            else if (base.startsWith("mod:")) mo.computeIfAbsent(base.substring(4), x -> new int[2][])[hi] = v;
            else if (base.startsWith("cat:")) {
                int ci = java.util.Arrays.asList(HandAnim.CATS).indexOf(base.substring(4));
                if (ci >= 0) { if (cat[ci] == null) cat[ci] = new int[2][]; cat[ci][hi] = v; }
            } else {
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(base);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) m.computeIfAbsent(BuiltInRegistries.ITEM.get(rl), x -> new int[2][])[hi] = v;
            }
        }
        ITEM = m; GROUP = g; MOD = mo; CAT = cat;
    }

    /** resolved settings for this stack and hand (null = no hanging physics) */
    public static int[] settings(ItemStack s, int hi) {
        if (s.isEmpty()) return null;
        int[] o = HandAnim.pick(ITEM, GROUP, MOD, s.getItem(), hi);
        if (o == null) { int c = HandAnim.cat(s); int[][] co = CAT[c]; o = co == null ? null : co[hi]; if (o == null && c == 9) o = PRESET_VALUES[0]; }
        return o == null || o[0] == 0 ? null : o;
    }

    // ---- state per hand ----
    private static final float[] aZ = new float[2], aX = new float[2], vZ = new float[2], vX = new float[2];
    private static float pYaw = Float.NaN, pPitch, pVy, pFwd, sVy, sFwd;
    private static final int[][] cur = new int[2][];

    /** small push for the settings preview button */
    public static void kick() { for (int h = 0; h < 2; h++) { vZ[h] += 160; vX[h] -= 90; } }

    public static void tick(LocalPlayer p, float dt) {
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (Float.isNaN(pYaw) || dt <= 0) { pYaw = yaw; pPitch = pitch; return; }
        float yr = Mth.clamp(Mth.wrapDegrees(yaw - pYaw) / dt, -720, 720), pr = Mth.clamp((pitch - pPitch) / dt, -720, 720);
        pYaw = yaw; pPitch = pitch;
        net.minecraft.world.phys.Vec3 mv = p.getDeltaMovement();
        // movement only changes per tick: low-pass it so the drive is smooth between ticks
        float k = 1 - (float) Math.exp(-dt * 18);
        float fwdNow = (float) (mv.x * -Mth.sin(yaw * Mth.DEG_TO_RAD) + mv.z * Mth.cos(yaw * Mth.DEG_TO_RAD));
        sVy += ((float) mv.y - sVy) * k; sFwd += (fwdNow - sFwd) * k;
        float vAcc = Mth.clamp((sVy - pVy) / dt, -40, 40), fAcc = Mth.clamp((sFwd - pFwd) / dt, -40, 40);
        pVy = sVy; pFwd = sFwd;
        float walkAcc = (float) (mv.horizontalDistance() * 40);
        float walkS = walkAcc * Mth.sin(p.tickCount * 0.6f);
        cur[0] = settings(p.getMainHandItem(), 0); cur[1] = settings(p.getOffhandItem(), 1);
        for (int h = 0; h < 2; h++) {
            int[] s = cur[h];
            if (s == null) { aZ[h] *= 0.8f; aX[h] *= 0.8f; vZ[h] = vX[h] = 0; continue; }
            float I = s[2] / 100f, K = s[3], C = s[4] / 10f, W = Math.max(0.2f, s[5] / 100f), M = s[6], R = s[7] / 100f;
            for (float t = dt; t > 0; t -= 0.01f) {
                float hs = Math.min(0.01f, t);
                float dZ = yr * 0.9f * I;
                // jump/fall (vertical acceleration) and starting/stopping (forward acceleration) tip it forward/back
                float dX = (-pr * 0.6f + walkS) * I + R * (vAcc * 14f - fAcc * 28f);
                vZ[h] += ((-K * aZ[h] - C * vZ[h] + dZ) / W) * hs;
                vX[h] += ((-K * aX[h] - C * vX[h] + dX) / W) * hs;
                aZ[h] += vZ[h] * hs; aX[h] += vX[h] * hs;
            }
            if (Math.abs(aZ[h]) > M) { aZ[h] = Math.copySign(M, aZ[h]); vZ[h] *= -0.3f; }
            if (Math.abs(aX[h]) > M) { aX[h] = Math.copySign(M, aX[h]); vX[h] *= -0.3f; }
        }
    }

    /** rotate the item around its hanging point; f = +1 right arm, -1 left; sc = item scale */
    public static void apply(PoseStack ps, int hi, float f, float sc) {
        int[] s = cur[hi];
        if (s == null) return;
        float py = s[1] == 1 ? 0.35f * sc : 0f;
        if (py != 0) ps.translate(0, py, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(aZ[hi] * f * 0.8f));
        ps.mulPose(Axis.XP.rotationDegrees(aX[hi] * 0.8f));
        if (py != 0) ps.translate(0, -py, 0);
    }
    /** live sideways angle of one hand, for the settings diagram */
    public static float previewAngle(int hi) { return aZ[hi]; }
    public static boolean active(int hi) { return cur[hi] != null; }
}
