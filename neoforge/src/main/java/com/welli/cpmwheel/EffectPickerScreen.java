package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.5.7: pick a status effect from every registered effect (modded ones too). */
public class EffectPickerScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final WheelConfig.EffectRule rule;
    private java.util.function.Consumer<String> pick; private String current = "";
    private final List<String> all = EffectTriggers.allEffects();
    private final List<String> shown = new ArrayList<>();
    private String query = "";
    private int page = 0;

    public EffectPickerScreen(Screen parent, WheelConfig cfg, WheelConfig.EffectRule rule) { super(Component.translatable("cpmwheel.eff.pick")); this.parent = parent; this.cfg = cfg; this.rule = rule; }
    /** 1.6.0: pick for a condition */
    public EffectPickerScreen(Screen parent, WheelConfig cfg, String current, java.util.function.Consumer<String> pick) { this(parent, cfg, null); this.pick = pick; this.current = current == null ? "" : current; }

    @Override
    protected void init() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (String id : all) if (q.isEmpty() || id.contains(q) || EffectTriggers.effectName(id).toLowerCase(Locale.ROOT).contains(q)) shown.add(id);
        int x = 8, y = 26, w = Math.min(300, width - 16);
        EditBox s = new EditBox(font, x, y, 200, 18, Component.literal(""));
        s.setHint(Component.translatable("cpmwheel.eff.pick.search"));
        s.setValue(query);
        s.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(s); s.setFocused(true); } });
        addRenderableWidget(s); setInitialFocus(s);
        y += 24;
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            String id = shown.get(i);
            Button b = Button.builder(Component.literal(EffectTriggers.effectName(id) + "  \u00A77" + id), bt -> { if (pick != null) pick.accept(id); else rule.effect = id; minecraft.setScreen(parent); }).bounds(x, y, w, 18).build();
            b.active = !id.equals(pick != null ? current : rule.effect);
            addRenderableWidget(b); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xC0050A12); g.drawString(font, title, 8, 10, Style.accent(cfg)); super.render(g, mx, my, pt); }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
