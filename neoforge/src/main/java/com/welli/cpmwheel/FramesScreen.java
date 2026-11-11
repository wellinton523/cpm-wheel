package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editor for a "frames" slot: count, and per frame name / exact CPM value / color. */
public class FramesScreen extends Screen {
    private static final int ROW = 22;
    private final Screen parent;
    private final WheelConfig.Slot slot;
    private int scroll = 0;

    public FramesScreen(Screen parent, WheelConfig.Slot slot) {
        super(Component.translatable("cpmwheel.frames.title", slot.display()));
        this.parent = parent; this.slot = slot;
        slot.ensureFrames();
    }

    private int rows() { return Math.max(1, (height - 90) / ROW); }

    @Override
    protected void init() {
        slot.ensureFrames();
        int n = slot.frames.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, n - rows())));
        int x = Math.max(8, width / 2 - 160);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> { if (slot.frames.size() > 2) { slot.frames.remove(slot.frames.size() - 1); rebuildWidgets(); } })
                .bounds(x + 150, 24, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { if (slot.frames.size() < 64) { slot.frames.add(new WheelConfig.Frame()); rebuildWidgets(); } })
                .bounds(x + 172, 24, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.frames.auto"), b -> {
            for (WheelConfig.Frame f : slot.frames) f.value = -1; rebuildWidgets(); }).bounds(x + 196, 24, 124, 18).build());
        int y = 52;
        for (int r = 0; r < rows() && r + scroll < n; r++) {
            final int i = r + scroll;
            WheelConfig.Frame f = slot.frames.get(i);
            EditBox name = new EditBox(font, x + 24, y, 150, 16, Component.literal(""));
            name.setMaxLength(64);
            name.setValue(f.name == null ? "" : f.name);
            name.setHint(Component.translatable("cpmwheel.frames.name", i + 1));
            name.setResponder(v -> f.name = v);
            addRenderableWidget(name);
            EditBox val = new EditBox(font, x + 178, y, 46, 16, Component.literal(""));
            val.setValue(f.value < 0 ? "" : Integer.toString(f.value));
            val.setHint(Component.literal(Integer.toString(slot.frameValue(i))));
            val.setFilter(v -> v.matches("\\d{0,4}"));
            val.setResponder(v -> f.value = v.isEmpty() ? -1 : Integer.parseInt(v));
            addRenderableWidget(val);
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.color"), b -> {
                int k = -1; for (int j = 0; j < WheelConfig.PALETTE.length; j++) if (WheelConfig.PALETTE[j] == f.color) k = j;
                f.color = k == WheelConfig.PALETTE.length - 1 ? 0 : WheelConfig.PALETTE[k + 1]; // 0 = no swatch
            }).bounds(x + 228, y - 1, 44, 18).build());
            addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> CpmBridge.play(slot.anim, slot.frameValue(i)))
                    .bounds(x + 276, y - 1, 20, 18).build());
            y += ROW;
        }
        if (n > rows()) {
            addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> { scroll = Math.max(0, scroll - rows()); rebuildWidgets(); }).bounds(x + 300, 52, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> { scroll += rows(); rebuildWidgets(); }).bounds(x + 300, 74, 20, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xD0050A12);
        int x = Math.max(8, width / 2 - 160);
        g.drawString(font, title, x, 8, 0x2EE6FF);
        g.drawString(font, Component.translatable("cpmwheel.frames.count", slot.frames.size(), slot.minV(), slot.maxV()), x, 29, 0xC8E8F0);
        int cur = slot.frameIndexOf(CpmBridge.playing(slot.anim));
        int y = 52;
        for (int r = 0; r < rows() && r + scroll < slot.frames.size(); r++) {
            int i = r + scroll;
            WheelConfig.Frame f = slot.frames.get(i);
            g.drawString(font, (i + 1) + "", x, y + 4, i == cur ? 0x3BFF9D : 0x7FA8B8);
            if (f.color != 0) g.fill(x + 14, y + 2, x + 20, y + 14, 0xFF000000 | f.color);
            y += ROW;
        }
        g.drawString(font, Component.translatable("cpmwheel.frames.help"), x, height - 40, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double d) {
        int old = scroll;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(d), Math.max(0, slot.frames.size() - rows())));
        if (old != scroll) rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
