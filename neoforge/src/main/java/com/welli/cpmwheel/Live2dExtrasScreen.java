package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.8.0 "Extras da física": switches of the new inputs (wind, impacts, look-at, voice, animation motion; all off by default)
 * and the breathing / blinking generators.
 */
public class Live2dExtrasScreen extends MiniScreen {
    private static String selGen = "";
    private int voiceY = 0;

    public Live2dExtrasScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.l2d.ext.title", parent, cfg); }

    private Live2dMath.Gen sel() { for (Live2dMath.Gen g : cfg.l2dGens) if (g.id.equals(selGen)) return g; return null; }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int x = 8, y = 26, w = 150;
        btn("cpmwheel.l2d.ext.wind", b -> { cfg.l2dWindOn = !cfg.l2dWindOn; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.wind.help", onoff(cfg.l2dWindOn)); y += 22;
        slider(x, y, w, "cpmwheel.l2d.ext.windpct", 0, 300, 10, () -> cfg.l2dWindPct, v -> cfg.l2dWindPct = v, "%", "cpmwheel.l2d.ext.wind.help"); y += 24;
        btn("cpmwheel.l2d.ext.impact", b -> { cfg.l2dImpactOn = !cfg.l2dImpactOn; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.impact.help", onoff(cfg.l2dImpactOn)); y += 22;
        slider(x, y, w, "cpmwheel.l2d.ext.impactpct", 0, 300, 10, () -> cfg.l2dImpactPct, v -> cfg.l2dImpactPct = v, "%", "cpmwheel.l2d.ext.impact.help"); y += 24;
        btn("cpmwheel.l2d.ext.look", b -> { cfg.l2dLookOn = !cfg.l2dLookOn; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.look.help", onoff(cfg.l2dLookOn)); y += 22;
        slider(x, y, w, "cpmwheel.l2d.ext.radius", 2, 32, 1, () -> cfg.l2dLookRadius, v -> cfg.l2dLookRadius = v, "", "cpmwheel.l2d.ext.look.help"); y += 22;
        btn(cfg.l2dLookPlayers ? "cpmwheel.l2d.ext.players" : "cpmwheel.l2d.ext.mobs", b -> { cfg.l2dLookPlayers = !cfg.l2dLookPlayers; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.look.help"); y += 24;
        btn("cpmwheel.l2d.ext.voice", b -> { cfg.l2dVoiceOn = !cfg.l2dVoiceOn; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.voice.help", onoff(cfg.l2dVoiceOn)); y += 22;
        slider(x, y, w, "cpmwheel.l2d.ext.voicepct", 0, 300, 10, () -> cfg.l2dVoicePct, v -> cfg.l2dVoicePct = v, "%", "cpmwheel.l2d.ext.voice.help"); y += 22;
        voiceY = y; y += 12;
        btn("cpmwheel.l2d.ext.layer", b -> { cfg.l2dLayerOn = !cfg.l2dLayerOn; rebuildWidgets(); }, x, y, w, "cpmwheel.l2d.ext.layer.help", onoff(cfg.l2dLayerOn)); y += 22;

        // generators
        int x2 = 166; y = 26;
        btn("cpmwheel.l2d.gen.on", b -> { cfg.l2dGenOn = !cfg.l2dGenOn; Live2d.reset(); rebuildWidgets(); }, x2, y, w, "cpmwheel.l2d.gen.help", onoff(cfg.l2dGenOn)); y += 24;
        Live2dMath.Gen s = sel();
        if (s == null && !cfg.l2dGens.isEmpty()) { s = cfg.l2dGens.get(0); selGen = s.id; }
        for (Live2dMath.Gen g : cfg.l2dGens) {
            String lbl = (g.on ? "" : "\u2716 ") + Component.translatable("cpmwheel.l2d.gen." + g.kind).getString() + ": " + (g.anim.isBlank() ? "\u2014" : g.anim);
            Button b = btn(Component.literal(font.plainSubstrByWidth(lbl, w - 6)), q -> { selGen = g.id; rebuildWidgets(); }, x2, y, w, 18, null);
            b.active = !g.id.equals(selGen);
            y += 20;
        }
        if (cfg.l2dGens.size() < 8) {
            btn("cpmwheel.l2d.gen.add.breath", b -> add("breath"), x2, y + 2, 74, "cpmwheel.l2d.gen.breath.help");
            btn("cpmwheel.l2d.gen.add.blink", b -> add("blink"), x2 + 76, y + 2, 74, "cpmwheel.l2d.gen.blink.help");
        }
        if (s != null) {
            final Live2dMath.Gen g = s;
            int x3 = 324, w3 = Math.max(150, Math.min(200, width - x3 - 8)); y = 26;
            btn(Component.translatable("cpmwheel.l2d.anim", font.plainSubstrByWidth(g.anim.isBlank() ? "\u2014" : g.anim, w3 - 50)),
                    b -> minecraft.setScreen(new ValueLayerPickerScreen(this, cfg, g.anim, v -> { g.anim = v; Live2d.reset(); })), x3, y, w3, 20, "cpmwheel.l2d.gen.anim.help"); y += 22;
            btn("cpmwheel.l2d.gen.genon", b -> { g.on = !g.on; rebuildWidgets(); }, x3, y, w3, null, onoff(g.on)); y += 22;
            boolean blink = "blink".equals(g.kind);
            slider(x3, y, w3, blink ? "cpmwheel.l2d.gen.interval" : "cpmwheel.l2d.gen.period", blink ? 1000 : 1000, blink ? 15000 : 10000, 100,
                    () -> g.periodMs, v -> g.periodMs = v, " ms", blink ? "cpmwheel.l2d.gen.blink.help" : "cpmwheel.l2d.gen.breath.help"); y += 22;
            slider(x3, y, w3, "cpmwheel.l2d.gen.lo", 0, 100, 5, () -> g.lo, v -> g.lo = v, "%", "cpmwheel.l2d.gen.range.help"); y += 22;
            slider(x3, y, w3, "cpmwheel.l2d.gen.hi", 0, 100, 5, () -> g.hi, v -> g.hi = v, "%", "cpmwheel.l2d.gen.range.help"); y += 22;
            slider(x3, y, w3, "cpmwheel.l2d.gen.rand", 0, 100, 5, () -> g.rand, v -> g.rand = v, "%", "cpmwheel.l2d.gen.rand.help"); y += 22;
            if (blink) { slider(x3, y, w3, "cpmwheel.l2d.gen.pulse", 50, 1000, 10, () -> g.pulseMs, v -> g.pulseMs = v, " ms", "cpmwheel.l2d.gen.blink.help"); y += 22; }
            btn("cpmwheel.l2d.gen.del", b -> { cfg.l2dGens.remove(g); selGen = ""; Live2d.reset(); rebuildWidgets(); }, x3, y, w3, null); y += 22;
        }
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    private void add(String kind) {
        Live2dMath.Gen g = new Live2dMath.Gen();
        g.id = WheelConfig.newId("g"); g.kind = kind;
        if (kind.equals("blink")) { g.periodMs = 4000; g.rand = 40; g.lo = 0; g.hi = 100; g.pulseMs = 150; }
        else { g.periodMs = 3500; g.rand = 15; g.lo = 0; g.hi = 100; }
        cfg.l2dGens.add(g); selGen = g.id; rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        String st = VoiceBridge.status();
        g.drawString(font, Component.translatable("cpmwheel.l2d.ext.voice.st." + st), 8, voiceY + 2, "ok".equals(st) ? 0x6CFF8F : "error".equals(st) ? 0xFF6060 : 0x7FA8B8);
        Live2dMath.Gen s = sel();
        if (s != null) {
            float[] lv = Live2d.liveGen(s.id);
            String t = lv == null ? Component.translatable("cpmwheel.l2d.live.none").getString() : Component.translatable("cpmwheel.l2d.gen.live", Math.round(lv[0] * 100), (int) lv[1], (int) lv[2]).getString();
            g.drawString(font, t, 324, height - 20, 0xCFE8F0);
        }
        if (!cfg.l2dOn) g.drawString(font, Component.translatable("cpmwheel.l2d.prev.off"), 166, height - 20, 0xFF6060);
    }
}
