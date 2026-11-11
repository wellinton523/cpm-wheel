package com.welli.cpmwheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class WheelConfig {
    public static final String RESET = "@reset";
    public static final int[] PALETTE = {0x2EE6FF, 0x4D7CFF, 0xFF3B5C, 0xB46CFF, 0x3BFF9D, 0xFFD23B, 0xFFFFFF, 0x8A97A8};

    public static class Slot {
        public String anim = "";
        public String label = "";
        public int color = 0x2EE6FF;
        public String icon = "";
        public int value = -1;
        public boolean favorite = false;
        /** non-null = this slot is a sub-wheel (folder) */
        public List<Slot> children = null;
        /** "" auto, "toggle", "slider", "presets" */
        public String mode = "";
        public int min = 0, max = -1, step = -1;
        public String presets = "0,25,50,75,100";
        /** frames mode: one entry per frame */
        public List<Frame> frames = null;
        /** timed: seconds until the animation is switched back (0 = stays) */
        public int duration = 0;
        /** how this button is drawn: "" = follow global icons-only, "icon", "text", "both" */
        public String displayMode = "";
        /** group slot ("Grupo"): members with a target action each. Old 1.4.5 field, migrated to groupMembers. */
        public List<String> members = null;
        public List<Member> groupMembers = null;
        public boolean isGroup() { return "group".equals(mode); }
        /** runtime: group applied + values before applying (restored on the second click) */
        public transient boolean groupOn = false;
        public transient Map<String, Integer> groupPrev = new HashMap<>();
        /** valid members, never null */
        public List<Member> groupList() {
            List<Member> l = new ArrayList<>();
            if (groupMembers != null) for (Member m : groupMembers) if (m != null && m.anim != null && !m.anim.isBlank()) l.add(m);
            return l;
        }
        public Member member(String a) { for (Member m : groupList()) if (m.anim.equals(a)) return m; return null; }
        /** 0 = off, 1 = mixed, 2 = all targets currently match (toggle members follow the applied flag) */
        public int groupState() {
            int match = 0, known = 0;
            for (Member m : groupList()) {
                if ("toggle".equals(m.action)) continue;
                int v = CpmBridge.playing(m.anim);
                if (v < 0) continue;
                known++;
                if (m.matches(v)) match++;
            }
            if (known == 0) return groupOn ? 2 : 0;
            return match == known ? 2 : match == 0 ? 0 : 1;
        }

        /** free keybind: GLFW key code (-1 = none) + GLFW modifier bits (1 shift, 2 ctrl, 4 alt) */
        public int key = -1;
        public int keyMods = 0;

        /** conditional slot ("Condicional"): first rule matching the held item plays its animation */
        public List<Rule> rules = null;
        public String fallback = "";
        public boolean condAuto = false;
        public boolean isCond() { return "cond".equals(mode); }
        public List<Rule> ruleList() {
            List<Rule> l = new ArrayList<>();
            if (rules != null) for (Rule r : rules) if (r != null) l.add(r);
            return l;
        }
        /** runtime: what the last evaluation matched (for HUD) and the animation auto-applied */
        public transient String lastMatch = null;
        public transient String autoApplied = null;

        public static final String[] DISPLAY_MODES = {"", "icon", "text", "both"};
        /** resolved: true = draw icon/glyph only */
        public boolean iconOnly(WheelConfig c) { String m = displayMode == null ? "" : displayMode; return m.equals("icon") || (m.isEmpty() && c.iconOnly); }
        /** resolved: true = draw icon above text (only when not icon-only) */
        public boolean showIcon(WheelConfig c) { return !"text".equals(displayMode); }

        public void ensureFrames() {
            if (frames == null) frames = new ArrayList<>();
            while (frames.size() < 2) frames.add(new Frame());
        }
        public int frameValue(int i) {
            ensureFrames();
            int n = frames.size();
            if (i < 0) i = 0; if (i >= n) i = n - 1;
            Frame f = frames.get(i);
            if (f.value >= 0) return f.value;
            return Math.round(minV() + (maxV() - minV()) * (i / (float) (n - 1)));
        }
        /** index of the frame whose value is closest to v (exact match wins) */
        public int frameIndexOf(int v) {
            ensureFrames();
            int best = 0, bd = Integer.MAX_VALUE;
            for (int i = 0; i < frames.size(); i++) { int d = Math.abs(frameValue(i) - v); if (d < bd) { bd = d; best = i; } }
            return best;
        }
        public String frameName(int i) {
            ensureFrames();
            if (i < 0 || i >= frames.size()) return "?";
            String n = frames.get(i).name;
            return n == null || n.isBlank() ? "#" + (i + 1) : n;
        }

        public boolean isFolder() { return children != null; }
        public String effectiveMode() {
            if (mode != null && !mode.isBlank()) return mode;
            return CpmBridge.maxValue(anim) > 1 ? "slider" : "toggle";
        }
        public int minV() { return Math.max(0, min); }
        public int maxV() { int m = max > 0 ? max : CpmBridge.maxValue(anim); return Math.max(minV() + 1, m > 1 ? m : 255); }
        public int stepV() { return step > 0 ? step : Math.max(1, (maxV() - minV()) / 20); }
        public int[] presetValues() {
            List<Integer> l = new ArrayList<>();
            for (String p : (presets == null ? "" : presets).split("[,; ]+")) {
                try { float f = Float.parseFloat(p.trim().replace("%", "")); l.add(Math.round(minV() + (maxV() - minV()) * Math.max(0, Math.min(100, f)) / 100f)); } catch (Exception ignored) {}
            }
            if (l.isEmpty()) { l.add(minV()); l.add(maxV()); }
            Collections.sort(l);
            return l.stream().mapToInt(Integer::intValue).toArray();
        }
        public Slot() {}
        public Slot(String a) { anim = a; }
        public String display() { return label != null && !label.isBlank() ? label : (isFolder() ? "..." : isGroup() ? "Grupo" : isCond() ? "Condicional" : RESET.equals(anim) ? "Reset" : anim); }
        public boolean empty() { return !isFolder() && !isGroup() && !isCond() && (anim == null || anim.isBlank()); }
    }

    /** group member: anim + action "on" | "off" | "toggle" | "value" (value or named frame) */
    public static class Member {
        public String anim = "";
        public String action = "on";
        public int value = -1;
        public String frame = "";
        public Member() {}
        public Member(String a) { anim = a; }
        /** target value for on/off/value, -1 for toggle */
        public int target() {
            switch (action == null ? "on" : action) {
                case "off": return 0;
                case "toggle": return -1;
                case "value": {
                    if (frame != null && !frame.isBlank() && current != null)
                        for (Slot s : current.allSlots())
                            if (anim.equals(s.anim) && s.frames != null)
                                for (int i = 0; i < s.frames.size(); i++) if (frame.equals(s.frameName(i))) return s.frameValue(i);
                    return Math.max(0, value);
                }
                default: return 1;
            }
        }
        boolean matches(int v) { int t = target(); return "on".equals(action) || action == null ? v > 0 : v == t; }
    }

    /** conditional rule; empty fields are ignored ("any") */
    public static class Rule {
        public String hand = "main";     // main | off | any
        public String item = "";         // minecraft:diamond_sword
        public String tag = "";          // #minecraft:swords
        public String category = "";     // sword axe bow crossbow trident shield tool block empty
        public String name = "";         // custom name contains
        public double dmgMin = -1, dmgMax = -1;
        public String anim = "";
        public int value = -1;
    }

    public static class Frame {
        public String name = "";
        /** exact CPM value, -1 = auto (evenly spread min..max) */
        public int value = -1;
        /** swatch color, 0 = none */
        public int color = 0;
    }

    public static class Page {
        public String name = "Page";
        /** HUD group bar / tab colour */
        public int color = 0x2EE6FF;
        public List<Slot> slots = new ArrayList<>();
        public Page() {}
        public Page(String n, int size) { name = n; for (int i = 0; i < size; i++) slots.add(new Slot()); }
    }

    public boolean holdToOpen = true;
    public boolean animations = true;
    public boolean livePreview = true;
    /** Hover preview really plays the animation on your model (CPM has no local-only preview). Off by default. */
    public boolean previewOnHover = false;
    /** center of the wheel: "player", "icon" or "none" */
    public String centerMode = "player";
    /** center size in percent (50-200) */
    public int centerSize = 100;
    public int configVersion = 12;
    private static final String LEGACY_DESIGN = "signalis";
    /** HUD grouped by wheel/folder with nested bars; false = flat list */
    public boolean hudGrouped = true;
    /** show group headers even when nothing in them is active */
    public boolean hudShowEmptyGroups = false;
    /** 1.4.0 look: design + accent theme */
    public String design = "classic";
    public String theme = "auto";
    /** icon-only segments (no text) and their size in percent */
    public boolean iconOnly = false;
    public int iconSize = 100;
    /** icon-only center (no text) and its icon size in percent */
    public boolean centerIconOnly = false;
    public int centerIconSize = 100;
    /** inner ring with favorites + recently used */
    public boolean innerRing = false;
    public List<String> recents = new ArrayList<>();
    public boolean sounds = true;
    public int soundVolume = 50;
    /** Alt+1..9 plays slot 1..9 of the last page without opening the wheel */
    public boolean altHotkeys = true;
    /** first-person hand follows CPM animations: "off", "layers" (layers/values), "all" (also gestures) */
    public String fpAnimations = "layers";
    /** glow parts on the 1st-person hand with shaders: "off", "emissive", "auto" (only with an Iris shader pack) */
    public String handGlow = "auto";
    /** 1.5.0 first-person hand animations: master toggle, disabled feature ids, intensities in percent (0-200) */
    public boolean handAnim = true;
    public List<String> handAnimOff = new ArrayList<>();
    /** 1.5.2: other mods whose compat rules are ignored (HandAnim.MODS keys) */
    public List<String> haIgnore = new ArrayList<>();
    /** 1.5.3: item in the fist: per-category user offsets {x,y,z,scale,rx,ry,rz}, vanilla item position fallback, blocks with 1 or 2 hands */
    public java.util.Map<String, int[]> haAdj = new java.util.HashMap<>();
    public boolean haVanillaItem = false;
    public boolean haTwoHandBlock = false;
    public int haIntensity = 100, haIdle = 100, haBob = 100, haInertia = 100, haPose = 100, haSwing = 100;
    public boolean frameSegments = true;
    public boolean hudEnabled = true;
    public String hudCorner = "top_left";
    public int hudScale = 100;
    public int hudOpacity = 60;
    public boolean hudOnlyNonDefault = true;
    public boolean showFavoritesPage = true;
    public int lastPage = 0;
    public List<Page> pages = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WheelConfig current;
    private static String currentKey;

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("cpmwheel"); }

    private static Path file(String key) {
        return dir().resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    public static WheelConfig get() {
        String key = CpmBridge.modelKey();
        if (current == null || !key.equals(currentKey)) { current = load(key); currentKey = key; }
        return current;
    }

    private static WheelConfig load(String key) {
        try {
            Path f = file(key);
            if (!Files.exists(f) && !key.equals("default")) f = file("default");
            if (Files.exists(f)) {
                WheelConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), WheelConfig.class);
                if (c != null && c.pages != null && !c.pages.isEmpty()) {
                    if (c.configVersion < 2) { // 1.2.0 had hover preview on by default and it changed the real model
                        c.previewOnHover = false;
                        if (!c.livePreview) c.centerMode = "icon";
                        c.configVersion = 2;
                    }
                    if (c.configVersion < 3) { c.design = "classic"; c.theme = "auto"; c.configVersion = 3; }
                    if (c.configVersion < 4) { // 1.4.1: page colours + distinct folder colours for the grouped HUD
                        c.hudGrouped = true;
                        for (Page pg : c.pages) if (pg.color == 0) pg.color = 0x2EE6FF;
                        int n = 0;
                        for (Slot sl : c.allSlots()) if (sl.isFolder() && sl.color == 0x2EE6FF) sl.color = folderColor(n++);
                        c.configVersion = 4;
                    }
                    if (c.configVersion < 7) c.innerRing = false; // 1.4.4: center ring off once on upgrade
                    if (c.configVersion < 12) { c.configVersion = 12; c.haTwoHandBlock = false; } // 1.5.3: blocks held with one hand by default
                    if (c.configVersion < 11) c.configVersion = 11; // 1.5.0: hand animations (new fields, defaults on, set in fixup)
                    if (c.configVersion < 10) c.configVersion = 10; // 1.4.7: handGlow (default auto, set in fixup) // 1.4.6: group members -> actions (fixup), keybinds, conditional slots // 1.4.5: group slots (new fields, default none)
                    if (c.configVersion < 6) c.configVersion = 6; // 1.4.2: design id rename + per-slot displayMode (defaults to "") handled in fixup()
                    c.fixup();
                    if (c.centerMode == null || c.centerMode.isBlank()) c.centerMode = "player";
                    if (c.hudCorner == null || c.hudCorner.isBlank()) c.hudCorner = "top_left";
                    if (c.hudScale <= 0) c.hudScale = 100;
                    c.centerSize = Math.max(50, Math.min(200, c.centerSize <= 0 ? 100 : c.centerSize));
                    return c;
                }
            }
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] config load failed", e); }
        return autoFill();
    }

    /** auto, distinct folder colours (never the wheel cyan) */
    public static int folderColor(int n) {
        int[] f = {0xFFD23B, 0xB46CFF, 0x3BFF9D, 0xFF3B5C, 0x4D7CFF, 0xFF8FD0, 0xFFFFFF};
        return f[Math.floorMod(n, f.length)];
    }
    public int nextFolderColor() { int n = 0; for (Slot s : allSlots()) if (s.isFolder()) n++; return folderColor(n); }

    /** parse "#RRGGBB" / "RRGGBB", -1 if invalid */
    public static int parseHex(String v) {
        if (v == null) return -1;
        v = v.trim(); if (v.startsWith("#")) v = v.substring(1);
        if (!v.matches("[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(v, 16);
    }

    void fixup() {
        if (LEGACY_DESIGN.equals(design)) design = "terminal"; // 1.4.0/1.4.1 id of the Terminal design
        for (Page pg : pages) { if (pg.slots == null) pg.slots = new ArrayList<>(); if (pg.color == 0) pg.color = 0x2EE6FF; }
        if (Style.idx(Style.DESIGNS, design) < 0) design = "classic";
        if (Style.idx(Style.THEMES, theme) < 0) theme = "auto";
        if (!java.util.Arrays.asList(FpState.MODES).contains(fpAnimations)) fpAnimations = "layers";
        if (!java.util.Arrays.asList(HandGlow.MODES).contains(handGlow)) handGlow = "auto";
        if (handAnimOff == null) handAnimOff = new ArrayList<>();
        if (haIgnore == null) haIgnore = new ArrayList<>();
        if (haAdj == null) haAdj = new java.util.HashMap<>();
        haAdj.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue().length != 7 || java.util.Arrays.asList(HandAnim.CATS).indexOf(e.getKey()) < 0);
        handAnimOff.removeIf(f -> f == null || HandAnim.fi(f) < 0);
        haIntensity = clamp(haIntensity, 0, 200); haIdle = clamp(haIdle, 0, 200); haBob = clamp(haBob, 0, 200);
        haInertia = clamp(haInertia, 0, 200); haPose = clamp(haPose, 0, 200); haSwing = clamp(haSwing, 0, 200);
        iconSize = clamp(iconSize <= 0 ? 100 : iconSize, 50, 250);
        centerIconSize = clamp(centerIconSize <= 0 ? 100 : centerIconSize, 50, 250);
        soundVolume = clamp(soundVolume, 0, 100);
        if (recents == null) recents = new ArrayList<>();
        recents.removeIf(r -> r == null || r.isBlank());
        while (recents.size() > 8) recents.remove(recents.size() - 1);
        for (Slot sl : allSlots()) {
            if (sl.duration < 0) sl.duration = 0;
            if (sl.members != null) { // 1.4.5 groups: every member "Ligar"
                if (sl.groupMembers == null) sl.groupMembers = new ArrayList<>();
                for (String a : sl.members) if (a != null && !a.isBlank() && sl.member(a) == null) sl.groupMembers.add(new Member(a));
                sl.members = null;
            }
            if (sl.groupMembers != null) sl.groupMembers.removeIf(m -> m == null || m.anim == null || m.anim.isBlank());
            if (sl.groupPrev == null) sl.groupPrev = new HashMap<>();
            if (sl.rules != null) sl.rules.removeIf(java.util.Objects::isNull);
            if (sl.fallback == null) sl.fallback = "";
            if (sl.key < -1) sl.key = -1;
            sl.keyMods &= 7;
            if (sl.displayMode == null || !Arrays.asList(Slot.DISPLAY_MODES).contains(sl.displayMode)) sl.displayMode = "";
        }
    }
    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    public String profileKey() { return currentKey == null ? "default" : currentKey; }

    public void addRecent(String anim) {
        if (anim == null || anim.isBlank() || RESET.equals(anim)) return;
        recents.remove(anim); recents.add(0, anim);
        while (recents.size() > 8) recents.remove(recents.size() - 1);
    }

    /** favorites first, then recents (as slots), at most 8, computed on demand (not per frame) */
    public List<Slot> ringSlots() {
        List<Slot> out = new ArrayList<>();
        List<Slot> all = allSlots();
        for (Slot s : all) if (s.favorite && !s.empty() && !s.isFolder() && out.size() < 8 && !out.contains(s)) out.add(s);
        for (String r : recents) {
            if (out.size() >= 8) break;
            Slot found = null;
            for (Slot s : all) if (r.equals(s.anim) && !s.isFolder()) { found = s; break; }
            if (found == null) found = new Slot(r);
            boolean dup = false; for (Slot o : out) if (r.equals(o.anim)) dup = true;
            if (!dup) out.add(found);
        }
        return out;
    }

    // ---------- layout presets (config/cpmwheel/presets/*.json) ----------
    private static Path presetDir() { return dir().resolve("presets"); }
    public static List<String> presetNames() {
        List<String> l = new ArrayList<>();
        try (var st = Files.list(presetDir())) {
            st.forEach(f -> { String n = f.getFileName().toString(); if (n.endsWith(".json")) l.add(n.substring(0, n.length() - 5)); });
        } catch (Exception ignored) {}
        Collections.sort(l);
        return l;
    }
    public boolean savePreset(String name) {
        try {
            name = name.replaceAll("[^A-Za-z0-9 ._-]", "_").trim();
            if (name.isEmpty()) return false;
            Files.createDirectories(presetDir());
            Files.writeString(presetDir().resolve(name + ".json"), GSON.toJson(pages.toArray(new Page[0])), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] preset save failed", e); return false; }
    }
    public boolean loadPreset(String name) {
        try {
            Page[] p = GSON.fromJson(Files.readString(presetDir().resolve(name + ".json"), StandardCharsets.UTF_8), Page[].class);
            if (p == null || p.length == 0) return false;
            pages = new ArrayList<>(Arrays.asList(p));
            for (Page pg : pages) if (pg.slots == null) pg.slots = new ArrayList<>();
            lastPage = 0; fixup(); save();
            return true;
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] preset load failed", e); return false; }
    }
    /** copy pages from the default profile into this model's profile */
    public boolean copyFromDefault() {
        try {
            Path f = file("default");
            if (!Files.exists(f) || "default".equals(profileKey())) return false;
            WheelConfig d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), WheelConfig.class);
            if (d == null || d.pages == null || d.pages.isEmpty()) return false;
            pages = d.pages; lastPage = 0; fixup(); save();
            return true;
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] copy failed", e); return false; }
    }

    /** First run: put every CPM animation on pages of 8, plus a reset slot. */
    public static WheelConfig autoFill() {
        WheelConfig c = new WheelConfig();
        List<String> names = new ArrayList<>(CpmBridge.listAnimations().keySet());
        names.add(0, RESET);
        int i = 0;
        do {
            Page p = new Page("Page " + (c.pages.size() + 1), 8);
            for (int s = 0; s < 8 && i < names.size(); s++, i++) {
                p.slots.get(s).anim = names.get(i);
                p.slots.get(s).color = RESET.equals(names.get(i)) ? 0xFF3B5C : PALETTE[(i) % 6];
            }
            c.pages.add(p);
        } while (i < names.size());
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Path f = file(currentKey == null ? "default" : currentKey);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] config save failed", e); }
    }

    /** Pages as shown in the wheel (virtual favorites page first). */
    public List<Page> viewPages() {
        List<Page> l = new ArrayList<>();
        if (showFavoritesPage) {
            Page fav = new Page("\u2605", 0);
            for (Slot s : allSlots()) if (s.favorite && !s.empty()) fav.slots.add(s);
            if (!fav.slots.isEmpty()) l.add(fav);
        }
        l.addAll(pages);
        return l;
    }

    /** every slot, including inside sub-wheels */
    public List<Slot> allSlots() {
        List<Slot> out = new ArrayList<>();
        for (Page p : pages) walk(p.slots, out);
        return out;
    }
    private static void walk(List<Slot> l, List<Slot> out) {
        if (l == null) return;
        for (Slot s : l) {
            if (s == null || out.contains(s)) continue; // null-safe, never loops on a folder seen twice
            out.add(s);
            if (s.children != null) walk(s.children, out);
        }
    }

    /** all on -> all off, otherwise (off or mixed) -> all on. Missing animations are skipped. */
    public static void toggleGroup(Slot s) {
        List<Member> l = s.groupList();
        if (l.isEmpty()) return;
        if (s.groupPrev == null) s.groupPrev = new HashMap<>();
        java.util.Set<String> known = CpmBridge.listAnimations().keySet();
        boolean revert = s.groupState() == 2;
        if (!revert) s.groupPrev.clear();
        for (Member m : l) {
            if (!known.isEmpty() && !known.contains(m.anim)) continue; // missing in this model
            int cur = CpmBridge.playing(m.anim);
            if (revert) {
                Integer prev = s.groupPrev.get(m.anim);
                if (prev != null) CpmBridge.play(m.anim, prev);
                else if ("toggle".equals(m.action)) CpmBridge.play(m.anim, cur > 0 ? 0 : 1);
                else CpmBridge.play(m.anim, "off".equals(m.action) ? 1 : 0);
            } else {
                s.groupPrev.put(m.anim, Math.max(0, cur));
                CpmBridge.play(m.anim, "toggle".equals(m.action) ? (cur > 0 ? 0 : 1) : m.target());
            }
        }
        s.groupOn = !revert;
        if (revert) s.groupPrev.clear();
        Hud.invalidate();
    }

    public static void run(Slot s) {
        if (s == null || s.empty() || s.isFolder()) return;
        if (s.isGroup()) { toggleGroup(s); return; }
        if (s.isCond()) { Conditions.click(s); return; }
        if (RESET.equals(s.anim)) CpmBridge.resetAll();
        else CpmBridge.play(s.anim, s.value);
    }
}
