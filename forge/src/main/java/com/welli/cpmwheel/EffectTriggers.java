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
 * - 1.5.8 lock (per rule, on by default): the wheel buttons of held animations are locked and any outside change is
 *   put back, so the end always restores the pre-trigger state; with the lock off, a manual change is kept and becomes
 *   the value restored at the end (old behaviour);
 * - debounce: a rule starts after the effect was present for "delayStart" ms and ends after it was gone for "delayEnd" ms.
 * Client side only, checked every client tick.
 */
public final class EffectTriggers {
    private EffectTriggers() {}

    private static final class RState {
        final TriggerLogic.Debounce db = new TriggerLogic.Debounce();
        boolean active; // actions applied
        long testUntil, testStart; String label = ""; String onEnd = "restore"; boolean lock = true; String effect = "", icon = "";
        /** 1.6.0 timed transition: "" / "in" (before applying) / "out" (before restoring); transition animation and its previous value */
        String phase = ""; long phaseUntil; String transAnim = ""; int transPrev = -1;
    }
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
        return r.name != null && !r.name.isBlank() ? r.name : condsText(r);
    }

    /** is this rule currently applied (for the editor) */
    public static boolean isActive(String ruleId) { RState s = RULES.get(ruleId); return s != null && s.active; }
    public static boolean isTesting(String ruleId) { RState s = RULES.get(ruleId); return s != null && s.testUntil > System.currentTimeMillis(); }

    /** "Testar": pretend the effect is on for a few seconds (then it ends and restores like a real effect) */
    public static void test(WheelConfig.EffectRule r, int seconds) {
        RState s = RULES.computeIfAbsent(r.id, k -> new RState());
        long now = System.currentTimeMillis();
        s.testUntil = seconds <= 0 ? 0 : now + seconds * 1000L;
        s.testStart = now;
    }
    /** "Testar" countdown for the toast timer bar (0..1), -1 when not testing */
    public static float testFraction(String ruleId) {
        RState s = RULES.get(ruleId);
        long now = System.currentTimeMillis();
        if (s == null || s.testUntil <= now || s.testUntil <= s.testStart) return -1;
        return (s.testUntil - now) / (float) (s.testUntil - s.testStart);
    }

    // ---------- 1.5.8 lock: buttons of animations held by a trigger ----------
    /** 1.7.0: a trigger rule currently holds (overrides) this animation */
    public static boolean held(String anim) { return anim != null && HOLDS.containsKey(anim); }
    /** name of the active locking rule holding this animation, or null */
    public static String lockedAnim(String anim) {
        if (anim == null) return null;
        Hold h = HOLDS.get(anim);
        if (h == null) return null;
        for (String id : h.by.keySet()) { RState s = RULES.get(id); if (s != null && s.active && s.lock) return s.label; }
        return null;
    }
    /** name of the rule locking this wheel button (animation, group containing a held animation, reset), or null */
    public static String lockedBy(WheelConfig.Slot s) {
        if (s == null || s.empty() || s.isFolder() || HOLDS.isEmpty()) return null;
        if (WheelConfig.RESET.equals(s.anim)) { for (String a : HOLDS.keySet()) { String l = lockedAnim(a); if (l != null) return l; } return null; }
        if (s.isGroup()) { for (WheelConfig.Member m : s.groupList()) { String l = lockedAnim(m.anim); if (l != null) return l; } return null; }
        if (s.isCond() || s.isEc()) return null;
        return lockedAnim(s.anim);
    }
    /** locked feedback: action bar + low click */
    public static void lockedFeedback(String label) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.translatable("cpmwheel.eff.locked", label), true);
        Actions.sound(WheelConfig.get(), 0.5f);
    }

    /** every client tick (Actions.tick) with the cached config */
    public static void tick(Minecraft mc, WheelConfig cfg) {
        if (failed || cfg == null) return;
        LocalPlayer p = mc.player;
        if (p == null) return; // world change / respawn screen: keep holds, they are released once the player is back
        try {
            long now = System.currentTimeMillis();
            TriggerLogic.Snapshot snap = cfg.effRules.isEmpty() ? null : snapshot(p, cfg, now);
            Set<String> seen = new HashSet<>();
            for (WheelConfig.EffectRule r : cfg.effRules) {
                seen.add(r.id);
                RState s = RULES.computeIfAbsent(r.id, k -> new RState());
                s.onEnd = r.onEnd; s.lock = r.lock;
                boolean extraOk = cfg.trigCondsOn || r.conds.stream().allMatch(c -> "effect".equals(c.type)); // "Condições extras" off: only effect rules run
                boolean testing = s.testUntil > now;
                boolean enabled = cfg.effOn && r.on;
                boolean want = enabled && (testing || (extraOk && snap != null && TriggerLogic.eval(r.conds, r.combine, snap)));
                int ev = s.db.update(want, now, testing ? 0 : r.delayStart, enabled ? r.delayEnd : 0);
                boolean trans = cfg.trigTransOn && r.transAnim != null && !r.transAnim.isBlank();
                if (ev != 0 || !s.phase.isEmpty() || s.label.isEmpty()) { s.label = ruleLabel(r); s.effect = iconEffect(r); s.icon = iconItem(r); }
                if (ev > 0) {
                    if ("out".equals(s.phase)) { finishTrans(s); s.phase = ""; }            // came back during the restore transition: stay on
                    else if (!s.active && s.phase.isEmpty()) {
                        if (trans) beginTrans(cfg, r, s, "in", now); else start(cfg, r, s, now);
                    }
                } else if (ev < 0) {
                    if ("in".equals(s.phase)) { finishTrans(s); s.phase = ""; }             // ended before switching: nothing was applied
                    else if (s.active && s.phase.isEmpty()) {
                        if (trans && r.transOnEnd && enabled) beginTrans(cfg, r, s, "out", now); else end(cfg, r.id, s, r.notify, now);
                    }
                }
                if (!s.phase.isEmpty() && now >= s.phaseUntil) {
                    String ph = s.phase; s.phase = "";
                    if (ph.equals("in")) start(cfg, r, s, now); else end(cfg, r.id, s, r.notify, now);
                    finishTrans(s);
                }
            }
            // rules deleted / other model profile: release what they still hold
            for (var it = RULES.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (seen.contains(e.getKey())) continue;
                if (!e.getValue().phase.isEmpty()) { finishTrans(e.getValue()); e.getValue().phase = ""; }
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
            if (cur >= 0 && cur != h.applied && lockedAnim(e.getKey()) != null) { // locked: put the trigger's value back
                CpmBridge.play(e.getKey(), h.applied); h.appliedAt = now;
                continue;
            }
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
        Sounds.play(cfg, r.sound, false);
        if (r.notify) TriggerToasts.start(r.id, s.effect, s.icon, s.label, condsText(r) + " \u2192 " + changes(r), color(cfg, r));
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
        if (!notify && cfg != null) for (WheelConfig.EffectRule r : cfg.effRules) if (r.id.equals(ruleId)) Sounds.play(cfg, r.sound, true);
        if (notify) {
            WheelConfig.EffectRule rr = null;
            if (cfg != null) for (WheelConfig.EffectRule r : cfg.effRules) if (r.id.equals(ruleId)) rr = r;
            if (rr != null) Sounds.play(cfg, rr.sound, true);
            TriggerToasts.end(ruleId, s.effect, s.icon, s.label, Component.translatable((restore ? "cpmwheel.toast.end" : "cpmwheel.toast.endkeep") + (s.effect.isEmpty() ? ".cond" : "")).getString(),
                    rr == null ? 0x3BFF9D : color(cfg, rr));
        }
    }

    // ---------- 1.6.0 conditions ----------
    private static long lastHurt = Long.MIN_VALUE / 2; private static int prevHurtTime = 0; private static float prevHealth = -1;

    /** the player right now, for TriggerLogic */
    static TriggerLogic.Snapshot snapshot(LocalPlayer p, WheelConfig cfg, long now) {
        TriggerLogic.Snapshot sn = new TriggerLogic.Snapshot();
        for (MobEffectInstance e : p.getActiveEffects()) {
            String id = ItemInfo.effectId(e);
            if (!id.isEmpty()) sn.effects.merge(id, e.getAmplifier(), Math::max);
        }
        boolean dead = p.isDeadOrDying();
        if (dead) sn.effects.clear();
        float hp = p.getHealth();
        if (p.hurtTime > prevHurtTime || (prevHealth >= 0 && hp < prevHealth - 0.01f)) lastHurt = now;
        prevHurtTime = p.hurtTime; prevHealth = hp;
        sn.health = hp;
        sn.msSinceHurt = now - lastHurt;
        var level = p.level();
        sn.dayTime = level.getDayTime();
        sn.fixedTime = level.dimensionType().hasFixedTime();
        sn.raining = level.isRaining(); sn.thundering = level.isThundering();
        sn.underwater = p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        sn.dimension = level.dimension().location().toString();
        sn.sneaking = p.isCrouching(); sn.sprinting = p.isSprinting();
        net.minecraft.core.BlockPos pos = p.blockPosition();
        sn.biome = v -> {
            var h = level.getBiome(pos);
            if (v.startsWith("#")) {
                ResourceLocation t = ResourceLocation.tryParse(v.substring(1));
                return t != null && h.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, t));
            }
            String id = h.unwrapKey().map(k -> k.location().toString()).orElse("");
            String q = v.toLowerCase(java.util.Locale.ROOT);
            return q.contains(":") ? id.equals(q) : id.endsWith(":" + q);
        };
        sn.hold = (v, hand) -> (hand != 2 && holds(cfg, p.getMainHandItem(), v)) || (hand != 1 && holds(cfg, p.getOffhandItem(), v));
        // 1.7.2
        sn.anim = (v, n) -> CpmBridge.playing(v) >= n;
        sn.flying = p.getAbilities().flying;
        sn.gliding = p.isFallFlying();
        sn.swimming = p.isSwimming() || p.isVisuallySwimming();
        sn.riding = p.isPassenger();
        sn.sleeping = p.isSleeping();
        sn.emote = v -> EmoteBridge.active(cfg) && EmoteBridge.matches(v);
        if (dead) { sn.sneaking = false; sn.sprinting = false; sn.flying = false; sn.gliding = false; sn.swimming = false; }
        return sn;
    }
    /** snapshot for screens (doesn't touch the "took damage" tracking) */
    static TriggerLogic.Snapshot peek(LocalPlayer p, WheelConfig cfg) {
        long lh = lastHurt; int ph = prevHurtTime; float pv = prevHealth;
        TriggerLogic.Snapshot sn = snapshot(p, cfg, System.currentTimeMillis());
        lastHurt = lh; prevHurtTime = ph; prevHealth = pv;
        sn.msSinceHurt = System.currentTimeMillis() - lastHurt;
        return sn;
    }
    /** "grp:<id>" = an item group, anything else = the same search as "Criar grupo" (backpack | mochila, @mod, -word) */
    static boolean holds(WheelConfig cfg, net.minecraft.world.item.ItemStack st, String v) {
        if (st.isEmpty()) return false;
        var it = st.getItem(); ResourceLocation rl = BuiltInRegistries.ITEM.getKey(it);
        if (v.startsWith("grp:")) { WheelConfig.ItemGroup g = cfg.group(v.substring(4)); return g != null && ItemGroups.matches(g, it, rl); }
        return ItemGroups.Query.of(v).matches(it, rl, true);
    }

    static String iconEffect(WheelConfig.EffectRule r) {
        for (TriggerLogic.Cond c : r.conds) if ("effect".equals(c.type) && !c.not && !c.value.isBlank()) return c.value;
        return "";
    }
    /** item shown in the toast for non-effect conditions */
    static String iconItem(WheelConfig.EffectRule r) {
        for (TriggerLogic.Cond c : r.conds) {
            String i = switch (c.type) {
                case "health" -> "minecraft:golden_apple"; case "damage" -> "minecraft:iron_sword";
                case "night" -> "minecraft:clock"; case "day" -> "minecraft:sunflower";
                case "rain" -> "minecraft:water_bucket"; case "thunder" -> "minecraft:lightning_rod";
                case "water" -> "minecraft:heart_of_the_sea"; case "biome" -> "minecraft:grass_block";
                case "dimension" -> "minecraft:obsidian"; case "sneak" -> "minecraft:leather_boots";
                case "sprint" -> "minecraft:feather"; case "hold" -> "minecraft:chest";
                case "anim" -> "minecraft:armor_stand"; case "fly" -> "minecraft:phantom_membrane"; case "glide" -> "minecraft:elytra";
                case "swim" -> "minecraft:tropical_fish"; case "ride" -> "minecraft:saddle"; case "sleep" -> "minecraft:red_bed";
                default -> "";
            };
            if (!i.isEmpty()) return i;
        }
        return "";
    }
    /** one condition in words: "Vida ≤ 3 ❤", "não Noite", "Bioma: deserto" */
    public static String condText(WheelConfig cfg, TriggerLogic.Cond c) {
        String t = switch (c.type) {
            case "effect" -> effectName(c.value) + (c.num > 0 ? " " + EffectTriggersScreen.roman(c.num + 1) + "+" : "");
            case "health" -> Component.translatable("cpmwheel.cond.t.health.v", String.format(java.util.Locale.ROOT, "%.1f", Math.max(1, c.num) / 2f)).getString();
            case "damage" -> Component.translatable("cpmwheel.cond.t.damage.v", Math.max(1, c.num)).getString();
            case "biome" -> Component.translatable("cpmwheel.cond.t.biome.v", c.value.isBlank() ? "?" : c.value).getString();
            case "dimension" -> Component.translatable("cpmwheel.cond.t.dimension.v", c.value.isBlank() ? "?" : c.value).getString();
            case "emote" -> Component.translatable("cpmwheel.cond.t.emote.v", EmoteLogic.isAny(c.value) ? Component.translatable("cpmwheel.ec.any").getString() : c.value).getString();
            case "anim" -> Component.translatable("cpmwheel.cond.t.anim.v", c.value.isBlank() ? "?" : c.value).getString() + (c.num > 1 ? " \u2265" + c.num : "");
            case "hold" -> Component.translatable("cpmwheel.cond.t.hold.v", holdName(cfg, c.value), Component.translatable("cpmwheel.cond.hand." + Math.min(2, c.num))).getString();
            default -> Component.translatable("cpmwheel.cond.t." + c.type).getString();
        };
        return c.not ? Component.translatable("cpmwheel.cond.not.v", t).getString() : t;
    }
    static String holdName(WheelConfig cfg, String v) {
        if (v == null || v.isBlank()) return "?";
        if (v.startsWith("grp:") && cfg != null) { WheelConfig.ItemGroup g = cfg.group(v.substring(4)); return g == null ? v : g.label(); }
        return v;
    }
    /** all conditions in words joined with "e" / "ou" */
    public static String condsText(WheelConfig.EffectRule r) {
        WheelConfig cfg = WheelConfig.get();
        StringBuilder sb = new StringBuilder();
        String j = " " + Component.translatable("or".equals(r.combine) ? "cpmwheel.cond.or" : "cpmwheel.cond.and").getString() + " ";
        for (TriggerLogic.Cond c : r.conds) { if (sb.length() > 0) sb.append(j); sb.append(condText(cfg, c)); }
        return sb.length() == 0 ? Component.translatable("cpmwheel.cond.none").getString() : sb.toString();
    }

    // ---------- 1.6.0 timed transition ----------
    private static void beginTrans(WheelConfig cfg, WheelConfig.EffectRule r, RState s, String phase, long now) {
        s.phase = phase; s.phaseUntil = now + Math.max(100, r.transMs);
        s.transAnim = r.transAnim; s.transPrev = CpmBridge.playing(r.transAnim);
        CpmBridge.play(r.transAnim, onValue(cfg, r.transAnim, -1));
    }
    private static void finishTrans(RState s) {
        if (s.transAnim == null || s.transAnim.isBlank()) return;
        if (HOLDS.containsKey(s.transAnim)) { s.transAnim = ""; return; } // the rule itself holds it: leave it alone
        CpmBridge.play(s.transAnim, Math.max(0, s.transPrev));
        s.transAnim = "";
    }

    /** "Humano desativado, chifres ativado" */
    static String changes(WheelConfig.EffectRule r) {
        StringBuilder sb = new StringBuilder();
        for (WheelConfig.EffectAction a : r.actions) {
            if (sb.length() > 0) sb.append(", ");
            String t = a.target.startsWith("group:") ? a.target.substring(6) : targetName(a.target);
            sb.append(t).append(' ').append(Component.translatable("cpmwheel.toast.op." + a.op).getString());
        }
        return sb.length() == 0 ? Component.translatable("cpmwheel.eff.noactions").getString() : sb.toString();
    }
    /** colour of the first wheel button the rule touches (group or animation), else the wheel accent */
    static int color(WheelConfig cfg, WheelConfig.EffectRule r) {
        if (cfg != null) for (WheelConfig.EffectAction a : r.actions) {
            if (a.target.startsWith("group:")) { WheelConfig.Slot g = cfg.groupSlot(a.target.substring(6)); if (g != null && g.color != 0) return g.color; }
            else if (a.target.startsWith("anim:")) {
                String an = a.target.substring(5);
                for (WheelConfig.Slot s : cfg.allSlots()) if (an.equals(s.anim) && !s.isGroup() && s.color != 0) return s.color;
            }
        }
        try { return Style.accent(cfg); } catch (Throwable t) { return 0x2EE6FF; }
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
