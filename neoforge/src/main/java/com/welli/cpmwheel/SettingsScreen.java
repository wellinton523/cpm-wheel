package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** Look & behaviour settings with a live preview of the selected design. */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private WheelConfig cfg;
    private List<String> presets;
    private int presetIdx = 0;
    private String status = "";
    private EditBox presetName;

    public SettingsScreen(Screen parent) { super(Component.translatable("cpmwheel.set.title")); this.parent = parent; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    private static String next(String[] o, String cur, int dir) {
        int i = Math.max(0, Style.idx(o, cur));
        return o[Math.floorMod(i + dir, o.length)];
    }

    private void slider(int x, int y, String key, int min, int max, int stepv, int val, IntConsumer set) {
        addRenderableWidget(new AbstractSliderButton(x, y, 150, 20, Component.translatable(key, val), (val - min) / (double) (max - min)) {
            int v = val;
            @Override protected void updateMessage() { setMessage(Component.translatable(key, v)); }
            @Override protected void applyValue() { v = min + (int) Math.round(value * (max - min) / stepv) * stepv; set.accept(v); }
        });
    }

    @Override
    protected void init() {
        if (cfg == null) { cfg = WheelConfig.get(); presets = WheelConfig.presetNames(); }
        int x = 8, y = 26, x2 = 162;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { cfg.design = next(Style.DESIGNS, cfg.design, -1); rebuildWidgets(); }).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.design", Component.translatable("cpmwheel.design." + Style.design(cfg))),
                b -> { cfg.design = next(Style.DESIGNS, cfg.design, 1); rebuildWidgets(); }).bounds(x + 22, y, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { cfg.design = next(Style.DESIGNS, cfg.design, 1); rebuildWidgets(); }).bounds(x + 130, y, 20, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.theme", Component.translatable("cpmwheel.theme." + (Style.idx(Style.THEMES, cfg.theme) < 0 ? "auto" : cfg.theme))),
                b -> { cfg.theme = next(Style.THEMES, cfg.theme, 1); rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.icononly", onoff(cfg.iconOnly)), b -> { cfg.iconOnly = !cfg.iconOnly; rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 22;
        slider(x, y, "cpmwheel.set.iconsize", 50, 250, 5, cfg.iconSize, v -> cfg.iconSize = v); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.centericononly", onoff(cfg.centerIconOnly)), b -> { cfg.centerIconOnly = !cfg.centerIconOnly; rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 22;
        slider(x, y, "cpmwheel.set.centericonsize", 50, 250, 5, cfg.centerIconSize, v -> cfg.centerIconSize = v); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.center", Component.translatable("cpmwheel.center." + cfg.centerMode)), b -> {
            String[] o = {"player", "icon", "none"}; cfg.centerMode = next(o, cfg.centerMode, 1); rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.ring", onoff(cfg.innerRing)), b -> { cfg.innerRing = !cfg.innerRing; rebuildWidgets(); }).bounds(x, y, 150, 20).build());

        y = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.sounds", onoff(cfg.sounds)), b -> { cfg.sounds = !cfg.sounds; rebuildWidgets(); if (cfg.sounds) Actions.sound(cfg, 1.2f); }).bounds(x2, y, 150, 20).build()); y += 22;
        slider(x2, y, "cpmwheel.set.volume", 0, 100, 5, cfg.soundVolume, v -> cfg.soundVolume = v); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.alt", onoff(cfg.altHotkeys)), b -> { cfg.altHotkeys = !cfg.altHotkeys; rebuildWidgets(); }).bounds(x2, y, 150, 20).build()); y += 26;
        presetName = new EditBox(font, x2, y, 96, 18, Component.literal(""));
        presetName.setHint(Component.translatable("cpmwheel.set.presetname"));
        presetName.setMaxLength(32);
        addRenderableWidget(presetName);
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.save"), b -> {
            String n = presetName.getValue().trim();
            status = cfg.savePreset(n) ? "\u2714 " + n : "\u2716";
            presets = WheelConfig.presetNames(); presetName.setValue("");
        }).bounds(x2 + 98, y - 1, 52, 20).build()); y += 22;
        String pn = presets.isEmpty() ? "-" : presets.get(Math.floorMod(presetIdx, presets.size()));
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.preset", pn), b -> { presetIdx++; rebuildWidgets(); }).bounds(x2, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.load"), b -> {
            if (!presets.isEmpty()) status = cfg.loadPreset(pn) ? "\u2714 " + pn : "\u2716";
        }).bounds(x2 + 98, y, 52, 20).build()); y += 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.copydefault"), b -> {
            status = cfg.copyFromDefault() ? "\u2714" : "\u2716 (default)"; }).bounds(x2, y + 12, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 304, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        g.drawString(font, Component.translatable("cpmwheel.set.profile", cfg.profileKey()), 162, 26 + 22 * 3 + 4 + 22 * 2 + 4, 0x7FA8B8);
        if (!status.isEmpty()) g.drawString(font, status, 162, height - 40, 0x3BFF9D);
        preview(g);
        super.render(g, mx, my, pt);
    }

    /** live preview of the selected design with the real slots of the current page */
    private void preview(GuiGraphics g) {
        int left = 318;
        if (width - left < 80) return;
        float cx = (left + width) / 2f, cy = height / 2f;
        float R = Math.min((width - left) / 2f - 14, height * 0.36f), r0 = R * 0.38f;
        g.enableScissor(left, 0, width, height);
        Style.background(g, cfg, width, height, 1f, cx, cy, R);
        g.disableScissor();
        List<WheelConfig.Page> pages = cfg.viewPages();
        List<WheelConfig.Slot> slots = pages.get(Math.floorMod(cfg.lastPage, pages.size())).slots;
        int n = Math.max(1, slots.size());
        double seg = Math.PI * 2 / n;
        int hv = (int) ((System.currentTimeMillis() / 900) % n);
        for (int i = 0; i < slots.size(); i++) {
            WheelConfig.Slot s = slots.get(i);
            boolean h = i == hv;
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            float ro = R + (h ? 7 : 0);
            Style.segment(g, cfg, cx, cy, r0, ro, a0, a1, s.color, h ? 1f : 0f, i == 0, s.empty(), 1f);
            float rr = (r0 + ro) / 2, tx = cx + (float) Math.sin(i * seg) * rr, ty = cy - (float) Math.cos(i * seg) * rr;
            int isz = Math.max(8, Math.round(Math.min((ro - r0) * 0.95f, 18 * cfg.iconSize / 100f)));
            if (s.iconOnly(cfg)) { if (!s.empty()) Style.icon(g, cfg, font, s, (int) tx, (int) ty, isz, s.color, 1f); }
            else {
                String l = Style.fit(cfg, font, s.empty() ? "\u2014" : s.display(), (int) (R * 0.75f));
                Style.text(g, cfg, font, l, (int) tx, (int) ty - 4, Style.textColor(cfg, h, s.empty()));
            }
        }
        Style.center(g, cfg, cx, cy, r0 - 4, 1f);
        WheelConfig.Slot hs = hv < slots.size() ? slots.get(hv) : null;
        int cis = Math.max(8, Math.round(Math.min(r0 * 1.5f, 28 * cfg.centerIconSize / 100f)));
        if (cfg.centerIconOnly) { if (hs != null && !hs.empty()) Style.icon(g, cfg, font, hs, (int) cx, (int) cy, cis, hs.color, 1f); }
        else if (hs != null) Style.text(g, cfg, font, Style.fit(cfg, font, hs.empty() ? "-" : hs.display(), (int) (r0 * 1.7f)), (int) cx, (int) cy - 4, Style.accent(cfg));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void onClose() { cfg.fixup(); cfg.save(); Hud.invalidate(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
