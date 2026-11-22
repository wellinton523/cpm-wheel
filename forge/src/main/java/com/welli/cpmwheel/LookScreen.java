package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.3 "Olhar: opções": targets, screen area (with live outline), privacy filters, stickiness and eye smoothing. */
public class LookScreen extends MiniScreen {
    private static boolean preview = true;

    public LookScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.look.title", parent, cfg); }

    private static String next(List<String> l, String cur) { int i = l.indexOf(cur); return l.get((i + 1) % l.size()); }

    @Override protected int backdrop() { return preview ? 0x70050A12 : super.backdrop(); }

    @Override
    protected void init() {
        Live2d.use(cfg);
        int w = Math.max(110, Math.min(150, (width - 24) / 3)), x1 = 8, x2 = x1 + w + 4, x3 = x2 + w + 4, y;
        // column 1: who / how
        y = 26;
        btn("cpmwheel.look.on", b -> { cfg.l2dLookOn = !cfg.l2dLookOn; rebuildWidgets(); }, x1, y, w, "cpmwheel.l2d.ext.look.help", onoff(cfg.l2dLookOn)); y += 22;
        btn("cpmwheel.look.targets", b -> { cfg.l2dLookTargets = next(List.of("players", "mobs", "both", "off"), cfg.l2dLookTargets); rebuildWidgets(); }, x1, y, w, "cpmwheel.look.targets.help",
                Component.translatable("cpmwheel.look.targets." + cfg.l2dLookTargets)); y += 22;
        btn("cpmwheel.look.mode", b -> { cfg.l2dLookMode = "screen".equals(cfg.l2dLookMode) ? "nearest" : "screen"; rebuildWidgets(); }, x1, y, w, "cpmwheel.look.mode.help",
                Component.translatable("cpmwheel.look.mode." + cfg.l2dLookMode)); y += 22;
        slider(x1, y, w, "cpmwheel.l2d.ext.radius", 2, 32, 1, () -> cfg.l2dLookRadius, v -> cfg.l2dLookRadius = v, "", "cpmwheel.look.radius.help"); y += 22;
        if ("nearest".equals(cfg.l2dLookMode)) { slider(x1, y, w, "cpmwheel.look.fov", 20, 360, 10, () -> cfg.l2dLookFov, v -> cfg.l2dLookFov = v, "°", "cpmwheel.look.fov.help"); y += 22; }
        btn("cpmwheel.look.sticky", b -> { cfg.l2dLookSticky = !cfg.l2dLookSticky; rebuildWidgets(); }, x1, y, w, "cpmwheel.look.sticky.help", onoff(cfg.l2dLookSticky)); y += 22;
        if (cfg.l2dLookSticky) { slider(x1, y, w, "cpmwheel.look.stick", 0, 100, 5, () -> cfg.l2dLookStickPct, v -> cfg.l2dLookStickPct = v, "%", "cpmwheel.look.sticky.help"); y += 22; }
        slider(x1, y, w, "cpmwheel.look.switch", 0, 2000, 50, () -> cfg.l2dLookSwitchMs, v -> cfg.l2dLookSwitchMs = v, " ms", "cpmwheel.look.switch.help"); y += 22;
        slider(x1, y, w, "cpmwheel.look.maxyaw", 5, 90, 5, () -> cfg.l2dLookMaxYaw, v -> cfg.l2dLookMaxYaw = v, "°", "cpmwheel.look.maxyaw.help"); y += 22;
        // column 2: screen area
        y = 26;
        if ("screen".equals(cfg.l2dLookMode)) {
            btn("cpmwheel.look.shape", b -> { cfg.l2dLookShape = "circle".equals(cfg.l2dLookShape) ? "rect" : "circle"; rebuildWidgets(); }, x2, y, w, "cpmwheel.look.area.help",
                    Component.translatable("cpmwheel.look.shape." + cfg.l2dLookShape)); y += 22;
            if ("rect".equals(cfg.l2dLookShape)) {
                slider(x2, y, w, "cpmwheel.look.areaw", 2, 100, 1, () -> cfg.l2dLookAreaW, v -> cfg.l2dLookAreaW = v, "%", "cpmwheel.look.area.help"); y += 22;
                slider(x2, y, w, "cpmwheel.look.areah", 2, 100, 1, () -> cfg.l2dLookAreaH, v -> cfg.l2dLookAreaH = v, "%", "cpmwheel.look.area.help"); y += 22;
            } else { slider(x2, y, w, "cpmwheel.look.arear", 2, 100, 1, () -> cfg.l2dLookAreaR, v -> cfg.l2dLookAreaR = v, "%", "cpmwheel.look.area.help"); y += 22; }
            slider(x2, y, w, "cpmwheel.look.offx", -50, 50, 1, () -> cfg.l2dLookOffX, v -> cfg.l2dLookOffX = v, "%", "cpmwheel.look.off.help"); y += 22;
            slider(x2, y, w, "cpmwheel.look.offy", -50, 50, 1, () -> cfg.l2dLookOffY, v -> cfg.l2dLookOffY = v, "%", "cpmwheel.look.off.help"); y += 22;
        } else { y += 4; }
        btn("cpmwheel.look.preview", b -> { preview = !preview; rebuildWidgets(); }, x2, y, w, "cpmwheel.look.preview.help", onoff(preview)); y += 22;
        btn("cpmwheel.look.debug", b -> { cfg.l2dLookDebug = !cfg.l2dLookDebug; rebuildWidgets(); }, x2, y, w, "cpmwheel.look.debug.help", onoff(cfg.l2dLookDebug)); y += 22;
        btn("cpmwheel.eyeov.open", b -> minecraft.setScreen(new EyeOverlayScreen(this, cfg)), x2, y, w, "cpmwheel.eyeov.help", onoff(cfg.l2dEyeOv)); y += 22;
        btn(Component.translatable("cpmwheel.lx.open"), b -> minecraft.setScreen(new LookExtrasScreen(this, cfg)), x2, y, w, 20, "cpmwheel.lx.open.help"); y += 22; // 1.9.0
        // column 3: privacy + smoothing
        y = 26;
        btn("cpmwheel.look.safecam", b -> { cfg.l2dLookSafeCam = !cfg.l2dLookSafeCam; rebuildWidgets(); }, x3, y, w, "cpmwheel.look.safecam.help", onoff(cfg.l2dLookSafeCam)); y += 22;
        btn("cpmwheel.look.hidden", b -> { cfg.l2dLookHideHidden = !cfg.l2dLookHideHidden; rebuildWidgets(); }, x3, y, w, "cpmwheel.look.hidden.help", onoff(cfg.l2dLookHideHidden)); y += 22;
        btn("cpmwheel.look.vanish", b -> { cfg.l2dLookVanishTag = !cfg.l2dLookVanishTag; rebuildWidgets(); }, x3, y, w, "cpmwheel.look.vanish.help", onoff(cfg.l2dLookVanishTag)); y += 22;
        btn("cpmwheel.look.los", b -> { cfg.l2dLookLos = !cfg.l2dLookLos; rebuildWidgets(); }, x3, y, w, "cpmwheel.look.los.help", onoff(cfg.l2dLookLos)); y += 24;
        btn("cpmwheel.look.smooth", b -> { cfg.l2dLookSmooth = next(List.of("spring", "lerp", "off"), cfg.l2dLookSmooth); rebuildWidgets(); }, x3, y, w, "cpmwheel.look.smooth.help",
                Component.translatable("cpmwheel.look.smooth." + cfg.l2dLookSmooth)); y += 22;
        if (!"off".equals(cfg.l2dLookSmooth)) { slider(x3, y, w, "cpmwheel.look.resp", 0, 2000, 10, () -> cfg.l2dLookRespMs, v -> cfg.l2dLookRespMs = v, " ms", "cpmwheel.look.resp.help"); y += 22; }
        slider(x3, y, w, "cpmwheel.look.maxspeed", 0, 3000, 30, () -> cfg.l2dLookMaxSpeed, v -> cfg.l2dLookMaxSpeed = v, "°/s", "cpmwheel.look.maxspeed.help"); y += 22;
        slider(x3, y, w, "cpmwheel.look.saccade", 0, 100, 5, () -> cfg.l2dLookSaccade, v -> cfg.l2dLookSaccade = v, "%", "cpmwheel.look.saccade.help"); y += 22;
        slider(x3, y, w, "cpmwheel.look.return", 0, 5000, 50, () -> cfg.l2dLookReturnMs, v -> cfg.l2dLookReturnMs = v, " ms", "cpmwheel.look.return.help"); y += 22;
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, Math.min(150, w), 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (preview) LookOverlay.draw(g, cfg, width, height, 0xC02EE6FF);
        super.render(g, mx, my, pt);
        String t = LookTargets.targetName.isEmpty() ? Component.translatable("cpmwheel.look.dbg.none").getString()
                : Component.translatable("cpmwheel.look.dbg.target", Component.translatable("cpmwheel.eyeov.kind." + LookTargets.targetName)).getString();
        String sk = LookOverlay.skipText();
        g.drawString(font, font.plainSubstrByWidth(t + (sk.isEmpty() ? "" : "   " + sk), Math.max(40, width - 166 - 8)), 166, height - 20, 0xCFE8F0);
        if (!cfg.l2dOn) g.drawString(font, Component.translatable("cpmwheel.l2d.prev.off"), 166, height - 32, 0xFF6060);
    }
}
