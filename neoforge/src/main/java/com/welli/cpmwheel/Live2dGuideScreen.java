package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** 1.7.0: how to set up a model in the CPM editor for "Física Live2D" (scrollable text). */
public class Live2dGuideScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int scroll = 0;

    public Live2dGuideScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.l2d.guide.title")); this.parent = parent; this.cfg = cfg; }

    @Override
    protected void init() {
        lines.clear();
        int w = Math.min(420, width - 24);
        for (String para : Component.translatable("cpmwheel.l2d.guide.text").getString().split("\n")) {
            if (para.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); continue; }
            lines.addAll(font.split(Component.literal(para), w));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
    }

    private int visible() { return Math.max(1, (height - 64) / 10); }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible()), scroll - (int) Math.signum(dy) * 3));
        return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 10, Style.accent(cfg));
        int w = Math.min(420, width - 24), x = width / 2 - w / 2, y = 28;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible()); i++) { g.drawString(font, lines.get(i), x, y, 0xDDEEF4); y += 10; }
        if (lines.size() > visible()) g.drawString(font, (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible()) + " / " + lines.size() + "  \u2195", x, height - 38, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
