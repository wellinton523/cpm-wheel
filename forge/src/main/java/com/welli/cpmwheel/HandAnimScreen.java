package com.welli.cpmwheel;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Animações da mão": master toggle, per-feature toggles, intensity sliders. Edits the parent's config copy. */
public class HandAnimScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private int confY;
    public HandAnimScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.ha.title")); this.parent = parent; this.cfg = cfg; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    private void slider(int x, int y, String key, int val, IntConsumer set) {
        addRenderableWidget(new AbstractSliderButton(x, y, 150, 20, Component.translatable(key, val), val / 200.0) {
            int v = val;
            @Override protected void updateMessage() { setMessage(Component.translatable(key, v)); }
            @Override protected void applyValue() { v = (int) Math.round(value * 200 / 10) * 10; set.accept(v); HandAnim.load(cfg); }
        });
    }

    @Override
    protected void init() {
        int x = 8, y = 26, x2 = 162;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.ha.master", onoff(cfg.handAnim)), b -> {
            cfg.handAnim = !cfg.handAnim; HandAnim.load(cfg); rebuildWidgets(); }).bounds(x, y, 304, 20).build());
        y += 24;
        int yy = y, bx = x;
        for (int i = 0; i < HandAnim.FEATURES.length; i++) {
            String f = HandAnim.FEATURES[i];
            boolean blk = HandAnim.allBlocked || HandAnim.blocked[i];
            Component lbl = Component.translatable("cpmwheel.ha.f." + f).append(": ").append(blk ? Component.translatable("cpmwheel.ha.blocked") : Component.literal(onoff(!cfg.handAnimOff.contains(f))));
            Button btn = Button.builder(lbl, b -> {
                if (!cfg.handAnimOff.remove(f)) cfg.handAnimOff.add(f);
                HandAnim.load(cfg); rebuildWidgets(); }).bounds(bx, yy, 150, 20).build();
            btn.active = cfg.handAnim && !blk;
            addRenderableWidget(btn); yy += 22;
            if (i == 9) { yy = y + 22 * 6 + 4; bx = x2; } // HMI-style extras go under the sliders
        }
        slider(x2, y, "cpmwheel.ha.int", cfg.haIntensity, v -> cfg.haIntensity = v); y += 22;
        slider(x2, y, "cpmwheel.ha.int.idle", cfg.haIdle, v -> cfg.haIdle = v); y += 22;
        slider(x2, y, "cpmwheel.ha.int.bob", cfg.haBob, v -> cfg.haBob = v); y += 22;
        slider(x2, y, "cpmwheel.ha.int.inertia", cfg.haInertia, v -> cfg.haInertia = v); y += 22;
        slider(x2, y, "cpmwheel.ha.int.pose", cfg.haPose, v -> cfg.haPose = v); y += 22;
        slider(x2, y, "cpmwheel.ha.int.swing", cfg.haSwing, v -> cfg.haSwing = v); y += 22;
        int iy = y + 22 * 3 + 4;
        for (int m = 0; m < HandAnim.MODS.length; m++) {
            if (!HandAnim.modLoaded[m]) continue;
            String key = HandAnim.MODS[m][0];
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.ha.ignore", HandAnim.MODS[m][1], onoff(cfg.haIgnore.contains(key))), b -> {
                if (!cfg.haIgnore.remove(key)) cfg.haIgnore.add(key);
                HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, iy, 150, 20).build());
            iy += 22;
        }
        confY = iy + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 304, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        String[] st = HandAnim.status();
        boolean ok = st[0].endsWith(".ok");
        g.drawString(font, Component.translatable("cpmwheel.ha.status", Component.translatable(st[0], st[1])), 8 + font.width(title) + 12, 10, ok ? 0x3BFF9D : 0xFF6B6B);
        if (!HandAnim.conflictText.isEmpty())
            g.drawWordWrap(font, Component.translatable("cpmwheel.ha.conflict", HandAnim.conflictText), 162, confY, Math.max(100, width - 170), 0xFFB347);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { cfg.fixup(); HandAnim.load(cfg); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
