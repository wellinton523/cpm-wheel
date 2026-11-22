package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.9.0 "Piscar: contexto": blink rate multipliers for daylight, underwater, rain / sandstorm and aiming. OFF by default. */
public class BlinkContextScreen extends MiniScreen {
    public BlinkContextScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.bctx.title", parent, cfg); }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int x = 8, y = 26, w = 200;
        btn("cpmwheel.bctx.on", b -> { cfg.l2dCtxOn = !cfg.l2dCtxOn; rebuildWidgets(); }, x, y, w, "cpmwheel.bctx.help", onoff(cfg.l2dCtxOn)); y += 26;
        if (cfg.l2dCtxOn) {
            slider(x, y, w, "cpmwheel.bctx.bright", 10, 400, 5, () -> cfg.l2dCtxBright, v -> cfg.l2dCtxBright = v, "%", "cpmwheel.bctx.bright.help"); y += 22;
            slider(x, y, w, "cpmwheel.bctx.water", 10, 400, 5, () -> cfg.l2dCtxWater, v -> cfg.l2dCtxWater = v, "%", "cpmwheel.bctx.water.help"); y += 22;
            slider(x, y, w, "cpmwheel.bctx.rain", 10, 400, 5, () -> cfg.l2dCtxRain, v -> cfg.l2dCtxRain = v, "%", "cpmwheel.bctx.rain.help"); y += 22;
            slider(x, y, w, "cpmwheel.bctx.aim", 5, 200, 5, () -> cfg.l2dCtxAim, v -> cfg.l2dCtxAim = v, "%", "cpmwheel.bctx.aim.help"); y += 22;
        }
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int y = 26;
        String[] lines = Component.translatable("cpmwheel.bctx.note").getString().split("\n");
        for (String l : lines) { for (var s : font.split(Component.literal(l), Math.max(80, width - 230))) { g.drawString(font, s, 220, y, 0x9FB8C8); y += 10; } }
        g.drawString(font, Component.translatable("cpmwheel.bctx.now", String.format("%.2f", Live2d.blinkRateNow())), 220, y + 6, 0xCFE8F0);
    }
}
