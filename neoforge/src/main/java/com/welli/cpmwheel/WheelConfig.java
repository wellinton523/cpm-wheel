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
        public Slot() {}
        public Slot(String a) { anim = a; }
        public String display() { return label != null && !label.isBlank() ? label : (RESET.equals(anim) ? "Reset" : anim); }
        public boolean empty() { return anim == null || anim.isBlank(); }
    }

    public static class Page {
        public String name = "Page";
        public List<Slot> slots = new ArrayList<>();
        public Page() {}
        public Page(String n, int size) { name = n; for (int i = 0; i < size; i++) slots.add(new Slot()); }
    }

    public boolean holdToOpen = true;
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
                if (c != null && c.pages != null && !c.pages.isEmpty()) return c;
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
            for (Page p : pages) for (Slot s : p.slots) if (s.favorite && !s.empty()) fav.slots.add(s);
            if (!fav.slots.isEmpty()) l.add(fav);
        }
        l.addAll(pages);
        return l;
    }

    public static void run(Slot s) {
        if (s == null || s.empty()) return;
        if (RESET.equals(s.anim)) CpmBridge.resetAll();
        else CpmBridge.play(s.anim, s.value);
    }
}
