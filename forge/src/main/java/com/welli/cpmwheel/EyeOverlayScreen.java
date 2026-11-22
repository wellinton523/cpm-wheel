package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.4 "Overlay dos olhos": switch, size, opacity, colour, area outline, ghost and label of the eye-aim overlay. */
public class EyeOverlayScreen extends MiniScreen {
    static final int[] COLORS = {0x2EE6FF, 0xFFFFFF, 0x6CFF8F, 0xFFD27F, 0xFF7FD8, 0xFF6060, 0xB48CFF};

    public EyeOverlayScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.eyeov.title", parent, cfg); }

    @Override protected int backdrop() { return 0x60050A12; }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int w = 160, x = 8, y = 26;
        btn("cpmwheel.eyeov.on", b -> { cfg.l2dEyeOv = !cfg.l2dEyeOv; rebuildWidgets(); }, x, y, w, "cpmwheel.eyeov.help", onoff(cfg.l2dEyeOv)); y += 22;
        slider(x, y, w, "cpmwheel.eyeov.size", 3, 20, 1, () -> cfg.l2dEyeOvSize, v -> cfg.l2dEyeOvSize = v, " px", "cpmwheel.eyeov.size.help"); y += 22;
        slider(x, y, w, "cpmwheel.eyeov.alpha", 10, 100, 5, () -> cfg.l2dEyeOvAlpha, v -> cfg.l2dEyeOvAlpha = v, "%", null); y += 22;
        btn("cpmwheel.eyeov.color", b -> { int i = 0; for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == cfg.l2dEyeOvColor) i = k + 1; cfg.l2dEyeOvColor = COLORS[i % COLORS.length]; rebuildWidgets(); },
                x, y, w, "cpmwheel.eyeov.color.help", String.format("#%06X", cfg.l2dEyeOvColor & 0xFFFFFF)); y += 22;
        slider(x, y, w, "cpmwheel.eyeov.r", 0, 255, 1, () -> (cfg.l2dEyeOvColor >> 16) & 255, v -> cfg.l2dEyeOvColor = (cfg.l2dEyeOvColor & 0x00FFFF) | (v << 16), "", "cpmwheel.eyeov.color.help"); y += 22;
        slider(x, y, w, "cpmwheel.eyeov.g", 0, 255, 1, () -> (cfg.l2dEyeOvColor >> 8) & 255, v -> cfg.l2dEyeOvColor = (cfg.l2dEyeOvColor & 0xFF00FF) | (v << 8), "", "cpmwheel.eyeov.color.help"); y += 22;
        slider(x, y, w, "cpmwheel.eyeov.b", 0, 255, 1, () -> cfg.l2dEyeOvColor & 255, v -> cfg.l2dEyeOvColor = (cfg.l2dEyeOvColor & 0xFFFF00) | v, "", "cpmwheel.eyeov.color.help"); y += 22;
        int x2 = x + w + 6; y = 26;
        btn("cpmwheel.eyeov.area", b -> { cfg.l2dEyeOvArea = !cfg.l2dEyeOvArea; rebuildWidgets(); }, x2, y, w, "cpmwheel.eyeov.area.help", onoff(cfg.l2dEyeOvArea)); y += 22;
        btn("cpmwheel.eyeov.ghost", b -> { cfg.l2dEyeOvGhost = !cfg.l2dEyeOvGhost; rebuildWidgets(); }, x2, y, w, "cpmwheel.eyeov.ghost.help", onoff(cfg.l2dEyeOvGhost)); y += 22;
        btn("cpmwheel.eyeov.label", b -> { cfg.l2dEyeOvLabel = !cfg.l2dEyeOvLabel; rebuildWidgets(); }, x2, y, w, "cpmwheel.eyeov.label.help", onoff(cfg.l2dEyeOvLabel)); y += 22;
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        EyeOverlay.draw(g, cfg, width, height, true);
        g.drawString(font, Component.translatable("cpmwheel.eyeov.key"), 166, height - 20, 0x9FB8C4);
        if (!cfg.l2dOn || !cfg.l2dLookOn) g.drawString(font, Component.translatable("cpmwheel.eyeov.needlook"), 166, height - 32, 0xFF6060);
    }
}
