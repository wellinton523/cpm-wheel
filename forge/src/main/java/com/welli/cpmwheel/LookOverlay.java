package com.welli.cpmwheel;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 1.8.3 "Olhar": outline of the screen area + target marker (settings preview and the optional debug overlay). Only drawn locally. */
public final class LookOverlay {
    private LookOverlay() {}

    /** debug overlay on the HUD (off by default) */
    public static void render(GuiGraphics g) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            WheelConfig c = Live2d.config();
            if (c == null || !c.l2dOn || !c.l2dLookOn || !c.l2dLookDebug) return;
            int w = g.guiWidth(), h = g.guiHeight();
            draw(g, c, w, h, 0xA02EE6FF);
            String t = LookTargets.targetName.isEmpty() ? Component.translatable("cpmwheel.look.dbg.none").getString()
                    : Component.translatable("cpmwheel.look.dbg.target", LookTargets.targetName).getString();
            g.drawString(mc.font, t, w / 2 + 6, h / 2 + 6, 0xFFE0F8FF);
            String sk = skipText();
            if (!sk.isEmpty()) g.drawString(mc.font, sk, w / 2 + 6, h / 2 + 17, 0xFF9FB8C4);
        } catch (Throwable t) { if (!warned) { warned = true; CpmWheel.LOG.warn("[CPM Wheel] look-at overlay failed", t); } }
    }
    private static boolean warned;

    public static String skipText() {
        Map<String, Integer> m = LookTargets.skipped;
        if (m.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(Component.translatable("cpmwheel.look.dbg.skip").getString());
        for (String k : new String[]{"camera", "hidden", "vanish", "los"}) {
            Integer n = m.get(k);
            if (n != null) sb.append(' ').append(Component.translatable("cpmwheel.look.dbg." + k, n).getString());
        }
        return sb.toString();
    }

    /** the area (screen mode) or a crosshair ring (nearest mode), plus the current target */
    public static void draw(GuiGraphics g, WheelConfig c, int w, int h, int col) {
        float cx = w / 2f + c.l2dLookOffX / 100f * w, cy = h / 2f - c.l2dLookOffY / 100f * h;
        if ("screen".equals(c.l2dLookMode)) {
            if ("rect".equals(c.l2dLookShape)) {
                int x1 = Math.round(cx - c.l2dLookAreaW / 200f * w), x2 = Math.round(cx + c.l2dLookAreaW / 200f * w);
                int y1 = Math.round(cy - c.l2dLookAreaH / 200f * h), y2 = Math.round(cy + c.l2dLookAreaH / 200f * h);
                g.fill(x1, y1, x2, y1 + 1, col); g.fill(x1, y2 - 1, x2, y2, col);
                g.fill(x1, y1, x1 + 1, y2, col); g.fill(x2 - 1, y1, x2, y2, col);
            } else ring(g, cx, cy, c.l2dLookAreaR / 100f * h, col);
        }
        g.fill(Math.round(cx) - 3, Math.round(cy), Math.round(cx) + 4, Math.round(cy) + 1, col);
        g.fill(Math.round(cx), Math.round(cy) - 3, Math.round(cx) + 1, Math.round(cy) + 4, col);
        float[] t = LookTargets.targetScreen;
        if (t != null) {
            int tx = Math.round(w / 2f + t[0] / 100f * w), ty = Math.round(h / 2f - t[1] / 100f * h);
            ring(g, tx, ty, 5, 0xFF6CFF8F);
        }
    }

    static void ring(GuiGraphics g, float cx, float cy, float r, int col) {
        int n = Math.max(24, Math.min(360, Math.round(r * 2.2f)));
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            int x = Math.round(cx + (float) Math.cos(a) * r), y = Math.round(cy + (float) Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
