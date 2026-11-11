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
        if (openedAt == 0) { openedAt = System.currentTimeMillis(); holdActive = openedByKey && cfg.holdToOpen; }
        transitionAt = openedAt;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit"), b -> { endPreview(true); minecraft.setScreen(new EditScreen(this)); })
                .bounds(width - 84, height - 26, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(cfg.previewOnHover ? "cpmwheel.preview.on" : "cpmwheel.preview.off"), b -> {
            cfg.previewOnHover = !cfg.previewOnHover; if (!cfg.previewOnHover) endPreview(true); cfg.save();
            b.setMessage(Component.translatable(cfg.previewOnHover ? "cpmwheel.preview.on" : "cpmwheel.preview.off"));
        }).bounds(width - 214, height - 26, 126, 20).build());
    }

    private float radius() { return Math.min(width, height) * 0.36f; }

    private WheelConfig.Page cur() { return pages.get(page); }

    private List<WheelConfig.Slot> slots() { return path.isEmpty() ? cur().slots : path.get(path.size() - 1).children; }

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
        List<WheelConfig.Slot> sl = slots();
        int h = hovered;
        if (h < 0 || h >= sl.size() || sl.get(h).empty()) { endPreview(true); onClose(); return; }
        WheelConfig.Slot s = sl.get(h);
        if (s.isFolder()) { enter(s); return; }
        if (isValue(s)) {
            endPreview(true);
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
        endPreview(false); // keep preview state if it is the chosen one
        if (!alreadyApplied) WheelConfig.run(s);
        previewSlot = null;
        super.onClose();
    }

    // ---------- live preview ----------
    private static boolean previewable(WheelConfig.Slot s) {
        return s != null && !s.empty() && !s.isFolder() && !WheelConfig.RESET.equals(s.anim);
    }

    private static boolean isValue(WheelConfig.Slot s) { return previewable(s) && !"toggle".equals(s.effectiveMode()); }

    /** current value of a value slot (min if unknown) */
    private static int curVal(WheelConfig.Slot s) { int v = CpmBridge.playing(s.anim); return v < 0 ? s.minV() : v; }

    private static float pct(WheelConfig.Slot s, int v) { int a = s.minV(), b = s.maxV(); return b <= a ? 0 : Math.max(0, Math.min(1, (v - a) / (float) (b - a))); }

    private void applyValue(WheelConfig.Slot s, int v) {
        v = Math.max(s.minV(), Math.min(s.maxV(), v));
        CpmBridge.play(s.anim, v);
    }

    private void updatePreview(WheelConfig.Slot s) {
        if (!cfg.previewOnHover) return;
        if (s == previewSlot) return;
        endPreview(true);
        if (!previewable(s) || isValue(s)) return;
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

        g.fillGradient(0, 0, width, height, fade(0x90050A12, open), fade(0xB0050A12, open));
        float cx = width / 2f, cy = height / 2f, R0 = radius();
        float scale = 0.8f + 0.2f * open;
        if (transitionDir != 0 && trans < 1) scale *= transitionDir > 0 ? 0.85f + 0.15f * trans : 1.15f - 0.15f * trans;
        float R = R0 * scale, r0 = R * 0.38f;
        List<WheelConfig.Slot> slots = slots();
        int n = Math.max(1, slots.size());
        double seg = Math.PI * 2 / n;
        if (hoverAmt.length != slots.size()) hoverAmt = new float[slots.size()];

        double dx = mx - cx, dy = my - cy;
        hovered = -1;
        if (dx * dx + dy * dy > r0 * r0 * 0.6) {
            double ang = Math.atan2(dx, -dy);
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.floor((ang + seg / 2) / seg) % n;
        }
        updatePreview(hovered >= 0 && hovered < slots.size() ? slots.get(hovered) : null);

        for (int i = 0; i < slots.size(); i++) {
            WheelConfig.Slot s = slots.get(i);
            boolean hv = i == hovered;
            hoverAmt[i] = anim ? hoverAmt[i] + ((hv ? 1f : 0f) - hoverAmt[i]) * Math.min(1f, dt * 18f) : (hv ? 1f : 0f);
            float h = hoverAmt[i];
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            boolean on = previewable(s) && CpmBridge.playing(s.anim) > 0;
            float ro = R + 7 * h;
            Draw.sector(g, cx, cy, r0, ro, a0, a1, fade(h > 0.5f ? 0xC0102838 : 0xA00A1420, alpha));
            Draw.sector(g, cx, cy, ro - 3, ro, a0, a1, Draw.argb(s.empty() ? 0x334455 : s.color, Math.round((170 + 85 * h) * alpha)));
            if (on) Draw.sector(g, cx, cy, r0, r0 + 3, a0, a1, Draw.argb(s.color, Math.round(255 * alpha)));
            if (isValue(s) && alpha > 0.5f) {
                float p = pct(s, curVal(s));
                Draw.sector(g, cx, cy, ro + 2, ro + 5, a0, a1, Draw.argb(0x1A2A3A, Math.round(220 * alpha)));
                Draw.sector(g, cx, cy, ro + 2, ro + 5, a0, a0 + (a1 - a0) * p, Draw.argb(s.color, Math.round(255 * alpha)));
            }
            if (s.isFolder()) Draw.sector(g, cx, cy, ro - 7, ro - 5, a0 + 0.08, a1 - 0.08, Draw.argb(s.color, Math.round(200 * alpha)));

            double mid = i * seg;
            float rr = (r0 + ro) / 2;
            float tx = cx + (float) Math.sin(mid) * rr, ty = cy - (float) Math.cos(mid) * rr;
            if (alpha < 0.35f) continue;
            int textY = (int) ty - 4;
            if (Icons.has(s.icon)) { Icons.draw(g, s.icon, (int) tx, (int) ty - 6, 16); textY = (int) ty + 4; }
            String label = s.empty() ? "—" : (s.isFolder() ? "\u25B8 " : "") + s.display();
            label = font.plainSubstrByWidth(label, (int) (R * 0.75f));
            g.drawCenteredString(font, label, (int) tx, textY, s.empty() ? 0x55667A : (hv ? 0xFFFFFF : 0xC8E8F0));
            if (s.favorite) g.drawString(font, "\u2605", (int) tx + font.width(label) / 2 + 2, textY, 0xFFD23B);
        }

        Draw.sector(g, cx, cy, 0, r0 - 4, 0, Math.PI * 2, fade(0xD0060C14, alpha));
        Draw.sector(g, cx, cy, r0 - 6, r0 - 4, 0, Math.PI * 2, fade(0xFF2EE6FF, alpha));
        WheelConfig.Slot hs = hovered >= 0 && hovered < slots.size() ? slots.get(hovered) : null;
        String center = hs != null && !hs.empty() ? hs.display() : (path.isEmpty() ? cur().name : path.get(path.size() - 1).display());

        if (cfg.livePreview && minecraft.player != null && alpha > 0.5f) {
            renderPlayer(g, cx, cy, r0, mx, my);
            if (hs != null && Icons.has(hs.icon)) { // small badge
                Draw.sector(g, cx + r0 * 0.55f, cy - r0 * 0.55f, 0, 11, 0, Math.PI * 2, 0xE0060C14);
                Icons.draw(g, hs.icon, (int) (cx + r0 * 0.55f), (int) (cy - r0 * 0.55f), 14);
            }
            g.drawCenteredString(font, font.plainSubstrByWidth(center, (int) (R * 2)), (int) cx, (int) (cy + R + 12), 0x2EE6FF);
            if (!path.isEmpty()) g.drawCenteredString(font, Component.translatable("cpmwheel.back"), (int) cx, (int) (cy + R + 24), 0x7FA8B8);
        } else {
            if (hs != null && Icons.has(hs.icon)) {
                int isz = (int) Math.min(40, r0 * 0.9f);
                Icons.draw(g, hs.icon, (int) cx, (int) cy - 12 - isz / 2, isz);
            }
            g.drawCenteredString(font, font.plainSubstrByWidth(center, (int) (r0 * 1.7f)), (int) cx, (int) cy - 8, 0x2EE6FF);
            g.drawCenteredString(font, path.isEmpty() ? (page + 1) + "/" + pages.size() : "\u2190", (int) cx, (int) cy + 4, 0x7FA8B8);
        }

        if (hs != null && isValue(hs)) {
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
            g.fill(tx, 8, tx + w, 22, i == page ? 0xE02EE6FF : 0xA0101C28);
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
    }

        private void renderPlayer(GuiGraphics g, float cx, float cy, float r0, int mx, int my) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, (int) cx, (int) (cy + r0 * 0.5f), (int) (r0 * 0.5f), cx - mx, cy - r0 * 0.3f - my, minecraft.player);
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
        boolean inCenter = Math.hypot(mx - width / 2f, my - height / 2f) < r0 * 0.78f;
        if (inCenter) { if (!back() && btn == 0) onClose(); return true; }
        if (btn == 0 && hovered >= 0) { activate(); return true; }
        if (btn == 1) {
            List<WheelConfig.Slot> sl = slots();
            if (hovered >= 0 && hovered < sl.size() && !sl.get(hovered).empty()) {
                WheelConfig.Slot s = sl.get(hovered);
                s.favorite = !s.favorite; cfg.save();
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
        page = Math.floorMod(p, pages.size());
        cfg.lastPage = page; cfg.save();
        transitionDir = 0; transitionAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        List<WheelConfig.Slot> sl = slots();
        if (hovered >= 0 && hovered < sl.size() && isValue(sl.get(hovered))) {
            WheelConfig.Slot s = sl.get(hovered);
            applyValue(s, curVal(s) + (d > 0 ? 1 : -1) * s.stepV() * (hasShiftDown() ? 5 : 1));
            return true;
        }
        setPage(page + (d > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
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
        if (!holdActive && !cfg.holdToOpen && CpmWheel.KEY_WHEEL.matches(key, sc) && System.currentTimeMillis() - openedAt > 150) { onClose(); return true; }
        return super.keyPressed(key, sc, mod);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
