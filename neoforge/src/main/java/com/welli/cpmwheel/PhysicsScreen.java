package com.welli.cpmwheel;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** "Física de pendurado" for one item / group / mod / item type, per hand or "Igual nas duas mãos", with presets and a live preview. */
public class PhysicsScreen extends Screen {
    private static final int[] SLIDERS = {PendulumMath.INTENSITY, PendulumMath.STIFF, PendulumMath.DAMP, PendulumMath.WEIGHT, PendulumMath.MAX, PendulumMath.REACT, PendulumMath.GRAVITY};
    private static final String[] SL = {null, null, "intensity", "stiff", "damp", "weight", "max", "react", "gravity"};
    private static final int[] STEP = {1, 1, 5, 1, 1, 10, 1, 5, 5};
    private final Screen parent;
    private final WheelConfig cfg;
    private final String base;     // "ns:id", "grp:<id>", "mod:<ns>", "cat:<type>"
    private final Component what;
    private boolean off, askSame;
    private Component src = Component.empty();

    public PhysicsScreen(Screen parent, WheelConfig cfg, String base, Component what, boolean off) {
        super(Component.translatable("cpmwheel.phys.title")); this.parent = parent; this.cfg = cfg; this.base = base; this.what = what; this.off = off;
    }

    private boolean same() { return SameHands.on(cfg, base); }
    private String key() { return key(off); }
    private String key(boolean o) { return base + "|" + (o ? "off" : "main"); }
    private int[] own() { return cfg.haPhys.get(key()); }

    private record Inh(Component label, int[] v) {}
    /** value of one level for this hand, else the other hand's (same as the game does) */
    private int[] lv(String b) {
        int[] v = cfg.haPhys.get(b + "|" + (off ? "off" : "main"));
        return v != null ? v : cfg.haPhys.get(b + "|" + (off ? "main" : "off"));
    }
    /** what this target gets when it has no own setting (item > group > mod > type > default) */
    private Inh inherited() {
        if (cfg.haPhys.get(key(!off)) != null) return new Inh(Component.translatable("cpmwheel.phys.from.otherhand"), cfg.haPhys.get(key(!off)));
        String cat = null; String ns = null;
        if (base.startsWith("grp:")) {
            WheelConfig.ItemGroup g = cfg.group(base.substring(4));
            ns = g == null || g.mod.isBlank() ? null : g.mod;
        } else if (base.startsWith("mod:")) {
        } else if (base.startsWith("cat:")) {
            cat = base.substring(4);
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(base);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                Item it = BuiltInRegistries.ITEM.get(rl);
                for (String gid : ItemGroups.groupsOf(it)) {
                    int[] v = lv("grp:" + gid);
                    if (v != null) { WheelConfig.ItemGroup g = cfg.group(gid); return new Inh(Component.translatable("cpmwheel.phys.from.group", g == null ? gid : g.label()), v); }
                }
                ns = rl.getNamespace();
                cat = HandAnim.CATS[HandAnim.cat(new ItemStack(it))];
            }
        }
        if (ns != null) { int[] v = lv("mod:" + ns); if (v != null) return new Inh(Component.translatable("cpmwheel.phys.from.mod", ns), v); }
        if (cat == null) return new Inh(Component.translatable("cpmwheel.phys.from.each"), null);
        int[] v = lv("cat:" + cat);
        Component cl = Component.translatable("cpmwheel.adj.c." + cat);
        if (v != null) return new Inh(Component.translatable("cpmwheel.phys.from.type", cl), v);
        if (cat.equals("lantern")) return new Inh(Component.translatable("cpmwheel.phys.from.default", cl), HangPhysics.lantern());
        return new Inh(Component.translatable("cpmwheel.phys.from.none"), null);
    }

    private int[] editable(int[] start) {
        return cfg.haPhys.computeIfAbsent(key(), k -> start != null ? start.clone() : HangPhysics.lantern());
    }
    private void changed() { SameHands.sync(cfg, base); cfg.fixup(); HandAnim.load(cfg); }
    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    @Override
    protected void init() {
        int x = 8, w = 170, hw = 84, y = 46, R = 19;
        boolean same = same();
        if (same) off = false;
        int[] own = own();
        Inh inh = inherited();
        src = own != null ? Component.translatable(same ? "cpmwheel.phys.own.same" : "cpmwheel.phys.own") : Component.translatable("cpmwheel.phys.inh", inh.label());
        int[] v = own != null ? own : inh.v() != null ? inh.v() : HangPhysics.lantern();
        boolean on = own != null ? own[0] == 1 : inh.v() != null && inh.v()[0] == 1;
        final int[] start = v.clone();
        if (own == null) { start[0] = on ? 1 : 0; start[PendulumMath.INVERT] = 0; }
        // row 1: hand (principal -> secundária -> igual nas duas) | on/off
        if (askSame) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.same.keep.main"), b -> { SameHands.enable(cfg, base, 0); askSame = false; off = false; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.same.keep.help")).bounds(x, y, 41, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.same.keep.off"), b -> { SameHands.enable(cfg, base, 1); askSame = false; off = false; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.same.keep.help")).bounds(x + 43, y, 41, 18).build());
        } else addRenderableWidget(Button.builder(Component.translatable(same ? "cpmwheel.same.short" : off ? "cpmwheel.arm.off" : "cpmwheel.arm.main"), b -> {
                if (same) { SameHands.disable(cfg, base); off = false; }
                else if (!off) off = true;
                else if (SameHands.differ(cfg, base)) askSame = true;
                else { SameHands.enable(cfg, base, 1); off = false; }
                rebuildWidgets(); }).tooltip(tip("cpmwheel.same.help")).bounds(x, y, hw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(on ? "cpmwheel.phys.on" : "cpmwheel.phys.off"), b -> {
            int[] e = editable(start); e[0] = on ? 0 : 1; changed(); rebuildWidgets(); })
            .tooltip(tip("cpmwheel.phys.on.help")).bounds(x + 86, y, hw, 18).build());
        y += R;
        // row 2: presets
        int pw = (w - 4) / 3;
        for (int i = 0; i < HangPhysics.PRESETS.length; i++) {
            final int pi = i;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.p." + HangPhysics.PRESETS[i]), b -> {
                int[] n = HangPhysics.PRESET_VALUES[pi].clone();
                int[] cur = cfg.haPhys.get(key()); if (cur != null) n[PendulumMath.INVERT] = cur[PendulumMath.INVERT];
                cfg.haPhys.put(key(), n); changed(); HangPhysics.kick(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.phys.p." + HangPhysics.PRESETS[i] + ".help")).bounds(x + i * (pw + 2), y, pw, 18).build());
        }
        y += R;
        // row 3: pivot | limit
        addRenderableWidget(Button.builder(Component.translatable(v[PendulumMath.PIVOT] == 1 ? "cpmwheel.phys.pivot.top" : "cpmwheel.phys.pivot.grip"), b -> {
            int[] e = editable(start); e[PendulumMath.PIVOT] = 1 - e[PendulumMath.PIVOT]; changed(); rebuildWidgets(); })
            .tooltip(tip("cpmwheel.phys.pivot.help")).bounds(x, y, hw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(v[PendulumMath.RAISE] == 1 ? "cpmwheel.phys.raise.on" : "cpmwheel.phys.raise.off"), b -> {
            int[] e = editable(start); e[PendulumMath.RAISE] = 1 - e[PendulumMath.RAISE]; changed(); rebuildWidgets(); })
            .tooltip(tip("cpmwheel.phys.raise.help")).bounds(x + 86, y, hw, 18).build());
        y += R;
        for (int i : SLIDERS) {
            final int idx = i, min = HangPhysics.MIN[i], max = HangPhysics.MAX[i], step = STEP[i];
            String k = "cpmwheel.phys." + SL[i];
            AbstractSliderButton s = new AbstractSliderButton(x, y, w, 18, Component.translatable(k, v[i]), (v[i] - min) / (double) (max - min)) {
                int val = v[idx];
                @Override protected void updateMessage() { setMessage(Component.translatable(k, val)); }
                @Override protected void applyValue() {
                    val = min + (int) Math.round(value * (max - min) / step) * step;
                    boolean fresh = !cfg.haPhys.containsKey(key());
                    editable(start)[idx] = val; SameHands.sync(cfg, base); HandAnim.load(cfg);
                    if (fresh) src = Component.translatable(same() ? "cpmwheel.phys.own.same" : "cpmwheel.phys.own");
                }
            };
            s.setTooltip(tip(k + ".help"));
            addRenderableWidget(s);
            y += R;
        }
        // invert ("Inverter balanço", debug escape hatch) - always per hand
        if (same) {
            for (int h = 0; h < 2; h++) {
                final boolean o = h == 1;
                int[] hv = cfg.haPhys.get(key(o));
                boolean inv = hv != null && hv[PendulumMath.INVERT] == 1;
                Button ib = Button.builder(Component.translatable(o ? "cpmwheel.phys.invert.off" : "cpmwheel.phys.invert.main", inv ? "ON" : "OFF"), b -> {
                    cfg.haPhys.computeIfAbsent(key(false), q -> start.clone());   // both hands need their own copy first
                    SameHands.sync(cfg, base);
                    int[] e = cfg.haPhys.computeIfAbsent(key(o), q -> start.clone()); e[PendulumMath.INVERT] = inv ? 0 : 1; changed(); rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.phys.invert.help")).bounds(x + h * 86, y, hw, 18).build();
                addRenderableWidget(ib);
            }
        } else {
            boolean inv = own != null && own[PendulumMath.INVERT] == 1;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.invert", inv ? "ON" : "OFF"), b -> {
                int[] e = editable(start); e[PendulumMath.INVERT] = inv ? 0 : 1; changed(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.phys.invert.help")).bounds(x, y, w, 18).build());
        }
        y += R;
        Button reset = Button.builder(Component.translatable("cpmwheel.io.reset"), b -> { cfg.haPhys.remove(key()); changed(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.phys.reset.help")).bounds(x, y, hw, 18).build();
        reset.active = own != null;
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.kick"), b -> HangPhysics.kick())
                .tooltip(tip("cpmwheel.phys.kick.help")).bounds(x + 86, y, hw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, Math.max(y + 22, height - 24), w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, 186, height, 0x90050A12); // only the left side darkened: the hand stays visible as the live preview
        g.drawString(font, title, 8, 8, Style.accent(cfg));
        g.drawString(font, font.plainSubstrByWidth(what.getString(), 172), 8, 20, 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(src.getString(), 172), 8, 32, 0x9FB8C8);
        // pivot diagram: fist, pivot dot, item hanging and swinging with the live angle
        int[] v = own(); if (v == null) v = inherited().v();
        int cx = 220, cy = height - 90;
        if (cx + 40 < width) {
            boolean top = v != null && v[PendulumMath.PIVOT] == 1;
            float ang = HangPhysics.previewAngle(off ? 1 : 0);
            g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFFC8A27A); // fist
            int py = top ? cy - 22 : cy;
            g.pose().pushPose();
            g.pose().translate(cx, py, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-ang)); // GUI y points down: screen-right swing = negative here
            g.fill(-1, 0, 1, top ? 26 : 8, 0xFF9FB8C8);       // strap
            g.fill(-9, top ? 26 : 8, 9, top ? 48 : 30, 0xFF6B8E5A); // item
            g.pose().popPose();
            g.fill(cx - 2, py - 2, cx + 2, py + 2, 0xFFFF3B5C); // pivot
            g.drawString(font, Component.translatable("cpmwheel.phys.preview"), cx - 30, cy + 44, 0x9FB8C8);
        }
        g.drawWordWrap(font, Component.translatable("cpmwheel.phys.help"), 196, 8, Math.max(80, width - 204), 0xC8D6E0);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { changed(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
