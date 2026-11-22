package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.9.0 "Olhar: extras": intentional glances, damage reaction, look at items / blocks / mobs and idle. Everything OFF by default. */
public class LookExtrasScreen extends MiniScreen {
    public LookExtrasScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.lx.title", parent, cfg); }

    private int x1, x2, x3, w, objY = 120;

    @Override
    protected void init() {
        Live2d.use(cfg);
        w = Math.max(110, Math.min(150, (width - 24) / 3)); x1 = 8; x2 = x1 + w + 4; x3 = x2 + w + 4;
        // column 1: glances, then objects; column 2: damage; column 3: idle (fits a 427 x 240 GUI)
        int y = 32;
        btn("cpmwheel.lx.glance", b -> { cfg.l2dGlanceOn = !cfg.l2dGlanceOn; rebuildWidgets(); }, x1, y, w, "cpmwheel.lx.glance.help", onoff(cfg.l2dGlanceOn)); y += 22;
        if (cfg.l2dGlanceOn) {
            slider(x1, y, w, "cpmwheel.lx.glance.ms", 100, 2000, 50, () -> cfg.l2dGlanceMs, v -> cfg.l2dGlanceMs = v, " ms", "cpmwheel.lx.glance.help"); y += 22;
            slider(x1, y, w, "cpmwheel.lx.glance.cd", 0, 30000, 500, () -> cfg.l2dGlanceCdMs, v -> cfg.l2dGlanceCdMs = v, " ms", "cpmwheel.lx.glance.cd.help"); y += 22;
            slider(x1, y, w, "cpmwheel.lx.glance.chance", 0, 100, 5, () -> cfg.l2dGlanceChance, v -> cfg.l2dGlanceChance = v, "%", "cpmwheel.lx.glance.help"); y += 22;
        }
        y += 12; objY = y - 10;
        btn("cpmwheel.lx.obj", b -> { cfg.l2dObjOn = !cfg.l2dObjOn; rebuildWidgets(); }, x1, y, w, "cpmwheel.lx.obj.help", onoff(cfg.l2dObjOn)); y += 22;
        if (cfg.l2dObjOn) {
            int sw = (w - 4) / 3;
            btn(Component.literal((cfg.l2dObjMining ? "\u2714 " : "\u2716 ") + Component.translatable("cpmwheel.lx.src.mining").getString()), b -> { cfg.l2dObjMining = !cfg.l2dObjMining; rebuildWidgets(); }, x1, y, sw, 20, "cpmwheel.lx.obj.mining.help");
            btn(Component.literal((cfg.l2dObjItems ? "\u2714 " : "\u2716 ") + Component.translatable("cpmwheel.lx.src.items").getString()), b -> { cfg.l2dObjItems = !cfg.l2dObjItems; rebuildWidgets(); }, x1 + sw + 2, y, sw, 20, "cpmwheel.lx.obj.items.help");
            btn(Component.literal((cfg.l2dObjMobs ? "\u2714 " : "\u2716 ") + Component.translatable("cpmwheel.lx.src.mobs").getString()), b -> { cfg.l2dObjMobs = !cfg.l2dObjMobs; rebuildWidgets(); }, x1 + 2 * sw + 4, y, w - 2 * sw - 4, 20, "cpmwheel.lx.obj.mobs.help"); y += 22;
            btn(Component.translatable("cpmwheel.lx.obj.order", orderText()), b -> { cfg.l2dObjOrder = LookLogic.nextOrder(cfg.l2dObjOrder); rebuildWidgets(); }, x1, y, w, 20, "cpmwheel.lx.obj.order.help"); y += 22;
        }
        // column 2: damage
        y = 32;
        btn("cpmwheel.lx.hurt", b -> { cfg.l2dHurtOn = !cfg.l2dHurtOn; rebuildWidgets(); }, x2, y, w, "cpmwheel.lx.hurt.help", onoff(cfg.l2dHurtOn)); y += 22;
        if (cfg.l2dHurtOn) {
            slider(x2, y, w, "cpmwheel.lx.hurt.pct", 0, 100, 5, () -> cfg.l2dHurtPct, v -> cfg.l2dHurtPct = v, "%", "cpmwheel.lx.hurt.pct.help"); y += 22;
            slider(x2, y, w, "cpmwheel.lx.hurt.ms", 100, 2000, 50, () -> cfg.l2dHurtMs, v -> cfg.l2dHurtMs = v, " ms", "cpmwheel.lx.hurt.pct.help"); y += 22;
            btn("cpmwheel.lx.hurt.snap", b -> { cfg.l2dHurtSnap = !cfg.l2dHurtSnap; rebuildWidgets(); }, x2, y, w, "cpmwheel.lx.hurt.snap.help", onoff(cfg.l2dHurtSnap)); y += 22;
            if (cfg.l2dHurtSnap) { slider(x2, y, w, "cpmwheel.lx.hurt.snapms", 100, 3000, 50, () -> cfg.l2dHurtSnapMs, v -> cfg.l2dHurtSnapMs = v, " ms", "cpmwheel.lx.hurt.snap.help"); y += 22; }
        }
        // column 3: idle
        y = 32;
        btn("cpmwheel.lx.idle", b -> { cfg.l2dIdleOn = !cfg.l2dIdleOn; rebuildWidgets(); }, x3, y, w, "cpmwheel.lx.idle.help", onoff(cfg.l2dIdleOn)); y += 22;
        if (cfg.l2dIdleOn) {
            slider(x3, y, w, "cpmwheel.lx.idle.sec", 3, 300, 1, () -> cfg.l2dIdleSec, v -> cfg.l2dIdleSec = v, " s", "cpmwheel.lx.idle.help"); y += 22;
            slider(x3, y, w, "cpmwheel.lx.idle.wander", 0, 40, 1, () -> cfg.l2dIdleWander, v -> cfg.l2dIdleWander = v, "°", "cpmwheel.lx.idle.wander.help"); y += 22;
            slider(x3, y, w, "cpmwheel.lx.idle.wanderms", 300, 8000, 100, () -> cfg.l2dIdleWanderMs, v -> cfg.l2dIdleWanderMs = v, " ms", "cpmwheel.lx.idle.wander.help"); y += 22;
            slider(x3, y, w, "cpmwheel.lx.idle.blink", 10, 200, 5, () -> cfg.l2dIdleBlinkPct, v -> cfg.l2dIdleBlinkPct = v, "%", "cpmwheel.lx.idle.blink.help"); y += 22;
            slider(x3, y, w, "cpmwheel.lx.idle.slow", 100, 400, 10, () -> cfg.l2dIdleSlowPct, v -> cfg.l2dIdleSlowPct = v, "%", "cpmwheel.lx.idle.slow.help"); y += 22;
            slider(x3, y, w, "cpmwheel.lx.idle.droop", 0, 80, 5, () -> cfg.l2dIdleDroop, v -> cfg.l2dIdleDroop = v, "%", "cpmwheel.lx.idle.droop.help"); y += 22;
        }
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    private String orderText() {
        StringBuilder sb = new StringBuilder();
        for (String s : LookLogic.order(cfg.l2dObjOrder).split(",")) { if (sb.length() > 0) sb.append(" > "); sb.append(Component.translatable("cpmwheel.lx.src." + s).getString()); }
        return sb.toString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int c = 0x7FA8B8;
        g.drawString(font, Component.translatable("cpmwheel.lx.h.glance"), x1, 22, c);
        g.drawString(font, Component.translatable("cpmwheel.lx.h.objects"), x1, objY, c);
        g.drawString(font, Component.translatable("cpmwheel.lx.h.hurt"), x2, 22, c);
        g.drawString(font, Component.translatable("cpmwheel.lx.h.idle"), x3, 22, c);
        String st = Live2d.eyeState;
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.lx.state",
                Component.translatable("cpmwheel.eyeov.state." + (st == null || st.isEmpty() ? "off" : st))).getString(), width - 170), 166, height - 20, 0xCFE8F0);
        if (!cfg.l2dLookOn) g.drawString(font, Component.translatable("cpmwheel.eyeov.needlook"), 166, height - 32, 0xFFC04A);
    }
}
