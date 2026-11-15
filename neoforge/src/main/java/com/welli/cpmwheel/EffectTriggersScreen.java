package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.5.7 "Gatilhos de efeito": add, edit, delete and test effect rules. Edits the live config (saved on close). */
public class EffectTriggersScreen extends Screen {
    private static final String[] OPS = {"on", "off", "toggle"};
    private final Screen parent;
    private final WheelConfig cfg;
    private static String selected = "";
    private int page = 0, actPage = 0;
    private boolean confirmDelete = false;

    public EffectTriggersScreen(Screen parent, WheelConfig cfg) { super(Component.translatable("cpmwheel.eff.title")); this.parent = parent; this.cfg = cfg; }

    private WheelConfig.EffectRule sel() { for (WheelConfig.EffectRule r : cfg.effRules) if (r.id.equals(selected)) return r; return null; }
    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }
    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    @Override
    protected void init() {
        int x = 8, y = 26;
        WheelConfig.EffectRule r = sel();
        if (r == null && !cfg.effRules.isEmpty()) { r = cfg.effRules.get(0); selected = r.id; }
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.master", onoff(cfg.effOn)), b -> { cfg.effOn = !cfg.effOn; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.eff.master.help")).bounds(x, y, 150, 20).build()); y += 22;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.add"), b -> {
            WheelConfig.EffectRule n = new WheelConfig.EffectRule(); n.id = WheelConfig.newId("r");
            cfg.effRules.add(n); selected = n.id; confirmDelete = false; rebuildWidgets(); })
            .tooltip(tip("cpmwheel.eff.add.help")).bounds(x, y, 150, 20).build()); y += 24;
        int per = Math.max(3, (height - 30 - y) / 20);
        int pages = Math.max(1, (cfg.effRules.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(cfg.effRules.size(), (page + 1) * per); i++) {
            WheelConfig.EffectRule q = cfg.effRules.get(i);
            String mark = EffectTriggers.isActive(q.id) ? "\u25CF " : q.on ? "" : "\u2716 ";
            Button bt = Button.builder(Component.literal(mark + EffectTriggers.ruleLabel(q)), b -> { selected = q.id; confirmDelete = false; actPage = 0; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.literal(EffectTriggers.summary(q)))).bounds(x, y, 150, 18).build();
            bt.active = !q.id.equals(selected);
            addRenderableWidget(bt); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 50, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 130, height - 50, 20, 18).build());
        }

        if (r != null) {
            final WheelConfig.EffectRule rule = r;
            int x2 = 164, y2 = 26;
            EditBox name = new EditBox(font, x2, y2 + 1, 150, 18, Component.literal(""));
            name.setMaxLength(48); name.setValue(rule.name); name.setHint(Component.translatable("cpmwheel.eff.name.hint"));
            name.setResponder(v -> rule.name = v);
            addRenderableWidget(name); y2 += 22;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.on", onoff(rule.on)), b -> { rule.on = !rule.on; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.eff.on.help")).bounds(x2, y2, 150, 20).build()); y2 += 22;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.effect", EffectTriggers.effectName(rule.effect)), b -> minecraft.setScreen(new EffectPickerScreen(this, cfg, rule)))
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.eff.effect.help").append("\n" + rule.effect))).bounds(x2, y2, 150, 20).build()); y2 += 22;
            addRenderableWidget(new AbstractSliderButton(x2, y2, 150, 20, ampMsg(rule.minAmp), rule.minAmp / 9.0) {
                @Override protected void updateMessage() { setMessage(ampMsg(rule.minAmp)); }
                @Override protected void applyValue() { rule.minAmp = (int) Math.round(value * 9); }
            });
            y2 += 22;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.end", Component.translatable("cpmwheel.eff.end." + rule.onEnd)), b -> {
                rule.onEnd = "restore".equals(rule.onEnd) ? "none" : "restore"; rebuildWidgets(); })
                .tooltip(tip("cpmwheel.eff.end.help")).bounds(x2, y2, 150, 20).build()); y2 += 22;
            delay(x2, y2, "cpmwheel.eff.delaystart", rule.delayStart, v -> rule.delayStart = v); y2 += 22;
            delay(x2, y2, "cpmwheel.eff.delayend", rule.delayEnd, v -> rule.delayEnd = v); y2 += 22;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.notify", onoff(rule.notify)), b -> { rule.notify = !rule.notify; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.eff.notify.help")).bounds(x2, y2, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.lock", onoff(rule.lock)), b -> { rule.lock = !rule.lock; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.eff.lock.help")).bounds(x2 + 76, y2, 74, 20).build()); y2 += 22;
            boolean testing = EffectTriggers.isTesting(rule.id);
            addRenderableWidget(Button.builder(Component.translatable(testing ? "cpmwheel.eff.test.stop" : "cpmwheel.eff.test"), b -> {
                EffectTriggers.test(rule, EffectTriggers.isTesting(rule.id) ? 0 : 5); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.eff.test.help")).bounds(x2, y2, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(confirmDelete ? "cpmwheel.eff.delete.sure" : "cpmwheel.eff.delete"), b -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                cfg.effRules.remove(rule); selected = ""; confirmDelete = false; rebuildWidgets(); })
                .bounds(x2 + 76, y2, 74, 20).build());

            // actions
            int x3 = 320, w3 = Math.max(150, Math.min(240, width - x3 - 8)), y3 = 38;
            int aper = Math.max(2, (height - 54 - y3) / 22);
            int apages = Math.max(1, (rule.actions.size() + aper - 1) / aper);
            actPage = Math.floorMod(actPage, apages);
            List<WheelConfig.EffectAction> acts = rule.actions;
            for (int i = actPage * aper; i < Math.min(acts.size(), (actPage + 1) * aper); i++) {
                final WheelConfig.EffectAction a = acts.get(i);
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.op." + a.op), b -> {
                    a.op = OPS[(java.util.Arrays.asList(OPS).indexOf(a.op) + 1) % OPS.length]; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.eff.op.help")).bounds(x3, y3, 64, 20).build());
                addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(EffectTriggers.targetName(a.target), w3 - 98)), b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, rule, a)))
                        .tooltip(Tooltip.create(Component.translatable("cpmwheel.eff.target.help").append("\n" + a.target))).bounds(x3 + 66, y3, w3 - 90, 20).build());
                addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { acts.remove(a); rebuildWidgets(); }).bounds(x3 + w3 - 22, y3, 22, 20).build());
                y3 += 22;
            }
            if (apages > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> { actPage--; rebuildWidgets(); }).bounds(x3, y3, 20, 18).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> { actPage++; rebuildWidgets(); }).bounds(x3 + 22, y3, 20, 18).build());
                y3 += 20;
            }
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.addaction"), b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, rule, null)))
                    .tooltip(tip("cpmwheel.eff.addaction.help")).bounds(x3, y3, w3, 20).build());
        }
        if (r == null) { // empty: one-click example (Veneno -> DESATIVAR the first group button, e.g. "Humano")
            WheelConfig.Slot grp = cfg.groupSlot("Humano");
            if (grp == null) for (WheelConfig.Slot s : cfg.allSlots()) if (s.isGroup()) { grp = s; break; }
            final WheelConfig.Slot eg = grp;
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.eff.example", eg == null ? "-" : eg.display()), b -> {
                WheelConfig.EffectRule n = new WheelConfig.EffectRule(); n.id = WheelConfig.newId("r"); n.effect = "minecraft:poison";
                if (eg != null) n.actions.add(new WheelConfig.EffectAction("group:" + eg.display(), "off"));
                cfg.effRules.add(n); selected = n.id; rebuildWidgets(); }).bounds(164, 60, 220, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 150, 20).build());
    }

    private static Component ampMsg(int a) {
        return a <= 0 ? Component.translatable("cpmwheel.eff.amp.any") : Component.translatable("cpmwheel.eff.amp", roman(a + 1));
    }
    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private void delay(int x, int y, String key, int ms, java.util.function.IntConsumer set) {
        addRenderableWidget(new AbstractSliderButton(x, y, 150, 20, Component.translatable(key, String.format(java.util.Locale.ROOT, "%.1f", ms / 1000f)), Math.min(1, ms / 5000.0)) {
            int v = ms;
            @Override protected void updateMessage() { setMessage(Component.translatable(key, String.format(java.util.Locale.ROOT, "%.1f", v / 1000f))); }
            @Override protected void applyValue() { v = (int) Math.round(value * 50) * 100; set.accept(v); }
        }).setTooltip(Tooltip.create(Component.translatable(key + ".help")));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(cfg));
        WheelConfig.EffectRule r = sel();
        if (r != null) {
            g.drawString(font, Component.translatable("cpmwheel.eff.actions"), 320, 26, 0x9FB8C8);
            if (EffectTriggers.isActive(r.id)) g.drawString(font, Component.translatable("cpmwheel.eff.activenow"), 164, 12, 0x3BFF9D);
        } else g.drawWordWrap(font, Component.translatable("cpmwheel.eff.empty"), 164, 30, Math.max(120, width - 172), 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { cfg.fixup(); cfg.save(); Actions.invalidate(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
