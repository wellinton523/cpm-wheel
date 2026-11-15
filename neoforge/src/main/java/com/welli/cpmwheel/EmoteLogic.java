package com.welli.cpmwheel;

import java.util.Locale;

/**
 * 1.8.1 pure Emotecraft logic (no Minecraft / Emotecraft classes; checked offline by tools/EmoteLogicCheck.java):
 * emote name cleanup and matching, the "ec:" reference used by chained animations, start/stop edges and
 * what a wheel button does when clicked.
 */
public final class EmoteLogic {
    private EmoteLogic() {}

    /** chained animations / references: "ec:*" = any Emotecraft emote, "ec:Wave" = that emote */
    public static final String PREFIX = "ec:";
    public static final String ANY = "*";
    public static final String[] ACTIONS = {"toggle", "play", "stop"};
    public static final String[] L2D_MODES = {"none", "off", "kick"};

    public static boolean isRef(String s) { return s != null && s.startsWith(PREFIX); }
    public static String refName(String s) { return isRef(s) ? s.substring(PREFIX.length()).trim() : ""; }
    public static String ref(String name) { return PREFIX + (name == null || name.isBlank() ? ANY : name.trim()); }
    public static boolean isAny(String want) { return want == null || want.isBlank() || ANY.equals(want.trim()); }

    /**
     * Emotecraft names come as plain text, as a JSON string ("\"Wave\"") or as a JSON text object ({"text":"Wave"}).
     * Returns the readable name ("" if none).
     */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("{")) {
            String t = jsonField(s, "text");
            if (t.isEmpty()) t = jsonField(s, "translate");
            return t;
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s.trim();
    }
    private static String jsonField(String s, String key) {
        int k = s.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int c = s.indexOf(':', k);
        int q1 = c < 0 ? -1 : s.indexOf('"', c + 1);
        if (q1 < 0) return "";
        StringBuilder b = new StringBuilder();
        for (int i = q1 + 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) { b.append(s.charAt(++i)); continue; }
            if (ch == '"') break;
            b.append(ch);
        }
        return b.toString().trim();
    }

    /** comparable form: cleaned, lower case, single spaces, no § colour codes */
    public static String norm(String s) {
        return clean(s).replaceAll("\u00A7.", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** "any" (blank / "*") matches every playing emote; a name matches the playing emote of that name (case and spaces ignored) */
    public static boolean matches(String want, boolean playing, String playingName) {
        if (!playing) return false;
        if (isAny(want)) return true;
        String w = norm(want);
        return !w.isEmpty() && w.equals(norm(playingName));
    }

    /** start/stop edges of "an emote is playing" (also restarts: a different emote starting counts as a new start) */
    public static final class Edge {
        public boolean playing;
        public String name = "";
        /** +1 = started (or switched to another emote), -1 = stopped, 0 = nothing */
        public int step(boolean now, String nowName) {
            String n = now ? norm(nowName) : "";
            int r = 0;
            if (now && !playing) r = 1;
            else if (!now && playing) r = -1;
            else if (now && !n.equals(name)) r = 1;
            playing = now; name = n;
            return r;
        }
    }

    /** a wheel button click: "play", "stop" or "none" */
    public static String click(String action, String slotName, boolean playing, String playingName) {
        String a = action == null ? "toggle" : action;
        switch (a) {
            case "stop": return playing ? "stop" : "none";
            case "play": return slotName == null || slotName.isBlank() ? "none" : "play";
            default:
                if (slotName == null || slotName.isBlank()) return playing ? "stop" : "none";
                return playing && norm(slotName).equals(norm(playingName)) ? "stop" : "play";
        }
    }

    public static String action(String a) { for (String s : ACTIONS) if (s.equals(a)) return s; return "toggle"; }
    public static String l2dMode(String a) { for (String s : L2D_MODES) if (s.equals(a)) return s; return "none"; }

    // ---- 1.8.2 emote icon and details ----
    /** PNG signature check (so random bytes never reach the image decoder) */
    public static boolean isPng(byte[] b) {
        return b != null && b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }
    /** Emotecraft keeps the icon as a ByteBuffer ("iconData"); never moves the original buffer's position. byte[] accepted too. */
    public static byte[] bytes(Object o) {
        if (o instanceof byte[] b) return b.clone();
        if (o instanceof java.nio.ByteBuffer bb) {
            java.nio.ByteBuffer d = bb.duplicate();
            d.rewind();
            byte[] out = new byte[d.remaining()];
            d.get(out);
            return out;
        }
        return null;
    }
    /** ticks -> "2,5 s" (20 ticks = 1 s); negative / zero -> "" */
    public static String seconds(int ticks) {
        if (ticks <= 0) return "";
        float s = ticks / 20f;
        return (s == Math.round(s) ? Integer.toString(Math.round(s)) : String.format(Locale.ROOT, "%.1f", s).replace('.', ',')) + " s";
    }
    /** emote length in ticks: stopTick when set (the real end), else endTick */
    public static int length(int endTick, int stopTick) { return stopTick > 0 ? stopTick : Math.max(0, endTick); }
    /** loop span (from returnToTick back to endTick) when looping, else 0 */
    public static int loopSpan(boolean infinite, int returnTick, int endTick) { return infinite && endTick > returnTick && returnTick >= 0 ? endTick - returnTick : 0; }
    /** "JSON_EMOTECRAFT" -> "json emotecraft" style label key part */
    public static String formatKey(String f) { return f == null || f.isBlank() ? "unknown" : f.trim().toLowerCase(Locale.ROOT); }
    /** wheel button icon id: "ec:<uuid>" (Icons draws the emote icon or the generic one) */
    public static String iconId(String uuid) { return uuid == null || uuid.isBlank() ? "" : PREFIX + uuid.trim(); }
}
