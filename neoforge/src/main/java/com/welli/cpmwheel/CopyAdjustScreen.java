package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 1.5.7 "Copiar ajuste para…": copies the current item/arm values to an item, a group or a whole mod (this hand or both). */
public class CopyAdjustScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final int[] values;
    private final boolean arm;
    private final boolean off;
    private final String ns, source;
    private static boolean both = false;
    private String query = "", status = "";
    private int page = 0;
    /** entries: "mod:ns", "grp:id", "ns:item" */
    private final List<String> targets = new ArrayList<>();

    public CopyAdjustScreen(Screen parent, WheelConfig cfg, int[] values, boolean arm, boolean off, String ns, String source) {
        super(Component.translatable("cpmwheel.copy.title"));
        this.parent = parent; this.cfg = cfg; this.values = values; this.arm = arm; this.off = off; this.ns = ns == null ? "" : ns; this.source = source;
    }

    private void build() {
        targets.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (!ns.isEmpty() && (q.isEmpty() || ns.contains(q))) targets.add("mod:" + ns);
        for (WheelConfig.ItemGroup g : cfg.haGroups)
            if (q.isEmpty() || (g.label() + " " + g.mod).toLowerCase(Locale.ROOT).contains(q)) targets.add("grp:" + g.id);
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            String id = rl.toString();
            if (q.isEmpty() ? !ns.isEmpty() && rl.getNamespace().equals(ns) : id.contains(q)) targets.add(id);
            if (targets.size() > 500) break;
        }
        targets.removeIf(t -> t.equals(source));
    }

    private String label(String t) {
        if (t.startsWith("mod:")) return Component.translatable("cpmwheel.copy.mod", t.substring(4)).getString();
        if (t.startsWith("grp:")) { WheelConfig.ItemGroup g = cfg.group(t.substring(4)); return Component.translatable("cpmwheel.copy.group", g == null ? t : g.label()).getString(); }
        return t;
    }

    private void copyTo(String t) {
        for (String h : both ? new String[]{"main", "off"} : new String[]{off ? "off" : "main"})
            cfg.haItem.put((arm ? "arm/" : "") + t + "|" + h, values.clone());
        HandAnim.load(cfg);
        status = "\u2714 " + label(t);
    }

    @Override
    protected void init() {
        build();
        int x = 8, y = 26, w = Math.min(300, width - 16);
        EditBox s = new EditBox(font, x, y, 150, 18, Component.literal(""));
        s.setHint(Component.translatable("cpmwheel.io.search"));
        s.setValue(query);
        s.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(s); s.setFocused(true); } });
        addRenderableWidget(s);
        addRenderableWidget(Button.builder(Component.translatable(both ? "cpmwheel.copy.both" : "cpmwheel.copy.one"), b -> { both = !both; rebuildWidgets(); })
                .bounds(x + 154, y - 1, 146, 20).build());
        y += 26;
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (targets.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(targets.size(), (page + 1) * per); i++) {
            String t = targets.get(i);
            boolean has = cfg.haItem.containsKey((arm ? "arm/" : "") + t + "|" + (off ? "off" : "main"));
            addRenderableWidget(Button.builder(Component.literal((has ? "\u2605 " : "") + label(t)), b -> copyTo(t)).bounds(x, y, w, 18).build());
            y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, Component.translatable("cpmwheel.copy.head", Component.translatable(arm ? "cpmwheel.io.arm" : "cpmwheel.io.item")), 8, 10, Style.accent(cfg));
        if (!status.isEmpty()) g.drawString(font, status, 120, height - 20, 0x3BFF9D);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
