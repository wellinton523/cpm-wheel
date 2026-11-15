package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.8.0 "Teclas de emotes": which animation each key (Options › Controls › CPM Wheel) plays without opening the wheel. */
public class EmoteKeysScreen extends MiniScreen {
    public EmoteKeysScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.ek.title", parent, cfg); }

    @Override
    protected void init() {
        cfg.fixup();
        int x = 8, y = 26, w = Math.min(330, width - 16);
        btn("cpmwheel.ek.on", b -> { cfg.emoteKeysOn = !cfg.emoteKeysOn; rebuildWidgets(); }, x, y, 160, "cpmwheel.ek.help", onoff(cfg.emoteKeysOn));
        btn("cpmwheel.ek.controls", b -> minecraft.setScreen(new net.minecraft.client.gui.screens.controls.KeyBindsScreen(this, minecraft.options)), x + 164, y, Math.max(100, w - 164), "cpmwheel.ek.controls.help");
        y += 26;
        for (int i = 0; i < CpmWheel.EMOTE_KEYS; i++) {
            final int idx = i;
            String a = cfg.emoteKeys.get(i);
            Component key = CpmWheel.KEY_EMOTE[i].getTranslatedKeyMessage();
            btn(Component.translatable("cpmwheel.ek.slot", i + 1, key, a.isBlank() ? "\u2014" : a), b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, v -> cfg.emoteKeys.set(idx, v))),
                    x, y, w - 22, 20, "cpmwheel.ek.pick.help");
            btn(Component.literal("\u2716"), b -> { cfg.emoteKeys.set(idx, ""); rebuildWidgets(); }, x + w - 20, y, 20, 20, null);
            y += 22;
        }
        btn(Component.translatable("gui.done"), b -> onClose(), x, height - 26, 150, 20, null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!cfg.emoteKeysOn) g.drawString(font, Component.translatable("cpmwheel.ek.offnote"), 166, height - 20, 0x7FA8B8);
    }
}
