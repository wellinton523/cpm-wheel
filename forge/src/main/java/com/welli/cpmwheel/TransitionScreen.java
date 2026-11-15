package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.6.0 "Transição": an animation played for a while before the rule switches (and optionally before it restores). */
public class TransitionScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final WheelConfig.EffectRule rule;

    public TransitionScreen(Screen parent, WheelConfig cfg, WheelConfig.EffectRule rule) {
        super(Component.translatable("cpmwheel.trans.title")); this.parent = parent; this.cfg = cfg; this.rule = rule;
    }
    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    @Override
    protected void init() {
        int w = 240, x = width / 2 - w / 2, y = 44;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.transon.long", cfg.trigTransOn ? "ON" : "OFF"), b -> { cfg.trigTransOn = !cfg.trigTransOn; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.eff.transon.help")).bounds(x, y, w, 20).build()); y += 26;
        String a = rule.transAnim == null || rule.transAnim.isBlank() ? Component.translatable("cpmwheel.trans.none").getString() : rule.transAnim;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.trans.anim", font.plainSubstrByWidth(a, 150)),
                b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, v -> rule.transAnim = v)))
                .tooltip(tip("cpmwheel.trans.anim.help")).bounds(x, y, w - 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { rule.transAnim = ""; rebuildWidgets(); }).bounds(x + w - 20, y, 20, 20).build()); y += 22;
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, msg(rule.transMs), (rule.transMs - 100) / 9900.0) {
            @Override protected void updateMessage() { setMessage(msg(rule.transMs)); }
            @Override protected void applyValue() { rule.transMs = 100 + (int) Math.round(value * 99) * 100; }
        }).setTooltip(tip("cpmwheel.trans.ms.help")); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.trans.onend", rule.transOnEnd ? "ON" : "OFF"), b -> { rule.transOnEnd = !rule.transOnEnd; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.trans.onend.help")).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
    }
    private static Component msg(int ms) { return Component.translatable("cpmwheel.trans.ms", String.format(java.util.Locale.ROOT, "%.1f", ms / 1000f)); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 12, Style.accent(cfg));
        g.drawCenteredString(font, Component.literal(EffectTriggers.ruleLabel(rule)), width / 2, 26, 0xFFFFFF);
        int y = 44 + 26 + 22 * 3 + 8;
        for (var l : font.split(Component.translatable("cpmwheel.trans.explain"), Math.min(320, width - 16)))
        { g.drawCenteredString(font, l, width / 2, y, 0x9FB8C4); y += 10; }
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
