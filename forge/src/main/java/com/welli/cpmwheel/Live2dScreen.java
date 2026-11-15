package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.7.0 "Física Live2D": chains (Cabelo, Cauda…) of CPM value layers moved by spring physics.
 * Left: global switches + chain list. Middle: the chain and its links with live values. Right: the selected link.
 */
public class Live2dScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private static String selChain = "";
    private static int selLink = 0;
    private int page = 0;
    private boolean confirmDelete = false;
    private int linksY = 0, liveY = 0;

    public Live2dScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.l2d.title")); this.parent = parent; this.cfg = cfg; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }
    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }
    private Live2dMath.Chain sel() { for (Live2dMath.Chain c : cfg.l2dChains) if (c.id.equals(selChain)) return c; return null; }

    private Live2dMath.Chain preset(String kind) {
        Live2dMath.Chain c = new Live2dMath.Chain();
        c.id = WheelConfig.newId("c");
        if (kind.equals("tail")) {
            c.name = Component.translatable("cpmwheel.l2d.preset.tail").getString();
            Live2dMath.Link a = new Live2dMath.Link("bodyYaw", true).add("side", 30, true); a.len = 160; a.weight = 150; a.damp = 25;
            Live2dMath.Link b = new Live2dMath.Link("prev", true); b.len = 140; b.damp = 25;
            c.links.add(a); c.links.add(b);
        } else {
            c.name = Component.translatable("cpmwheel.l2d.preset.hair").getString();
            // head turn 100 % + body turn 50 % inverted + a little forward/backward walking (follow-through)
            Live2dMath.Link a = new Live2dMath.Link("headYaw", true).add("bodyYaw", 50, true).add("fwd", 20, true);
            Live2dMath.Link b = new Live2dMath.Link("prev", true); b.len = 80;
            c.links.add(a); c.links.add(b);
        }
        return c;
    }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int x = 8, y = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.on", onoff(cfg.l2dOn)), b -> { cfg.l2dOn = !cfg.l2dOn; Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.on.help")).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.sync", onoff(cfg.l2dSync)), b -> { cfg.l2dSync = !cfg.l2dSync; Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.sync.help")).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.pause", onoff(cfg.l2dPauseTrig)), b -> { cfg.l2dPauseTrig = !cfg.l2dPauseTrig; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.pause.help")).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.reset"), b -> Live2d.reset())
                .tooltip(tip("cpmwheel.l2d.reset.help")).bounds(x, y, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.guide"), b -> minecraft.setScreen(new Live2dGuideScreen(this, cfg)))
                .tooltip(tip("cpmwheel.l2d.guide.help")).bounds(x + 76, y, 74, 20).build()); y += 26;

        // chains
        Live2dMath.Chain c = sel();
        if (c == null && !cfg.l2dChains.isEmpty()) { c = cfg.l2dChains.get(0); selChain = c.id; selLink = 0; }
        int per = Math.max(2, (height - 54 - y) / 20);
        int pages = Math.max(1, (cfg.l2dChains.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(cfg.l2dChains.size(), (page + 1) * per); i++) {
            Live2dMath.Chain q = cfg.l2dChains.get(i);
            Button bt = Button.builder(Component.literal((q.on ? "" : "\u2716 ") + (q.name.isBlank() ? "?" : q.name) + " (" + q.links.size() + ")"),
                    b -> { selChain = q.id; selLink = 0; confirmDelete = false; rebuildWidgets(); }).bounds(x, y, 150, 18).build();
            bt.active = !q.id.equals(selChain);
            addRenderableWidget(bt); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 130, y, 20, 18).build());
        }
        if (cfg.l2dChains.size() < 16) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.add.hair"), b -> { Live2dMath.Chain n = preset("hair"); cfg.l2dChains.add(n); selChain = n.id; selLink = 0; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.l2d.add.hair.help")).bounds(x, height - 50, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.add.tail"), b -> { Live2dMath.Chain n = preset("tail"); cfg.l2dChains.add(n); selChain = n.id; selLink = 0; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.l2d.add.tail.help")).bounds(x + 76, height - 50, 74, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 150, 20).build());

        if (c == null) return;
        final Live2dMath.Chain ch = c;
        int x2 = 164, y2 = 26;
        EditBox name = new EditBox(font, x2 + 1, y2 + 1, 148, 18, Component.literal(""));
        name.setMaxLength(32); name.setValue(ch.name); name.setHint(Component.translatable("cpmwheel.l2d.name.hint"));
        name.setResponder(v -> ch.name = v);
        addRenderableWidget(name); y2 += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.chain.on", onoff(ch.on)), b -> { ch.on = !ch.on; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.chain.on.help")).bounds(x2, y2, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmDelete ? "cpmwheel.eff.delete.sure" : "cpmwheel.l2d.chain.del"), b -> {
            if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
            cfg.l2dChains.remove(ch); selChain = ""; confirmDelete = false; rebuildWidgets(); }).bounds(x2 + 76, y2, 74, 20).build()); y2 += 22;
        int ci = cfg.l2dChains.indexOf(ch);
        Button up = Button.builder(Component.translatable("cpmwheel.l2d.chain.up"), b -> { move(cfg.l2dChains, ci, -1); rebuildWidgets(); }).bounds(x2, y2, 74, 20).build();
        up.active = ci > 0; addRenderableWidget(up);
        Button dn = Button.builder(Component.translatable("cpmwheel.l2d.chain.down"), b -> { move(cfg.l2dChains, ci, 1); rebuildWidgets(); }).bounds(x2 + 76, y2, 74, 20).build();
        dn.active = ci < cfg.l2dChains.size() - 1; addRenderableWidget(dn); y2 += 22;
        // 1.7.2 "Desativar quando"
        addRenderableWidget(Button.builder(Component.translatable(ch.off.isEmpty() ? "cpmwheel.l2d.off" : "cpmwheel.l2d.off.n", ch.off.size()), b -> minecraft.setScreen(
                new CondEditScreen(this, cfg, Component.translatable("cpmwheel.l2d.off.title"), ch.off, () -> ch.offCombine, v -> ch.offCombine = v,
                        () -> ch.name.isBlank() ? "?" : ch.name).suspendMode(() -> ch.fadeMs, v -> ch.fadeMs = v)))
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.l2d.off.help").append(ch.off.isEmpty() ? Component.empty() : Component.literal("\n\n" + condsText(ch.off, ch.offCombine)))))
                .bounds(x2, y2, 150, 20).build()); y2 += 26;
        linksY = y2;
        if (selLink >= ch.links.size()) selLink = Math.max(0, ch.links.size() - 1);
        for (int i = 0; i < ch.links.size(); i++) {
            final int li = i;
            Live2dMath.Link l = ch.links.get(i);
            String an = l.anim.isBlank() ? "\u2014" : l.anim;
            int sh = shareCount(l.anim);
            Button lb = Button.builder(Component.literal(font.plainSubstrByWidth((i + 1) + ". " + an, sh > 1 ? 84 : 100) + (sh > 1 ? " \u29C9" + sh : "")), b -> { selLink = li; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.l2d.link.tip", inputsText(l))
                            .append(sh > 1 ? Component.literal("\n").append(Component.translatable("cpmwheel.l2d.shared", sh)) : Component.empty()))).bounds(x2, y2, 106, 18).build();
            lb.active = li != selLink;
            addRenderableWidget(lb); y2 += 20;
        }
        if (ch.links.size() < 8)
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.link.add"), b -> {
                Live2dMath.Link n = new Live2dMath.Link(ch.links.isEmpty() ? "headYaw" : "prev", true);
                ch.links.add(n); selLink = ch.links.size() - 1; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.link.add.help")).bounds(x2, y2 + 2, 150, 20).build());

        if (ch.links.isEmpty()) return;
        final Live2dMath.Link l = ch.links.get(selLink);
        int x3 = 320, w3 = Math.max(150, Math.min(200, width - x3 - 8)), y3 = 26, h3 = (w3 - 2) / 2;
        int sh = shareCount(l.anim);
        int aw = sh > 1 ? w3 - 50 : w3;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.anim", font.plainSubstrByWidth(l.anim.isBlank() ? "\u2014" : l.anim, aw - 50)),
                b -> minecraft.setScreen(new ValueLayerPickerScreen(this, cfg, l.anim, v -> { l.anim = v; Live2d.reset(); })))
                .tooltip(tip("cpmwheel.l2d.anim.help")).bounds(x3, y3, aw, 20).build());
        if (sh > 1) { // 1.7.1 shared layer: how the links combine
            String mode = cfg.l2dModes.getOrDefault(l.anim, "sum");
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.mode." + mode), b -> {
                List<String> ms = java.util.Arrays.asList(Live2dMath.MODES);
                String nm = ms.get((ms.indexOf(mode) + 1) % ms.size());
                if (nm.equals("sum")) cfg.l2dModes.remove(l.anim); else cfg.l2dModes.put(l.anim, nm);
                rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.l2d.shared", sh).append("\n\n").append(Component.translatable("cpmwheel.l2d.mode.help"))))
                .bounds(x3 + w3 - 48, y3, 48, 20).build());
        }
        y3 += 22;
        // 1.7.1 weighted inputs: [source] [weight] [+/−] [✖]
        for (int k = 0; k < l.inputs.size(); k++) {
            final Live2dMath.Input in = l.inputs.get(k);
            int sw = w3 - 40 - 54;
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(Component.translatable("cpmwheel.l2d.in." + in.src + ".short").getString(), sw - 6)), b -> {
                List<String> ins = java.util.Arrays.asList(Live2dMath.INPUTS);
                in.src = ins.get((ins.indexOf(in.src) + 1) % ins.size()); Live2d.reset(); rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.l2d.in." + in.src).append("\n").append(Component.translatable("cpmwheel.l2d.in." + in.src + ".help"))
                        .append("\n\n").append(Component.translatable("cpmwheel.l2d.input.help")))).bounds(x3, y3, sw, 20).build());
            addRenderableWidget(new AbstractSliderButton(x3 + sw + 2, y3, 52, 20, Component.literal(in.weight + "%"), in.weight / 300.0) {
                @Override protected void updateMessage() { setMessage(Component.literal(in.weight + "%")); }
                @Override protected void applyValue() { in.weight = (int) Math.round(value * 60) * 5; }
            }).setTooltip(tip("cpmwheel.l2d.inw.help"));
            addRenderableWidget(Button.builder(Component.literal(in.invert ? "\u2212" : "+"), b -> { in.invert = !in.invert; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.l2d.inv.help")).bounds(x3 + sw + 56, y3, 18, 20).build());
            addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { l.inputs.remove(in); Live2d.reset(); rebuildWidgets(); })
                    .bounds(x3 + w3 - 18, y3, 18, 20).build());
            y3 += 21;
        }
        if (l.inputs.size() < 4) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.in.add"), b -> {
                l.inputs.add(new Live2dMath.Input(l.inputs.isEmpty() ? (selLink == 0 ? "headYaw" : "prev") : "bodyYaw", l.inputs.isEmpty() ? 100 : 50, false));
                Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.in.add.help")).bounds(x3, y3, w3, 18).build());
            y3 += 20;
        }
        y3 += 2;
        addRenderableWidget(Button.builder(Component.translatable(l.lag ? "cpmwheel.l2d.out.lag" : "cpmwheel.l2d.out.follow"), b -> { l.lag = !l.lag; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.out.help")).bounds(x3, y3, h3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.l2d.invert", onoff(l.invert)), b -> { l.invert = !l.invert; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.invert.help")).bounds(x3 + w3 - h3, y3, h3, 20).build()); y3 += 22;
        slider(x3, y3, h3, "cpmwheel.l2d.len", 10, 400, 10, () -> l.len, v -> l.len = v, "%");
        slider(x3 + w3 - h3, y3, h3, "cpmwheel.l2d.weight", 10, 500, 10, () -> l.weight, v -> l.weight = v, "%"); y3 += 22;
        slider(x3, y3, h3, "cpmwheel.l2d.damp", 0, 100, 5, () -> l.damp, v -> l.damp = v, "%");
        slider(x3 + w3 - h3, y3, h3, "cpmwheel.l2d.gain", 0, 300, 10, () -> l.gain, v -> l.gain = v, "%"); y3 += 22;
        slider(x3, y3, h3, "cpmwheel.l2d.min", -180, 180, 5, () -> l.min, v -> l.min = v, "\u00B0");
        slider(x3 + w3 - h3, y3, h3, "cpmwheel.l2d.max", -180, 180, 5, () -> l.max, v -> l.max = v, "\u00B0"); y3 += 22;
        int q = (w3 - 6) / 4;
        Button lu = Button.builder(Component.literal("\u25B2"), b -> { move(ch.links, selLink, -1); selLink--; Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.link.up")).bounds(x3, y3, q, 20).build();
        lu.active = selLink > 0; addRenderableWidget(lu);
        Button ld = Button.builder(Component.literal("\u25BC"), b -> { move(ch.links, selLink, 1); selLink++; Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.link.down")).bounds(x3 + q + 2, y3, q, 20).build();
        ld.active = selLink < ch.links.size() - 1; addRenderableWidget(ld);
        addRenderableWidget(Button.builder(Component.literal("\u23F8" + (l.off.isEmpty() ? "" : l.off.size())), b -> minecraft.setScreen(
                new CondEditScreen(this, cfg, Component.translatable("cpmwheel.l2d.off.link.title"), l.off, () -> l.offCombine, v -> l.offCombine = v,
                        () -> (ch.name.isBlank() ? "?" : ch.name) + " \u203A " + (selLink + 1) + ". " + (l.anim.isBlank() ? "\u2014" : l.anim)).suspendMode(null, null)))
                .tooltip(Tooltip.create(Component.translatable("cpmwheel.l2d.off.link.help").append(l.off.isEmpty() ? Component.empty() : Component.literal("\n\n" + condsText(l.off, l.offCombine)))))
                .bounds(x3 + 2 * (q + 2), y3, q, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { ch.links.remove(l); Live2d.reset(); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.l2d.link.del.help")).bounds(x3 + 3 * (q + 2), y3, w3 - 3 * (q + 2), 20).build());
        liveY = y3 + 24;
    }

    /** links (in active chains) using this layer */
    private int shareCount(String anim) {
        if (anim == null || anim.isBlank()) return 0;
        int n = 0;
        for (Live2dMath.Chain c : cfg.l2dChains) if (c.on) for (Live2dMath.Link l : c.links) if (anim.equals(l.anim)) n++;
        return n;
    }
    static String condsText(List<TriggerLogic.Cond> cs, String combine) {
        WheelConfig cfg = WheelConfig.get();
        String j = " " + Component.translatable("or".equals(combine) ? "cpmwheel.cond.or" : "cpmwheel.cond.and").getString() + " ";
        StringBuilder sb = new StringBuilder();
        for (TriggerLogic.Cond c : cs) { if (sb.length() > 0) sb.append(j); sb.append(EffectTriggers.condText(cfg, c)); }
        return sb.toString();
    }
    static Component inputsText(Live2dMath.Link l) {
        if (l.inputs.isEmpty()) return Component.translatable("cpmwheel.l2d.in.none");
        net.minecraft.network.chat.MutableComponent m = Component.empty();
        for (int i = 0; i < l.inputs.size(); i++) {
            Live2dMath.Input in = l.inputs.get(i);
            if (i > 0) m.append(" + ");
            m.append(Component.translatable("cpmwheel.l2d.in." + in.src)).append(" " + (in.invert ? "\u2212" : "") + in.weight + "%");
        }
        return m;
    }

    private static <T> void move(List<T> l, int i, int d) {
        int j = i + d;
        if (i < 0 || j < 0 || i >= l.size() || j >= l.size()) return;
        T t = l.get(i); l.set(i, l.get(j)); l.set(j, t);
    }

    private void slider(int x, int y, int w, String key, int min, int max, int step, java.util.function.IntSupplier get, java.util.function.IntConsumer set, String unit) {
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.translatable(key, get.getAsInt() + unit), (get.getAsInt() - min) / (double) (max - min)) {
            @Override protected void updateMessage() { setMessage(Component.translatable(key, get.getAsInt() + unit)); }
            @Override protected void applyValue() { set.accept(min + (int) Math.round(value * (max - min) / step) * step); }
        }).setTooltip(tip(key + ".help"));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        Component status = Live2d.failed() ? Component.translatable("cpmwheel.l2d.failed")
                : !cfg.l2dOn ? Component.translatable("cpmwheel.l2d.status.off") : Component.translatable("cpmwheel.l2d.status.on");
        g.drawString(font, status, 164, 10, Live2d.failed() ? 0xFF5A5A : cfg.l2dOn ? 0x3BFF9D : 0x7FA8B8);
        super.render(g, mx, my, pt);
        Live2dMath.Chain ch = sel();
        if (ch == null) {
            if (cfg.l2dChains.isEmpty()) {
                int y = 40;
                for (var line : font.split(Component.translatable("cpmwheel.l2d.empty"), Math.max(150, width - 180))) { g.drawString(font, line, 164, y, 0x9FB8C4); y += 10; }
            }
            return;
        }
        // live preview: angle and value bar next to every link
        int x = 164 + 108, y = linksY;
        for (int i = 0; i < ch.links.size(); i++) {
            float[] lv = Live2d.live(ch.id, i);
            int bw = 40;
            g.fill(x, y + 4, x + bw, y + 14, 0x60000000);
            if (lv != null) {
                if (lv[1] >= 0 && lv[2] > 0) {
                    int fw = Math.round(bw * lv[1] / lv[2]);
                    g.fill(x, y + 4, x + fw, y + 14, lv.length > 4 && lv[4] > 0 ? 0xA0FFC04A : 0xA02EE6FF);
                    g.fill(x + bw / 2, y + 3, x + bw / 2 + 1, y + 15, 0xFFFFFFFF);
                }
                String t = lv[1] >= 0 ? String.valueOf((int) lv[1]) : Math.round(lv[0]) + "\u00B0";
                g.drawString(font, t, x + 2, y + 5, 0xFFFFFF);
            } else g.drawString(font, "-", x + 2, y + 5, 0x7FA8B8);
            y += 20;
        }
        if (!ch.links.isEmpty() && selLink < ch.links.size()) {
            float[] lv = Live2d.live(ch.id, selLink);
            Live2dMath.Link l = ch.links.get(selLink);
            int x3 = 320, w3 = Math.max(150, Math.min(200, width - x3 - 8)), y3 = liveY;
            int sh = shareCount(l.anim);
            Integer comb = Live2d.combined(l.anim);
            if (sh > 1 && y3 + 10 < height - 4) g.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.l2d.shared.live", sh,
                    Component.translatable("cpmwheel.l2d.mode." + cfg.l2dModes.getOrDefault(l.anim, "sum")), comb == null ? "-" : String.valueOf(comb)).getString(), w3), x3, y3 + 10, 0xFFC04A);
            String held = cfg.l2dPauseTrig && EffectTriggers.held(l.anim) ? " " + Component.translatable("cpmwheel.l2d.paused").getString() : "";
            if (lv != null && lv.length > 4 && lv[4] > 0) held += " " + Component.translatable(lv[3] <= 0 ? "cpmwheel.l2d.susp.done" : "cpmwheel.l2d.susp.fading").getString();
            else if (lv != null && lv.length > 3 && lv[3] < 1) held += " " + Component.translatable("cpmwheel.l2d.susp.back").getString();
            String txt = lv == null ? Component.translatable("cpmwheel.l2d.live.none").getString()
                    : Component.translatable("cpmwheel.l2d.live", Math.round(lv[0]), lv[1] < 0 ? "-" : String.valueOf((int) lv[1]), lv[2] <= 0 ? "-" : String.valueOf((int) lv[2])).getString() + held;
            if (y3 < height - 4) g.drawString(font, font.plainSubstrByWidth(txt, w3), x3, y3, 0xCFE8F0);
        }
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { cfg.fixup(); cfg.save(); Live2d.use(cfg); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
