package com.welli.cpmwheel;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.0: small base for the new option screens (dark backdrop, title, helpers; saves the config when closed). */
public abstract class MiniScreen extends Screen {
    protected final Screen parent;
    protected final WheelConfig cfg;

    protected MiniScreen(String titleKey, Screen parent, WheelConfig cfg) { super(Component.translatable(titleKey)); this.parent = parent; this.cfg = cfg; }

    /** 1.8.3: backdrop colour (the look-at screen uses a lighter one to see the area over the world) */
    protected int backdrop() { return 0xC0050A12; }

    static String onoff(boolean b) { return b ? "ON" : "OFF"; }
    static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    protected Button btn(Component label, Button.OnPress p, int x, int y, int w, int h, String tipKey) {
        Button.Builder b = Button.builder(label, p).bounds(x, y, w, h);
        if (tipKey != null) b.tooltip(tip(tipKey));
        return addRenderableWidget(b.build());
    }
    protected Button btn(String key, Button.OnPress p, int x, int y, int w, String tipKey, Object... args) {
        return btn(Component.translatable(key, args), p, x, y, w, 20, tipKey);
    }

    protected void slider(int x, int y, int w, String key, int min, int max, int step, IntSupplier get, IntConsumer set, String unit, String tipKey) {
        int cur = Math.max(min, Math.min(max, get.getAsInt()));
        AbstractSliderButton s = new AbstractSliderButton(x, y, w, 20, Component.translatable(key, cur + unit), (cur - min) / (double) (max - min)) {
            @Override protected void updateMessage() { setMessage(Component.translatable(key, get.getAsInt() + unit)); }
            @Override protected void applyValue() { set.accept(min + (int) Math.round(value * (max - min) / step) * step); }
        };
        if (tipKey != null) s.setTooltip(tip(tipKey));
        addRenderableWidget(s);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, backdrop());
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { cfg.fixup(); cfg.save(); Live2d.use(cfg); Actions.invalidate(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
