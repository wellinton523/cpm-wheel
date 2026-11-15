package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game overlay with what is active on the character, optionally grouped by wheel (page) and folder (sub-wheel),
 * each group with its own nested colored side bar. State + layout are rebuilt 4x per second, render only draws.
 */
public final class Hud {
    private static final int MAX_DEPTH = 16, ROW = 10, INDENT = 5;

    /** one text row: header (group name) or item (label: value) */
    private record Row(int depth, boolean header, String label, String value, int color, String icon) {
        Row(int depth, boolean header, String label, String value, int color) { this(depth, header, label, value, color, ""); }
    }
    /** 1.8.2: Emotecraft buttons show their emote icon in the HUD */
    private static String hudIcon(WheelConfig.Slot s) { return s.isEc() ? s.iconKey() : ""; }
    /** vertical bar of a group, covering rows [from, to] */
    private record Bar(int depth, int from, int to, int color) {}

    private static List<Row> rows = new ArrayList<>();
    private static List<Bar> bars = new ArrayList<>();
    private static int layoutW = 0, maxDepth = 0;
    private static long lastPoll = 0;
    private static WheelConfig cfg;
    private static boolean present;

    private Hud() {}

    /** {value, color} if this slot should be listed, else null */
    private static String[] item(WheelConfig cfg, WheelConfig.Slot s) {
        if (s == null || s.empty() || s.isFolder() || WheelConfig.RESET.equals(s.anim)) return null;
        if (s.isEc()) { // 1.8.1 Emotecraft button: playing / stopped
            boolean on = EmoteBridge.playing() && ("stop".equals(s.ecAction) || EmoteLogic.matches(s.ecName, true, EmoteBridge.playingName()));
            if (cfg.hudOnlyNonDefault && !on) return null;
            return new String[]{on ? "\u25B6" : "\u25A0", Integer.toString(s.color)};
        }
        if (s.isCond()) {
            if (s.lastMatch == null) return cfg.hudOnlyNonDefault ? null : new String[]{"-", Integer.toString(s.color)};
            return new String[]{s.lastMatch, Integer.toString(s.color)};
        }
        if (s.isGroup()) {
            int st = s.groupState();
            if (cfg.hudOnlyNonDefault && st == 0) return null;
            return new String[]{st == 2 ? "ON" : st == 1 ? "\u25D0" : "OFF", Integer.toString(s.color)};
        }
        int v = CpmBridge.playing(s.anim);
        if (v < 0) return null;
        String mode = s.effectiveMode(), val;
        int color = s.color;
        boolean nonDefault;
        if ("toggle".equals(mode)) { nonDefault = v > 0; val = v > 0 ? "ON" : "OFF"; }
        else if ("frames".equals(mode)) {
            int k = s.frameIndexOf(v);
            nonDefault = k != 0;
            val = s.frameName(k);
            WheelConfig.Frame f = s.frames != null && k >= 0 && k < s.frames.size() ? s.frames.get(k) : null;
            if (f != null && f.color != 0) color = f.color;
        } else {
            nonDefault = v != s.minV();
            int a = s.minV(), b = s.maxV();
            val = (b > a ? Math.round((v - a) * 100f / (b - a)) : 0) + "%";
        }
        if (cfg.hudOnlyNonDefault && !nonDefault) return null;
        int rem = Actions.remaining(s.anim);
        if (rem >= 0) val += "  \u23F1" + rem + "s";
        return new String[]{val, Integer.toString(color)};
    }

    /** adds the group's rows + bar; returns true if anything (active) was added */
    private static boolean group(WheelConfig cfg, String name, int color, List<WheelConfig.Slot> slots, int depth,
                                 List<Row> outRows, List<Bar> outBars, Set<WheelConfig.Slot> visiting) {
        if (slots == null || depth > MAX_DEPTH) return false;
        int start = outRows.size(), barStart = outBars.size();
        outRows.add(new Row(depth, true, name, "", color));
        boolean any = false;
        Set<String> seen = new HashSet<>();
        for (WheelConfig.Slot s : slots) {
            if (s == null) continue;
            if (s.isFolder()) {
                if (!visiting.add(s)) continue; // guards against a folder containing itself
                any |= group(cfg, s.display(), s.color, s.children, depth + 1, outRows, outBars, visiting);
                visiting.remove(s);
            } else if (s.isGroup() || s.isCond() || s.isEc() || seen.add(s.anim)) {
                String[] it = item(cfg, s);
                if (it != null) { outRows.add(new Row(depth + 1, false, s.display(), it[0], Integer.parseInt(it[1]), hudIcon(s))); any = true; }
            }
        }
        if (!any && !cfg.hudShowEmptyGroups) { // hide empty group entirely
            outRows.subList(start, outRows.size()).clear();
            outBars.subList(barStart, outBars.size()).clear();
            return false;
        }
        outBars.add(new Bar(depth, start, outRows.size() - 1, color));
        return any;
    }

    private static void poll(WheelConfig cfg, Font font) {
        List<Row> r = new ArrayList<>();
        List<Bar> b = new ArrayList<>();
        if (cfg.hudGrouped) {
            for (WheelConfig.Page p : cfg.pages)
                group(cfg, p.name == null ? "?" : p.name, p.color == 0 ? 0x2EE6FF : p.color, p.slots, 0, r, b, new HashSet<>());
        } else {
            Set<String> seen = new HashSet<>();
            for (WheelConfig.Slot s : cfg.allSlots()) {
                if (s.empty() || s.isFolder() || (!s.isGroup() && !s.isCond() && !s.isEc() && !seen.add(s.anim))) continue;
                String[] it = item(cfg, s);
                if (it != null) r.add(new Row(0, false, s.display(), it[0], Integer.parseInt(it[1]), hudIcon(s)));
            }
            if (!r.isEmpty()) b.add(new Bar(-1, 0, r.size() - 1, 0x2EE6FF));
        }
        int w = 0, md = 0;
        for (Row row : r) {
            int x = textX(row.depth);
            w = Math.max(w, x + font.width(row.header ? row.label : row.label + ": " + row.value) + 6 + (row.icon.isEmpty() ? 0 : 10));
            md = Math.max(md, row.depth);
        }
        rows = r; bars = b; layoutW = w; maxDepth = md;
    }

    private static int barX(int depth) { return depth < 0 ? 0 : depth * INDENT; }
    private static int textX(int depth) { return (depth + 1) * INDENT + 5; }

    // ---------- 1.5.8 movable HUD ----------
    public static final String[] ANCHORS = {"tl", "tc", "tr", "ml", "mc", "mr", "bl", "bc", "br"};
    public static final String[] ELEMENTS = {"list", "toasts"};
    /** set by the HUD edit screen: draw samples so every element can be grabbed even when empty */
    public static boolean editing = false;
    /** last drawn screen rectangle {x, y, w, h} per element (GUI pixels) */
    public static final java.util.Map<String, int[]> BOUNDS = new java.util.HashMap<>();

    public static WheelConfig.HudPos defaultPos(String el, WheelConfig c) {
        if ("toasts".equals(el)) return new WheelConfig.HudPos("mr", -4, 0, 100);
        String corner = c == null || c.hudCorner == null ? "top_left" : c.hudCorner;
        int sc = c == null || c.hudScale <= 0 ? 100 : c.hudScale;
        return switch (corner) {
            case "top_right" -> new WheelConfig.HudPos("tr", -4, 4, sc);
            case "bottom_left" -> new WheelConfig.HudPos("bl", 4, -4, sc);
            case "bottom_right" -> new WheelConfig.HudPos("br", -4, -4, sc);
            default -> new WheelConfig.HudPos("tl", 4, 4, sc);
        };
    }
    public static WheelConfig.HudPos pos(WheelConfig c, String el) {
        WheelConfig.HudPos p = c.hudPos == null ? null : c.hudPos.get(el);
        if (p == null) { p = defaultPos(el, c); if (c.hudPos != null) c.hudPos.put(el, p); }
        return p;
    }
    static float ax(String a) { return a.endsWith("l") ? 0f : a.endsWith("c") ? 0.5f : 1f; }
    static float ay(String a) { return a.startsWith("t") ? 0f : a.startsWith("m") ? 0.5f : 1f; }
    /** top-left corner on screen for an element of (already scaled) size w x h */
    public static int[] place(WheelConfig.HudPos p, int w, int h, int sw, int sh) {
        float fx = ax(p.anchor), fy = ay(p.anchor);
        int x = Math.round(fx * sw + p.dx - fx * w), y = Math.round(fy * sh + p.dy - fy * h);
        return new int[]{Math.max(0, Math.min(sw - Math.min(w, sw), x)), Math.max(0, Math.min(sh - Math.min(h, sh), y))};
    }
    /** store a dragged position relative to the nearest screen anchor (so it survives resolution / GUI scale changes) */
    public static void store(WheelConfig.HudPos p, int x, int y, int w, int h, int sw, int sh) {
        float cx = (x + w / 2f) / sw, cy = (y + h / 2f) / sh;
        String a = (cy < 1 / 3f ? "t" : cy < 2 / 3f ? "m" : "b") + (cx < 1 / 3f ? "l" : cx < 2 / 3f ? "c" : "r");
        float fx = ax(a), fy = ay(a);
        p.anchor = a;
        p.dx = Math.round(x - fx * sw + fx * w);
        p.dy = Math.round(y - fy * sh + fy * h);
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.options.hideGui && !editing) || mc.screen instanceof WheelScreen) return;
        long now = System.currentTimeMillis();
        if (cfg == null || now - lastPoll > 250) { // config, CPM state and layout are rebuilt only 4x per second
            lastPoll = now;
            cfg = WheelConfig.get();
            present = CpmBridge.cpmPresent();
            if (cfg.hudEnabled && present) poll(cfg, mc.font);
        }
        BOUNDS.clear();
        renderList(g, mc);
        TriggerToasts.render(g, cfg, editing);
    }

    private static void renderList(GuiGraphics g, Minecraft mc) {
        List<Row> rows = Hud.rows; List<Bar> bars = Hud.bars; int w = layoutW;
        if (!cfg.hudEnabled || !present || rows.isEmpty()) {
            if (!editing) return;
            rows = List.of(new Row(0, true, "CPM Wheel", "", 0x2EE6FF), new Row(1, false, "Exemplo", "ON", 0x3BFF9D));
            bars = List.of(new Bar(0, 0, 1, 0x2EE6FF));
            w = textX(1) + mc.font.width("Exemplo: ON") + 6;
        }
        var font = mc.font;
        WheelConfig.HudPos hp = pos(cfg, "list");
        float sc = hp.scale / 100f;
        int h = rows.size() * ROW + 6;
        int[] xy = place(hp, Math.round(w * sc), Math.round(h * sc), g.guiWidth(), g.guiHeight());
        BOUNDS.put("list", new int[]{xy[0], xy[1], Math.round(w * sc), Math.round(h * sc)});
        int bgA = Math.max(0, Math.min(255, cfg.hudOpacity * 255 / 100));
        g.pose().pushPose();
        g.pose().translate(xy[0], xy[1], 0);
        g.pose().scale(sc, sc, 1);
        int x = 0, y = 0;
        g.fill(x, y, x + w, y + h, (bgA << 24) | 0x050A12);
        for (Bar b : bars) {
            int bx = x + barX(b.depth);
            int y0 = b.depth < 0 ? y : y + 3 + b.from * ROW, y1 = b.depth < 0 ? y + h : y + 3 + (b.to + 1) * ROW - 1;
            g.fill(bx, y0, bx + 2, y1, 0xFF000000 | b.color);
        }
        int ly = y + 4;
        for (Row r : rows) {
            int tx = x + textX(r.depth);
            if (r.header) g.drawString(font, r.label, tx - 2, ly, 0xFF000000 | r.color);
            else {
                g.fill(tx - 5, ly + 2, tx - 2, ly + 6, 0xFF000000 | r.color);
                int ix = 0;
                if (!r.icon.isEmpty()) { Icons.draw(g, r.icon, tx + 4, ly + 3, 9); ix = 10; } // 1.8.2 emote icon
                g.drawString(font, r.label + ": ", tx + ix, ly, 0xC8E8F0);
                g.drawString(font, r.value, tx + ix + font.width(r.label + ": "), ly, 0xFFFFFF);
            }
            ly += ROW;
        }
        g.pose().popPose();
    }

    /** small padlock drawn with fills, centered on (x, y) */
    public static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 4, y - 1, x + 4, y + 5, 0xFF000000 | color);
        g.fill(x - 3, y - 5, x - 1, y - 1, 0xFF000000 | color);
        g.fill(x + 1, y - 5, x + 3, y - 1, 0xFF000000 | color);
        g.fill(x - 3, y - 6, x + 3, y - 4, 0xFF000000 | color);
        g.fill(x - 1, y + 1, x + 1, y + 3, 0xFF101820);
    }

    public static void invalidate() { lastPoll = 0; }
}
