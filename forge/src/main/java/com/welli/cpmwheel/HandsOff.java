package com.welli.cpmwheel;

import java.util.Map;

/**
 * 1.9.1 "Animações da mão por mod": turn CPM Wheel's first-person hand animations off while holding an item of a given mod
 * (namespace) or a given item. Pure logic (checked by tools/HandsOffCheck.java).
 * Config map haHandsOff: "mod:<namespace>" or "<namespace>:<item>" -> mask of hands with the animations OFF
 * (1 = main hand, 2 = off hand, 3 = both). An item entry wins over its mod; an item entry of 0 forces them back ON.
 */
public final class HandsOff {
    private HandsOff() {}

    public static final int MAIN = 1, OFF = 2, BOTH = 3;
    /** state cycle in the screen: mod = on -> both off -> main off -> off-hand off; item adds "inherit" (-1) */
    public static final int[] MOD_CYCLE = {0, BOTH, MAIN, OFF};
    public static final int[] ITEM_CYCLE = {-1, 0, BOTH, MAIN, OFF};

    public static String modKey(String ns) { return "mod:" + ns; }

    /** mask for an item: its own entry, else its mod's, else 0 (animations on) */
    public static int mask(Map<String, Integer> m, String itemId, String ns) {
        if (m == null || m.isEmpty()) return 0;
        if (itemId != null) { Integer v = m.get(itemId); if (v != null) return v & BOTH; }
        if (ns != null) { Integer v = m.get(modKey(ns)); if (v != null) return v & BOTH; }
        return 0;
    }
    public static boolean off(int mask, boolean mainHand) { return (mask & (mainHand ? MAIN : OFF)) != 0; }

    /** current state for a key: mod -> 0..3, item -> -1 (inherit) or 0..3 */
    public static int state(Map<String, Integer> m, String key) { Integer v = m == null ? null : m.get(key); return v == null ? (key.startsWith("mod:") ? 0 : -1) : v & BOTH; }
    /** next state in the screen cycle */
    public static int next(int cur, boolean isMod) {
        int[] c = isMod ? MOD_CYCLE : ITEM_CYCLE;
        for (int i = 0; i < c.length; i++) if (c[i] == cur) return c[(i + 1) % c.length];
        return c[0];
    }
    /** writes a state (mod 0 / item -1 remove the entry) */
    public static void set(Map<String, Integer> m, String key, int st) {
        boolean isMod = key.startsWith("mod:");
        if (st < 0 || (isMod && st == 0)) m.remove(key); else m.put(key, st & BOTH);
    }
    /** command word -> state ("on", "off", "principal" = only the main hand off, "secundaria" = only the off hand off, "herdar" = item inherits); null = unknown */
    public static Integer parse(String w) {
        if (w == null) return null;
        switch (w.toLowerCase(java.util.Locale.ROOT)) {
            case "on": case "ligar": case "ligado": return 0;
            case "off": case "desligar": case "desligado": return BOTH;
            case "principal": case "main": return MAIN;
            case "secundaria": case "secundária": case "offhand": return OFF;
            case "herdar": case "inherit": return -1;
            default: return null;
        }
    }
    /** state -> lang suffix */
    public static String word(int st) { return switch (st) { case -1 -> "inherit"; case 0 -> "on"; case MAIN -> "main"; case OFF -> "offhand"; default -> "off"; }; }
    /** a valid key: "mod:<ns>" or "<ns>:<path>" with resource-location characters */
    public static boolean validKey(String k) {
        if (k == null) return false;
        if (k.startsWith("mod:")) return k.length() > 4 && k.substring(4).matches("[a-z0-9_.-]+");
        return k.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}
