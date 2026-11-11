package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** "Itens e mods": per-item / per-mod hand pose overrides (priority item > mod > category), with live preview. */
public class ItemOverrideScreen extends Screen {
    private static final String[] KEYS = {"x", "y", "z", "scale", "rx", "ry", "rz"};
    private static final int[] MIN = {-50, -50, -50, -80, -180, -180, -180}, MAX = {50, 50, 50, 150, 180, 180, 180}, STEP = {1, 1, 1, 5, 5, 5, 5};
    private static boolean modMode = false, off = false;
    private static String target = "", query = "";
    private final Screen parent;
    private final WheelConfig cfg;
    private EditBox search;
    private final List<String> results = new ArrayList<>();
    private int page = 0;

    public ItemOverrideScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.io.title")); this.parent = parent; this.cfg = cfg; }

    private String key() { return (modMode ? "mod:" : "") + target + "|" + (off ? "off" : "main"); }

    private void filter() {
        results.clear();
        String q = query.toLowerCase(java.util.Locale.ROOT).trim();
        if (modMode) {
            TreeSet<String> ns = new TreeSet<>();
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) ns.add(rl.getNamespace());
            for (String n : ns) if (q.isEmpty() || n.contains(q)) results.add(n);
        } else {
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                String id = rl.toString();
                if (q.isEmpty() ? cfg.haItem.keySet().stream().anyMatch(k -> k.startsWith(id + "|")) : id.contains(q)) results.add(id);
                if (results.size() > 300) break;
            }
            results.sort(null);
        }
    }

    @Override
    protected void init() {
        int x = 8, y = 26, x2 = 166;
        filter();
        addRenderableWidget(Button.builder(Component.translatable(modMode ? "cpmwheel.io.modemod" : "cpmwheel.io.modeitem"), b -> {
            modMode = !modMode; target = ""; page = 0; rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 22;
        search = new EditBox(font, x, y, 150, 18, Component.literal(""));
        search.setHint(Component.translatable("cpmwheel.io.search"));
        search.setValue(query);
        search.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(search); search.setFocused(true); } });
        addRenderableWidget(search); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.held"), b -> {
            ItemStack st = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(off ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!st.isEmpty()) { ResourceLocation rl = BuiltInRegistries.ITEM.getKey(st.getItem()); target = modMode ? rl.getNamespace() : rl.toString(); }
            rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 24;
        int per = Math.max(3, (height - 26 - 24 - y) / 20);
        int pages = Math.max(1, (results.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(results.size(), (page + 1) * per); i++) {
            String r = results.get(i);
            boolean set = cfg.haItem.containsKey((modMode ? "mod:" : "") + r + "|main") || cfg.haItem.containsKey((modMode ? "mod:" : "") + r + "|off");
            Button bt = Button.builder(Component.literal((set ? "\u2605 " : "") + r), b -> { target = r; rebuildWidgets(); }).bounds(x, y, 150, 18).build();
            bt.active = !r.equals(target);
            addRenderableWidget(bt); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, y, 106, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 130, y, 20, 18).build());
        }

        int ry = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.enabled", cfg.haItemOn ? "ON" : "OFF"), b -> {
            cfg.haItemOn = !cfg.haItemOn; HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, ry, 150, 20).build()); ry += 22;
        addRenderableWidget(Button.builder(Component.translatable(off ? "cpmwheel.arm.off" : "cpmwheel.arm.main"), b -> { off = !off; rebuildWidgets(); }).bounds(x2, ry, 150, 20).build()); ry += 34;
        if (!target.isEmpty()) {
            int[] v = cfg.haItem.getOrDefault(key(), new int[7]);
            for (int i = 0; i < 7; i++) {
                final int idx = i, min = MIN[i], max = MAX[i], step = STEP[i];
                String k = "cpmwheel.adj." + KEYS[i];
                addRenderableWidget(new AbstractSliderButton(x2, ry, 150, 20, Component.translatable(k, v[i]), (v[i] - min) / (double) (max - min)) {
                    int val = v[idx];
                    @Override protected void updateMessage() { setMessage(Component.translatable(k, val)); }
                    @Override protected void applyValue() {
                        val = min + (int) Math.round(value * (max - min) / step) * step;
                        cfg.haItem.computeIfAbsent(key(), q -> new int[7])[idx] = val; HandAnim.load(cfg);
                    }
                });
                ry += 22;
            }
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.adj.reset"), b -> { cfg.haItem.remove(key()); HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, ry, 150, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 24, 308, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, 324, height, 0x60050A12); // backdrop only behind the controls: the hand stays visible on the right
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        g.drawString(font, target.isEmpty() ? Component.translatable("cpmwheel.io.pick") : Component.literal((modMode ? "mod: " : "") + target), 166, 26 + 48, 0xFFE08A);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void onClose() { cfg.fixup(); HandAnim.load(cfg); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
