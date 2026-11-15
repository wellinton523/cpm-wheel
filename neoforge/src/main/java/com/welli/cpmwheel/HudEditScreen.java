package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.5.8 HUD edit mode: drag every HUD element with the mouse, snap to edges / centre / grid (Shift = free),
 * mouse wheel = size, right click = reset that element. Positions are saved relative to the nearest screen anchor.
 */
public class HudEditScreen extends Screen {
    private static final int GRID = 8, SNAP = 6, MARGIN = 4;
    private final Screen parent;
    private WheelConfig cfg;
    private String drag = null;
    private int offX, offY;

    public HudEditScreen(Screen parent) { super(Component.translatable("cpmwheel.hudedit.title")); this.parent = parent; }

    @Override
    protected void init() {
        cfg = WheelConfig.get();
        Hud.editing = true;
        int cx = width / 2, y = height / 2 - 32;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hudedit.snap", cfg.hudSnap ? "ON" : "OFF"), b -> { cfg.hudSnap = !cfg.hudSnap; rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.hudedit.snap.help"))).bounds(cx - 60, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hudedit.resetall"), b -> {
            for (String el : Hud.ELEMENTS) cfg.hudPos.put(el, Hud.defaultPos(el, null)); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.hudedit.resetall.help"))).bounds(cx - 60, y + 22, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 60, y + 44, 120, 20).build());
    }

    private String at(double mx, double my) {
        for (String el : Hud.ELEMENTS) {
            int[] b = Hud.BOUNDS.get(el);
            if (b != null && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return el;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        String el = at(mx, my);
        if (el == null) return false;
        if (btn == 1) { cfg.hudPos.put(el, Hud.defaultPos(el, null)); return true; }
        int[] b = Hud.BOUNDS.get(el);
        drag = el; offX = (int) mx - b[0]; offY = (int) my - b[1];
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (drag == null) return super.mouseDragged(mx, my, btn, dx, dy);
        int[] b = Hud.BOUNDS.get(drag);
        if (b == null) return true;
        int w = b[2], h = b[3];
        int x = (int) mx - offX, y = (int) my - offY;
        if (cfg.hudSnap && !hasShiftDown()) { x = snap(x, w, width); y = snap(y, h, height); }
        x = Math.max(0, Math.min(width - w, x)); y = Math.max(0, Math.min(height - h, y));
        Hud.store(Hud.pos(cfg, drag), x, y, w, h, width, height);
        b[0] = x; b[1] = y;
        return true;
    }

    /** edges first, then the screen centre, then the grid */
    private static int snap(int v, int size, int screen) {
        if (Math.abs(v - MARGIN) <= SNAP) return MARGIN;
        if (Math.abs(v + size - (screen - MARGIN)) <= SNAP) return screen - MARGIN - size;
        if (Math.abs(v + size / 2 - screen / 2) <= SNAP) return screen / 2 - size / 2;
        return Math.round(v / (float) GRID) * GRID;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (drag != null) { drag = null; return true; }
        return super.mouseReleased(mx, my, btn);
    }

    public boolean mouseScrolled(double mx, double my, double d) { return scroll(mx, my, d); }
    public boolean mouseScrolled(double mx, double my, double sx, double sy) { return scroll(mx, my, sy); }
    private boolean scroll(double mx, double my, double d) {
        String el = at(mx, my);
        if (el == null) return false;
        WheelConfig.HudPos p = Hud.pos(cfg, el);
        p.scale = Math.max(50, Math.min(300, p.scale + (d > 0 ? 5 : -5)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (cfg.hudSnap) { // faint grid
            for (int x = 0; x < width; x += GRID * 4) g.fill(x, 0, x + 1, height, 0x182EE6FF);
            for (int y = 0; y < height; y += GRID * 4) g.fill(0, y, width, y + 1, 0x182EE6FF);
            g.fill(width / 2, 0, width / 2 + 1, height, 0x302EE6FF);
            g.fill(0, height / 2, width, height / 2 + 1, 0x302EE6FF);
        }
        String hov = drag != null ? drag : at(mx, my);
        for (String el : Hud.ELEMENTS) {
            int[] b = Hud.BOUNDS.get(el);
            if (b == null) continue;
            int c = el.equals(hov) ? 0xFFFFD23B : 0xFF2EE6FF;
            g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], c);
            g.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, c);
            g.fill(b[0] - 1, b[1], b[0], b[1] + b[3], c);
            g.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], c);
            String lbl = Component.translatable("cpmwheel.hudedit.el." + el).getString() + "  " + Hud.pos(cfg, el).scale + "%";
            int ly = b[1] > 12 ? b[1] - 11 : b[1] + b[3] + 2;
            g.drawString(font, lbl, b[0], ly, c);
        }
        g.drawCenteredString(font, title, width / 2, height / 2 - 58, 0x2EE6FF);
        g.drawCenteredString(font, Component.translatable("cpmwheel.hudedit.help"), width / 2, height / 2 - 46, 0xC8D6E0);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { Hud.editing = false; cfg.save(); Hud.invalidate(); minecraft.setScreen(parent); }
    @Override public void removed() { Hud.editing = false; super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
