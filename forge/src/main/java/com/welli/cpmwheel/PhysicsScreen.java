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

/** 1.5.8 "Física de pendurado" for one item / group / mod / item type, per hand, with presets and a live preview. */
public class PhysicsScreen extends Screen {
    private static final String[] SL = {null, null, "intensity", "stiff", "damp", "weight", "max", "react"};
    private static final int[] STEP = {1, 1, 5, 1, 1, 10, 1, 5};
    private final Screen parent;
    private final WheelConfig cfg;
    private final String base;     // "ns:id", "grp:<id>", "mod:<ns>", "cat:<type>"
    private final Component what;
    private boolean off;
    private Component src = Component.empty();

    public PhysicsScreen(Screen parent, WheelConfig cfg, String base, Component what, boolean off) {
        super(Component.translatable("cpmwheel.phys.title")); this.parent = parent; this.cfg = cfg; this.base = base; this.what = what; this.off = off;
    }

    private String key() { return base + "|" + (off ? "off" : "main"); }
    private int[] own() { return cfg.haPhys.get(key()); }

    private record Inh(Component label, int[] v) {}
    /** what this target gets when it has no own setting (item > group > mod > type > default) */
    private Inh inherited() {
        String h = "|" + (off ? "off" : "main");
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
                    int[] v = cfg.haPhys.get("grp:" + gid + h);
                    if (v != null) { WheelConfig.ItemGroup g = cfg.group(gid); return new Inh(Component.translatable("cpmwheel.phys.from.group", g == null ? gid : g.label()), v); }
                }
                ns = rl.getNamespace();
                cat = HandAnim.CATS[HandAnim.cat(new ItemStack(it))];
            }
        }
        if (ns != null) { int[] v = cfg.haPhys.get("mod:" + ns + h); if (v != null) return new Inh(Component.translatable("cpmwheel.phys.from.mod", ns), v); }
        if (cat == null) return new Inh(Component.translatable("cpmwheel.phys.from.each"), null);
        int[] v = cfg.haPhys.get("cat:" + cat + h);
        Component cl = Component.translatable("cpmwheel.adj.c." + cat);
        if (v != null) return new Inh(Component.translatable("cpmwheel.phys.from.type", cl), v);
        if (cat.equals("lantern")) return new Inh(Component.translatable("cpmwheel.phys.from.default", cl), HangPhysics.lantern());
        return new Inh(Component.translatable("cpmwheel.phys.from.none"), null);
    }

    private int[] editable(int[] start) {
        return cfg.haPhys.computeIfAbsent(key(), k -> start != null ? start.clone() : HangPhysics.lantern());
    }
    private void changed() { cfg.fixup(); HandAnim.load(cfg); }

    @Override
    protected void init() {
        int x = 8, w = 170, y = 34;
        int[] own = own();
        Inh inh = inherited();
        src = own != null ? Component.translatable("cpmwheel.phys.own") : Component.translatable("cpmwheel.phys.inh", inh.label());
        int[] v = own != null ? own : inh.v() != null ? inh.v() : HangPhysics.lantern();
        boolean on = own != null ? own[0] == 1 : inh.v() != null && inh.v()[0] == 1;
        final int[] start = v.clone();
        if (own == null) start[0] = on ? 1 : 0;
        y += 12;
        addRenderableWidget(Button.builder(Component.translatable(off ? "cpmwheel.arm.off" : "cpmwheel.arm.main"), b -> { off = !off; rebuildWidgets(); }).bounds(x, y, 84, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(on ? "cpmwheel.phys.on" : "cpmwheel.phys.off"), b -> {
            int[] e = editable(start); e[0] = on ? 0 : 1; changed(); rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.on.help"))).bounds(x + 86, y, 84, 18).build());
        y += 20;
        int pw = (w - 4) / 3;
        for (int i = 0; i < HangPhysics.PRESETS.length; i++) {
            final int pi = i;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.p." + HangPhysics.PRESETS[i]), b -> {
                cfg.haPhys.put(key(), HangPhysics.PRESET_VALUES[pi].clone()); changed(); HangPhysics.kick(); rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.p." + HangPhysics.PRESETS[i] + ".help"))).bounds(x + i * (pw + 2), y, pw, 18).build());
        }
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.pivot", Component.translatable(v[1] == 1 ? "cpmwheel.phys.pivot.top" : "cpmwheel.phys.pivot.grip")), b -> {
            int[] e = editable(start); e[1] = 1 - e[1]; changed(); rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.pivot.help"))).bounds(x, y, w, 18).build());
        y += 20;
        for (int i = 2; i < 8; i++) {
            final int idx = i, min = HangPhysics.MIN[i], max = HangPhysics.MAX[i], step = STEP[i];
            String k = "cpmwheel.phys." + SL[i];
            AbstractSliderButton s = new AbstractSliderButton(x, y, w, 18, Component.translatable(k, v[i]), (v[i] - min) / (double) (max - min)) {
                int val = v[idx];
                @Override protected void updateMessage() { setMessage(Component.translatable(k, val)); }
                @Override protected void applyValue() {
                    val = min + (int) Math.round(value * (max - min) / step) * step;
                    boolean fresh = !cfg.haPhys.containsKey(key());
                    editable(start)[idx] = val; HandAnim.load(cfg);
                    if (fresh) src = Component.translatable("cpmwheel.phys.own");
                }
            };
            s.setTooltip(Tooltip.create(Component.translatable(k + ".help")));
            addRenderableWidget(s);
            y += 20;
        }
        Button reset = Button.builder(Component.translatable("cpmwheel.io.reset"), b -> { cfg.haPhys.remove(key()); changed(); rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.reset.help"))).bounds(x, y, 84, 18).build();
        reset.active = own != null;
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.phys.kick"), b -> HangPhysics.kick())
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.phys.kick.help"))).bounds(x + 86, y, 84, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 24, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, 186, height, 0x90050A12); // only the left side darkened: the hand stays visible as the live preview
        g.drawString(font, title, 8, 8, Style.accent(cfg));
        g.drawString(font, font.plainSubstrByWidth(what.getString(), 172), 8, 20, 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(src.getString(), 172), 8, 34, 0x9FB8C8);
        // pivot diagram: fist, pivot dot, item hanging and swinging with the live angle
        int[] v = own(); Inh inh = v == null ? inherited() : null;
        if (v == null) v = inh.v();
        int cx = 220, cy = height - 90;
        if (cx + 40 < width) {
            boolean top = v != null && v[1] == 1;
            float ang = HangPhysics.previewAngle(off ? 1 : 0);
            g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFFC8A27A); // fist
            int py = top ? cy - 22 : cy;
            g.pose().pushPose();
            g.pose().translate(cx, py, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-ang));
            g.fill(-1, 0, 1, top ? 26 : 8, 0xFF9FB8C8);       // strap
            g.fill(-9, top ? 26 : 8, 9, top ? 48 : 30, 0xFF6B8E5A); // item
            g.pose().popPose();
            g.fill(cx - 2, py - 2, cx + 2, py + 2, 0xFFFF3B5C); // pivot
            g.drawString(font, Component.translatable("cpmwheel.phys.preview"), cx - 30, cy + 44, 0x9FB8C8);
        }
        g.drawWordWrap(font, Component.translatable("cpmwheel.phys.help"), 196, 8, Math.max(80, width - 204), 0xC8D6E0);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { changed(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
