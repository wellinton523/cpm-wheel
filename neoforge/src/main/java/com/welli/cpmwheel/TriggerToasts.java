package com.welli.cpmwheel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 1.5.8 trigger notifications: small styled toasts (effect icon, rule name, what changed, coloured side bar),
 * slide/fade in and out, a thin timer bar with the effect's remaining time; while the effect lasts the toast
 * shrinks to a compact row with the timer (optional); "Efeito acabou → restaurado" when it ends.
 */
public final class TriggerToasts {
    private TriggerToasts() {}
    private static final int W = 172, H = 30, MINI_H = 14, GAP = 3, IN_MS = 260, OUT_MS = 320;

    private static final class T {
        String ruleId, effect, icon = "", title, text; int color; boolean end;
        long start, dur; long outAt = -1; int maxDur = 1;
        float hNow = H;
    }
    private static final List<T> LIST = new ArrayList<>();

    /** effect started: icon + rule name + "Veneno → Humano desativado" */
    public static void start(String ruleId, String effect, String title, String text, int color) { start(ruleId, effect, "", title, text, color); }
    public static void start(String ruleId, String effect, String icon, String title, String text, int color) {
        WheelConfig c = WheelConfig.get();
        if (!c.toastOn) return;
        for (T t : LIST) if (ruleId.equals(t.ruleId) && t.outAt < 0) t.outAt = System.currentTimeMillis();
        T t = new T(); t.ruleId = ruleId; t.effect = effect; t.icon = icon; t.title = title; t.text = text; t.color = color;
        t.start = System.currentTimeMillis(); t.dur = c.toastSecs * 1000L;
        LIST.add(t);
        while (LIST.size() > 6) LIST.remove(0);
    }
    /** effect ended: the start toast leaves, "Efeito acabou → restaurado" comes in */
    public static void end(String ruleId, String effect, String icon, String title, String text, int color) {
        long now = System.currentTimeMillis();
        for (T t : LIST) if (ruleId.equals(t.ruleId) && !t.end && t.outAt < 0) t.outAt = now;
        WheelConfig c = WheelConfig.get();
        if (!c.toastOn) return;
        T t = new T(); t.ruleId = ruleId; t.effect = effect; t.icon = icon; t.title = title; t.text = text; t.color = color; t.end = true;
        t.start = now; t.dur = Math.max(1500L, c.toastSecs * 750L);
        LIST.add(t);
        while (LIST.size() > 6) LIST.remove(0);
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); float u = 1 - t; return 1 - u * u * u; }

    /** remaining fraction of the effect (0..1), or -1 if unknown / infinite */
    private static float remaining(T t) {
        float test = EffectTriggers.testFraction(t.ruleId);
        if (test >= 0) return test;
        var p = Minecraft.getInstance().player;
        if (p == null) return -1;
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (!t.effect.equals(ItemInfo.effectId(e))) continue;
            if (e.isInfiniteDuration()) return -1;
            t.maxDur = Math.max(t.maxDur, e.getDuration());
            return Math.max(0, Math.min(1, e.getDuration() / (float) t.maxDur));
        }
        return -1;
    }

    public static void render(GuiGraphics g, WheelConfig cfg, boolean editing) {
        long now = System.currentTimeMillis();
        List<T> show = LIST;
        if (editing && LIST.isEmpty()) {
            T s = new T(); s.ruleId = "_sample"; s.effect = "minecraft:poison"; s.title = Component.translatable("cpmwheel.toast.sample.title").getString();
            s.text = Component.translatable("cpmwheel.toast.sample.text").getString(); s.color = 0x3BFF9D; s.start = now - IN_MS; s.dur = Long.MAX_VALUE / 4;
            show = List.of(s);
        }
        if (show.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        WheelConfig.HudPos hp = Hud.pos(cfg, "toasts");
        float sc = hp.scale / 100f;
        int sw = g.guiWidth(), sh = g.guiHeight();
        // advance states, total height
        int total = 0;
        for (Iterator<T> it = LIST.iterator(); it.hasNext(); ) {
            T t = it.next();
            boolean holdMini = !t.end && cfg.toastMini && EffectTriggers.isActive(t.ruleId);
            if (t.outAt < 0 && now - t.start > t.dur && !holdMini) t.outAt = now;
            if (t.outAt >= 0 && now - t.outAt > OUT_MS) { it.remove(); continue; }
        }
        for (T t : show) {
            boolean mini = !t.end && t.outAt < 0 && now - t.start > t.dur && t.dur < Long.MAX_VALUE / 8;
            float target = mini ? MINI_H : H;
            t.hNow += (target - t.hNow) * 0.2f;
            if (Math.abs(target - t.hNow) < 0.3f) t.hNow = target;
            total += Math.round(t.hNow) + GAP;
        }
        if (show.isEmpty()) return;
        int[] xy = Hud.place(hp, Math.round(W * sc), Math.max(1, Math.round(total * sc)), sw, sh);
        Hud.BOUNDS.put("toasts", new int[]{xy[0], xy[1], Math.round(W * sc), Math.max(Math.round(H * sc), Math.round(total * sc))});
        boolean fromRight = Hud.ax(hp.anchor) >= 0.5f;
        boolean upward = Hud.ay(hp.anchor) >= 1f; // anchored at the bottom: newest at the bottom, list grows up
        int bg = Math.max(0, Math.min(255, cfg.toastOpacity * 255 / 100));
        g.pose().pushPose();
        g.pose().translate(xy[0], xy[1], 0);
        g.pose().scale(sc, sc, 1);
        int y = 0;
        List<T> order = new ArrayList<>(show);
        if (!upward) java.util.Collections.reverse(order); // newest first when growing down
        for (T t : order) {
            int h = Math.round(t.hNow);
            float in = ease((now - t.start) / (float) IN_MS);
            float out = t.outAt < 0 ? 1f : 1f - ease((now - t.outAt) / (float) OUT_MS);
            float a = Math.min(in, out);
            if (a <= 0.03f) { y += h + GAP; continue; }
            int off = Math.round((1 - a) * (W + 10)) * (fromRight ? 1 : -1);
            g.pose().pushPose();
            g.pose().translate(off, y, 0);
            int A = Math.max(4, Math.round(a * 255));
            g.fill(0, 0, W, h, (Math.round(bg * a) << 24) | 0x070D16);
            g.fill(0, 0, 3, h, (A << 24) | (t.color & 0xFFFFFF));               // coloured side bar
            boolean mini = h < (H + MINI_H) / 2;
            int is = mini ? 10 : 18;
            TextureAtlasSprite sp = ItemInfo.effectSprite(t.effect);
            if (sp != null) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1, 1, 1, a);
                g.blit(6, (h - is) / 2, 0, is, is, sp);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            } else if (t.icon != null && !t.icon.isEmpty() && a > 0.5f) {               // 1.6.0: item icon for non-effect triggers
                var rl = net.minecraft.resources.ResourceLocation.tryParse(t.icon);
                var item = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                if (item != null) {
                    g.pose().pushPose();
                    g.pose().translate(6, (h - is) / 2f, 0);
                    g.pose().scale(is / 16f, is / 16f, 1);
                    g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
                    g.pose().popPose();
                }
            }
            int tx = 6 + is + 5;
            if (mini) {
                String txt = font.plainSubstrByWidth(t.title, W - tx - 6);
                g.drawString(font, txt, tx, (h - 8) / 2, (A << 24) | 0xE8F4F8, false);
            } else {
                g.drawString(font, font.plainSubstrByWidth(t.title, W - tx - 6), tx, 5, (A << 24) | 0xFFFFFF, false);
                g.drawString(font, font.plainSubstrByWidth(t.text, W - tx - 6), tx, 16, (A << 24) | (t.end ? 0x9FE8B8 : 0xB8CCD8), false);
            }
            if (!t.end) {                                                          // thin timer bar: effect's remaining time
                float r = remaining(t);
                if (r >= 0) {
                    g.fill(tx, h - 3, W - 6, h - 2, (Math.round(a * 90) << 24) | 0x1A2A3A);
                    g.fill(tx, h - 3, tx + Math.round((W - 6 - tx) * r), h - 2, (A << 24) | (t.color & 0xFFFFFF));
                }
            }
            g.pose().popPose();
            y += h + GAP;
        }
        g.pose().popPose();
    }
}
