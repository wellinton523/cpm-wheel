package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.3 "Piscar: suavização": close / hold / open times, curve, double blink and extra layers of one blink generator. */
public class BlinkScreen extends MiniScreen {
    private final Live2dMath.Gen g;

    public BlinkScreen(Screen parent, WheelConfig cfg, Live2dMath.Gen g) { super("cpmwheel.blink.title", parent, cfg); this.g = g; }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int[] t = Live2dMath.blinkTimes(g);
        if (g.closeMs < 0 || g.holdMs < 0 || g.openMs < 0) { g.closeMs = t[0]; g.holdMs = t[1]; g.openMs = t[2]; }
        int x = 8, y = 26, w = 170;
        boolean blink = "blink".equals(g.kind);
        if (blink) {
        slider(x, y, w, "cpmwheel.blink.close", 0, 1000, 10, () -> g.closeMs, v -> g.closeMs = v, " ms", "cpmwheel.blink.times.help"); y += 22;
        slider(x, y, w, "cpmwheel.blink.hold", 0, 1000, 10, () -> g.holdMs, v -> g.holdMs = v, " ms", "cpmwheel.blink.times.help"); y += 22;
        slider(x, y, w, "cpmwheel.blink.open", 0, 1000, 10, () -> g.openMs, v -> g.openMs = v, " ms", "cpmwheel.blink.times.help"); y += 22;
        btn("cpmwheel.blink.curve", b -> { int i = java.util.Arrays.asList(Live2dMath.CURVES).indexOf(Live2dMath.curve(g.curve)); g.curve = Live2dMath.CURVES[(i + 1) % Live2dMath.CURVES.length]; rebuildWidgets(); },
                x, y, w, "cpmwheel.blink.curve.help", Component.translatable("cpmwheel.blink.curve." + Live2dMath.curve(g.curve))); y += 22;
        slider(x, y, w, "cpmwheel.blink.double", 0, 100, 5, () -> g.doubleChance, v -> g.doubleChance = v, "%", "cpmwheel.blink.double.help"); y += 22;
        }
        // extra layers moved together with the main one
        int x2 = blink ? x + w + 8 : x; y = 26;
        if (g.more == null) g.more = new java.util.ArrayList<>();
        for (int i = 0; i < g.more.size(); i++) {
            final int k = i;
            btn(Component.literal(font.plainSubstrByWidth(g.more.get(i), w - 30)), b -> minecraft.setScreen(new ValueLayerPickerScreen(this, cfg, g.more.get(k), v -> { g.more.set(k, v); Live2d.reset(); })), x2, y, w - 24, 20, "cpmwheel.blink.more.help");
            btn(Component.literal("\u2716"), b -> { g.more.remove(k); Live2d.reset(); rebuildWidgets(); }, x2 + w - 22, y, 22, 20, null);
            y += 22;
        }
        if (g.more.size() < 4) btn("cpmwheel.blink.more.add", b -> minecraft.setScreen(new ValueLayerPickerScreen(this, cfg, "", v -> { if (v != null && !v.isBlank() && !v.equals(g.anim) && !g.more.contains(v)) g.more.add(v); Live2d.reset(); })), x2, y, w, "cpmwheel.blink.more.help");
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
        btn(Component.translatable("cpmwheel.bctx.open"), b -> minecraft.setScreen(new BlinkContextScreen(this, cfg)), width - 158, height - 26, 150, 20, "cpmwheel.bctx.help"); // 1.9.0
    }

    @Override
    public void render(GuiGraphics gr, int mx, int my, float pt) {
        super.render(gr, mx, my, pt);
        if (!"blink".equals(g.kind)) return;
        // curve preview: eyelid level over one blink
        int[] t = Live2dMath.blinkTimes(g); int total = Math.max(1, t[0] + t[1] + t[2]);
        int gx = 8, gy = height - 90, gw = 170, gh = 50;
        gr.fill(gx, gy, gx + gw, gy + gh, 0x60000000);
        for (int i = 0; i < gw; i++) {
            float lv = Live2dMath.blinkLevel(g, i / (float) (gw - 1) * total);
            if (lv < 0) lv = 0;
            int yy = gy + gh - 1 - Math.round(lv * (gh - 2));
            gr.fill(gx + i, yy, gx + i + 1, yy + 1, 0xFF2EE6FF);
        }
        for (int s = 0; s <= total; s += 50) { int xx = gx + Math.round(s / (float) total * (gw - 1)); gr.fill(xx, gy + gh, xx + 1, gy + gh + 2, 0x80FFFFFF); }
        gr.drawString(font, Component.translatable("cpmwheel.blink.graph", total), gx, gy - 11, 0xCFE8F0);
        if (total < 150) gr.drawString(font, Component.translatable("cpmwheel.blink.short"), gx + gw + 8, gy + 10, 0xFFD27F);
    }
}
