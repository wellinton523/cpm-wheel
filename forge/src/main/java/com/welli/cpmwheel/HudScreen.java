package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** HUD options. */
public class HudScreen extends Screen {
    private final Screen parent;
    private WheelConfig cfg;

    public HudScreen(Screen parent) { super(Component.translatable("cpmwheel.hud.title")); this.parent = parent; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    @Override
    protected void init() {
        cfg = WheelConfig.get();
        int x = width / 2 - 80, y = 40;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.enabled", onoff(cfg.hudEnabled)), b -> { cfg.hudEnabled = !cfg.hudEnabled; rebuildWidgets(); })
                .bounds(x, y, 160, 20).build()); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.corner", Component.translatable("cpmwheel.hud.corner." + cfg.hudCorner)), b -> {
            String[] o = {"top_left", "top_right", "bottom_left", "bottom_right"};
            int i = java.util.Arrays.asList(o).indexOf(cfg.hudCorner);
            cfg.hudCorner = o[(i + 1) % o.length]; rebuildWidgets(); }).bounds(x, y, 160, 20).build()); y += 24;
        addRenderableWidget(new AbstractSliderButton(x, y, 160, 20, Component.translatable("cpmwheel.hud.scale", cfg.hudScale), (cfg.hudScale - 50) / 150.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.hud.scale", cfg.hudScale)); }
            @Override protected void applyValue() { cfg.hudScale = 50 + (int) Math.round(value * 150 / 5) * 5; }
        }); y += 24;
        addRenderableWidget(new AbstractSliderButton(x, y, 160, 20, Component.translatable("cpmwheel.hud.opacity", cfg.hudOpacity), cfg.hudOpacity / 100.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.hud.opacity", cfg.hudOpacity)); }
            @Override protected void applyValue() { cfg.hudOpacity = (int) Math.round(value * 20) * 5; }
        }); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.onlynondefault", onoff(cfg.hudOnlyNonDefault)), b -> {
            cfg.hudOnlyNonDefault = !cfg.hudOnlyNonDefault; Hud.invalidate(); rebuildWidgets(); }).bounds(x, y, 160, 20).build()); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.grouped", onoff(cfg.hudGrouped)), b -> {
            cfg.hudGrouped = !cfg.hudGrouped; Hud.invalidate(); rebuildWidgets(); }).bounds(x, y, 160, 20).build()); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.showempty", onoff(cfg.hudShowEmptyGroups)), b -> {
            cfg.hudShowEmptyGroups = !cfg.hudShowEmptyGroups; Hud.invalidate(); rebuildWidgets(); }).bounds(x, y, 160, 20).build()); y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, y, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0050A12);
        g.drawCenteredString(font, title, width / 2, 20, 0x2EE6FF);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("cpmwheel.hud.keyhint"), width / 2, height - 20, 0x7FA8B8);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void onClose() { cfg.save(); Hud.invalidate(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
