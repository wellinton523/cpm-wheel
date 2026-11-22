package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 1.8.4 eye-aim overlay (client only, off by default): a small marker roughly where the eyes are pointing, seen from your
 * own view. Shows the smoothed angles (what the model shows), a faint ghost of the raw aim, a bracket on the chosen target
 * (never its name; filtered / hidden entities are never targets, so they never get a bracket), the target area and a tiny
 * state label. With a free / third-person camera the screen no longer matches your character's view, so it switches to a
 * small gauge instead.
 */
public final class EyeOverlay {
    private EyeOverlay() {}

    private static float dy, dp, gy, gp; private static long lastNs; private static boolean init; private static boolean warned;

    public static void render(GuiGraphics g) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            WheelConfig c = Live2d.config();
            if (c == null || !c.l2dEyeOv) return;
            draw(g, c, g.guiWidth(), g.guiHeight(), false);
        } catch (Throwable t) { if (!warned) { warned = true; CpmWheel.LOG.warn("[CPM Wheel] eye-aim overlay failed", t); } }
    }

    static int col(WheelConfig c, float a) { return (Math.round(Math.max(0, Math.min(1, a)) * c.l2dEyeOvAlpha / 100f * 255) << 24) | (c.l2dEyeOvColor & 0xFFFFFF); }

    /** preview = drawn from the options screen (shows a sample when there is no live data) */
    public static void draw(GuiGraphics g, WheelConfig c, int w, int h, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        float[] v = Live2d.eyeView();
        boolean live = p != null && c.l2dOn && c.l2dLookOn && v != null;
        if (!live && !preview) return;
        if (!live) v = new float[]{12, -6, 18, -8, 1};
        // smooth the 20 Hz values per frame so the marker glides (display only)
        long now = System.nanoTime();
        float dt = init ? Math.min(0.25f, (now - lastNs) / 1e9f) : 1f; lastNs = now; init = true;
        float k = 1 - (float) Math.exp(-dt / 0.05f);
        dy += (v[0] - dy) * k; dp += (v[1] - dp) * k; gy += (v[2] - gy) * k; gp += (v[3] - gp) * k;
        boolean has = v[4] > 0;
        int s = c.l2dEyeOvSize;
        String state = live ? Live2d.eyeState : "target";
        boolean firstPerson = p != null && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == p;
        if (firstPerson) {
            LookLogic.Opts o = LookTargets.opts(mc, c);
            if (c.l2dEyeOvArea) areaOutline(g, c, w, h, col(c, 0.35f));
            float headYaw = p.getYHeadRot(), pitch = p.getXRot(), viewYaw = p.getYRot();
            if (has && c.l2dEyeOvGhost) {
                float[] r = LookLogic.aimScreen(headYaw, pitch, gy, gp, viewYaw, pitch, o.viewFov, o.aspect);
                if (r != null) { int rx = sx(r[0], w), ry = sy(r[1], h); cross(g, rx, ry, Math.max(2, s / 2), col(c, 0.3f)); }
            }
            float[] t = live ? LookTargets.targetScreen : null;
            if (has && t != null) bracket(g, sx(t[0], w), sy(t[1], h), s + 4, col(c, 0.7f));
            float[] m = LookLogic.aimScreen(headYaw, pitch, dy, dp, viewYaw, pitch, o.viewFov, o.aspect);
            int mx = m == null ? w / 2 : sx(m[0], w), my = m == null ? h / 2 : sy(m[1], h);
            marker(g, mx, my, s, col(c, 1f));
            if (c.l2dEyeOvLabel) label(g, mc, c, state, has, mx + s + 3, my - 4);
        } else {
            // free camera / F5 / spectating: a small gauge of the eye angles instead of a wrong screen point
            int bw = s * 8, bh = s * 6, bx = w / 2 - bw / 2, by = 6;
            g.fill(bx, by, bx + bw, by + bh, col(c, 0.18f));
            frame(g, bx, by, bx + bw, by + bh, col(c, 0.5f));
            g.fill(bx + bw / 2, by + 2, bx + bw / 2 + 1, by + bh - 2, col(c, 0.25f));
            g.fill(bx + 2, by + bh / 2, bx + bw - 2, by + bh / 2 + 1, col(c, 0.25f));
            float maxY = Math.max(5, c.l2dLookMaxYaw);
            if (has && c.l2dEyeOvGhost) cross(g, gx(gy, maxY, bx, bw), gpy(gp, by, bh), 2, col(c, 0.35f));
            marker(g, gx(dy, maxY, bx, bw), gpy(dp, by, bh), Math.max(2, s / 2), col(c, 1f));
            if (c.l2dEyeOvLabel) label(g, mc, c, state, has, bx + bw + 4, by + 2);
        }
    }

    static int sx(float pct, int w) { return Math.round(w / 2f + pct / 100f * w); }
    static int sy(float pct, int h) { return Math.round(h / 2f - pct / 100f * h); }
    static int gx(float yaw, float maxY, int bx, int bw) { return bx + Math.round(bw / 2f + Math.max(-1, Math.min(1, yaw / maxY)) * (bw / 2f - 3)); }
    static int gpy(float pitch, int by, int bh) { return by + Math.round(bh / 2f + Math.max(-1, Math.min(1, pitch / 45f)) * (bh / 2f - 3)); }

    static void label(GuiGraphics g, Minecraft mc, WheelConfig c, String state, boolean has, int x, int y) {
        String st = Component.translatable("cpmwheel.eyeov.state." + state).getString();
        if (has && !LookTargets.targetName.isEmpty()) st += " (" + Component.translatable("cpmwheel.eyeov.kind." + LookTargets.targetName).getString() + ")";
        String txt = st + "  " + Math.round(dy) + "\u00b0 / " + Math.round(-dp) + "\u00b0";
        var ps = g.pose(); ps.pushPose(); ps.translate(x, y, 0); ps.scale(0.75f, 0.75f, 1f);
        g.drawString(mc.font, txt, 0, 0, col(c, 1f), true);
        ps.popPose();
    }

    static void marker(GuiGraphics g, int x, int y, int s, int col) {
        LookOverlay.ring(g, x, y, s, col);
        g.fill(x - 1, y - 1, x + 2, y + 2, col);
    }
    static void cross(GuiGraphics g, int x, int y, int s, int col) {
        g.fill(x - s, y, x + s + 1, y + 1, col);
        g.fill(x, y - s, x + 1, y + s + 1, col);
    }
    static void frame(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        g.fill(x1, y1, x2, y1 + 1, col); g.fill(x1, y2 - 1, x2, y2, col); g.fill(x1, y1, x1 + 1, y2, col); g.fill(x2 - 1, y1, x2, y2, col);
    }
    /** corner brackets around the chosen target */
    static void bracket(GuiGraphics g, int x, int y, int r, int col) {
        int l = Math.max(2, r / 2);
        int x1 = x - r, x2 = x + r, y1 = y - r, y2 = y + r;
        g.fill(x1, y1, x1 + l, y1 + 1, col); g.fill(x1, y1, x1 + 1, y1 + l, col);
        g.fill(x2 - l + 1, y1, x2 + 1, y1 + 1, col); g.fill(x2, y1, x2 + 1, y1 + l, col);
        g.fill(x1, y2, x1 + l, y2 + 1, col); g.fill(x1, y2 - l + 1, x1 + 1, y2 + 1, col);
        g.fill(x2 - l + 1, y2, x2 + 1, y2 + 1, col); g.fill(x2, y2 - l + 1, x2 + 1, y2 + 1, col);
    }
    static void areaOutline(GuiGraphics g, WheelConfig c, int w, int h, int col) {
        if (!"screen".equals(c.l2dLookMode)) return;
        float cx = w / 2f + c.l2dLookOffX / 100f * w, cy = h / 2f - c.l2dLookOffY / 100f * h;
        if ("rect".equals(c.l2dLookShape)) {
            frame(g, Math.round(cx - c.l2dLookAreaW / 200f * w), Math.round(cy - c.l2dLookAreaH / 200f * h),
                    Math.round(cx + c.l2dLookAreaW / 200f * w), Math.round(cy + c.l2dLookAreaH / 200f * h), col);
        } else LookOverlay.ring(g, cx, cy, c.l2dLookAreaR / 100f * h, col);
    }
}
