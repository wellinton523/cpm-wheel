package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Ajuste do item": per-category offsets/scale/rotation of the item in the fist, vanilla fallback, 1/2-hand blocks. */
public class ItemAdjustScreen extends Screen {
    private static final String[] KEYS = {"x", "y", "z", "scale", "rx", "ry", "rz"};
    private static final int[] MIN = {-50, -50, -50, -80, -180, -180, -180}, MAX = {50, 50, 50, 150, 180, 180, 180}, STEP = {1, 1, 1, 5, 5, 5, 5};
    private static int catIdx = 0;
    private final Screen parent;
    private final WheelConfig cfg;
    public ItemAdjustScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.adj.title")); this.parent = parent; this.cfg = cfg; }

    private int[] cur() { return cfg.haAdj.computeIfAbsent(HandAnim.CATS[catIdx], k -> new int[7]); }

    @Override
    protected void init() {
        int x = 8, y = 26, x2 = 162;
        String cat = HandAnim.CATS[catIdx];
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { catIdx = Math.floorMod(catIdx - 1, HandAnim.CATS.length); rebuildWidgets(); }).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.adj.cat", Component.translatable("cpmwheel.adj.c." + cat)),
                b -> { catIdx = Math.floorMod(catIdx + 1, HandAnim.CATS.length); rebuildWidgets(); }).bounds(x + 22, y, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { catIdx = Math.floorMod(catIdx + 1, HandAnim.CATS.length); rebuildWidgets(); }).bounds(x + 130, y, 20, 20).build());
        y += 24;
        int[] v = cfg.haAdj.getOrDefault(cat, new int[7]);
        for (int i = 0; i < 7; i++) {
            final int idx = i, min = MIN[i], max = MAX[i], step = STEP[i];
            String key = "cpmwheel.adj." + KEYS[i];
            addRenderableWidget(new AbstractSliderButton(x, y, 150, 20, Component.translatable(key, v[i]), (v[i] - min) / (double) (max - min)) {
                int val = v[idx];
                @Override protected void updateMessage() { setMessage(Component.translatable(key, val)); }
                @Override protected void applyValue() { val = min + (int) Math.round(value * (max - min) / step) * step; cur()[idx] = val; HandAnim.load(cfg); }
            });
            y += 22;
        }
        if (cat.equals("lantern") || cat.equals("torch") || cat.equals("block")) {
            boolean flat = cfg.haFlat.contains(cat);
            addRenderableWidget(Button.builder(Component.translatable(flat ? "cpmwheel.adj.r2d" : "cpmwheel.adj.r3d"), b -> {
                if (!cfg.haFlat.remove(cat)) cfg.haFlat.add(cat); HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, 26 + 44, 150, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.adj.reset"), b -> { cfg.haAdj.remove(cat); HandAnim.load(cfg); rebuildWidgets(); }).bounds(x, y, 150, 20).build());
        int ry = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.adj.vanilla", cfg.haVanillaItem ? "ON" : "OFF"),
                b -> { cfg.haVanillaItem = !cfg.haVanillaItem; HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, ry, 150, 20).build()); ry += 22;
        addRenderableWidget(Button.builder(Component.translatable(cfg.haTwoHandBlock ? "cpmwheel.adj.block2" : "cpmwheel.adj.block1"),
                b -> { cfg.haTwoHandBlock = !cfg.haTwoHandBlock; HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, ry, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 304, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x60050A12); // light backdrop so the hand stays visible while tuning
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        g.drawWordWrap(font, Component.translatable("cpmwheel.adj.help"), 162, 26 + 72, 150, 0x9FB8C8);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { cfg.fixup(); HandAnim.load(cfg); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
