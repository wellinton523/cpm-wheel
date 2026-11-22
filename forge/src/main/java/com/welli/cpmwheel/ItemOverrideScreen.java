package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
 * "Itens e mods": per-item / per-group / per-mod hand pose overrides with live preview.
 * Priority (1.5.7): item > group > mod > item type (category) > default. Works for the item and for the arm, per hand.
 */
public class ItemOverrideScreen extends Screen {
    private static final String[] KEYS = {"x", "y", "z", "scale", "rx", "ry", "rz"};
    private static final int[] MIN = {-50, -50, -50, -80, -180, -180, -180}, MAX = {50, 50, 50, 150, 180, 180, 180}, STEP = {1, 1, 1, 5, 5, 5, 5};
    private static final String[] AKEYS = {"x", "y", "z", "rx", "ry", "rz", "scale"};
    private static final int[] AMIN = {-60, -60, -60, -90, -90, -90, -60}, AMAX = {60, 60, 60, 90, 90, 90, 100}, ASTEP = {1, 1, 1, 1, 1, 1, 5};
    static final String[] MODES = {"item", "group", "mod"};
    static final String[] FILTERS = {"set", "all", "mod", "inherit"};
    /** remembered between openings */
    private static String mode = "item", filterMode = "set";
    private static boolean off = false, armMode = false;
    /** 1.5.9: waiting for "keep main / keep off-hand values" before turning "Igual nas duas mãos" on */
    private String askSame = null;
    private boolean same() { return !target.isEmpty() && SameHands.on(cfg, prefix() + target); }
    private static String target = "", query = "";
    private final Screen parent;
    private final WheelConfig cfg;
    private EditBox search;
    private final List<String> results = new ArrayList<>();
    private int page = 0;
    private Component srcLine = Component.empty(), groupsLine = Component.empty();
    private int infoY = 0;

    public ItemOverrideScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.io.title")); this.parent = parent; this.cfg = cfg; }

    // ---------- keys ----------
    private String hand() { return off ? "off" : "main"; }
    private String prefix() { return switch (mode) { case "group" -> "grp:"; case "mod" -> "mod:"; default -> ""; }; }
    private String key() { return key(prefix() + target); }
    private String key(String base) { return (armMode ? "arm/" : "") + base + "|" + hand(); }
    private boolean hasAny(String base) {
        for (String h : new String[]{"|main", "|off"}) if (cfg.haItem.containsKey(base + h) || cfg.haItem.containsKey("arm/" + base + h)) return true;
        return false;
    }

    /** what the current target inherits (not counting its own value): label + values (null = zeros / category) */
    private boolean hasPhys(String base) { return cfg.haPhys.containsKey(base + "|main") || cfg.haPhys.containsKey(base + "|off"); }
    private record Src(Component label, int[] values) {}
    private Src inherited() {
        if (mode.equals("item")) {
            ResourceLocation rl = ResourceLocation.tryParse(target);
            Item it = rl == null || !BuiltInRegistries.ITEM.containsKey(rl) ? null : BuiltInRegistries.ITEM.get(rl);
            if (it != null) {
                for (WheelConfig.ItemGroup g : cfg.haGroups)
                    if (ItemGroups.matches(g, it, rl)) { int[] v = cfg.haItem.get(key("grp:" + g.id)); if (v != null) return new Src(Component.translatable("cpmwheel.io.src.group", g.label()), v); }
                int[] v = cfg.haItem.get(key("mod:" + rl.getNamespace()));
                if (v != null) return new Src(Component.translatable("cpmwheel.io.src.mod", rl.getNamespace()), v);
                return base(new ItemStack(it));
            }
            return base(null);
        }
        if (mode.equals("group")) {
            WheelConfig.ItemGroup g = cfg.group(target);
            if (g != null && !g.mod.isBlank()) { int[] v = cfg.haItem.get(key("mod:" + g.mod)); if (v != null) return new Src(Component.translatable("cpmwheel.io.src.mod", g.mod), v); }
        }
        return base(null);
    }
    private Src base(ItemStack st) {
        if (armMode) return new Src(Component.translatable("cpmwheel.io.src.global"), null);
        if (st != null) return new Src(Component.translatable("cpmwheel.io.src.cat", Component.translatable("cpmwheel.adj.c." + HandAnim.CATS[HandAnim.cat(st)])), null);
        return new Src(Component.translatable("cpmwheel.io.src.cat", Component.translatable("cpmwheel.io.src.cattype")), null);
    }

    private String nsOfTarget() {
        if (target.isEmpty()) return "";
        if (mode.equals("mod")) return target;
        if (mode.equals("group")) { WheelConfig.ItemGroup g = cfg.group(target); return g == null ? "" : g.mod; }
        int c = target.indexOf(':'); return c < 0 ? "" : target.substring(0, c);
    }

    private void filter() {
        results.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        switch (mode) {
            case "mod" -> {
                TreeSet<String> ns = new TreeSet<>();
                for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) ns.add(rl.getNamespace());
                for (String n : ns) if (q.isEmpty() || n.contains(q) || ModPickerScreen.modName(n).toLowerCase(Locale.ROOT).contains(q)) results.add(n);   // 1.9.1: display name too
            }
            case "group" -> {
                for (WheelConfig.ItemGroup g : cfg.haGroups) {
                    String hay = (g.label() + " " + g.mod + " " + g.value).toLowerCase(Locale.ROOT);
                    if (q.isEmpty() || hay.contains(q)) results.add(g.id);
                }
            }
            default -> {
                String ns = nsOfTarget();
                for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                    String id = rl.toString();
                    if (!q.isEmpty() && !id.contains(q)) continue;
                    boolean keep = switch (filterMode) {
                        case "all" -> true;
                        case "mod" -> ns.isEmpty() || rl.getNamespace().equals(ns);
                        case "inherit" -> !hasAny(id) && inheritsFromGroupOrMod(rl);
                        default -> hasAny(id);
                    };
                    // "com ajuste" with a search text: search everything (old behaviour), starred ones first
                    if (!keep && "set".equals(filterMode) && !q.isEmpty()) keep = true;
                    if (keep) results.add(id);
                    if (results.size() > 400) break;
                }
                results.sort((a, b) -> { boolean sa = hasAny(a), sb = hasAny(b); return sa != sb ? (sa ? -1 : 1) : a.compareTo(b); });
            }
        }
    }
    private boolean inheritsFromGroupOrMod(ResourceLocation rl) {
        if (hasAny("mod:" + rl.getNamespace())) return true;
        Item it = BuiltInRegistries.ITEM.get(rl);
        for (WheelConfig.ItemGroup g : cfg.haGroups) if (hasAny("grp:" + g.id) && ItemGroups.matches(g, it, rl)) return true;
        return false;
    }

    private String rowLabel(String r) {
        if (mode.equals("group")) {
            WheelConfig.ItemGroup g = cfg.group(r);
            String l = g == null ? r : g.label() + (g.mod.isBlank() ? "" : " (" + g.mod + ")");
            return (hasAny("grp:" + r) ? "\u2605 " : "") + (SameHands.on(cfg, "grp:" + r) ? "\u21C6 " : "") + l;
        }
        return (hasAny(prefix() + r) ? "\u2605 " : "") + (SameHands.on(cfg, prefix() + r) ? "\u21C6 " : "") + handsMark(r) + r;
    }

    // ---------- 1.9.1 hand animations off per mod / item ----------
    private String handsKey(String r) { return mode.equals("mod") ? HandsOff.modKey(r) : r; }
    /** row marker: animations off (both / one hand) or an item forced back on */
    private String handsMark(String r) {
        if (mode.equals("group")) return "";
        int st = HandsOff.state(cfg.haHandsOff, handsKey(r));
        return st == HandsOff.BOTH ? "\u2716 " : st > 0 ? "\u00BD\u2716 " : (st == 0 && !mode.equals("mod")) ? "\u2714 " : "";
    }
    private Component handsLabel() {
        String k = handsKey(target);
        int st = HandsOff.state(cfg.haHandsOff, k);
        Component s = Component.translatable("cpmwheel.hoff.st." + HandsOff.word(st));
        if (st == -1) {   // item inherits: show what its mod says
            int c = target.indexOf(':');
            int ms = c < 0 ? 0 : HandsOff.state(cfg.haHandsOff, HandsOff.modKey(target.substring(0, c)));
            s = Component.translatable("cpmwheel.hoff.st.inherit", Component.translatable("cpmwheel.hoff.st." + HandsOff.word(ms)));
        }
        return Component.translatable("cpmwheel.hoff.btn", s);
    }

    @Override
    protected void init() {
        int x = 8, y = 26, x2 = 166;
        if (mode.equals("group") && !target.isEmpty() && cfg.group(target) == null) target = "";
        filter();
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.mode." + mode), b -> {
            mode = MODES[(java.util.Arrays.asList(MODES).indexOf(mode) + 1) % MODES.length]; target = ""; page = 0; rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.translatable("cpmwheel.io.mode.help"))).bounds(x, y, 150, 20).build()); y += 22;
        search = new EditBox(font, x, y, 150, 18, Component.literal(""));
        search.setHint(Component.translatable(mode.equals("group") ? "cpmwheel.io.search.group" : "cpmwheel.io.search"));
        search.setValue(query);
        search.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(search); search.setFocused(true); } });
        addRenderableWidget(search); y += 22;
        if (mode.equals("item")) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.filter", Component.translatable("cpmwheel.io.filter." + filterMode)), b -> {
                filterMode = FILTERS[(java.util.Arrays.asList(FILTERS).indexOf(filterMode) + 1) % FILTERS.length]; page = 0; rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.io.filter.help"))).bounds(x, y, 150, 20).build()); y += 22;
        }
        if (mode.equals("group")) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.newgroup"), b -> {
                WheelConfig.ItemGroup g = new WheelConfig.ItemGroup(); g.id = WheelConfig.newId("g"); g.mod = heldNs(); g.kind = "query";
                minecraft.setScreen(new GroupEditScreen(this, cfg, g, true)); }).bounds(x, y, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.suggest"), b -> minecraft.setScreen(new GroupSuggestScreen(this, cfg, heldNs())))
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.io.suggest.help"))).bounds(x + 76, y, 74, 20).build()); y += 24;
        } else {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.held"), b -> {
                ItemStack st = heldStack();
                if (!st.isEmpty()) { ResourceLocation rl = BuiltInRegistries.ITEM.getKey(st.getItem()); target = mode.equals("mod") ? rl.getNamespace() : rl.toString(); }
                rebuildWidgets(); }).bounds(x, y, 150, 20).build()); y += 24;
        }
        int per = Math.max(3, (height - 26 - 24 - y) / 20);
        int pages = Math.max(1, (results.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(results.size(), (page + 1) * per); i++) {
            String r = results.get(i);
            Component lbl = Component.literal(rowLabel(r));
            if (mode.equals("group")) { WheelConfig.ItemGroup rg = cfg.group(r); if (rg != null) lbl = Component.empty().append(Component.literal("\u25A0 ").withStyle(st -> st.withColor(rg.color))).append(lbl); }
            Button bt = Button.builder(lbl, b -> { target = r; rebuildWidgets(); }).bounds(x, y, 150, 18).build();
            bt.active = !r.equals(target);
            addRenderableWidget(bt); y += 20;
        }
        if (results.isEmpty()) { /* text drawn in render */ }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, y, 106, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 130, y, 20, 18).build());
        }

        int ry = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.enabled", cfg.haItemOn ? "ON" : "OFF"), b -> {
            cfg.haItemOn = !cfg.haItemOn; HandAnim.load(cfg); rebuildWidgets(); }).bounds(x2, ry, 150, 20).build()); ry += 22;
        // hand: Mão principal -> Mão secundária -> Igual nas duas mãos (per target)
        boolean same = same();
        if (same) off = false;
        if (askSame != null && askSame.equals(prefix() + target)) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.same.keep.main"), b -> { SameHands.enable(cfg, askSame, 0); askSame = null; off = false; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.same.keep.help"))).bounds(x2, ry, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.same.keep.off"), b -> { SameHands.enable(cfg, askSame, 1); askSame = null; off = false; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.same.keep.help"))).bounds(x2 + 76, ry, 74, 20).build());
        } else {
            askSame = null;
            addRenderableWidget(Button.builder(Component.translatable(same ? "cpmwheel.same.on" : off ? "cpmwheel.arm.off" : "cpmwheel.arm.main"), b -> {
                String base = prefix() + target;
                if (same) { SameHands.disable(cfg, base); off = false; }           // split back into two independent copies
                else if (!off) off = true;
                else if (target.isEmpty()) off = false;
                else if (SameHands.differ(cfg, base)) askSame = base;             // ask which hand's values to keep
                else { SameHands.enable(cfg, base, 1); off = false; }
                rebuildWidgets(); }).tooltip(Tooltip.create(Component.translatable("cpmwheel.same.help"))).bounds(x2, ry, 150, 20).build());
        }
        ry += 22;
        addRenderableWidget(Button.builder(Component.translatable(armMode ? "cpmwheel.io.arm" : "cpmwheel.io.item"), b -> { armMode = !armMode; rebuildWidgets(); }).bounds(x2, ry, 96, 20).build());
        Button phys = Button.builder(Component.translatable(hasPhys(prefix() + target) ? "cpmwheel.phys.btn.set" : "cpmwheel.phys.btn"), b -> minecraft.setScreen(new PhysicsScreen(this, cfg, prefix() + target,
                Component.literal(rowLabel(target).replace("\u2605 ", "").replace("\u21C6 ", "")), off)))
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.btn.help"))).bounds(x2 + 98, ry, 52, 20).build();
        phys.active = !target.isEmpty();
        addRenderableWidget(phys); ry += 46;
        if (!target.isEmpty() && !mode.equals("group")) {   // 1.9.1: "Animações da mão" for this mod / item (in the gap under the title lines)
            addRenderableWidget(Button.builder(handsLabel(), b -> {
                String k = handsKey(target);
                HandsOff.set(cfg.haHandsOff, k, HandsOff.next(HandsOff.state(cfg.haHandsOff, k), mode.equals("mod")));
                HandAnim.load(cfg); rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable(mode.equals("mod") ? "cpmwheel.hoff.help.mod" : "cpmwheel.hoff.help.item")))
                .bounds(x2, ry - 2, 150, 20).build());
            ry += 20;
        }
        srcLine = Component.empty(); groupsLine = Component.empty();
        if (!target.isEmpty()) {
            int[] own = cfg.haItem.get(key());
            Src src = inherited();
            srcLine = own != null ? Component.translatable("cpmwheel.io.own", Component.translatable("cpmwheel.io.lvl." + mode))
                    : Component.translatable("cpmwheel.io.inh", src.label());
            if (mode.equals("item")) groupsLine = groupsOf(target);
            int[] v = own != null ? own : src.values() != null ? src.values() : new int[7];
            final int[] start = v.clone();
            for (int i = 0; i < 7; i++) {
                final int idx = i, min = armMode ? AMIN[i] : MIN[i], max = armMode ? AMAX[i] : MAX[i], step = armMode ? ASTEP[i] : STEP[i];
                String k = armMode ? "cpmwheel.arm." + AKEYS[i] : "cpmwheel.adj." + KEYS[i];
                addRenderableWidget(new AbstractSliderButton(x2, ry, 150, 20, Component.translatable(k, v[i]), (v[i] - min) / (double) (max - min)) {
                    int val = v[idx];
                    @Override protected void updateMessage() { setMessage(Component.translatable(k, val)); }
                    @Override protected void applyValue() {
                        val = min + (int) Math.round(value * (max - min) / step) * step;
                        boolean fresh = !cfg.haItem.containsKey(key());
                        cfg.haItem.computeIfAbsent(key(), q -> start.clone())[idx] = val; HandAnim.load(cfg);
                        SameHands.sync(cfg, prefix() + target);
                        if (fresh) srcLine = Component.translatable("cpmwheel.io.own", Component.translatable("cpmwheel.io.lvl." + mode));
                    }
                });
                ry += 22;
            }
            Button reset = Button.builder(Component.translatable("cpmwheel.io.reset"), b -> { cfg.haItem.remove(key()); SameHands.sync(cfg, prefix() + target); HandAnim.load(cfg); rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.io.reset.help"))).bounds(x2, ry, 74, 20).build();
            reset.active = own != null;
            addRenderableWidget(reset);
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.copy"), b -> {
                int[] cur = cfg.haItem.get(key());
                if (cur == null) cur = start;
                minecraft.setScreen(new CopyAdjustScreen(this, cfg, cur.clone(), armMode, off, nsOfTarget(), prefix() + target)); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.io.copy.help"))).bounds(x2 + 76, ry, 74, 20).build());
            ry += 22;
            if (mode.equals("group")) {
                WheelConfig.ItemGroup g = cfg.group(target);
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.io.editgroup"), b -> minecraft.setScreen(new GroupEditScreen(this, cfg, g, false)))
                        .bounds(x2, ry, 150, 20).build());
                ry += 22;
            }
            infoY = ry + 2;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 24, 308, 20).build());
    }

    private Component groupsOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return Component.empty();
        Item it = BuiltInRegistries.ITEM.get(rl);
        List<String> n = new ArrayList<>();
        for (WheelConfig.ItemGroup g : cfg.haGroups) if (ItemGroups.matches(g, it, rl)) n.add(g.label());
        return n.isEmpty() ? Component.empty() : Component.translatable("cpmwheel.io.ingroups", String.join(", ", n));
    }

    private ItemStack heldStack() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(off ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    private String heldNs() {
        String ns = nsOfTarget();
        if (!ns.isEmpty()) return ns;
        ItemStack st = heldStack();
        return st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace();
    }

    void selectGroup(String id) { mode = "group"; target = id; query = ""; page = 0; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, 324, height, 0x60050A12); // backdrop only behind the controls: the hand stays visible on the right
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        Component head;
        if (target.isEmpty()) head = Component.translatable("cpmwheel.io.pick." + mode);
        else if (mode.equals("group")) { WheelConfig.ItemGroup gr = cfg.group(target); head = Component.literal("grupo: " + (gr == null ? target : gr.label())); }
        else head = Component.literal((mode.equals("mod") ? "mod: " : "") + target);
        g.drawString(font, font.plainSubstrByWidth(head.getString(), 156), 166, 26 + 68, 0xFFE08A);
        g.drawString(font, font.plainSubstrByWidth(srcLine.getString(), 156), 166, 26 + 79, 0x9FB8C8);
        if (!groupsLine.getString().isEmpty()) g.drawString(font, font.plainSubstrByWidth(groupsLine.getString(), 156), 166, infoY, 0x7FA8B8);
        if (results.isEmpty()) g.drawString(font, Component.translatable("cpmwheel.io.empty." + mode), 10, 26 + 22 * 3 + 30, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void onClose() { cfg.fixup(); HandAnim.load(cfg); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
