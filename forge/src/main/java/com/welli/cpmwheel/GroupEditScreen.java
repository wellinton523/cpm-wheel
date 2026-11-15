package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** 1.5.7: create / edit an item group (wildcard or regex on the id, item class, item tag, or a hand-picked list). */
public class GroupEditScreen extends Screen {
    static final String[] KINDS = {"pattern", "class", "tag", "list"};
    private final ItemOverrideScreen parent;
    private final WheelConfig cfg;
    private final WheelConfig.ItemGroup orig, g;
    private final boolean isNew;
    private EditBox name, mod, value;
    private List<String> preview = List.of();
    private int count = 0;
    private boolean confirmDelete = false;

    public GroupEditScreen(ItemOverrideScreen parent, WheelConfig cfg, WheelConfig.ItemGroup group, boolean isNew) {
        super(Component.translatable(isNew ? "cpmwheel.grp.new" : "cpmwheel.grp.edit"));
        this.parent = parent; this.cfg = cfg; this.orig = group; this.isNew = isNew;
        g = new WheelConfig.ItemGroup();
        g.id = group.id; g.name = group.name; g.mod = group.mod; g.kind = group.kind; g.value = group.value; g.items = new java.util.ArrayList<>(group.items);
    }

    private void refreshPreview() { preview = ItemGroups.members(g, 2000); count = preview.size(); }

    @Override
    protected void init() {
        int x = 8, y = 26, w = 220;
        name = box(x, y, w, g.name, "cpmwheel.grp.name", v -> g.name = v); y += 34;
        mod = box(x, y, w, g.mod, "cpmwheel.grp.mod", v -> { g.mod = v.trim(); refreshPreview(); }); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.kind", Component.translatable("cpmwheel.grp.kind." + g.kind)), b -> {
            g.kind = KINDS[(java.util.Arrays.asList(KINDS).indexOf(g.kind) + 1) % KINDS.length]; refreshPreview(); rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.translatable("cpmwheel.grp.kind.help"))).bounds(x, y, w, 20).build()); y += 34;
        value = box(x, y, w, g.value, "cpmwheel.grp.value." + g.kind, v -> { g.value = v.trim(); refreshPreview(); });
        value.active = !"list".equals(g.kind); y += 24;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.addheld"), b -> {
            ItemStack st = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
            if (!st.isEmpty()) { String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); if (!g.items.remove(id)) g.items.add(id); if (g.mod.isBlank()) g.mod = id.substring(0, id.indexOf(':')); }
            refreshPreview(); rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.translatable("cpmwheel.grp.addheld.help"))).bounds(x, y, 109, 20).build());
        Button clr = Button.builder(Component.translatable("cpmwheel.grp.clearitems", g.items.size()), b -> { g.items.clear(); refreshPreview(); rebuildWidgets(); }).bounds(x + 111, y, 109, 20).build();
        clr.active = !g.items.isEmpty();
        addRenderableWidget(clr); y += 26;

        int by = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.save"), b -> save()).bounds(x, by, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(x + 74, by, 72, 20).build());
        if (!isNew) {
            addRenderableWidget(Button.builder(Component.translatable(confirmDelete ? "cpmwheel.grp.delete.sure" : "cpmwheel.grp.delete"), b -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                cfg.haGroups.removeIf(q -> q.id.equals(g.id));
                cfg.haItem.keySet().removeIf(k -> k.startsWith("grp:" + g.id + "|") || k.startsWith("arm/grp:" + g.id + "|"));
                HandAnim.load(cfg); parent.selectGroup(""); minecraft.setScreen(parent); }).bounds(x + 148, by, 72, 20).build());
        }
        if (preview.isEmpty() && count == 0) refreshPreview();
    }

    private EditBox box(int x, int y, int w, String v, String hint, java.util.function.Consumer<String> set) {
        EditBox e = new EditBox(font, x, y + 10, w, 18, Component.literal(""));
        e.setMaxLength(200);
        e.setValue(v == null ? "" : v);
        e.setHint(Component.translatable(hint + ".hint"));
        e.setResponder(set);
        addRenderableWidget(e);
        return e;
    }

    private void save() {
        if (g.name == null || g.name.isBlank()) g.name = g.value.isBlank() ? "Grupo" : ItemGroups.cap(g.value.replace("*", "").replace("re:", "").replace("#", ""));
        orig.name = g.name; orig.mod = g.mod; orig.kind = g.kind; orig.value = g.value; orig.items = g.items;
        if (isNew && cfg.group(orig.id) == null) cfg.haGroups.add(orig);
        cfg.fixup(); HandAnim.load(cfg);
        parent.selectGroup(orig.id);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        gg.fill(0, 0, width, height, 0xC0050A12);
        gg.drawString(font, title, 8, 10, Style.accent(cfg));
        gg.drawString(font, Component.translatable("cpmwheel.grp.name.label"), 8, 26, 0x9FB8C8);
        gg.drawString(font, Component.translatable("cpmwheel.grp.mod.label"), 8, 60, 0x9FB8C8);
        gg.drawString(font, Component.translatable("cpmwheel.grp.value.label"), 8, 26 + 34 + 24 + 34, 0x9FB8C8);
        int px = 240, py = 26;
        gg.drawString(font, Component.translatable("cpmwheel.grp.count", count), px, py, count > 0 ? 0x3BFF9D : 0xFF6B6B); py += 12;
        int lines = Math.max(1, (height - 40 - py) / 10);
        for (int i = 0; i < Math.min(preview.size(), lines); i++) {
            String id = preview.get(i);
            boolean extra = g.items.contains(id);
            gg.drawString(font, font.plainSubstrByWidth((extra ? "+ " : "  ") + id, Math.max(60, width - px - 8)), px, py, extra ? 0xFFE08A : 0xC8D6E0); py += 10;
        }
        if (preview.size() > lines) gg.drawString(font, "…", px, py, 0x7FA8B8);
        super.render(gg, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
