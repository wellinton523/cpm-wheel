package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** HUD options: active-items list (left) and trigger notifications (right); "Mover HUD…" opens the drag edit mode. */
public class HudScreen extends Screen {
    private final Screen parent;
    private WheelConfig cfg;

    public HudScreen(Screen parent) { super(Component.translatable("cpmwheel.hud.title")); this.parent = parent; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    @Override
    protected void init() {
        cfg = WheelConfig.get();
        int x = width / 2 - 164, x2 = width / 2 + 4, y = 40;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.move"), b -> { cfg.save(); minecraft.setScreen(new HudEditScreen(this)); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.hud.move.help"))).bounds(width / 2 - 80, 30, 160, 20).build());
        y = 56;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.hud.enabled", onoff(cfg.hudEnabled)), b -> { cfg.hudEnabled = !cfg.hudEnabled; rebuildWidgets(); })
                .bounds(x, y, 160, 20).build()); y += 24;
        WheelConfig.HudPos lp = Hud.pos(cfg, "list");
        addRenderableWidget(new AbstractSliderButton(x, y, 160, 20, Component.translatable("cpmwheel.hud.scale", lp.scale), (lp.scale - 50) / 250.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.hud.scale", lp.scale)); }
            @Override protected void applyValue() { lp.scale = 50 + (int) Math.round(value * 250 / 5) * 5; }
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
            cfg.hudShowEmptyGroups = !cfg.hudShowEmptyGroups; Hud.invalidate(); rebuildWidgets(); }).bounds(x, y, 160, 20).build()); y += 24;
        int bottom = y;
        // trigger notifications
        y = 56;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.toast.on", onoff(cfg.toastOn)), b -> { cfg.toastOn = !cfg.toastOn; rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.toast.on.help"))).bounds(x2, y, 160, 20).build()); y += 24;
        addRenderableWidget(new AbstractSliderButton(x2, y, 160, 20, Component.translatable("cpmwheel.toast.secs", cfg.toastSecs), (cfg.toastSecs - 1) / 14.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.toast.secs", cfg.toastSecs)); }
            @Override protected void applyValue() { cfg.toastSecs = 1 + (int) Math.round(value * 14); }
        }); y += 24;
        WheelConfig.HudPos tp = Hud.pos(cfg, "toasts");
        addRenderableWidget(new AbstractSliderButton(x2, y, 160, 20, Component.translatable("cpmwheel.toast.size", tp.scale), (tp.scale - 50) / 250.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.toast.size", tp.scale)); }
            @Override protected void applyValue() { tp.scale = 50 + (int) Math.round(value * 250 / 5) * 5; }
        }); y += 24;
        addRenderableWidget(new AbstractSliderButton(x2, y, 160, 20, Component.translatable("cpmwheel.toast.opacity", cfg.toastOpacity), cfg.toastOpacity / 100.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.toast.opacity", cfg.toastOpacity)); }
            @Override protected void applyValue() { cfg.toastOpacity = (int) Math.round(value * 20) * 5; }
        }); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.toast.mini", onoff(cfg.toastMini)), b -> { cfg.toastMini = !cfg.toastMini; rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.toast.mini.help"))).bounds(x2, y, 160, 20).build()); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.toast.preview"), b -> {
            TriggerToasts.start("_preview", "minecraft:poison", Component.translatable("cpmwheel.toast.sample.title").getString(),
                    Component.translatable("cpmwheel.toast.sample.text").getString(), 0x3BFF9D); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.toast.preview.help"))).bounds(x2, y, 160, 20).build()); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 80, Math.max(bottom, y) + 6, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x90050A12);
        g.drawCenteredString(font, title, width / 2, 12, 0x2EE6FF);
        g.drawString(font, Component.translatable("cpmwheel.hud.col.list"), width / 2 - 164, 46, 0x9FB8C8);
        g.drawString(font, Component.translatable("cpmwheel.hud.col.toast"), width / 2 + 4, 46, 0x9FB8C8);
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
