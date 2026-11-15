package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.0: extra settings of one link (soft angle limit). */
public class Live2dLinkScreen extends MiniScreen {
    private final Live2dMath.Link l;
    public Live2dLinkScreen(Screen parent, WheelConfig cfg, Live2dMath.Link l) { super("cpmwheel.l2d.more.title", parent, cfg); this.l = l; }

    @Override
    protected void init() {
        int x = 8, y = 26;
        btn("cpmwheel.l2d.lim.on", b -> { l.softLim = !l.softLim; rebuildWidgets(); }, x, y, 200, "cpmwheel.l2d.lim.help", onoff(l.softLim)); y += 22;
        slider(x, y, 200, "cpmwheel.l2d.lim.lo", -180, 180, 5, () -> l.limLo, v -> l.limLo = v, "\u00B0", "cpmwheel.l2d.lim.help"); y += 22;
        slider(x, y, 200, "cpmwheel.l2d.lim.hi", -180, 180, 5, () -> l.limHi, v -> l.limHi = v, "\u00B0", "cpmwheel.l2d.lim.help"); y += 22;
        btn(Component.translatable("gui.done"), b -> onClose(), x, height - 26, 200, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int y = 26 + 22 * 3 + 6;
        for (var line : font.split(Component.translatable("cpmwheel.l2d.lim.text"), Math.max(200, width - 16))) { g.drawString(font, line, 8, y, 0xCFE8F0); y += 10; }
    }
}
