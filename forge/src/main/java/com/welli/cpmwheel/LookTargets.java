package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.8.3 "Olhar": finds the look-at target with the privacy filters.
 * Everything is measured from the REAL player (its eyes and its own view direction), never from the camera, so a free
 * camera (Freecam, Flashback / ReplayMod cameras, F5, spectating another entity) can't move the eyes and isn't a target.
 * Hidden players (invisible, spectators, not in the tab list, marked as vanished) and, optionally, anything behind blocks are skipped.
 */
public final class LookTargets {
    private LookTargets() {}

    /** for the screens / overlays: kind of the current target ("player" / "mob", never its name), its screen point (% from the centre)
     *  and why others were skipped */
    public static volatile String targetName = "";
    public static volatile float[] targetScreen = null;
    public static volatile Map<String, Integer> skipped = Map.of();
    private static int current = -1;

    public static int current() { return current; }
    public static void clear() { current = -1; targetName = ""; targetScreen = null; skipped = Map.of(); ELIGIBLE.clear(); }

    public static LookLogic.Opts opts(Minecraft mc, WheelConfig c) {
        LookLogic.Opts o = new LookLogic.Opts();
        o.mode = c.l2dLookMode; o.radius = c.l2dLookRadius; o.fov = c.l2dLookFov;
        o.shape = c.l2dLookShape; o.areaR = c.l2dLookAreaR; o.areaW = c.l2dLookAreaW; o.areaH = c.l2dLookAreaH;
        o.offX = c.l2dLookOffX; o.offY = c.l2dLookOffY; o.sticky = c.l2dLookSticky; o.stickPct = c.l2dLookStickPct;
        try { o.viewFov = mc.options.fov().get(); } catch (Throwable t) { o.viewFov = 70; }
        try { int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight(); o.aspect = h > 0 ? w / (float) h : 16f / 9f; } catch (Throwable t) { o.aspect = 16f / 9f; }
        return o;
    }

    /** 1.9.0: a look target: entity id (or MINING_ID for the block being mined), point to look at, kind ("player" / "mob" / "item" / "block") */
    public static final class Target {
        public final int id; public final Vec3 pos; public final String kind;
        Target(int id, Vec3 pos, String kind) { this.id = id; this.pos = pos; this.kind = kind; }
    }
    public static final int MINING_ID = Integer.MIN_VALUE + 7;
    /** eligible entity targets of the last search (id -> target), for glances; filled on the client thread only */
    public static final Map<Integer, Target> ELIGIBLE = new HashMap<>();

    /** the chosen target, or null; the eye angles are computed by the caller */
    public static Target find(Minecraft mc, LocalPlayer p, WheelConfig c) {
        String who = c.l2dLookTargets;
        ELIGIBLE.clear();
        if ("off".equals(who) || p == null || p.level() == null) { clear(); return null; }
        LookLogic.Opts o = opts(mc, c);
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot(), pitch = p.getXRot();     // the player's own view (the crosshair), not the camera
        double r = c.l2dLookRadius;
        boolean obj = c.l2dObjOn;
        boolean wantPlayers = "players".equals(who) || "both".equals(who);
        boolean wantMobs = "mobs".equals(who) || "both".equals(who) || (obj && c.l2dObjMobs);
        Map<String, Integer> skip = new HashMap<>();
        Map<String, List<LookLogic.Cand>> groups = new HashMap<>();
        Map<Integer, Target> byId = new HashMap<>();
        Entity cam = mc.getCameraEntity();
        List<Entity> pool = new ArrayList<>();
        if (wantPlayers) for (Player o2 : p.level().players()) pool.add(o2);
        if (wantMobs) pool.addAll(p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), e -> true));
        if (obj && c.l2dObjItems) pool.addAll(p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r), e -> true));
        for (Entity e : pool) {
            if (e == p || !e.isAlive() || e.isRemoved()) continue;
            if (e.distanceToSqr(p) > r * r) continue;
            String why = reject(mc, p, cam, e, c);
            if (why == null && c.l2dLookLos && !lineOfSight(p, eye, e)) why = "los";
            if (why != null) { skip.merge(why, 1, Integer::sum); continue; }
            String kind = e instanceof Player ? "player" : e instanceof ItemEntity ? "item" : "mob";
            Vec3 t = e instanceof ItemEntity ? e.position().add(0, 0.25, 0) : e.getEyePosition();
            Target tg = new Target(e.getId(), t, kind);
            String grp = !obj ? "main" : "player".equals(kind) ? "players" : "item".equals(kind) ? "items" : "mobs";
            groups.computeIfAbsent(grp, k -> new ArrayList<>()).add(new LookLogic.Cand(e.getId(), t.x - eye.x, t.y - eye.y, t.z - eye.z));
            byId.put(e.getId(), tg); ELIGIBLE.put(e.getId(), tg);
        }
        // the block being mined (its middle), when looking at objects
        if (obj && c.l2dObjMining && mc.gameMode != null && mc.gameMode.isDestroying() && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            Vec3 t = Vec3.atCenterOf(bh.getBlockPos());
            if (t.distanceToSqr(eye) <= r * r) {
                groups.computeIfAbsent("mining", k -> new ArrayList<>()).add(new LookLogic.Cand(MINING_ID, t.x - eye.x, t.y - eye.y, t.z - eye.z));
                byId.put(MINING_ID, new Target(MINING_ID, t, "block"));
            }
        }
        List<String> seq = new ArrayList<>();
        if (!obj) seq.add("main");
        else { seq.add("players"); for (String k : LookLogic.order(c.l2dObjOrder).split(",")) seq.add(k); }
        int id = -1;
        for (String k : seq) {
            List<LookLogic.Cand> g = groups.get(k);
            if (g == null || g.isEmpty()) continue;
            boolean has = false; for (LookLogic.Cand cd : g) if (cd.id == current) { has = true; break; }
            id = LookLogic.pick(g, o, yaw, pitch, has ? current : -1);
            if (id != -1) break;
        }
        current = id;
        skipped = Map.copyOf(skip);
        Target tgt = id == -1 ? null : byId.get(id);
        if (tgt == null) { current = -1; targetName = ""; targetScreen = null; return null; }
        targetName = tgt.kind;
        Vec3 t = tgt.pos;
        targetScreen = LookLogic.project(t.x - eye.x, t.y - eye.y, t.z - eye.z, yaw, pitch, o.viewFov, o.aspect);
        return tgt;
    }

    /** 1.9.0 damage snap: the attacker as a target only if it passes every privacy filter, the range and (if on) line of sight */
    public static Target eligibleEntity(Minecraft mc, LocalPlayer p, WheelConfig c, Entity e) {
        if (e == null || e == p || !e.isAlive() || e.isRemoved() || p == null) return null;
        if (e.distanceToSqr(p) > (double) c.l2dLookRadius * c.l2dLookRadius) return null;
        if (reject(mc, p, mc.getCameraEntity(), e, c) != null) return null;
        Vec3 eye = p.getEyePosition();
        if (c.l2dLookLos && !lineOfSight(p, eye, e)) return null;
        return new Target(e.getId(), e.getEyePosition(), e instanceof Player ? "player" : "mob");
    }

    /** null = can be a target, else the reason it is skipped */
    static String reject(Minecraft mc, LocalPlayer p, Entity cam, Entity e, WheelConfig c) {
        if (c.l2dLookSafeCam) {
            if (e instanceof LocalPlayer) return "camera";                       // Freecam's camera is a second LocalPlayer
            if (cam != null && cam != p && e == cam) return "camera";            // spectating / camera mods: the camera entity
            if (e.getId() < 0) return "camera";                                  // client-only fake entities (Freecam uses a negative id)
            if (e.getUUID().equals(p.getUUID())) return "camera";               // a copy of yourself
            String cn = e.getClass().getName().toLowerCase(Locale.ROOT);
            if (cn.contains("freecam") || cn.contains("camera") || cn.contains("replaymod") || cn.contains("flashback")) return "camera";
        }
        if (c.l2dLookHideHidden) {
            if (e.isInvisible() || e.isInvisibleTo(p)) return "hidden";
            if (e instanceof Player pl) {
                if (pl.isSpectator()) return "hidden";
                var conn = mc.getConnection();
                if (conn != null) {
                    PlayerInfo info = conn.getPlayerInfo(pl.getUUID());
                    if (info == null) return "hidden";                           // not known to the server list: fake / hidden
                    boolean listed = false;
                    try { listed = conn.getListedOnlinePlayers().contains(info); } catch (Throwable t) { listed = true; }
                    if (!listed) return "hidden";                                // removed from the tab list (common vanish trick)
                    if (info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR) return "hidden";
                    if (c.l2dLookVanishTag && vanishTag(pl, info)) return "vanish";
                }
            } else if (!e.shouldRender(p.getX(), p.getEyeY(), p.getZ())) return "hidden";
        }
        return null;
    }

    /** best-effort vanish markers some plugins put on the tab name or the team (e.g. "[V]", "Vanished") */
    static boolean vanishTag(Player pl, PlayerInfo info) {
        StringBuilder sb = new StringBuilder();
        try { if (info.getTabListDisplayName() != null) sb.append(info.getTabListDisplayName().getString()).append(' '); } catch (Throwable ignored) {}
        try { var tm = pl.getTeam(); if (tm != null) sb.append(tm.getName()).append(' '); } catch (Throwable ignored) {}
        try { var tm = info.getTeam(); if (tm != null) { sb.append(tm.getName()).append(' ').append(tm.getPlayerPrefix().getString()).append(' ').append(tm.getPlayerSuffix().getString()); } } catch (Throwable ignored) {}
        return isVanishText(sb.toString());
    }
    public static boolean isVanishText(String raw) {
        String s = raw.replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
        return s.contains("vanish") || s.contains("[v]") || s.contains("(v)") || s.contains("invis\u00edvel") || s.contains("[hidden]");
    }

    /** blocks between the real player's eyes and the target's eyes (or its middle) */
    static boolean lineOfSight(LocalPlayer p, Vec3 eye, Entity e) {
        Vec3 a = e.getEyePosition(), b = e.getBoundingBox().getCenter();
        for (Vec3 t : new Vec3[]{a, b}) {
            HitResult h = p.level().clip(new ClipContext(eye, t, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (h == null || h.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
