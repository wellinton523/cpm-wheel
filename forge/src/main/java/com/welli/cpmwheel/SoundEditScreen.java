package com.welli.cpmwheel;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.6.0: sound of one wheel button or trigger rule: activate sound, optional restore sound, volume, pitch, preview. */
public class SoundEditScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final Component what;
    private final WheelConfig.SoundSpec spec;
    private final Consumer<WheelConfig.SoundSpec> save;
    private final boolean rule;

    /** @param save receives the spec (null when cleared) */
    public SoundEditScreen(Screen parent, WheelConfig cfg, Component what, WheelConfig.SoundSpec current, boolean rule, Consumer<WheelConfig.SoundSpec> save) {
        super(Component.translatable("cpmwheel.snd.title")); this.parent = parent; this.cfg = cfg; this.what = what; this.rule = rule; this.save = save;
        spec = new WheelConfig.SoundSpec();
        if (current != null) { spec.id = current.id; spec.endId = current.endId; spec.vol = current.vol; spec.pitch = current.pitch; }
    }

    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }
    private static String name(String id) { return id == null || id.isBlank() ? "-" : id; }

    @Override
    protected void init() {
        int w = 220, x = width / 2 - w / 2, y = 44;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.snd.global", cfg.customSounds ? "ON" : "OFF"), b -> { cfg.customSounds = !cfg.customSounds; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.snd.global.help")).bounds(x, y, w, 20).build()); y += 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.snd.on", font.plainSubstrByWidth(name(spec.id), 130)),
                b -> minecraft.setScreen(new SoundPickerScreen(this, spec.id, spec.vol, spec.pitch, v -> spec.id = v)))
                .tooltip(tip(rule ? "cpmwheel.snd.on.rule.help" : "cpmwheel.snd.on.help")).bounds(x, y, w - 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> Sounds.play(spec.id, spec.vol, spec.pitch)).bounds(x + w - 42, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { spec.id = ""; rebuildWidgets(); }).bounds(x + w - 20, y, 20, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable(rule ? "cpmwheel.snd.end.rule" : "cpmwheel.snd.end", font.plainSubstrByWidth(name(spec.endId), 110)),
                b -> minecraft.setScreen(new SoundPickerScreen(this, spec.endId, spec.vol, spec.pitch, v -> spec.endId = v)))
                .tooltip(tip(rule ? "cpmwheel.snd.end.rule.help" : "cpmwheel.snd.end.help")).bounds(x, y, w - 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> Sounds.play(spec.endId, spec.vol, spec.pitch)).bounds(x + w - 42, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { spec.endId = ""; rebuildWidgets(); }).bounds(x + w - 20, y, 20, 20).build()); y += 26;
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.translatable("cpmwheel.snd.vol", spec.vol), spec.vol / 200.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.snd.vol", spec.vol)); }
            @Override protected void applyValue() { spec.vol = (int) Math.round(value * 40) * 5; }
        }); y += 22;
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.translatable("cpmwheel.snd.pitch", spec.pitch), (spec.pitch - 50) / 150.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.snd.pitch", spec.pitch)); }
            @Override protected void applyValue() { spec.pitch = 50 + (int) Math.round(value * 30) * 5; }
        }); y += 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.snd.preview"), b -> Sounds.play(spec.id.isBlank() ? spec.endId : spec.id, spec.vol, spec.pitch)).bounds(x, y, w, 20).build()); y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(x + 112, y, 108, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 12, 0x2EE6FF);
        g.drawCenteredString(font, what, width / 2, 26, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { save.accept(spec.empty() ? null : spec); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
