package com.welli.cpmwheel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 1.5.7 item groups inside a mod ("Mochilas" = every *backpack of Sophisticated Backpacks...).
 * Matching is cached per item and rebuilt whenever the config is reloaded (every 2 s / on change),
 * so tags that only exist after joining a world are picked up too.
 */
public final class ItemGroups {
    private ItemGroups() {}

    private static volatile List<WheelConfig.ItemGroup> groups = List.of();
    private static final Map<String, Pattern> PATTERNS = new HashMap<>();
    /** item -> ids of all groups containing it, in config order */
    private static final Map<Item, List<String>> CACHE = new IdentityHashMap<>();

    static synchronized void load(WheelConfig c) {
        groups = c.haGroups == null ? List.of() : new ArrayList<>(c.haGroups);
        CACHE.clear();
        PATTERNS.clear();
    }

    public static synchronized List<String> groupsOf(Item it) {
        List<String> r = CACHE.get(it);
        if (r == null) {
            r = new ArrayList<>();
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(it);
            for (WheelConfig.ItemGroup g : groups) if (matches(g, it, rl)) r.add(g.id);
            if (CACHE.size() > 8192) CACHE.clear();
            CACHE.put(it, r);
        }
        return r;
    }

    /** does the group contain this item? (uncached, used by the editor preview too) */
    public static boolean matches(WheelConfig.ItemGroup g, Item it, ResourceLocation rl) {
        if (g == null || rl == null) return false;
        String id = rl.toString();
        if (g.items != null && g.items.contains(id)) return true;
        String v = g.value == null ? "" : g.value.trim();
        if ("list".equals(g.kind) || v.isEmpty()) return false;
        if (g.mod != null && !g.mod.isBlank() && !g.mod.equals(rl.getNamespace())) return false;
        try {
            switch (g.kind) {
                case "class": {
                    for (Class<?> c = it.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                        if (c.getName().equals(v) || c.getSimpleName().equals(v)) return true;
                    return false;
                }
                case "tag": {
                    ResourceLocation t = ResourceLocation.tryParse(v.startsWith("#") ? v.substring(1) : v);
                    return t != null && it.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, t));
                }
                default: {
                    Pattern p = pattern(v);
                    if (p == null) return false;
                    String subject = v.startsWith("re:") || v.contains(":") ? id : rl.getPath();
                    return p.matcher(subject).matches();
                }
            }
        } catch (Throwable t) { return false; }
    }

    /** "re:<regex>" or a wildcard (* any text, ? one char); case-insensitive */
    static Pattern pattern(String v) {
        synchronized (PATTERNS) {
            Pattern p = PATTERNS.get(v);
            if (p == null && !PATTERNS.containsKey(v)) {
                try {
                    if (v.startsWith("re:")) p = Pattern.compile(v.substring(3), Pattern.CASE_INSENSITIVE);
                    else {
                        StringBuilder sb = new StringBuilder();
                        for (char ch : v.toCharArray()) {
                            if (ch == '*') sb.append(".*");
                            else if (ch == '?') sb.append('.');
                            else sb.append(Pattern.quote(String.valueOf(ch)));
                        }
                        p = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
                    }
                } catch (Throwable t) { p = null; }
                PATTERNS.put(v, p);
            }
            return p;
        }
    }

    /** ids of all registered items in the group (editor preview / counts) */
    public static List<String> members(WheelConfig.ItemGroup g, int limit) {
        List<String> out = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(it);
            if (matches(g, it, rl)) { out.add(rl.toString()); if (out.size() >= limit) break; }
        }
        out.sort(null);
        return out;
    }

    /** automatic group suggestion for one mod */
    public record Suggestion(String kind, String value, String name, int count) {}

    /** groups found automatically in a mod: same id ending (*_backpack), same item class, same item tag */
    public static List<Suggestion> suggest(String ns) {
        List<Suggestion> out = new ArrayList<>();
        if (ns == null || ns.isBlank()) return out;
        List<Item> items = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) if (BuiltInRegistries.ITEM.getKey(it).getNamespace().equals(ns)) items.add(it);
        if (items.size() < 2) return out;
        // id ending: last word of the path ("iron_backpack" -> backpack)
        Map<String, Integer> tail = new TreeMap<>();
        for (Item it : items) {
            String p = BuiltInRegistries.ITEM.getKey(it).getPath();
            int u = p.lastIndexOf('_');
            String t = u >= 0 ? p.substring(u + 1) : p;
            if (t.length() >= 3) tail.merge(t, 1, Integer::sum);
        }
        for (var e : tail.entrySet()) if (e.getValue() >= 2) {
            WheelConfig.ItemGroup g = new WheelConfig.ItemGroup(); g.mod = ns; g.kind = "pattern"; g.value = "*" + e.getKey();
            int n = count(g, items);
            if (n >= 2) out.add(new Suggestion("pattern", ns + ":*" + e.getKey(), cap(e.getKey()), n));
        }
        // same class (skipping the plain vanilla Item / BlockItem)
        Map<Class<?>, Integer> cls = new LinkedHashMap<>();
        for (Item it : items) if (it.getClass() != Item.class && it.getClass() != BlockItem.class) cls.merge(it.getClass(), 1, Integer::sum);
        for (var e : cls.entrySet()) if (e.getValue() >= 2)
            out.add(new Suggestion("class", e.getKey().getName(), e.getKey().getSimpleName(), e.getValue()));
        // tags with at least two items of this mod
        try {
            BuiltInRegistries.ITEM.getTags().forEach(pair -> {
                int n = 0;
                for (var h : pair.getSecond()) if (h.unwrapKey().map(k -> k.location().getNamespace().equals(ns)).orElse(false)) n++;
                if (n >= 2) out.add(new Suggestion("tag", "#" + pair.getFirst().location(), "#" + pair.getFirst().location().getPath(), n));
            });
        } catch (Throwable ignored) {}
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        return out;
    }

    private static int count(WheelConfig.ItemGroup g, List<Item> items) {
        int n = 0;
        for (Item it : items) if (matches(g, it, BuiltInRegistries.ITEM.getKey(it))) n++;
        return n;
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' '); }

    /** a group made from a suggestion */
    public static WheelConfig.ItemGroup fromSuggestion(Suggestion s, String ns) {
        WheelConfig.ItemGroup g = new WheelConfig.ItemGroup();
        g.id = WheelConfig.newId("g");
        g.mod = ns;
        g.kind = s.kind();
        g.value = s.kind().equals("pattern") ? s.value().substring(s.value().indexOf(':') + 1) : s.value();
        g.name = s.name();
        return g;
    }
}
