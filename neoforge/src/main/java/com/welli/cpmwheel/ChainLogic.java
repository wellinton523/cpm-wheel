package com.welli.cpmwheel;

/**
 * 1.8.0 "Encadear animações": when an animation ends (or starts), play / stop another one after a delay.
 * Pure logic (no Minecraft classes; checked offline by tools/ChainLogicCheck.java).
 */
public final class ChainLogic {
    private ChainLogic() {}

    /** minimum time between two fires of the same rule (stops instant A -> B -> A loops) */
    public static final long MIN_GAP = 250;

    public static class Rule {
        public String id = "";
        public boolean on = true;
        /** animation watched */
        public String from = "";
        /** "end" = when it stops, "start" = when it starts */
        public String when = "end";
        public int delayMs = 0;
        /** animation changed */
        public String to = "";
        /** "on" (ATIVAR), "off" (DESATIVAR), "toggle" (ALTERNAR) */
        public String op = "on";
        /** value for "on" (-1 = button value / max) */
        public int value = -1;
    }

    public static final class State { public int prev = Integer.MIN_VALUE; public long fireAt = -1, lastFire = Long.MIN_VALUE / 2; }

    /** feed the watched animation's current value (≤ 0 or -1 = not playing); returns true when the rule fires now */
    public static boolean step(Rule r, State s, int playing, long now) {
        if (r == null || !r.on || r.from == null || r.from.isBlank() || r.to == null || r.to.isBlank()) { s.prev = playing; s.fireAt = -1; return false; }
        if (s.prev == Integer.MIN_VALUE) { s.prev = playing; return false; } // first look: no edge
        boolean was = s.prev > 0, is = playing > 0;
        s.prev = playing;
        boolean edge = "start".equals(r.when) ? !was && is : was && !is;
        if (edge) s.fireAt = now + Math.max(0, r.delayMs);
        if (s.fireAt >= 0 && now >= s.fireAt) {
            s.fireAt = -1;
            if (now - s.lastFire < MIN_GAP) return false;
            s.lastFire = now;
            return true;
        }
        return false;
    }

    /** value to write for the target: off -> 0, on -> onValue, toggle -> off if playing else onValue */
    public static int target(String op, int current, int onValue) {
        if ("off".equals(op)) return 0;
        if ("toggle".equals(op)) return current > 0 ? 0 : onValue;
        return onValue;
    }
}
