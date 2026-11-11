package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small in-game overlay with what is active on the character. State is polled 4x per second. */
public final class Hud {
    private static List<String[]> lines = new ArrayList<>(); // {label, value, color}
    private static long lastPoll = 0;
    private static WheelConfig cfg;
    private static boolean present;

    private Hud() {}

    private static void poll(WheelConfig cfg) {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (WheelConfig.Slot s : cfg.allSlots()) {
            if (s.empty() || s.isFolder() || WheelConfig.RESET.equals(s.anim) || !seen.add(s.anim)) continue;
            int v = CpmBridge.playing(s.anim);
            if (v < 0) continue;
            String mode = s.effectiveMode(), val;
            int color = s.color;
            boolean nonDefault;
            if ("toggle".equals(mode)) { nonDefault = v > 0; val = v > 0 ? "ON" : "OFF"; }
            else if ("frames".equals(mode)) {
                int k = s.frameIndexOf(v);
                nonDefault = k != 0;
                val = s.frameName(k);
                WheelConfig.Frame f = k >= 0 && k < s.frames.size() ? s.frames.get(k) : null;
                if (f != null && f.color != 0) color = f.color;
            } else {
                nonDefault = v != s.minV();
                int a = s.minV(), b = s.maxV();
                val = (b > a ? Math.round((v - a) * 100f / (b - a)) : 0) + "%";
            }
            if (cfg.hudOnlyNonDefault && !nonDefault) continue;
            out.add(new String[]{s.display(), val, Integer.toString(color)});
        }
        lines = out;
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof WheelScreen) return;
        long now = System.currentTimeMillis();
        if (cfg == null || now - lastPoll > 250) { // config + CPM state are read only 4x per second
            lastPoll = now;
            cfg = WheelConfig.get();
            present = CpmBridge.cpmPresent();
            if (cfg.hudEnabled && present) poll(cfg);
        }
        if (!cfg.hudEnabled || !present) return;
        if (lines.isEmpty()) return;
        var font = mc.font;
        float sc = cfg.hudScale / 100f;
        int w = 0;
        for (String[] l : lines) w = Math.max(w, font.width(l[0] + ": " + l[1]) + 12);
        int h = lines.size() * 10 + 6;
        int sw = (int) (g.guiWidth() / sc), sh = (int) (g.guiHeight() / sc);
        String c = cfg.hudCorner == null ? "top_left" : cfg.hudCorner;
        int x = c.endsWith("right") ? sw - w - 4 : 4;
        int y = c.startsWith("bottom") ? sh - h - 4 : 4;
        int bgA = Math.max(0, Math.min(255, cfg.hudOpacity * 255 / 100));
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1);
        g.fill(x, y, x + w, y + h, (bgA << 24) | 0x050A12);
        g.fill(x, y, x + 2, y + h, 0xFF2EE6FF);
        int ly = y + 4;
        for (String[] l : lines) {
            int col = Integer.parseInt(l[2]);
            g.fill(x + 5, ly + 2, x + 8, ly + 6, 0xFF000000 | col);
            g.drawString(font, l[0] + ": ", x + 10, ly, 0xC8E8F0);
            g.drawString(font, l[1], x + 10 + font.width(l[0] + ": "), ly, 0xFFFFFF);
            ly += 10;
        }
        g.pose().popPose();
    }

    public static void invalidate() { lastPoll = 0; }
}
