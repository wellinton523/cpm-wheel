package com.welli.cpmwheel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Wheel designs (shape, borders, highlight, center, text). Pure drawing, no state. */
public final class Style {
    private Style() {}
    public static final String[] DESIGNS = {"classic", "neon", "minimal", "terminal", "glass", "petals", "hex"};
    public static final String[] THEMES = {"auto", "cyan", "red", "purple", "green", "gold", "white"};
    private static final int[] THEME_RGB = {0, 0x2EE6FF, 0xFF3B5C, 0xB46CFF, 0x3BFF9D, 0xFFD23B, 0xFFFFFF};
    private static final int RED = 0xFF2A3C;

    static int idx(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return -1; }
    public static String design(WheelConfig c) { return c == null || idx(DESIGNS, c.design) < 0 ? "classic" : c.design; }

    public static int accent(WheelConfig c) {
        int i = c == null ? -1 : idx(THEMES, c.theme);
        if (i > 0) return THEME_RGB[i];
        switch (design(c)) {
            case "terminal": return 0xFF3030;
            case "minimal": return 0xE6EEF5;
            case "glass": return 0xBFEFFF;
            case "petals": return 0xFF8FD0;
            case "hex": return 0x7DFF6A;
            default: return 0x2EE6FF;
        }
    }
    /** colour of a slot: the slot colour, except terminal which is monochrome red unless a theme is set */
    static int slotColor(WheelConfig c, int col, boolean empty) {
        if (empty) return 0x334455;
        if ("terminal".equals(design(c)) && (c.theme == null || c.theme.equals("auto"))) return accent(c);
        return col;
    }

    static int fade(int argb, float a) { return (Math.round((argb >>> 24) * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF); }
    static int a(int rgb, float alpha255) { return Draw.argb(rgb, Math.max(0, Math.min(255, Math.round(alpha255)))); }

    // ---------- background ----------
    public static void background(GuiGraphics g, WheelConfig c, int w, int h, float open, float cx, float cy, float R) {
        int ac = accent(c);
        switch (design(c)) {
            case "neon" -> {
                g.fillGradient(0, 0, w, h, fade(0xA0020409, open), fade(0xD0000000, open));
                for (int k = 0; k < 4; k++) Draw.sector(g, cx, cy, R * (1.04f + k * 0.05f), R * (1.09f + k * 0.05f), 0, Math.PI * 2, a(ac, 22 * (4 - k) / 4f * open));
            }
            case "minimal" -> g.fill(0, 0, w, h, fade(0x70000000, open));
            case "terminal" -> {
                g.fill(0, 0, w, h, fade(0xC8080000, open));
                Draw.grid(g, 0, 0, w, h, 12, 1, a(ac, 50 * open));
                Draw.scanlines(g, w, h, 3, fade(0x22000000, open));
            }
            case "glass" -> {
                g.fillGradient(0, 0, w, h, fade(0x60101820, open), fade(0x90101820, open));
                for (int k = 0; k < 5; k++) Draw.sector(g, cx, cy, 0, R * (1.06f + k * 0.07f), 0, Math.PI * 2, a(0xFFFFFF, 7 * open));
            }
            case "petals" -> g.fillGradient(0, 0, w, h, fade(0x90180820, open), fade(0xB0050A12, open));
            case "hex" -> {
                g.fill(0, 0, w, h, fade(0xB8000000, open));
                Draw.grid(g, 0, 0, w, h, 16, 2, a(ac, 22 * open));
            }
            default -> g.fillGradient(0, 0, w, h, fade(0x90050A12, open), fade(0xB0050A12, open));
        }
    }

    // ---------- one slot ----------
    /** h = hover amount 0..1, on = animation active */
    public static void segment(GuiGraphics g, WheelConfig c, float cx, float cy, float r0, float ro, double a0, double a1,
                               int color, float h, boolean on, boolean empty, float alpha) {
        int col = slotColor(c, color, empty), ac = accent(c);
        double mid = (a0 + a1) / 2, half = (a1 - a0) / 2;
        switch (design(c)) {
            case "neon" -> {
                Draw.sector(g, cx, cy, r0, ro, a0, a1, fade(h > 0.5f ? 0xD0081A24 : 0xB0040810, alpha));
                for (int k = 0; k < 4; k++)
                    Draw.sector(g, cx, cy, ro - 2 + k * 1.5f, ro + k * 1.5f, a0, a1, a(col, (200 - k * 50) * (0.45f + 0.55f * h) * alpha));
                Draw.sector(g, cx, cy, r0, ro, a0, a0 + 0.012, a(RED, 170 * alpha));
                if (on || h > 0.5f) Draw.sector(g, cx, cy, r0, r0 + 2.5f, a0, a1, a(RED, 230 * alpha));
            }
            case "minimal" -> {
                if (h > 0.01f) Draw.sector(g, cx, cy, r0, ro, a0, a1, a(0xFFFFFF, 34 * h * alpha));
                Draw.sector(g, cx, cy, ro - 1.2f - h, ro, a0, a1, a(col, (110 + 145 * h) * alpha));
                if (on) Draw.sector(g, cx, cy, r0 + 3, r0 + 6, mid - 0.04, mid + 0.04, a(col, 255 * alpha));
            }
            case "terminal" -> {
                Draw.sector(g, cx, cy, r0, ro, a0, a1, h > 0.5f ? a(col, (150 + 80 * h) * alpha) : fade(0xD0140303, alpha));
                int steps = Math.max(4, (int) ((a1 - a0) * ro / 6));
                for (int i = 0; i < steps; i += 2) {
                    double s0 = a0 + (a1 - a0) * i / steps, s1 = a0 + (a1 - a0) * (i + 1) / steps;
                    Draw.sector(g, cx, cy, ro - 2, ro, s0, s1, a(col, 230 * alpha));
                }
                Draw.sector(g, cx, cy, r0, r0 + 1, a0, a1, a(col, 160 * alpha));
                if (on) Draw.sector(g, cx, cy, ro - 9, ro - 5, mid - 0.05, mid + 0.05, a(h > 0.5f ? 0x100000 : col, 255 * alpha));
            }
            case "glass" -> {
                Draw.sector(g, cx, cy, r0, ro, a0, a1, a(0xFFFFFF, (26 + 34 * h) * alpha));
                Draw.sector(g, cx, cy, r0, ro, a0, a1, a(col, (22 + 30 * h) * alpha));
                Draw.sector(g, cx, cy, ro - 1.5f, ro, a0, a1, a(0xFFFFFF, (110 + 100 * h) * alpha));
                Draw.sector(g, cx, cy, r0, r0 + 1, a0, a1, a(0xFFFFFF, 60 * alpha));
                if (on) Draw.sector(g, cx, cy, r0 + 1, r0 + 4, a0, a1, a(col, 230 * alpha));
            }
            case "petals" -> {
                float grow = 1 + 0.06f * h;
                petal(g, cx, cy, r0, ro + 2, mid, half * 1.06 * grow, a(col, (150 + 105 * h) * alpha));
                petal(g, cx, cy, r0 + 2, ro - 1, mid, half * 0.92 * grow, fade(h > 0.5f ? 0xE0281434 : 0xC0180A20, alpha));
                if (on) petal(g, cx, cy, r0 + 3, r0 + (ro - r0) * 0.35f, mid, half * 0.5, a(col, 230 * alpha));
            }
            case "hex" -> {
                float rr = (r0 + ro) / 2;
                float hx = cx + (float) Math.sin(mid) * rr, hy = cy - (float) Math.cos(mid) * rr;
                float rad = (float) Math.min((ro - r0) / 2.0, rr * Math.sin(half)) * (0.98f + 0.12f * h);
                hexagon(g, hx, hy, rad + 2, a(col, (170 + 85 * h) * alpha));
                hexagon(g, hx, hy, rad, h > 0.5f ? a(col, 90 * alpha) : fade(0xE0080C08, alpha));
                if (on) g.fill((int) (hx + rad * 0.45f), (int) (hy - rad * 0.75f), (int) (hx + rad * 0.45f) + 3, (int) (hy - rad * 0.75f) + 3, a(col, 255 * alpha));
            }
            default -> {
                Draw.sector(g, cx, cy, r0, ro, a0, a1, fade(h > 0.5f ? 0xC0102838 : 0xA00A1420, alpha));
                Draw.sector(g, cx, cy, ro - 3, ro, a0, a1, a(col, (170 + 85 * h) * alpha));
                if (on) Draw.sector(g, cx, cy, r0, r0 + 3, a0, a1, a(col, 255 * alpha));
            }
        }
    }

    // ---------- center ----------
    public static void center(GuiGraphics g, WheelConfig c, float cx, float cy, float r, float alpha) {
        int ac = accent(c);
        switch (design(c)) {
            case "neon" -> {
                Draw.sector(g, cx, cy, 0, r, 0, Math.PI * 2, fade(0xE0020608, alpha));
                for (int k = 0; k < 3; k++) Draw.sector(g, cx, cy, r - 1 + k * 1.5f, r + 0.5f + k * 1.5f, 0, Math.PI * 2, a(ac, (220 - k * 70) * alpha));
                Draw.sector(g, cx, cy, r - 4, r - 3, 0, Math.PI * 2, a(RED, 150 * alpha));
            }
            case "minimal" -> Draw.sector(g, cx, cy, r - 1, r, 0, Math.PI * 2, a(ac, 140 * alpha));
            case "terminal" -> {
                Draw.sector(g, cx, cy, 0, r, 0, Math.PI * 2, fade(0xE00A0101, alpha));
                for (int i = 0; i < 36; i += 2) Draw.sector(g, cx, cy, r - 2, r, i * Math.PI / 18, (i + 1) * Math.PI / 18, a(ac, 220 * alpha));
                g.fill((int) (cx - r * 0.8f), (int) cy, (int) (cx + r * 0.8f), (int) cy + 1, a(ac, 60 * alpha));
                g.fill((int) cx, (int) (cy - r * 0.8f), (int) cx + 1, (int) (cy + r * 0.8f), a(ac, 60 * alpha));
            }
            case "glass" -> {
                Draw.sector(g, cx, cy, 0, r, 0, Math.PI * 2, a(0xFFFFFF, 40 * alpha));
                Draw.sector(g, cx, cy, r - 1.5f, r, 0, Math.PI * 2, a(0xFFFFFF, 150 * alpha));
            }
            case "petals" -> {
                Draw.sector(g, cx, cy, 0, r, 0, Math.PI * 2, fade(0xD0180A20, alpha));
                Draw.sector(g, cx, cy, r - 2, r, 0, Math.PI * 2, a(ac, 220 * alpha));
            }
            case "hex" -> { hexagon(g, cx, cy, r, a(ac, 220 * alpha)); hexagon(g, cx, cy, r - 2, fade(0xF0060806, alpha)); }
            default -> {
                Draw.sector(g, cx, cy, 0, r, 0, Math.PI * 2, fade(0xD0060C14, alpha));
                Draw.sector(g, cx, cy, r - 2, r, 0, Math.PI * 2, a(ac, 255 * alpha));
            }
        }
    }

    // ---------- text ----------
    public static int textColor(WheelConfig c, boolean hv, boolean empty) {
        if (empty) return "terminal".equals(design(c)) ? 0x5A2020 : 0x55667A;
        return switch (design(c)) {
            case "terminal" -> hv ? 0x140000 : 0xFF7070;
            case "minimal" -> hv ? 0xFFFFFF : 0xB8C2CC;
            case "hex" -> hv ? 0xFFFFFF : 0xB8FFB0;
            default -> hv ? 0xFFFFFF : 0xC8E8F0;
        };
    }

    public static int textWidth(WheelConfig c, Font f, String s) {
        return "terminal".equals(design(c)) ? s.length() * 6 : f.width(s);
    }

    public static String fit(WheelConfig c, Font f, String s, int w) {
        if ("terminal".equals(design(c))) { s = s.toUpperCase(); return s.length() * 6 > w ? s.substring(0, Math.max(0, w / 6)) : s; }
        return f.plainSubstrByWidth(s, w);
    }

    /** centered label in the design's font styling */
    public static void text(GuiGraphics g, WheelConfig c, Font f, String s, int x, int y, int color) {
        switch (design(c)) {
            case "terminal" -> { // fake monospace, upper case, no shadow
                s = s.toUpperCase();
                int sx = x - s.length() * 3;
                for (int i = 0; i < s.length(); i++) {
                    String ch = String.valueOf(s.charAt(i));
                    g.drawString(f, ch, sx + i * 6 + (6 - f.width(ch)) / 2, y, color, false);
                }
            }
            case "neon" -> {
                int glow = Draw.argb(accent(c), 70);
                g.drawString(f, s, x - f.width(s) / 2 - 1, y, glow, false);
                g.drawString(f, s, x - f.width(s) / 2 + 1, y, glow, false);
                g.drawString(f, s, x - f.width(s) / 2, y, color, false);
            }
            case "minimal", "hex" -> g.drawString(f, s, x - f.width(s) / 2, y, color, false);
            default -> g.drawCenteredString(f, s, x, y, color);
        }
    }

    /** icon if present, otherwise a first-letter glyph badge */
    public static void icon(GuiGraphics g, WheelConfig c, Font f, WheelConfig.Slot s, int x, int y, int size, int color, float alpha) {
        if (s == null || size < 4) return;
        if (Icons.has(s.iconKey())) { Icons.draw(g, s.iconKey(), x, y, size); return; }
        if (s.empty()) return;
        String d = WheelConfig.RESET.equals(s.anim) ? "\u21BA" : s.display();
        String ch = d.isBlank() ? "?" : new String(Character.toChars(d.codePointAt(0))).toUpperCase();
        float r = size / 2f;
        int col = slotColor(c, color, false);
        switch (design(c)) {
            case "terminal", "hex" -> {
                if ("hex".equals(design(c))) hexagon(g, x, y, r, a(col, 150 * alpha));
                else { g.fill((int) (x - r), (int) (y - r), (int) (x + r), (int) (y + r), a(col, 150 * alpha)); g.fill((int) (x - r + 1), (int) (y - r + 1), (int) (x + r - 1), (int) (y + r - 1), fade(0xE0140303, alpha)); }
            }
            default -> {
                Draw.sector(g, x, y, 0, r, 0, Math.PI * 2, a(col, 110 * alpha));
                Draw.sector(g, x, y, r - 1.2f, r, 0, Math.PI * 2, a(col, 230 * alpha));
            }
        }
        float sc = Math.max(0.5f, size / 14f);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(f, ch, -f.width(ch) / 2 + ("terminal".equals(design(c)) ? 0 : 0), -4, 0xFFFFFF, !"terminal".equals(design(c)) && !"minimal".equals(design(c)));
        g.pose().popPose();
    }

    // ---------- shapes ----------
    static void petal(GuiGraphics g, float cx, float cy, float r0, float r1, double mid, double half, int argb) {
        int n = 14;
        float[] xs = new float[n * 2 + 2], ys = new float[n * 2 + 2];
        int k = 0;
        for (int i = 0; i <= n; i++) { // left edge outward
            double t = i / (double) n, r = r0 + (r1 - r0) * t, w = half * Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15)), 0.7);
            xs[k] = cx + (float) (Math.sin(mid - w) * r); ys[k] = cy - (float) (Math.cos(mid - w) * r); k++;
        }
        for (int i = n - 1; i >= 1; i--) { // right edge inward
            double t = i / (double) n, r = r0 + (r1 - r0) * t, w = half * Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15)), 0.7);
            xs[k] = cx + (float) (Math.sin(mid + w) * r); ys[k] = cy - (float) (Math.cos(mid + w) * r); k++;
        }
        float rc = (r0 + r1) / 2;
        Draw.poly(g, xs, ys, k, cx + (float) Math.sin(mid) * rc, cy - (float) Math.cos(mid) * rc, argb);
    }

    static void hexagon(GuiGraphics g, float x, float y, float r, int argb) {
        float[] xs = new float[6], ys = new float[6];
        for (int i = 0; i < 6; i++) { double t = Math.PI / 3 * i + Math.PI / 6; xs[i] = x + (float) Math.cos(t) * r; ys[i] = y + (float) Math.sin(t) * r; }
        Draw.poly(g, xs, ys, 6, x, y, argb);
    }
}
