package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.5.7: groups found automatically inside one mod (same id ending, same item class, same tag); one click creates the group. */
public class GroupSuggestScreen extends Screen {
    private final ItemOverrideScreen parent;
    private final WheelConfig cfg;
    private String ns;
    private List<ItemGroups.Suggestion> list = new ArrayList<>();
    private int page = 0;

    public GroupSuggestScreen(ItemOverrideScreen parent, WheelConfig cfg, String ns) {
        super(Component.translatable("cpmwheel.sug.title")); this.parent = parent; this.cfg = cfg; this.ns = ns == null ? "" : ns;
        list = ItemGroups.suggest(this.ns);
    }

    @Override
    protected void init() {
        int x = 8, y = 26, w = Math.min(360, width - 16);
        EditBox mod = new EditBox(font, x, y, 150, 18, Component.literal(""));
        mod.setHint(Component.translatable("cpmwheel.grp.mod.hint"));
        mod.setValue(ns);
        addRenderableWidget(mod);
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.sug.find"), b -> { ns = mod.getValue().trim(); list = ItemGroups.suggest(ns); page = 0; rebuildWidgets(); })
                .bounds(x + 154, y - 1, 80, 20).build());
        y += 26;
        int per = Math.max(3, (height - 30 - y) / 22);
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(list.size(), (page + 1) * per); i++) {
            ItemGroups.Suggestion s = list.get(i);
            Component l = Component.translatable("cpmwheel.sug.row", s.name(), Component.translatable("cpmwheel.grp.kind." + s.kind()), s.value(), s.count());
            addRenderableWidget(Button.builder(l, b -> {
                WheelConfig.ItemGroup g = ItemGroups.fromSuggestion(s, ns);
                for (WheelConfig.ItemGroup e : cfg.haGroups) if (e.kind.equals(g.kind) && e.value.equals(g.value) && e.mod.equals(g.mod)) { parent.selectGroup(e.id); minecraft.setScreen(parent); return; }
                cfg.haGroups.add(g); cfg.fixup(); HandAnim.load(cfg);
                parent.selectGroup(g.id); minecraft.setScreen(parent);
            }).bounds(x, y, w, 20).build());
            y += 22;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        if (list.isEmpty()) g.drawString(font, Component.translatable(ns.isBlank() ? "cpmwheel.sug.nomod" : "cpmwheel.sug.none"), 8, 56, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
