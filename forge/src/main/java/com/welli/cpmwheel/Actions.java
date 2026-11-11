package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shared actions: timed animations, recents, sounds and Alt+1..9 hotkeys. */
public final class Actions {
    private Actions() {}

    private record Timer(String anim, int revertTo, long at) {}
    private static final List<Timer> TIMERS = new ArrayList<>();
    private static final boolean[] DOWN = new boolean[9];

    /** after a slot was played: remember it, schedule timed revert, click sound */
    public static void played(WheelConfig cfg, WheelConfig.Slot s, int prior) {
        if (s == null || s.empty() || s.isFolder()) return;
        cfg.addRecent(s.anim);
        if (s.duration > 0 && !WheelConfig.RESET.equals(s.anim) && "toggle".equals(s.effectiveMode())) {
            TIMERS.removeIf(t -> t.anim.equals(s.anim));
            int now = CpmBridge.playing(s.anim);
            int back = prior >= 0 ? prior : 0;
            if (now < 0 || now != back) TIMERS.add(new Timer(s.anim, back, System.currentTimeMillis() + s.duration * 1000L));
        }
        cfg.save();
    }

    /** seconds left on a timed animation, -1 if none */
    public static int remaining(String anim) {
        for (Timer t : TIMERS) if (t.anim.equals(anim)) return (int) Math.max(0, (t.at - System.currentTimeMillis() + 999) / 1000);
        return -1;
    }

    /** Play a slot outside the wheel (hotkey / inner ring). Value slots step to their next value. */
    public static void trigger(WheelConfig cfg, WheelConfig.Slot s) {
        if (s == null || s.empty() || s.isFolder()) return;
        int prior = WheelConfig.RESET.equals(s.anim) ? -1 : CpmBridge.playing(s.anim);
        String m = WheelConfig.RESET.equals(s.anim) ? "toggle" : s.effectiveMode();
        int cur = prior < 0 ? s.minV() : prior;
        switch (m) {
            case "frames" -> CpmBridge.play(s.anim, s.frameValue((s.frameIndexOf(cur) + 1) % Math.max(1, s.frames.size())));
            case "presets" -> { int[] ps = s.presetValues(); int next = ps[0]; for (int p : ps) if (p > cur) { next = p; break; } CpmBridge.play(s.anim, next); }
            case "slider" -> CpmBridge.play(s.anim, s.value >= 0 ? (cur == s.value ? s.minV() : s.value) : (cur > s.minV() ? s.minV() : s.maxV()));
            default -> WheelConfig.run(s);
        }
        played(cfg, s, prior);
        sound(cfg, 1.2f);
    }

    public static void sound(WheelConfig cfg, float pitch) {
        if (cfg == null || !cfg.sounds || cfg.soundVolume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, cfg.soundVolume / 100f * 0.5f));
    }

    public static void hoverSound(WheelConfig cfg) {
        if (cfg == null || !cfg.sounds || cfg.soundVolume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, cfg.soundVolume / 100f * 0.25f));
    }

    /** called every client tick */
    public static void tick(Minecraft mc) {
        if (!TIMERS.isEmpty()) {
            long now = System.currentTimeMillis();
            for (Iterator<Timer> it = TIMERS.iterator(); it.hasNext(); ) {
                Timer t = it.next();
                if (now >= t.at) { it.remove(); CpmBridge.play(t.anim, t.revertTo); Hud.invalidate(); }
            }
        }
        if (mc.screen != null || mc.player == null) { java.util.Arrays.fill(DOWN, false); return; }
        long w = mc.getWindow().getWindow();
        boolean alt = InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT);
        if (!alt) { java.util.Arrays.fill(DOWN, false); return; }
        for (int i = 0; i < 9; i++) {
            boolean d = InputConstants.isKeyDown(w, GLFW.GLFW_KEY_1 + i);
            if (d && !DOWN[i]) {
                WheelConfig cfg = WheelConfig.get(); // only on key press, not per tick
                if (!cfg.altHotkeys) { DOWN[i] = d; continue; }
                List<WheelConfig.Page> pages = cfg.viewPages();
                WheelConfig.Page p = pages.get(Math.floorMod(cfg.lastPage, pages.size()));
                if (i < p.slots.size()) {
                    WheelConfig.Slot s = p.slots.get(i);
                    trigger(cfg, s);
                    if (!s.empty() && !s.isFolder())
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u25B6 " + s.display()), true);
                }
            }
            DOWN[i] = d;
        }
    }
}
