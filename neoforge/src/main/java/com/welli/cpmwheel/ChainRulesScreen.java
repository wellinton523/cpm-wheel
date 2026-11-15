package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.0 "Encadear animações": when animation A ends (or starts), after X ms, play / stop / toggle animation B. */
public class ChainRulesScreen extends MiniScreen {
    private static String selRule = "";
    public ChainRulesScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.ct.title", parent, cfg); }

    private ChainLogic.Rule sel() { for (ChainLogic.Rule r : cfg.chainRules) if (r.id.equals(selRule)) return r; return null; }
    static String opKey(String op) { return "off".equals(op) ? "cpmwheel.eff.op.off" : "toggle".equals(op) ? "cpmwheel.eff.op.toggle" : "cpmwheel.eff.op.on"; }

    @Override
    protected void init() {
        int x = 8, y = 26, w = 180;
        btn("cpmwheel.ct.on", b -> { cfg.chainTrigOn = !cfg.chainTrigOn; rebuildWidgets(); }, x, y, w, "cpmwheel.ct.help", onoff(cfg.chainTrigOn)); y += 24;
        ChainLogic.Rule s = sel();
        if (s == null && !cfg.chainRules.isEmpty()) { s = cfg.chainRules.get(0); selRule = s.id; }
        int per = Math.max(3, (height - 80 - y) / 20), shown = 0;
        for (ChainLogic.Rule r : cfg.chainRules) {
            if (shown++ >= per) break;
            String t = (r.on ? "" : "\u2716 ") + (r.from.isBlank() ? "?" : r.from) + ("start".equals(r.when) ? " \u25B6" : " \u25A0") + " \u2192 " + (r.to.isBlank() ? "?" : r.to);
            Button b = btn(Component.literal(font.plainSubstrByWidth(t, w - 6)), q -> { selRule = r.id; rebuildWidgets(); }, x, y, w, 18, null);
            b.active = !r.id.equals(selRule);
            y += 20;
        }
        if (cfg.chainRules.size() < 32)
            btn("cpmwheel.ct.add", b -> { ChainLogic.Rule n = new ChainLogic.Rule(); n.id = WheelConfig.newId("t"); cfg.chainRules.add(n); selRule = n.id; rebuildWidgets(); }, x, y + 2, w, "cpmwheel.ct.add.help");
        if (s != null) {
            final ChainLogic.Rule r = s;
            int x2 = x + w + 8, w2 = Math.max(160, Math.min(220, width - x2 - 8)); y = 26;
            btn("cpmwheel.ct.rule.on", b -> { r.on = !r.on; rebuildWidgets(); }, x2, y, w2, null, onoff(r.on)); y += 22;
            btn(Component.translatable("cpmwheel.ct.from", r.from.isBlank() ? "\u2014" : r.from), b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, v -> r.from = v)), x2, y, w2, 20, "cpmwheel.ct.from.help"); y += 22;
            btn("start".equals(r.when) ? "cpmwheel.ct.when.start" : "cpmwheel.ct.when.end", b -> { r.when = "start".equals(r.when) ? "end" : "start"; rebuildWidgets(); }, x2, y, w2, "cpmwheel.ct.when.help"); y += 22;
            slider(x2, y, w2, "cpmwheel.ct.delay", 0, 10000, 100, () -> r.delayMs, v -> r.delayMs = v, " ms", "cpmwheel.ct.delay.help"); y += 22;
            btn(Component.translatable("cpmwheel.ct.to", r.to.isBlank() ? "\u2014" : r.to), b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, v -> r.to = v)), x2, y, w2, 20, "cpmwheel.ct.to.help"); y += 22;
            btn(Component.translatable("cpmwheel.ct.op", Component.translatable(opKey(r.op))), b -> { r.op = "on".equals(r.op) ? "off" : "off".equals(r.op) ? "toggle" : "on"; rebuildWidgets(); }, x2, y, w2, 20, "cpmwheel.ct.op.help"); y += 24;
            int h = (w2 - 2) / 2;
            btn("cpmwheel.ct.test", b -> ChainTriggers.test(cfg, r), x2, y, h, "cpmwheel.ct.test.help");
            btn("cpmwheel.ct.del", b -> { cfg.chainRules.remove(r); selRule = ""; rebuildWidgets(); }, x2 + h + 2, y, h, null);
        }
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        ChainLogic.Rule r = sel();
        if (r != null && !r.from.isBlank()) {
            int v = CpmBridge.playing(r.from);
            g.drawString(font, Component.translatable("cpmwheel.ct.live", r.from, v > 0 ? Component.translatable("cpmwheel.ct.playing", v) : Component.translatable("cpmwheel.ct.stopped")), 196, height - 20, v > 0 ? 0x6CFF8F : 0x7FA8B8);
        }
    }
}
