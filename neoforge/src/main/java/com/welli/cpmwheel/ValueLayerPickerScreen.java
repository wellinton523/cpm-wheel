package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.7.0: pick a CPM value layer (max value > 1), command-controlled ones included; a name can also be typed. */
public class ValueLayerPickerScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final Consumer<String> pick;
    private final String current;
    private final LinkedHashMap<String, Integer> all;
    private final Set<String> cmd = new HashSet<>();
    private final List<String> shown = new ArrayList<>();
    private String query = "";
    private int page = 0;

    public ValueLayerPickerScreen(Screen parent, WheelConfig cfg, String current, Consumer<String> pick) {
        super(Component.translatable("cpmwheel.l2d.pick")); this.parent = parent; this.cfg = cfg; this.pick = pick; this.current = current == null ? "" : current;
        all = CpmBridge.listValueLayers(cmd);
    }

    @Override
    protected void init() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (String k : all.keySet()) if (q.isEmpty() || k.toLowerCase(Locale.ROOT).contains(q)) shown.add(k);
        int x = 8, y = 26, w = Math.min(320, width - 16);
        EditBox s = new EditBox(font, x, y, 200, 18, Component.literal(""));
        s.setHint(Component.translatable("cpmwheel.l2d.pick.search"));
        s.setValue(query);
        s.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(s); s.setFocused(true); } });
        addRenderableWidget(s);
        Button typed = Button.builder(Component.translatable("cpmwheel.l2d.pick.typed"), b -> { if (!query.isBlank()) { pick.accept(query.trim()); minecraft.setScreen(parent); } })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cpmwheel.l2d.pick.typed.help"))).bounds(x + 204, y, 110, 18).build();
        typed.active = !query.isBlank();
        addRenderableWidget(typed);
        y += 24;
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            String k = shown.get(i);
            Component label = Component.translatable(cmd.contains(k) ? "cpmwheel.l2d.pick.row.cmd" : "cpmwheel.l2d.pick.row", k, all.get(k));
            Button b = Button.builder(label, bt -> { pick.accept(k); minecraft.setScreen(parent); }).bounds(x, y, w, 18).build();
            b.active = !k.equals(current);
            addRenderableWidget(b);
            y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.pick.clear"), b -> { pick.accept(""); minecraft.setScreen(parent); }).bounds(width - 214, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12); g.drawString(font, title, 8, 10, Style.accent(cfg));
        if (all.isEmpty()) {
            int y = 54;
            for (var l : font.split(Component.translatable("cpmwheel.l2d.pick.none"), width - 16)) { g.drawString(font, l, 8, y, 0x7FA8B8); y += 10; }
        }
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
