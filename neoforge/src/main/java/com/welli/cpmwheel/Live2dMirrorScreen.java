package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.0 "Espelhar corrente": the mirrored copy was added; here each link picks its layer (left <-> right guessed by name). */
public class Live2dMirrorScreen extends MiniScreen {
    private final Live2dMath.Chain made;
    public Live2dMirrorScreen(Screen parent, WheelConfig cfg, Live2dMath.Chain made) { super("cpmwheel.l2d.mirror.title", parent, cfg); this.made = made; }

    @Override
    protected void init() {
        int x = 8, y = 40, w = Math.min(300, width - 16);
        for (int i = 0; i < made.links.size(); i++) {
            final Live2dMath.Link l = made.links.get(i);
            btn(Component.literal((i + 1) + ". " + (l.anim.isBlank() ? Component.translatable("cpmwheel.l2d.mirror.pick").getString() : l.anim)),
                    b -> minecraft.setScreen(new ValueLayerPickerScreen(this, cfg, l.anim, v -> { l.anim = v; Live2d.reset(); })), x, y, w, 20, "cpmwheel.l2d.mirror.help");
            y += 22;
        }
        btn("cpmwheel.l2d.mirror.undo", b -> { cfg.l2dChains.remove(made); Live2d.reset(); onClose(); }, x, height - 50, 150, null);
        btn(Component.translatable("gui.done"), b -> onClose(), x, height - 26, 150, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.l2d.mirror.info", made.name).getString(), width - 16), 8, 26, 0xCFE8F0);
    }
}
