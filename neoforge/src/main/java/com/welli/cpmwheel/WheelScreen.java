package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class WheelScreen extends Screen {
    private static final long ANIM_MS = 150;
    private final boolean openedByKey;
    private boolean holdActive;
    private WheelConfig cfg;
    private List<WheelConfig.Page> pages;
    private int page;
    private int hovered = -1;
    private int hoveredFrame = -1;
    private int hoveredRing = -1, lastHovered = -1;
    private List<WheelConfig.Slot> ring = new ArrayList<>();   // favorites + recents (inner ring)
    private String query = "";
    private List<WheelConfig.Slot> filtered = null;            // search results, null = no search // outer frame segment under the mouse (frames slots)
    private long openedAt, transitionAt, lastFrame;
    private int transitionDir = 0; // +1 entering sub-wheel, -1 leaving
    private final List<WheelConfig.Slot> path = new ArrayList<>(); // open folders
    private float[] hoverAmt = new float[0];

    // live preview state
    private WheelConfig.Slot previewSlot;
    private int previewPrev = -1;

    public WheelScreen(boolean openedByKey) {
        super(Component.translatable("cpmwheel.title"));
        this.openedByKey = openedByKey;
    }

    @Override
    protected void init() {
        cfg = WheelConfig.get();
        pages = cfg.viewPages();
        page = Math.floorMod(cfg.lastPage, pages.size());
        if (openedAt == 0) { openedAt = System.currentTimeMillis(); holdActive = openedByKey && cfg.holdToOpen; Actions.sound(cfg, 0.8f); }
        ring = cfg.ringSlots();
        transitionAt = openedAt;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit"), b -> { endPreview(true); minecraft.setScreen(new EditScreen(this)); })
                .bounds(width - 84, height - 26, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(cfg.previewOnHover ? "cpmwheel.preview.on" : "cpmwheel.preview.off"), b -> {
            cfg.previewOnHover = !cfg.previewOnHover; if (!cfg.previewOnHover) endPreview(true); cfg.save();
            b.setMessage(Component.translatable(cfg.previewOnHover ? "cpmwheel.preview.on" : "cpmwheel.preview.off"));
        }).bounds(width - 254, height - 26, 166, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud"), b -> { endPreview(true); minecraft.setScreen(new HudScreen(this)); })
                .bounds(width - 304, height - 26, 46, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.style"), b -> { endPreview(true); minecraft.setScreen(new SettingsScreen(this)); })
                .bounds(width - 362, height - 26, 54, 20).build());
    }

    private boolean showRing() { return cfg.innerRing && filtered == null && path.isEmpty() && !ring.isEmpty(); }

    private void refilter() {
        endPreview(true);
        hovered = -1; lastHovered = -1;
        if (query.isEmpty()) { filtered = null; return; }
        String q = query.toLowerCase();
        List<WheelConfig.Slot> out = new ArrayList<>();
        for (WheelConfig.Slot s : cfg.allSlots()) {
            if (s.empty() || s.isFolder() || out.contains(s)) continue;
            if (s.display().toLowerCase().contains(q) || s.anim.toLowerCase().contains(q)) out.add(s);
            if (out.size() >= 12) break;
        }
        filtered = out;
    }

    /** play an inner-ring entry; toggles close the wheel, value slots keep it open */
    private void playRing(WheelConfig.Slot s) {
        endPreview(true);
        String lk = EffectTriggers.lockedBy(s);
        if (lk != null) { EffectTriggers.lockedFeedback(lk); return; } // 1.5.8: held by a trigger
        Actions.trigger(cfg, s);
        ring = cfg.ringSlots();
        if (WheelConfig.RESET.equals(s.anim) || "toggle".equals(s.effectiveMode())) super.onClose();
        else holdActive = false;
    }

    private float radius() { return Math.min(width, height) * 0.36f; }

    private WheelConfig.Page cur() { return pages.get(page); }

    private List<WheelConfig.Slot> slots() { return filtered != null ? filtered : path.isEmpty() ? cur().slots : path.get(path.size() - 1).children; }

    private void enter(WheelConfig.Slot folder) {
        endPreview(true);
        path.add(folder); hovered = -1; transitionDir = 1; transitionAt = System.currentTimeMillis();
    }

    private boolean back() {
        if (path.isEmpty()) return false;
        endPreview(true);
        path.remove(path.size() - 1); hovered = -1; transitionDir = -1; transitionAt = System.currentTimeMillis();
        return true;
    }

    @Override
    public void tick() {
        if (holdActive && System.currentTimeMillis() - openedAt > 120) {
            InputConstants.Key k = CpmWheel.KEY_WHEEL.getKey();
            if (k.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), k.getValue())) {
                holdActive = false;
                activate();
            }
        }
    }

    /** Select hovered slot: folder -> enter (screen stays open), animation -> play and close. */
    private void activate() {
        if (hoveredRing >= 0 && hoveredRing < ring.size() && showRing()) { playRing(ring.get(hoveredRing)); return; }
        List<WheelConfig.Slot> sl = slots();
        int h = hovered;
        if (h < 0 || h >= sl.size() || sl.get(h).empty()) { endPreview(true); onClose(); return; }
        WheelConfig.Slot s = sl.get(h);
        if (s.isFolder()) { enter(s); return; }
        String lk = EffectTriggers.lockedBy(s);
        if (lk != null) { endPreview(true); EffectTriggers.lockedFeedback(lk); holdActive = false; return; } // 1.5.8: does nothing while a trigger holds it
        if (isValue(s)) {
            endPreview(true);
            cfg.addRecent(s.anim);
            if ("frames".equals(s.effectiveMode())) {
                int k = hoveredFrame >= 0 ? hoveredFrame : (s.frameIndexOf(curVal(s)) + 1) % s.frames.size();
                applyValue(s, s.frameValue(k));
                holdActive = false; // stay open
                return;
            }
            if ("presets".equals(s.effectiveMode())) {
                int[] ps = s.presetValues();
                int c = curVal(s), next = ps[0];
                for (int p : ps) if (p > c) { next = p; break; }
                applyValue(s, next);
                holdActive = false; // stay open so the user can keep cycling
                return;
            }
            if (s.value >= 0) applyValue(s, s.value);
            super.onClose();
            return;
        }
        boolean alreadyApplied = s == previewSlot;
        int prior = alreadyApplied ? previewPrev : (WheelConfig.RESET.equals(s.anim) ? -1 : CpmBridge.playing(s.anim));
        endPreview(false); // keep preview state if it is the chosen one
        if (!alreadyApplied) WheelConfig.run(s);
        Actions.played(cfg, s, prior);
        Actions.sound(cfg, 1.2f);
        previewSlot = null;
        super.onClose();
    }

    // ---------- live preview ----------
    private static boolean previewable(WheelConfig.Slot s) {
        return s != null && !s.empty() && !s.isFolder() && !s.isGroup() && !s.isCond() && !s.isEc() && !WheelConfig.RESET.equals(s.anim);
    }

    private static boolean isValue(WheelConfig.Slot s) { return previewable(s) && !"toggle".equals(s.effectiveMode()); }

    /** current value of a value slot (min if unknown) */
    private static int curVal(WheelConfig.Slot s) { int v = CpmBridge.playing(s.anim); return v < 0 ? s.minV() : v; }

    private static float pct(WheelConfig.Slot s, int v) { int a = s.minV(), b = s.maxV(); return b <= a ? 0 : Math.max(0, Math.min(1, (v - a) / (float) (b - a))); }

    private void applyValue(WheelConfig.Slot s, int v) {
        v = Math.max(s.minV(), Math.min(s.maxV(), v));
        CpmBridge.play(s.anim, v);
        Actions.sound(cfg, 1.5f);
        Sounds.slot(cfg, s, -1); // 1.6.0 button sound
    }

    private void updatePreview(WheelConfig.Slot s) {
        if (!cfg.previewOnHover) return;
        if (s == previewSlot) return;
        endPreview(true);
        if (!previewable(s) || isValue(s) || EffectTriggers.lockedBy(s) != null) return;
        int prev = CpmBridge.playing(s.anim);
        if (prev < 0) return; // unknown state: cannot revert safely, skip preview
        int v = s.value >= 0 ? s.value : (prev > 0 ? 0 : 1);
        if (v == prev) return;
        if (CpmBridge.play(s.anim, v)) { previewSlot = s; previewPrev = prev; }
    }

    private void endPreview(boolean revert) {
        if (previewSlot != null && revert) CpmBridge.play(previewSlot.anim, previewPrev);
        if (revert) previewSlot = null;
    }

    @Override
    public void onClose() { endPreview(true); super.onClose(); }

    @Override
    public void removed() { endPreview(true); super.removed(); }

    // ---------- rendering ----------
    private static float easeOutCubic(float t) { t = Math.max(0, Math.min(1, t)); float u = 1 - t; return 1 - u * u * u; }

    private static int fade(int argb, float a) { return (Math.round((argb >>> 24) * a) << 24) | (argb & 0xFFFFFF); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean anim = cfg.animations;
        float open = anim ? easeOutCubic((now - openedAt) / (float) ANIM_MS) : 1f;
        float trans = anim ? easeOutCubic((now - transitionAt) / (float) ANIM_MS) : 1f;
        float alpha = Math.min(open, trans);

        float cx = width / 2f, cy = height / 2f, R0 = radius();
        float scale = 0.8f + 0.2f * open;
        if (transitionDir != 0 && trans < 1) scale *= transitionDir > 0 ? 0.85f + 0.15f * trans : 1.15f - 0.15f * trans;
        float R = R0 * scale, r0 = R * 0.38f;
        Style.background(g, cfg, width, height, open, cx, cy, R);
        int accent = Style.accent(cfg);
        boolean ringOn = showRing();
        int m = ring.size();
        float ringR = r0 * 0.74f, ringIr = m == 0 ? 0 : (float) Math.min(r0 * 0.2f, ringR * Math.sin(Math.PI / Math.max(2, m)) * 0.9f);
        List<WheelConfig.Slot> slots = slots();
        int n = Math.max(1, slots.size());
        double seg = Math.PI * 2 / n;
        if (hoverAmt.length != slots.size()) hoverAmt = new float[slots.size()];

        double dx = mx - cx, dy = my - cy;
        hovered = -1; hoveredRing = -1;
        if (ringOn) for (int i = 0; i < m; i++) {
            double ra = i * Math.PI * 2 / m;
            if (Math.hypot(mx - (cx + Math.sin(ra) * ringR), my - (cy - Math.cos(ra) * ringR)) <= ringIr + 1) hoveredRing = i;
        }
        if (hoveredRing < 0 && dx * dx + dy * dy > (ringOn ? r0 * r0 : r0 * r0 * 0.6)) {
            double ang = Math.atan2(dx, -dy);
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.floor((ang + seg / 2) / seg) % n;
        }
        updatePreview(hovered >= 0 && hovered < slots.size() ? slots.get(hovered) : null);
        int hoverId = hoveredRing >= 0 ? 100 + hoveredRing : hovered;
        if (hoverId != lastHovered && hoverId >= 0 && open >= 1f) Actions.hoverSound(cfg);
        lastHovered = hoverId;
        if (hovered < 0 || hovered >= slots.size() || !"frames".equals(slots.get(hovered).effectiveMode())) hoveredFrame = -1;

        for (int i = 0; i < slots.size(); i++) {
            WheelConfig.Slot s = slots.get(i);
            boolean hv = i == hovered;
            hoverAmt[i] = anim ? hoverAmt[i] + ((hv ? 1f : 0f) - hoverAmt[i]) * Math.min(1f, dt * 18f) : (hv ? 1f : 0f);
            float h = hoverAmt[i];
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            int gst = s.isGroup() ? s.groupState() : 0;
            boolean on = s.isGroup() ? gst == 2 : previewable(s) && CpmBridge.playing(s.anim) > 0;
            float ro = R + 7 * h;
            Style.segment(g, cfg, cx, cy, r0, ro, a0, a1, s.color, h, on, s.empty(), alpha);
            if (isValue(s) && alpha > 0.5f && "frames".equals(s.effectiveMode()) && cfg.frameSegments) {
                s.ensureFrames();
                int nf = s.frames.size(), curK = s.frameIndexOf(curVal(s));
                double fs = (a1 - a0) / nf;
                if (hv) { // which frame segment is under the mouse
                    double md = Math.hypot(mx - cx, my - cy);
                    hoveredFrame = -1;
                    if (md >= ro + 1 && md <= ro + 16) {
                        double ang = Math.atan2(mx - cx, -(my - cy));
                        double rel = ang - a0;
                        while (rel < 0) rel += Math.PI * 2;
                        while (rel >= Math.PI * 2) rel -= Math.PI * 2;
                        if (rel <= a1 - a0) hoveredFrame = Math.min(nf - 1, (int) (rel / fs));
                    }
                }
                for (int f = 0; f < nf; f++) {
                    WheelConfig.Frame fr = s.frames.get(f);
                    int col = fr.color != 0 ? fr.color : s.color;
                    boolean big = hv && f == hoveredFrame;
                    float rin = ro + 2, rout = ro + (big ? 14 : (f == curK ? 10 : 6));
                    Draw.sector(g, cx, cy, rin, rout, a0 + f * fs + 0.006, a0 + (f + 1) * fs - 0.006,
                            Draw.argb(col, Math.round((f == curK || big ? 255 : 110) * alpha)));
                }
            } else if (isValue(s) && alpha > 0.5f) {
                float p = pct(s, curVal(s));
                Draw.sector(g, cx, cy, ro + 2, ro + 5, a0, a1, Draw.argb(0x1A2A3A, Math.round(220 * alpha)));
                Draw.sector(g, cx, cy, ro + 2, ro + 5, a0, a0 + (a1 - a0) * p, Draw.argb(s.color, Math.round(255 * alpha)));
            }
            if (s.isFolder()) Draw.sector(g, cx, cy, ro - 7, ro - 5, a0 + 0.08, a1 - 0.08, Draw.argb(s.color, Math.round(200 * alpha)));

            double mid = i * seg;
            float rr = (r0 + ro) / 2;
            float tx = cx + (float) Math.sin(mid) * rr, ty = cy - (float) Math.cos(mid) * rr;
            if (alpha < 0.35f) continue;
            int tcol = Style.textColor(cfg, hv && h > 0.5f, s.empty());
            int isz = Math.max(8, Math.round(Math.min((ro - r0) * 0.95f, 18 * cfg.iconSize / 100f) * (1 + 0.08f * h)));
            if (s.iconOnly(cfg)) { // icon (or first-letter glyph), no text
                if (s.empty()) Draw.sector(g, tx, ty, 0, 1.5f, 0, Math.PI * 2, Draw.argb(0x55667A, Math.round(200 * alpha)));
                else Style.icon(g, cfg, font, s, (int) tx, (int) ty, isz, s.color, alpha);
                if (s.favorite) g.drawString(font, "\u2605", (int) tx + isz / 2 - 2, (int) ty - isz / 2 - 4, 0xFFD23B, false);
            } else {
                int textY = (int) ty - 4;
                if (s.showIcon(cfg) && Icons.has(s.icon)) { int is2 = Math.min(isz, (int) ((ro - r0) * 0.6f)); Icons.draw(g, s.icon, (int) tx, (int) ty - 2 - is2 / 2, is2); textY = (int) ty + is2 / 2 - 4; }
                String label = s.empty() ? "\u2014" : (s.isFolder() ? "\u25B8 " : "") + s.display();
                label = Style.fit(cfg, font, label, (int) (R * 0.75f));
                Style.text(g, cfg, font, label, (int) tx, textY, tcol);
                if (s.favorite) g.drawString(font, "\u2605", (int) tx + Style.textWidth(cfg, font, label) / 2 + 2, textY, 0xFFD23B);
            }
            if (s.isGroup()) { // ON / OFF / partial
                int iy = (int) ty + Math.max(8, isz / 2 + 2) + (s.iconOnly(cfg) ? 0 : 4);
                g.drawCenteredString(font, gst == 2 ? "ON" : gst == 1 ? "\u25D0" : "OFF", (int) tx, iy, gst == 2 ? 0x3BFF9D : gst == 1 ? 0xFFD23B : 0x7FA8B8);
            }
            if (s.isCond() && s.lastMatch != null) g.drawCenteredString(font, font.plainSubstrByWidth(s.lastMatch, (int) (R * 0.7f)), (int) tx, (int) ty + Math.max(8, isz / 2 + 2) + (s.iconOnly(cfg) ? 0 : 4), 0x7FA8B8);
            if (EffectTriggers.lockedBy(s) != null) Hud.lock(g, (int) tx + isz / 2 + 4, (int) ty - isz / 2 - 2, 0xFFD23B); // 1.5.8 held by a trigger
            int rem = s.empty() || s.isFolder() || s.isGroup() || s.isCond() || s.isEc() ? -1 : Actions.remaining(s.anim);
            if (rem >= 0) g.drawCenteredString(font, "\u23F1" + rem + "s", (int) tx, (int) ty + Math.max(8, isz / 2 + 2), 0xFFD23B);
        }

        Style.center(g, cfg, cx, cy, r0 - 4, alpha);
        WheelConfig.Slot hs = hovered >= 0 && hovered < slots.size() ? slots.get(hovered) : null;
        WheelConfig.Slot cs = hoveredRing >= 0 && hoveredRing < m ? ring.get(hoveredRing) : hs; // what the center shows
        String center = cs != null && !cs.empty() ? cs.display() : filtered != null ? "\u2315 " + query : (path.isEmpty() ? cur().name : path.get(path.size() - 1).display());
        if (ringOn && alpha > 0.35f) for (int i = 0; i < m; i++) { // favorites + recents inner ring
            double ra = i * Math.PI * 2 / m;
            float rx = cx + (float) Math.sin(ra) * ringR, ry = cy - (float) Math.cos(ra) * ringR;
            boolean rh = i == hoveredRing;
            WheelConfig.Slot rs = ring.get(i);
            Draw.sector(g, rx, ry, 0, ringIr * (rh ? 1.15f : 1f), 0, Math.PI * 2, Draw.argb(rh ? accent : 0x000000, Math.round((rh ? 120 : 90) * alpha)));
            if (Icons.has(rs.icon)) Icons.draw(g, rs.icon, (int) rx, (int) ry, Math.max(6, (int) (ringIr * 1.5f)));
            else Draw.sector(g, rx, ry, 0, ringIr * 0.35f, 0, Math.PI * 2, Draw.argb(rs.color, Math.round(230 * alpha))); // no letter glyph inside the center
            if (rs.favorite) Draw.sector(g, rx + ringIr * 0.7f, ry - ringIr * 0.7f, 0, 1.6f, 0, Math.PI * 2, 0xFFFFD23B);
        }

        float k = cfg.centerSize / 100f, rc = ringOn ? r0 * 0.5f : r0;
        String cm = cfg.centerMode == null ? "player" : cfg.centerMode;
        boolean noText = cfg.centerIconOnly;
        int cis = Math.max(8, Math.round(Math.min(rc * 1.5f, 28 * cfg.centerIconSize / 100f)));
        if (cm.equals("player") && minecraft.player != null && alpha > 0.5f) {
            renderPlayer(g, cx, cy, rc * k, mx, my);
            if (cs != null && !cs.empty() && Icons.has(cs.icon)) { // small badge, real icons only (never a letter glyph)
                int bs = Math.min(cis, (int) (r0 * 0.5f));
                float bx = cx + r0 * 0.62f, by = cy - r0 * 0.62f;
                Draw.sector(g, bx, by, 0, bs * 0.7f, 0, Math.PI * 2, 0xE0060C14);
                Icons.draw(g, cs.icon, (int) bx, (int) by, bs);
            }
            if (!noText) {
                Style.text(g, cfg, font, Style.fit(cfg, font, center, (int) (R * 2)), (int) cx, (int) (cy + R + 12), accent);
                if (!path.isEmpty()) g.drawCenteredString(font, Component.translatable("cpmwheel.back"), (int) cx, (int) (cy + R + 24), 0x7FA8B8);
            }
        } else if (noText) {
            if (cm.equals("icon") && cs != null && !cs.empty() && Icons.has(cs.icon)) Icons.draw(g, cs.icon, (int) cx, (int) cy, cis);
        } else {
            WheelConfig.Slot is = cs != null && Icons.has(cs.icon) ? cs : null;
            boolean showIcon = cm.equals("icon") && is != null;
            if (showIcon) Icons.draw(g, is.icon, (int) cx, (int) cy - 10 - cis / 2, (int) (cis * k));
            Style.text(g, cfg, font, Style.fit(cfg, font, center, (int) (rc * 1.7f)), (int) cx, (int) cy - 8 + (showIcon ? 0 : 4), accent);
            if (!ringOn || !showIcon) g.drawCenteredString(font, path.isEmpty() ? (page + 1) + "/" + pages.size() : "\u2190", (int) cx, (int) cy + 8, 0x7FA8B8);
        }
        if (filtered != null) {
            g.drawString(font, Component.translatable("cpmwheel.search", query), 8, 40, accent);
            if (filtered.isEmpty()) g.drawCenteredString(font, Component.translatable("cpmwheel.search.none"), (int) cx, (int) (cy + R + 12), 0xFF3B5C);
        }

        if (hs != null && isValue(hs) && "frames".equals(hs.effectiveMode())) {
            int v = curVal(hs);
            int fk = hoveredFrame >= 0 ? hoveredFrame : hs.frameIndexOf(v);
            boolean pending = hoveredFrame >= 0 && hoveredFrame != hs.frameIndexOf(v);
            int ty2 = (int) (cy - R - 40);
            g.pose().pushPose();
            g.pose().translate(cx, ty2, 0);
            g.pose().scale(2f, 2f, 1f);
            String nm = (pending ? "\u2192 " : "") + hs.frameName(fk);
            g.drawCenteredString(font, nm, 0, 0, pending ? 0xFFD23B : 0xFFFFFF);
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("cpmwheel.frames.of", fk + 1, hs.frames.size(), hs.frameValue(fk)), (int) cx, ty2 + 20, 0x2EE6FF);
            g.drawCenteredString(font, Component.translatable("cpmwheel.value.hint.frames"), (int) cx, ty2 - 12, 0x7FA8B8);
        } else if (hs != null && isValue(hs)) {
            int v = curVal(hs);
            String txt = Math.round(pct(hs, v) * 100) + "%  (" + v + ")";
            int ty2 = (int) (cy - R - 22);
            g.drawCenteredString(font, txt, (int) cx, ty2, 0xFFFFFF);
            g.drawCenteredString(font, Component.translatable("presets".equals(hs.effectiveMode()) ? "cpmwheel.value.hint.presets" : "cpmwheel.value.hint.slider"), (int) cx, ty2 - 11, 0x7FA8B8);
        }

        // tabs + breadcrumb
        int tx = 8;
        for (int i = 0; i < pages.size(); i++) {
            String nm = pages.get(i).name;
            int w = font.width(nm) + 10;
            g.fill(tx, 8, tx + w, 22, i == page ? Draw.argb(accent, 0xE0) : 0xA0101C28);
            g.drawString(font, nm, tx + 5, 11, i == page ? 0x041018 : 0xC8E8F0, false);
            tx += w + 3;
        }
        if (!path.isEmpty()) {
            StringBuilder sb = new StringBuilder(cur().name);
            for (WheelConfig.Slot f : path) sb.append("  \u203A  ").append(f.display());
            g.drawString(font, sb.toString(), 8, 27, 0x2EE6FF);
        }
        if (!CpmBridge.cpmPresent()) g.drawCenteredString(font, Component.translatable("cpmwheel.nocpm"), width / 2, height - 20, 0xFF3B5C);
        else g.drawString(font, Component.translatable("cpmwheel.hint"), 8, height - 20, 0x7FA8B8);
        super.render(g, mx, my, pt);
        String lk = cs == null ? null : EffectTriggers.lockedBy(cs);
        if (lk != null) g.renderTooltip(font, Component.translatable("cpmwheel.eff.locked", lk), mx, my);
    }

        private void renderPlayer(GuiGraphics g, float cx, float cy, float r0, int mx, int my) {
        int r = (int) (r0 * 0.8f);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, (int) cx - r, (int) cy - r, (int) cx + r, (int) cy + r, (int) (r0 * 0.5f), 0.0625f, mx, my, minecraft.player);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (my < 24) {
            int tx = 8;
            for (int i = 0; i < pages.size(); i++) {
                int w = font.width(pages.get(i).name) + 10;
                if (mx >= tx && mx < tx + w) { setPage(i); return true; }
                tx += w + 3;
            }
        }
        holdActive = false; // once the mouse is used, stop closing on key release
        float r0 = radius() * 0.38f;
        if (hoveredRing >= 0 && hoveredRing < ring.size() && showRing()) { if (btn == 0) playRing(ring.get(hoveredRing)); else { ring.get(hoveredRing).favorite = !ring.get(hoveredRing).favorite; cfg.save(); ring = cfg.ringSlots(); } return true; }
        boolean inCenter = Math.hypot(mx - width / 2f, my - height / 2f) < r0 * 0.78f;
        if (inCenter) { if (!back() && btn == 0) onClose(); return true; }
        if (btn == 0 && hovered >= 0) { activate(); return true; }
        if (btn == 1) {
            List<WheelConfig.Slot> sl = slots();
            if (hovered >= 0 && hovered < sl.size() && !sl.get(hovered).empty()) {
                WheelConfig.Slot s = sl.get(hovered);
                s.favorite = !s.favorite; cfg.save(); ring = cfg.ringSlots();
                if (path.isEmpty()) { pages = cfg.viewPages(); page = Math.min(page, pages.size() - 1); }
                return true;
            }
            back();
            return true;
        }
        return false;
    }

    private void setPage(int p) {
        endPreview(true);
        path.clear();
        query = ""; filtered = null;
        page = Math.floorMod(p, pages.size());
        cfg.lastPage = page; cfg.save();
        transitionDir = 0; transitionAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double d) {
        List<WheelConfig.Slot> sl = slots();
        if (hovered >= 0 && hovered < sl.size() && isValue(sl.get(hovered))) {
            WheelConfig.Slot s = sl.get(hovered);
            String lk = EffectTriggers.lockedBy(s);
            if (lk != null) { EffectTriggers.lockedFeedback(lk); return true; }
            if ("frames".equals(s.effectiveMode())) {
                s.ensureFrames();
                int k = Math.max(0, Math.min(s.frames.size() - 1, s.frameIndexOf(curVal(s)) + (d > 0 ? 1 : -1)));
                applyValue(s, s.frameValue(k));
            } else
            applyValue(s, curVal(s) + (d > 0 ? 1 : -1) * s.stepV() * (hasShiftDown() ? 5 : 1));
            return true;
        }
        setPage(page + (d > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        if (!query.isEmpty() && (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE)) {
            query = key == InputConstants.KEY_ESCAPE ? "" : query.substring(0, query.length() - 1);
            refilter();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE) {
            if (back()) return true;
            if (key == InputConstants.KEY_BACKSPACE) return true;
        }
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            int i = key - InputConstants.KEY_1;
            if (i < slots().size()) { hovered = i; activate(); }
            return true;
        }
        if (key == InputConstants.KEY_TAB) { setPage(page + (hasShiftDown() ? -1 : 1)); return true; }
        if (filtered == null && !holdActive && !cfg.holdToOpen && CpmWheel.KEY_WHEEL.matches(key, sc) && System.currentTimeMillis() - openedAt > 150) { onClose(); return true; }
        return super.keyPressed(key, sc, mod);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    /** typing letters filters every slot of every page */
    @Override
    public boolean charTyped(char c, int mod) {
        if (!Character.isLetter(c) && !(c == ' ' && !query.isEmpty())) return super.charTyped(c, mod);
        InputConstants.Key k = CpmWheel.KEY_WHEEL.getKey();
        if (query.isEmpty() && k.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), k.getValue()))
            return true; // key repeat of the (held) wheel key, not typing
        if (query.length() >= 24) return true;
        holdActive = false;
        query += c;
        refilter();
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
