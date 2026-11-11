package com.welli.cpmwheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

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
        public String display() { return label != null && !label.isBlank() ? label : (isFolder() ? "..." : RESET.equals(anim) ? "Reset" : anim); }
        public boolean empty() { return !isFolder() && (anim == null || anim.isBlank()); }
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
    public int configVersion = 2;
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
        for (Slot s : l) { out.add(s); if (s.children != null) walk(s.children, out); }
    }

    public static void run(Slot s) {
        if (s == null || s.empty() || s.isFolder()) return;
        if (RESET.equals(s.anim)) CpmBridge.resetAll();
        else CpmBridge.play(s.anim, s.value);
    }
}
