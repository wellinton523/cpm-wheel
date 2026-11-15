package com.welli.cpmwheel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 1.6.0 pure trigger logic (no Minecraft classes; checked offline by tools/TriggerLogicCheck.java):
 * condition evaluation (AND / OR, NOT per condition) on a snapshot of the player, and the start/end debounce.
 */
public final class TriggerLogic {
    private TriggerLogic() {}

    public static final String[] TYPES = {"effect", "health", "damage", "night", "day", "rain", "thunder", "water", "biome", "dimension", "sneak", "sprint", "hold"};

    /** one condition (also the stored config object) */
    public static class Cond {
        public String type = "effect";
        /** effect id, biome id or #tag, dimension id, item query / "grp:<id>" */
        public String value = "";
        /** effect: min amplifier; health: max HP (half hearts); damage: seconds; hold: 0 any hand, 1 main, 2 off */
        public int num = 0;
        public boolean not = false;
        public Cond() {}
        public Cond(String t, String v, int n) { type = t; value = v; num = n; }
    }

    /** what the game looks like right now (filled by EffectTriggers each tick) */
    public static class Snapshot {
        public Map<String, Integer> effects = new HashMap<>(); // id -> max amplifier
        public float health = 20;
        public long msSinceHurt = Long.MAX_VALUE;
        public long dayTime = 1000;          // level day time
        public boolean fixedTime = false;    // nether / end: no day or night
        public boolean raining, thundering, underwater, sneaking, sprinting;
        public String dimension = "minecraft:overworld";
        /** biome id ("minecraft:desert") or tag ("#minecraft:is_ocean") */
        public Predicate<String> biome = v -> false;
        /** (item query or "grp:<id>", hand 0 any / 1 main / 2 off) */
        public BiPredicate<String, Integer> hold = (v, h) -> false;
    }

    public static boolean night(Snapshot s) {
        if (s.fixedTime) return false;
        long t = Math.floorMod(s.dayTime, 24000L);
        return t >= 13000 && t < 23000;
    }

    public static boolean test(Cond c, Snapshot s) {
        boolean r;
        switch (c.type == null ? "" : c.type) {
            case "effect" -> { Integer a = s.effects.get(c.value); r = a != null && a >= c.num; }
            case "health" -> r = s.health <= Math.max(1, c.num);
            case "damage" -> r = s.msSinceHurt <= Math.max(1, c.num) * 1000L;
            case "night" -> r = night(s);
            case "day" -> r = !s.fixedTime && !night(s);
            case "rain" -> r = s.raining;
            case "thunder" -> r = s.thundering;
            case "water" -> r = s.underwater;
            case "biome" -> r = c.value != null && !c.value.isBlank() && s.biome.test(c.value.trim());
            case "dimension" -> r = dimMatches(c.value, s.dimension);
            case "sneak" -> r = s.sneaking;
            case "sprint" -> r = s.sprinting;
            case "hold" -> r = c.value != null && !c.value.isBlank() && s.hold.test(c.value.trim(), c.num);
            default -> r = false;
        }
        return c.not != r;
    }

    /** "the_nether" matches "minecraft:the_nether"; a full id must match exactly */
    public static boolean dimMatches(String v, String dim) {
        if (v == null || v.isBlank() || dim == null) return false;
        v = v.trim().toLowerCase(java.util.Locale.ROOT);
        return v.contains(":") ? v.equals(dim) : dim.endsWith(":" + v) || dim.equals(v);
    }

    /** all ("and") or any ("or") of the conditions; no conditions = never */
    public static boolean eval(List<Cond> conds, String combine, Snapshot s) {
        if (conds == null || conds.isEmpty()) return false;
        boolean or = "or".equals(combine);
        for (Cond c : conds) {
            boolean v = test(c, s);
            if (or && v) return true;
            if (!or && !v) return false;
        }
        return !or;
    }

    /**
     * Debounce: the rule starts after the condition held true for delayStart ms and ends after it was false for delayEnd ms.
     * A condition that flips back before the delay resets the timer, so quick toggles never flicker the animation.
     */
    public static final class Debounce {
        public boolean cond, active;
        public long since;
        /** +1 = start now, -1 = end now, 0 = nothing */
        public int update(boolean want, long now, long delayStart, long delayEnd) {
            if (want != cond) { cond = want; since = now; }
            if (want && !active && now - since >= delayStart) { active = true; return 1; }
            if (!want && active && now - since >= delayEnd) { active = false; return -1; }
            return 0;
        }
    }
}
