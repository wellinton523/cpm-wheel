package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.5.8 "Criar grupo": step by step (1 mod, 2 what the name must have, 3 what to leave out, 4 translated names),
 * live preview with item icons (click = leave out / bring back), example chips, plain help, collapsible "Modo avançado"
 * (pattern / regex / tag / class / list), group name + colour, Salvar -> straight to adjusting the group.
 * The same screen edits an existing group.
 */
public class GroupEditScreen extends Screen {
    static final String[] KINDS = {"query", "pattern", "class", "tag", "list"};
    static final int[] COLORS = {0xFFD23B, 0x2EE6FF, 0xFF3B5C, 0xB46CFF, 0x3BFF9D, 0xFF8FD0, 0x4D7CFF, 0xFFFFFF};
    /** example chips: label key, words, words to leave out */
    private static final String[][] EXAMPLES = {
            {"cpmwheel.grp.ex.backpacks", "backpack|mochila", "upgrade"},
            {"cpmwheel.grp.ex.swords", "sword|espada", ""},
            {"cpmwheel.grp.ex.netherite", "netherite", ""},
            {"cpmwheel.grp.ex.tools", "pickaxe|_axe|shovel|hoe|picareta|machado|pá|enxada", ""},
            {"cpmwheel.grp.ex.lanterns", "lantern|lanterna", ""},
            {"cpmwheel.grp.ex.potions", "potion|poção", ""}};
    private static boolean advanced = false;

    private final ItemOverrideScreen parent;
    private final WheelConfig cfg;
    private final WheelConfig.ItemGroup orig, g;
    private final boolean isNew;
    private EditBox name, value, notq;
    /** preview: every item the words match (also the ones clicked off) */
    private final List<Item> found = new ArrayList<>();
    private int count = 0, prevPage = 0, prevX, prevY, prevCols, prevRows;
    private boolean confirmDelete = false;
    private List<ItemGroups.Suggestion> sugg = List.of();
    private String suggNs = null;

    public GroupEditScreen(ItemOverrideScreen parent, WheelConfig cfg, WheelConfig.ItemGroup group, boolean isNew) {
        super(Component.translatable(isNew ? "cpmwheel.grp.new" : "cpmwheel.grp.edit"));
        this.parent = parent; this.cfg = cfg; this.orig = group; this.isNew = isNew;
        g = new WheelConfig.ItemGroup();
        g.id = group.id; g.name = group.name; g.mod = group.mod == null ? "" : group.mod; g.kind = group.kind; g.value = group.value; g.items = new ArrayList<>(group.items);
        g.names = group.names; g.notq = group.notq == null ? "" : group.notq; g.exclude = new ArrayList<>(group.exclude == null ? List.of() : group.exclude);
        g.color = group.color == 0 ? COLORS[0] : group.color;
        if (isNew && (g.kind == null || g.kind.isBlank() || "pattern".equals(g.kind) && g.value.isBlank())) g.kind = "query";
        if (!"query".equals(g.kind)) advanced = true;
        refreshPreview();
    }

    void setMod(String ns) { g.mod = ns == null ? "" : ns; prevPage = 0; refreshPreview(); }

    private void refreshPreview() {
        found.clear(); count = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(it);
            if (ItemGroups.matchesRaw(g, it, rl)) {
                found.add(it);
                if (!g.exclude.contains(rl.toString())) count++;
            }
            if (found.size() >= 3000) break;
        }
        found.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).toString().compareTo(BuiltInRegistries.ITEM.getKey(b).toString()));
        if (!g.mod.equals(suggNs)) { suggNs = g.mod; sugg = g.mod.isBlank() ? List.of() : ItemGroups.suggest(g.mod).stream().filter(s -> s.kind().equals("query")).limit(3).toList(); }
    }

    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    @Override
    protected void init() {
        int x = 8, w = 200, y = 22;
        // 1) mod
        y += 10;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.mod.btn", g.mod.isBlank() ? Component.translatable("cpmwheel.grp.mod.all") : Component.literal(g.mod)),
                b -> minecraft.setScreen(new ModPickerScreen(this, g.mod, this::setMod))).tooltip(tip("cpmwheel.grp.step1.help")).bounds(x, y, w, 20).build());
        y += 32;
        // 2) words (or the advanced value)
        value = new EditBox(font, x, y, w, 18, Component.literal(""));
        value.setMaxLength(300);
        value.setValue(g.value == null ? "" : g.value);
        value.setHint(Component.translatable("cpmwheel.grp.value." + g.kind + ".hint"));
        value.setTooltip(tip("query".equals(g.kind) ? "cpmwheel.grp.step2.help" : "cpmwheel.grp.kind.help"));
        value.setResponder(v -> { g.value = v.trim(); prevPage = 0; refreshPreview(); });
        addRenderableWidget(value);
        y += 30;
        // 3) leave out
        notq = new EditBox(font, x, y, w, 18, Component.literal(""));
        notq.setMaxLength(200);
        notq.setValue(g.notq);
        notq.setHint(Component.translatable("cpmwheel.grp.not.hint"));
        notq.setTooltip(tip("cpmwheel.grp.step3.help"));
        notq.setResponder(v -> { g.notq = v; prevPage = 0; refreshPreview(); });
        notq.active = "query".equals(g.kind);
        addRenderableWidget(notq);
        y += 22;
        // 4) translated names
        Button nm = Button.builder(Component.literal((g.names ? "\u2611 " : "\u2610 ")).append(Component.translatable("cpmwheel.grp.names")), b -> {
            g.names = !g.names; refreshPreview(); rebuildWidgets(); }).tooltip(tip("cpmwheel.grp.names.help")).bounds(x, y, w, 20).build();
        nm.active = "query".equals(g.kind);
        addRenderableWidget(nm);
        y += 34;
        // examples (static) + suggestions for the picked mod
        int cw = (w - 4) / 3;
        List<String[]> chips = new ArrayList<>();
        for (int i = 0; i < 3; i++) chips.add(EXAMPLES[i]);
        if (!sugg.isEmpty()) for (ItemGroups.Suggestion s : sugg) chips.add(new String[]{"#" + s.name() + " (" + s.count() + ")", s.value().replace("$", ""), ""});
        else for (int i = 3; i < 6; i++) chips.add(EXAMPLES[i]);
        for (int i = 0; i < chips.size(); i++) {
            String[] ex = chips.get(i);
            Component lbl = ex[0].startsWith("#") ? Component.literal(ex[0].substring(1)) : Component.translatable(ex[0]);
            addRenderableWidget(Button.builder(lbl, b -> {
                g.kind = "query"; g.value = ex[1]; g.notq = ex[2]; g.exclude.clear(); prevPage = 0;
                if (g.name == null || g.name.isBlank() || isExampleName(g.name)) g.name = lbl.getString().replaceAll(" \\(\\d+\\)$", "");
                refreshPreview(); rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.grp.ex.help", ex[1], ex[2].isBlank() ? "-" : ex[2])))
                .bounds(x + (i % 3) * (cw + 2), y + (i / 3) * 18, cw, 16).build());
        }
        y += 38;
        // advanced
        addRenderableWidget(Button.builder(Component.translatable(advanced ? "cpmwheel.grp.adv.on" : "cpmwheel.grp.adv.off"), b -> { advanced = !advanced; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.grp.adv.help")).bounds(x, y, w, 16).build());
        y += 18;
        if (advanced) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.kind", Component.translatable("cpmwheel.grp.kind." + g.kind)), b -> {
                g.kind = KINDS[(java.util.Arrays.asList(KINDS).indexOf(g.kind) + 1) % KINDS.length]; prevPage = 0; refreshPreview(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.grp.kind.help")).bounds(x, y, 120, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.grp.addheld"), b -> {
                ItemStack st = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
                if (!st.isEmpty()) { String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); if (!g.items.remove(id)) g.items.add(id); }
                refreshPreview(); rebuildWidgets(); }).tooltip(tip("cpmwheel.grp.addheld.help")).bounds(x + 122, y, 78, 16).build());
            y += 18;
        }

        // preview (right)
        prevX = x + w + 12; prevY = 40;
        prevCols = Math.max(4, (width - prevX - 8) / 18);
        prevRows = Math.max(2, (height - 32 - prevY - (advanced ? 58 : 0)) / 18);
        int per = prevCols * prevRows, pages = Math.max(1, (found.size() + per - 1) / per);
        prevPage = Math.floorMod(prevPage, pages);
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { prevPage--; rebuildWidgets(); }).bounds(width - 52, 26, 20, 12).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { prevPage++; rebuildWidgets(); }).bounds(width - 30, 26, 20, 12).build());
        }

        // bottom: name, colour, save
        int by = height - 24;
        name = new EditBox(font, x, by + 1, 110, 18, Component.literal(""));
        name.setMaxLength(40);
        name.setValue(g.name == null ? "" : g.name);
        name.setHint(Component.translatable("cpmwheel.grp.name.hint"));
        name.setResponder(v -> g.name = v);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("\u25A0").withStyle(s -> s.withColor(g.color)), b -> {
            int i = 0; for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == g.color) i = k;
            g.color = COLORS[(i + 1) % COLORS.length]; rebuildWidgets(); }).tooltip(tip("cpmwheel.grp.color.help")).bounds(x + 112, by, 20, 20).build());
        Button save = Button.builder(Component.translatable("cpmwheel.grp.save"), b -> save()).tooltip(tip("cpmwheel.grp.save.help")).bounds(x + 134, by, 96, 20).build();
        save.active = count > 0;
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent)).bounds(x + 232, by, 60, 20).build());
        if (!isNew) {
            addRenderableWidget(Button.builder(Component.translatable(confirmDelete ? "cpmwheel.grp.delete.sure" : "cpmwheel.grp.delete"), b -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                cfg.haGroups.removeIf(q -> q.id.equals(g.id));
                cfg.haItem.keySet().removeIf(k -> k.startsWith("grp:" + g.id + "|") || k.startsWith("arm/grp:" + g.id + "|"));
                if (cfg.haPhys != null) cfg.haPhys.keySet().removeIf(k -> k.startsWith("grp:" + g.id + "|"));
                HandAnim.load(cfg); parent.selectGroup(""); minecraft.setScreen(parent); }).bounds(x + 294, by, 64, 20).build());
        }
    }

    private boolean isExampleName(String n) {
        for (String[] e : EXAMPLES) if (Component.translatable(e[0]).getString().equals(n)) return true;
        for (ItemGroups.Suggestion s : sugg) if (s.name().equals(n)) return true;
        return false;
    }

    private void save() {
        if (g.name == null || g.name.isBlank()) {
            String v = g.value.replace("$", "").replace("*", "").replace("re:", "").replace("#", "").trim();
            g.name = v.isEmpty() ? (g.mod.isBlank() ? "Grupo" : g.mod) : ItemGroups.cap(v.split("[|\\s]")[0]);
        }
        orig.name = g.name; orig.mod = g.mod; orig.kind = g.kind; orig.value = g.value; orig.items = g.items;
        orig.names = g.names; orig.notq = g.notq; orig.exclude = g.exclude; orig.color = g.color;
        if (cfg.group(orig.id) == null) cfg.haGroups.add(orig);
        cfg.fixup(); HandAnim.load(cfg);
        parent.selectGroup(orig.id); // straight to adjusting the group
        minecraft.setScreen(parent);
    }

    /** item under the mouse in the preview grid, or -1 */
    private int hovered(double mx, double my) {
        if (mx < prevX || my < prevY) return -1;
        int c = (int) ((mx - prevX) / 18), r = (int) ((my - prevY) / 18);
        if (c >= prevCols || r >= prevRows) return -1;
        int i = prevPage * prevCols * prevRows + r * prevCols + c;
        return i < found.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = hovered(mx, my);
        if (i >= 0) {
            String id = BuiltInRegistries.ITEM.getKey(found.get(i)).toString();
            if (!g.exclude.remove(id)) g.exclude.add(id);
            refreshPreview(); rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        gg.fill(0, 0, width, height, 0xC0050A12);
        gg.drawString(font, title, 8, 8, Style.accent(cfg));
        int x = 8;
        gg.drawString(font, Component.translatable("cpmwheel.grp.step1"), x, 22, 0x9FB8C8);
        gg.drawString(font, Component.translatable("query".equals(g.kind) ? "cpmwheel.grp.step2" : "cpmwheel.grp.step2.adv"), x, 54, 0x9FB8C8);
        gg.drawString(font, Component.translatable("cpmwheel.grp.step3"), x, 84, 0x9FB8C8);
        gg.drawString(font, Component.translatable("cpmwheel.grp.examples"), x, 128, 0x9FB8C8);
        // preview header + plain help
        gg.drawString(font, Component.translatable("cpmwheel.grp.found", count).withStyle(s -> s.withColor(count > 0 ? 0x3BFF9D : 0xFF6B6B)), prevX, 8, 0xFFFFFF);
        gg.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.grp.help.short").getString(), Math.max(60, width - prevX - 60)), prevX, 26, 0x7FA8B8);
        int per = prevCols * prevRows;
        int hov = hovered(mx, my);
        for (int k = 0; k < per; k++) {
            int i = prevPage * per + k;
            if (i >= found.size()) break;
            Item it = found.get(i);
            int ix = prevX + (k % prevCols) * 18, iy = prevY + (k / prevCols) * 18;
            boolean off = g.exclude.contains(BuiltInRegistries.ITEM.getKey(it).toString());
            gg.fill(ix, iy, ix + 17, iy + 17, i == hov ? 0x602EE6FF : 0x30FFFFFF);
            gg.renderItem(new ItemStack(it), ix, iy);
            if (off) { gg.fill(ix, iy, ix + 17, iy + 17, 0xA0200000); gg.drawString(font, "\u2716", ix + 5, iy + 5, 0xFF5050); }
        }
        if (found.isEmpty()) gg.drawWordWrap(font, Component.translatable("cpmwheel.grp.none"), prevX, prevY + 4, Math.max(80, width - prevX - 8), 0x7FA8B8);
        if (advanced) {
            int hy = prevY + prevRows * 18 + 4;
            gg.drawWordWrap(font, Component.translatable("cpmwheel.grp.adv.text"), prevX, hy, Math.max(80, width - prevX - 8), 0xC8D6E0);
        }
        super.render(gg, mx, my, pt);
        if (hov >= 0) {
            Item it = found.get(hov);
            boolean off = g.exclude.contains(BuiltInRegistries.ITEM.getKey(it).toString());
            gg.renderComponentTooltip(font, List.of(it.getDescription(), Component.literal("\u00A77" + BuiltInRegistries.ITEM.getKey(it)),
                    Component.translatable(off ? "cpmwheel.grp.click.back" : "cpmwheel.grp.click.out")), mx, my);
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
