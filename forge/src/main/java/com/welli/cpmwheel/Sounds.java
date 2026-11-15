package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/** 1.6.0 custom sounds (client side only): per wheel button and per trigger rule, any vanilla / mod / resource-pack sound id. */
public final class Sounds {
    private Sounds() {}

    /** play one sound id at volume / pitch in % (ignores the global switch: used by the preview buttons) */
    public static void play(String id, int vol, int pitch) {
        try {
            if (id == null || id.isBlank() || vol <= 0) return;
            ResourceLocation rl = ResourceLocation.tryParse(id.trim());
            if (rl == null) return;
            SoundEvent ev = SoundEvent.createVariableRangeEvent(rl);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, Math.max(0.5f, Math.min(2f, pitch / 100f)), Math.min(2f, vol / 100f)));
        } catch (Throwable t) { CpmWheel.LOG.debug("[cpmwheel] sound failed: " + id, t); }
    }

    /** spec: "end" = restore / turned off (only if an end sound is set) */
    public static void play(WheelConfig cfg, WheelConfig.SoundSpec s, boolean end) {
        if (cfg == null || !cfg.customSounds || s == null) return;
        play(end ? s.endId : s.id, s.vol, s.pitch);
    }

    /** after a wheel button was used: activate sound if it is on now, restore sound if it was switched off */
    public static void slot(WheelConfig cfg, WheelConfig.Slot s, int prior) {
        if (cfg == null || !cfg.customSounds || s == null || s.sound == null || s.sound.empty()) return;
        boolean on;
        if (s.isGroup()) on = s.groupState() == 2;
        else if (s.isEc()) on = EmoteBridge.playing();
        else if (s.isCond() || WheelConfig.RESET.equals(s.anim)) on = true;
        else on = CpmBridge.playing(s.anim) != 0; // unknown (-1) counts as on
        play(cfg, s.sound, !on);
    }

    /** every known sound id (vanilla, mods, resource packs), sorted */
    public static List<String> all() {
        List<String> l = new ArrayList<>();
        try { for (ResourceLocation rl : Minecraft.getInstance().getSoundManager().getAvailableSounds()) l.add(rl.toString()); } catch (Throwable ignored) {}
        l.sort(null);
        return l;
    }
}
