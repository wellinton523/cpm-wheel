package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.*;

/**
 * 1.5.7 "Gatilhos de efeito": while a status effect is on the local player, rules switch CPM animations / group buttons;
 * when it ends (timeout, milk, death...) the exact previous value of every touched animation comes back.
 *
 * Bookkeeping per animation ("hold"): the value before the first rule touched it + every active rule's value in order.
 * - several rules on the same animation: the most recent still-active rule wins; the original value only comes back
 *   when no active rule holds it any more;
 * - if you change a held animation yourself while the effect is on, the mod does not fight you: your value becomes
 *   the one restored at the end (so it simply stays as you left it);
 * - debounce: a rule starts after the effect was present for "delayStart" ms and ends after it was gone for "delayEnd" ms.
 * Client side only, checked every client tick.
 */
public final class EffectTriggers {
    private EffectTriggers() {}

    private static final class RState { boolean cond; long since; boolean active; long testUntil; String label = ""; String onEnd = "restore"; }
    private static final class Hold { int original; int applied; long appliedAt; final LinkedHashMap<String, Integer> by = new LinkedHashMap<>(); }

    private static final Map<String, RState> RULES = new HashMap<>();
    private static final Map<String, Hold> HOLDS = new LinkedHashMap<>();
    private static long lastManualCheck = 0;
    private static boolean failed = false;

    public static String effectName(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            MobEffect e = rl == null ? null : BuiltInRegistries.MOB_EFFECT.get(rl);
            if (e != null) return e.getDisplayName().getString();
        } catch (Throwable ignored) {}
        return id;
    }
    public static List<String> allEffects() {
        List<String> l = new ArrayList<>();
        for (ResourceLocation rl : BuiltInRegistries.MOB_EFFECT.keySet()) l.add(rl.toString());
        l.sort(Comparator.comparing(EffectTriggers::effectName, String.CASE_INSENSITIVE_ORDER));
        return l;
    }

    public static String ruleLabel(WheelConfig.EffectRule r) {
        return r.name != null && !r.name.isBlank() ? r.name : effectName(r.effect);
    }

    /** is this rule currently applied (for the editor) */
    public static boolean isActive(String ruleId) { RState s = RULES.get(ruleId); return s != null && s.active; }
    public static boolean isTesting(String ruleId) { RState s = RULES.get(ruleId); return s != null && s.testUntil > System.currentTimeMillis(); }

    /** "Testar": pretend the effect is on for a few seconds (then it ends and restores like a real effect) */
    public static void test(WheelConfig.EffectRule r, int seconds) {
        RState s = RULES.computeIfAbsent(r.id, k -> new RState());
        long now = System.currentTimeMillis();
        s.testUntil = seconds <= 0 ? 0 : now + seconds * 1000L;
    }

    /** every client tick (Actions.tick) with the cached config */
    public static void tick(Minecraft mc, WheelConfig cfg) {
        if (failed || cfg == null) return;
        LocalPlayer p = mc.player;
        if (p == null) return; // world change / respawn screen: keep holds, they are released once the player is back
        try {
            long now = System.currentTimeMillis();
            Map<String, Integer> eff = new HashMap<>();
            for (MobEffectInstance e : p.getActiveEffects()) {
                String id = ItemInfo.effectId(e);
                if (!id.isEmpty()) eff.merge(id, e.getAmplifier(), Math::max);
            }
            if (p.isDeadOrDying()) eff.clear();
            Set<String> seen = new HashSet<>();
            for (WheelConfig.EffectRule r : cfg.effRules) {
                seen.add(r.id);
                RState s = RULES.computeIfAbsent(r.id, k -> new RState());
                s.label = ruleLabel(r); s.onEnd = r.onEnd;
                Integer amp = eff.get(r.effect);
                boolean want = cfg.effOn && r.on && ((amp != null && amp >= r.minAmp) || s.testUntil > now);
                if (want != s.cond) { s.cond = want; s.since = now; }
                if (want && !s.active && now - s.since >= (s.testUntil > now ? 0 : r.delayStart)) start(cfg, r, s, now);
                else if (!want && s.active && (now - s.since >= r.delayEnd || !cfg.effOn || !r.on)) end(cfg, r.id, s, r.notify, now);
            }
            // rules deleted / other model profile: release what they still hold
            for (var it = RULES.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (seen.contains(e.getKey())) continue;
                if (e.getValue().active) end(cfg, e.getKey(), e.getValue(), false, now);
                it.remove();
            }
            if (!HOLDS.isEmpty() && now - lastManualCheck > 500) { lastManualCheck = now; manual(now); }
        } catch (Throwable t) {
            failed = true;
            CpmWheel.LOG.warn("[cpmwheel] effect triggers disabled after an error", t);
        }
    }

    /** you changed a held animation yourself: keep it, and make it the value restored at the end */
    private static void manual(long now) {
        for (var e : HOLDS.entrySet()) {
            Hold h = e.getValue();
            if (now - h.appliedAt < 1000) continue;
            int cur = CpmBridge.playing(e.getKey());
            if (cur >= 0 && cur != h.applied) {
                h.original = cur; h.applied = cur; h.appliedAt = now;
                for (var k : h.by.keySet()) h.by.put(k, cur);
                CpmWheel.LOG.info("[cpmwheel] effect trigger: '" + e.getKey() + "' changed by hand while held -> keeping " + cur);
            }
        }
    }

    private static void start(WheelConfig cfg, WheelConfig.EffectRule r, RState s, long now) {
        s.active = true;
        LinkedHashMap<String, Integer> plan = resolve(cfg, r.actions);
        for (var e : plan.entrySet()) {
            String anim = e.getKey(); int v = e.getValue();
            Hold h = HOLDS.get(anim);
            if (h == null) { h = new Hold(); h.original = Math.max(0, CpmBridge.playing(anim)); HOLDS.put(anim, h); }
            h.by.remove(r.id); h.by.put(r.id, v);
            if (CpmBridge.playing(anim) != v) CpmBridge.play(anim, v);
            h.applied = v; h.appliedAt = now;
        }
        markGroups(cfg, r.actions, true);
        Hud.invalidate();
        if (r.notify) notice(Component.translatable("cpmwheel.eff.notice.on", s.label));
    }

    private static void end(WheelConfig cfg, String ruleId, RState s, boolean notify, long now) {
        s.active = false;
        s.testUntil = 0;
        boolean restore = !"none".equals(s.onEnd);
        for (var it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next(); Hold h = e.getValue();
            if (h.by.remove(ruleId) == null) continue;
            if (!h.by.isEmpty()) { // another active rule still holds it: its value
                int v = new ArrayList<>(h.by.values()).get(h.by.size() - 1);
                if (v != h.applied) { CpmBridge.play(e.getKey(), v); h.applied = v; h.appliedAt = now; }
                continue;
            }
            if (restore && CpmBridge.playing(e.getKey()) != h.original) CpmBridge.play(e.getKey(), h.original);
            it.remove();
        }
        if (cfg != null) for (WheelConfig.EffectRule r : cfg.effRules) if (r.id.equals(ruleId)) markGroups(cfg, r.actions, false);
        Hud.invalidate();
        if (notify) notice(Component.translatable(restore ? "cpmwheel.eff.notice.off" : "cpmwheel.eff.notice.offkeep", s.label));
    }

    private static void notice(Component c) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(c, true);
    }

    /** group buttons: keep their on/off flag in sync for the HUD (values come from resolve) */
    private static void markGroups(WheelConfig cfg, List<WheelConfig.EffectAction> acts, boolean starting) {
        for (WheelConfig.EffectAction a : acts) {
            if (!a.target.startsWith("group:")) continue;
            WheelConfig.Slot g = cfg.groupSlot(a.target.substring(6));
            if (g == null) continue;
            g.groupState(); // HUD reads live values; groupOn only matters for toggle-only groups
            if (g.groupPrev != null) g.groupPrev.clear();
            if (starting) g.groupOn = "on".equals(a.op) || ("toggle".equals(a.op) && g.groupState() == 2);
            else g.groupOn = g.groupState() == 2;
        }
    }

    /** actions -> animation -> value (later actions win) */
    static LinkedHashMap<String, Integer> resolve(WheelConfig cfg, List<WheelConfig.EffectAction> acts) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        Set<String> known = CpmBridge.listAnimations().keySet();
        for (WheelConfig.EffectAction a : acts) {
            if (a.target.startsWith("anim:")) {
                String anim = a.target.substring(5);
                if (!known.isEmpty() && !known.contains(anim)) continue;
                int cur = Math.max(0, CpmBridge.playing(anim));
                int on = onValue(cfg, anim, a.value);
                out.put(anim, switch (a.op) { case "on" -> on; case "toggle" -> cur > 0 ? 0 : on; default -> 0; });
            } else if (a.target.startsWith("group:")) {
                WheelConfig.Slot g = cfg.groupSlot(a.target.substring(6));
                if (g == null) continue;
                boolean apply = switch (a.op) { case "on" -> true; case "toggle" -> g.groupState() != 2; default -> false; };
                for (WheelConfig.Member m : g.groupList()) {
                    if (!known.isEmpty() && !known.contains(m.anim)) continue;
                    int cur = Math.max(0, CpmBridge.playing(m.anim));
                    int v;
                    if ("toggle".equals(m.action)) v = cur > 0 ? 0 : 1;
                    else if (apply) v = m.target();
                    else v = "off".equals(m.action) ? 1 : 0; // DESATIVAR = the opposite of what the group switches on
                    out.put(m.anim, v);
                }
            }
        }
        return out;
    }

    /** value used by ATIVAR: explicit value, else the wheel button's value, else 1 / max for value animations */
    static int onValue(WheelConfig cfg, String anim, int explicit) {
        if (explicit >= 0) return explicit;
        for (WheelConfig.Slot s : cfg.allSlots()) if (anim.equals(s.anim) && !s.isGroup() && s.value > 0) return s.value;
        int max = CpmBridge.maxValue(anim);
        return max > 1 ? max : 1;
    }

    /** text for the rule list: "Veneno → Humano: DESATIVAR" */
    public static String summary(WheelConfig.EffectRule r) {
        StringBuilder sb = new StringBuilder();
        for (WheelConfig.EffectAction a : r.actions) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(targetName(a.target)).append(' ').append(Component.translatable("cpmwheel.eff.op.short." + a.op).getString());
        }
        return sb.length() == 0 ? Component.translatable("cpmwheel.eff.noactions").getString() : sb.toString();
    }
    public static String targetName(String t) {
        if (t.startsWith("group:")) return Component.translatable("cpmwheel.eff.t.group", t.substring(6)).getString();
        if (t.startsWith("anim:")) return t.substring(5);
        return t;
    }
}
