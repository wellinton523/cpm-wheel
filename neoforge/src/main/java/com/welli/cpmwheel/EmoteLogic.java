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
}
