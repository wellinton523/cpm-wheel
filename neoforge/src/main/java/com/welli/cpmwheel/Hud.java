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
    private record Row(int depth, boolean header, String label, String value, int color) {}
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
            } else if (s.isGroup() || s.isCond() || seen.add(s.anim)) {
                String[] it = item(cfg, s);
                if (it != null) { outRows.add(new Row(depth + 1, false, s.display(), it[0], Integer.parseInt(it[1]))); any = true; }
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
                if (s.empty() || s.isFolder() || (!s.isGroup() && !s.isCond() && !seen.add(s.anim))) continue;
                String[] it = item(cfg, s);
                if (it != null) r.add(new Row(0, false, s.display(), it[0], Integer.parseInt(it[1])));
            }
            if (!r.isEmpty()) b.add(new Bar(-1, 0, r.size() - 1, 0x2EE6FF));
        }
        int w = 0, md = 0;
        for (Row row : r) {
            int x = textX(row.depth);
            w = Math.max(w, x + font.width(row.header ? row.label : row.label + ": " + row.value) + 6);
            md = Math.max(md, row.depth);
        }
        rows = r; bars = b; layoutW = w; maxDepth = md;
    }

    private static int barX(int depth) { return depth < 0 ? 0 : depth * INDENT; }
    private static int textX(int depth) { return (depth + 1) * INDENT + 5; }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof WheelScreen) return;
        long now = System.currentTimeMillis();
        if (cfg == null || now - lastPoll > 250) { // config, CPM state and layout are rebuilt only 4x per second
            lastPoll = now;
            cfg = WheelConfig.get();
            present = CpmBridge.cpmPresent();
            if (cfg.hudEnabled && present) poll(cfg, mc.font);
        }
        if (!cfg.hudEnabled || !present || rows.isEmpty()) return;
        var font = mc.font;
        float sc = cfg.hudScale / 100f;
        int w = layoutW, h = rows.size() * ROW + 6;
        int sw = (int) (g.guiWidth() / sc), sh = (int) (g.guiHeight() / sc);
        String c = cfg.hudCorner == null ? "top_left" : cfg.hudCorner;
        int x = c.endsWith("right") ? sw - w - 4 : 4;
        int y = c.startsWith("bottom") ? sh - h - 4 : 4;
        int bgA = Math.max(0, Math.min(255, cfg.hudOpacity * 255 / 100));
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1);
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
                g.drawString(font, r.label + ": ", tx, ly, 0xC8E8F0);
                g.drawString(font, r.value, tx + font.width(r.label + ": "), ly, 0xFFFFFF);
            }
            ly += ROW;
        }
        g.pose().popPose();
    }

    public static void invalidate() { lastPoll = 0; }
}
