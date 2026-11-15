package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "Física de pendurado" (1.5.8, reworked in 1.5.9): first-person hanging sway for any item, group, mod or item type.
 * Settings per target and hand: int[11] {on, pivot (0 alça / 1 topo), intensity %, stiffness, damping x10, weight %,
 * max angle, react %, gravity % ("Seguir a gravidade"), raise limit (0/1), invert (0/1, per hand)}
 * stored in cfg.haPhys as "ns:id|main", "grp:<id>|main", "mod:<ns>|main", "cat:<type>|main"; priority item > group > mod > type.
 * At each level a hand without its own value uses the other hand's value (so one setting gives the same feel in both hands).
 * 1.5.9: the math (PendulumMath) runs in camera space at the pivot, identical for both hands; 1.5.8 multiplied the sideways
 * angle by the arm side and applied it inside the mirrored item frame, which inverted the right (main) hand.
 */
public final class HangPhysics {
    private HangPhysics() {}
    public static final String[] PRESETS = {"lantern", "backpack", "keychain"};
    public static final int[][] PRESET_VALUES = {
            {1, 0, 80, 45, 35, 100, 45, 0, 100, 1, 0},   // Lanterna: old lantern swing (x0.8 amplitude kept), hangs straight down
            {1, 0, 60, 22, 45, 260, 25, 90, 40, 0, 0},   // Mochila pesada: slow, heavy, small swing, reacts to jumps/stops, some gravity
            {1, 1, 120, 70, 18, 40, 70, 130, 100, 1, 0}}; // Chaveiro leve: fast, lively, big swing, hangs from the top
    public static final int[] MIN = {0, 0, 0, 5, 5, 20, 5, 0, 0, 0, 0}, MAX = {1, 1, 200, 150, 100, 400, 90, 200, 100, 1, 1};
    public static int[] lantern() { return PRESET_VALUES[0].clone(); }
    /** 1.8.0: hanging physics of lanterns when nothing is set (the author's tuning; the "Lanterna" preset is unchanged) */
    public static final int[] DEF_LANTERN = {1, 0, 160, 31, 7, 60, 75, 130, 100, 0, 0};

    /** 1.5.8 arrays had 8 values: add gravity 60%, normal limit, not inverted */
    public static int[] upgrade(int[] v) {
        if (v == null) return null;
        if (v.length == PendulumMath.LEN) return v;
        if (v.length != 8) return null;
        int[] n = new int[PendulumMath.LEN];
        System.arraycopy(v, 0, n, 0, 8);
        n[PendulumMath.GRAVITY] = 60; n[PendulumMath.RAISE] = 0; n[PendulumMath.INVERT] = 0;
        return n;
    }

    static volatile java.util.Map<Item, int[][]> ITEM = java.util.Map.of();
    static volatile java.util.Map<String, int[][]> GROUP = java.util.Map.of(), MOD = java.util.Map.of();
    static volatile int[][][] CAT = new int[HandAnim.CATS.length][][];

    public static void load(WheelConfig c) {
        java.util.Map<Item, int[][]> m = new java.util.IdentityHashMap<>();
        java.util.Map<String, int[][]> g = new java.util.HashMap<>(), mo = new java.util.HashMap<>();
        int[][][] cat = new int[HandAnim.CATS.length][][];
        if (c.haPhys != null) for (var e : c.haPhys.entrySet()) {
            String k = e.getKey(); int bar = k.lastIndexOf('|');
            int[] v = upgrade(e.getValue());
            if (bar < 0 || v == null) continue;
            int hi = "off".equals(k.substring(bar + 1)) ? 1 : 0;
            String base = k.substring(0, bar);
            v = v.clone();
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

    /** this hand's value at one level, else the other hand's (invert only counts for the hand that owns it) */
    private static int[] at(int[][] lv, int hi, boolean[] own) {
        if (lv == null) return null;
        if (lv[hi] != null) { own[0] = true; return lv[hi]; }
        if (lv[1 - hi] != null) { own[0] = false; return lv[1 - hi]; }
        return null;
    }
    /** resolved settings for this stack and hand (null = no hanging physics); own[0] = value belongs to this hand */
    public static int[] settings(ItemStack s, int hi, boolean[] own) {
        if (s.isEmpty()) return null;
        own[0] = true;
        Item it = s.getItem();
        int[] o = at(ITEM.get(it), hi, own);
        if (o == null && !GROUP.isEmpty()) for (String gid : ItemGroups.groupsOf(it)) { o = at(GROUP.get(gid), hi, own); if (o != null) break; }
        if (o == null && !MOD.isEmpty()) o = at(MOD.get(BuiltInRegistries.ITEM.getKey(it).getNamespace()), hi, own);
        if (o == null) { int c = HandAnim.cat(s); o = at(CAT[c], hi, own); if (o == null && c == 9) { own[0] = true; o = DEF_LANTERN; } }
        return o == null || o[0] == 0 ? null : o;
    }
    public static int[] settings(ItemStack s, int hi) { return settings(s, hi, new boolean[1]); }

    /** +1 = mass below the pivot (3D lantern hanging from the fist, or pivot "topo"), -1 = item standing up in the fist */
    static float side(ItemStack s, int[] p) {
        if (p[PendulumMath.PIVOT] == 1) return 1f;
        int c = HandAnim.cat(s);
        return c == 9 && !HandAnim.flatCats[9] ? 1f : -1f;
    }

    // ---- state per hand (same timestep and equations for both) ----
    private static final PendulumMath.State[] ST = {new PendulumMath.State(), new PendulumMath.State()};
    private static final PendulumMath.In IN = new PendulumMath.In();
    private static float pYaw = Float.NaN, pPitch, pVy, pFwd, pSide, sVy, sFwd, sSide;
    private static final int[][] cur = new int[2][];
    private static final boolean[] own = new boolean[1];

    /** small push for the settings preview button */
    public static void kick() { for (PendulumMath.State s : ST) { s.vZ += 160; s.vX -= 90; } }

    public static void tick(LocalPlayer p, float dt) {
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (Float.isNaN(pYaw) || dt <= 0) { pYaw = yaw; pPitch = pitch; return; }
        IN.yawRate = Mth.clamp(Mth.wrapDegrees(yaw - pYaw) / dt, -720, 720);
        IN.pitchRate = Mth.clamp((pitch - pPitch) / dt, -720, 720);
        IN.pitch = pitch;
        pYaw = yaw; pPitch = pitch;
        net.minecraft.world.phys.Vec3 mv = p.getDeltaMovement();
        // movement only changes per tick: low-pass it so the drive is smooth between ticks
        float k = 1 - (float) Math.exp(-dt * 18);
        float sy = Mth.sin(yaw * Mth.DEG_TO_RAD), cy = Mth.cos(yaw * Mth.DEG_TO_RAD);
        float fwdNow = (float) (mv.x * -sy + mv.z * cy);       // along the view direction
        float sideNow = (float) (mv.x * -cy + mv.z * -sy);     // to the player's right
        sVy += ((float) mv.y - sVy) * k; sFwd += (fwdNow - sFwd) * k; sSide += (sideNow - sSide) * k;
        IN.vertAcc = Mth.clamp((sVy - pVy) / dt, -40, 40);
        IN.fwdAcc = Mth.clamp((sFwd - pFwd) / dt, -40, 40);
        IN.strafeAcc = Mth.clamp((sSide - pSide) / dt, -40, 40);
        pVy = sVy; pFwd = sFwd; pSide = sSide;
        IN.walk = (float) (mv.horizontalDistance() * 40) * Mth.sin(p.tickCount * 0.6f);
        ItemStack[] held = {p.getMainHandItem(), p.getOffhandItem()};
        for (int h = 0; h < 2; h++) {
            int[] s = settings(held[h], h, own);
            cur[h] = s;
            PendulumMath.State st = ST[h];
            if (s == null) { st.aZ *= 0.8f; st.aX *= 0.8f; st.vZ = st.vX = 0; continue; }
            boolean inv = own[0] && s[PendulumMath.INVERT] == 1;
            PendulumMath.step(st, s, IN, dt, side(held[h], s), inv);
        }
    }

    /** rotate around the hanging point, in camera space (call right after moving to the item's grip point); sc = item scale */
    public static void apply(PoseStack ps, int hi, float sc) {
        int[] s = cur[hi];
        if (s == null) return;
        float py = s[PendulumMath.PIVOT] == 1 ? 0.35f * sc : 0f;
        if (py != 0) ps.translate(0, py, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(ST[hi].aZ));
        ps.mulPose(Axis.XP.rotationDegrees(ST[hi].aX));
        if (py != 0) ps.translate(0, -py, 0);
    }
    /** live sideways angle of one hand, for the settings diagram */
    public static float previewAngle(int hi) { return ST[hi].aZ; }
    public static boolean active(int hi) { return cur[hi] != null; }
}
