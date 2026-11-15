package com.welli.cpmwheel;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.6.0: choose a condition type; every type with its explanation next to it. */
public class CondTypeScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final Consumer<String> pick;

    public CondTypeScreen(Screen parent, WheelConfig cfg, Consumer<String> pick) {
        super(Component.translatable("cpmwheel.cond.type.title")); this.parent = parent; this.cfg = cfg; this.pick = pick;
    }

    @Override
    protected void init() {
        int cols = width >= 520 ? 2 : 1, cw = Math.min(250, (width - 16 - (cols - 1) * 8) / cols);
        String[] ts = TriggerLogic.TYPES;
        int rows = (ts.length + cols - 1) / cols;
        int x0 = width / 2 - (cols * cw + (cols - 1) * 8) / 2, y0 = 30, rowH = Math.max(22, Math.min(30, (height - 40) / rows));
        for (int i = 0; i < ts.length; i++) {
            String t = ts[i];
            int col = i % cols, row = i / cols;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.t." + t), b -> { pick.accept(t); minecraft.setScreen(parent); })
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cpmwheel.cond.t." + t + ".help")))
                    .bounds(x0 + col * (cw + 8), y0 + row * rowH, 90, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 66, 4, 60, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 12, Style.accent(cfg));
        int cols = width >= 520 ? 2 : 1, cw = Math.min(250, (width - 16 - (cols - 1) * 8) / cols);
        String[] ts = TriggerLogic.TYPES;
        int rows = (ts.length + cols - 1) / cols;
        int x0 = width / 2 - (cols * cw + (cols - 1) * 8) / 2, y0 = 30, rowH = Math.max(22, Math.min(30, (height - 40) / rows));
        for (int i = 0; i < ts.length; i++) {
            int col = i % cols, row = i / cols;
            var lines = font.split(Component.translatable("cpmwheel.cond.t." + ts[i] + ".short"), cw - 96);
            int ly = y0 + row * rowH + (lines.size() > 1 ? 1 : 6);
            if (rowH < 26) ly = y0 + row * rowH + (lines.size() > 1 ? 0 : 6);
            for (int k = 0; k < Math.min(2, lines.size()); k++) g.drawString(font, lines.get(k), x0 + col * (cw + 8) + 94, ly + k * 10, 0xA8C4D0);
        }
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
