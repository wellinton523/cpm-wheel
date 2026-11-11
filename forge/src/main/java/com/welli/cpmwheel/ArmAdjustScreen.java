package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Ajuste do braço": first-person arm position/rotation/scale for main and off hand (works with hand animations off). */
public class ArmAdjustScreen extends Screen {
    private static final String[] KEYS = {"x", "y", "z", "rx", "ry", "rz", "scale"};
    private static final int[] MIN = {-60, -60, -60, -90, -90, -90, -60}, MAX = {60, 60, 60, 90, 90, 90, 100}, STEP = {1, 1, 1, 1, 1, 1, 5};
    private static boolean off = false;
    private final Screen parent;
    private final WheelConfig cfg;
    public ArmAdjustScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.arm.title")); this.parent = parent; this.cfg = cfg; }

    private String key() { return off ? "off" : "main"; }
    private int[] cur() { return cfg.haArm.computeIfAbsent(key(), k -> new int[7]); }

    @Override
    protected void init() {
        int x = 8, y = 26;
        addRenderableWidget(Button.builder(Component.translatable(off ? "cpmwheel.arm.off" : "cpmwheel.arm.main"), b -> { off = !off; rebuildWidgets(); }).bounds(x, y, 150, 20).build());
        y += 24;
        int[] v = cfg.haArm.getOrDefault(key(), new int[7]);
        for (int i = 0; i < 7; i++) {
            final int idx = i, min = MIN[i], max = MAX[i], step = STEP[i];
            String k = "cpmwheel.arm." + KEYS[i];
            addRenderableWidget(new AbstractSliderButton(x, y, 150, 20, Component.translatable(k, v[i]), (v[i] - min) / (double) (max - min)) {
                int val = v[idx];
                @Override protected void updateMessage() { setMessage(Component.translatable(k, val)); }
                @Override protected void applyValue() { val = min + (int) Math.round(value * (max - min) / step) * step; cur()[idx] = val; HandAnim.load(cfg); }
            });
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.adj.reset"), b -> { cfg.haArm.remove(key()); HandAnim.load(cfg); rebuildWidgets(); }).bounds(x, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, 162, height, 0x60050A12); // backdrop only behind the controls: the hand stays visible
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { cfg.fixup(); HandAnim.load(cfg); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
