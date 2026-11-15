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
        if (g.exclude != null && g.exclude.contains(rl.toString())) return false; // clicked off in the preview
        return matchesRaw(g, it, rl);
    }

    /** match ignoring the items clicked off in the preview */
    public static boolean matchesRaw(WheelConfig.ItemGroup g, Item it, ResourceLocation rl) {
        if (g == null || rl == null) return false;
        String id = rl.toString();
        if (g.items != null && g.items.contains(id)) return true;
        String v = g.value == null ? "" : g.value.trim();
        if ("list".equals(g.kind)) return false;
        boolean anyMod = g.mod == null || g.mod.isBlank();
        if (!anyMod && !g.mod.equals(rl.getNamespace())) return false;
        if ("query".equals(g.kind)) {
            String full = v;
            if (g.notq != null && !g.notq.isBlank())
                for (String w : g.notq.trim().split("\\s+")) { w = w.replaceFirst("^[-!]+", ""); if (!w.isEmpty()) full += " -" + w; }
            Query q = Query.of(full);
            if (v.isEmpty() && q.mods.isEmpty()) return !anyMod && q.matchesNot(it, rl, g.names); // only a mod picked: the whole mod (minus "não incluir")
            try { return q.matches(it, rl, g.names); } catch (Throwable t) { return false; }
        }
        if (v.isEmpty()) return false;
        try {
            switch (g.kind) {
                case "class": {
                    for (Class<?> c = it.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                        if (c.getName().equals(v) || c.getSimpleName().equals(v)) return true;
                    return false;
                }
                case "query": return Query.of(v).matches(it, rl, g.names);
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

    /**
     * 1.5.8 SQL-like query: "$backpack" = id path or display name contains "backpack" (case-insensitive);
     * words separated by spaces = AND; "a|b" or "a | b" or "a OR b" = OR; "-word" / "!word" = NOT; "@mod" (or "@a|b") = only that mod.
     * A word with ":" is matched against the full id. Spaces in names count as "_" ("$netherite_backpack" matches "Netherite Backpack").
     */
    public static final class Query {
        final List<List<String>> all = new ArrayList<>();
        final List<String> not = new ArrayList<>();
        final Set<String> mods = new HashSet<>();
        private static final Map<String, Query> CACHE = new HashMap<>();

        public static Query of(String v) {
            synchronized (CACHE) {
                Query q = CACHE.get(v);
                if (q == null) { q = parse(v); if (CACHE.size() > 512) CACHE.clear(); CACHE.put(v, q); }
                return q;
            }
        }
        static String norm(String s) {
            s = s.toLowerCase(Locale.ROOT).trim();
            while (s.startsWith("$")) s = s.substring(1);
            return s.replace(' ', '_');
        }
        static Query parse(String v) {
            Query q = new Query();
            boolean orNext = false;
            for (String tok : v.trim().split("\\s+")) {
                if (tok.isEmpty()) continue;
                if (tok.equals("|") || tok.equalsIgnoreCase("or")) { orNext = !q.all.isEmpty(); continue; }
                if (tok.startsWith("@")) { for (String m : tok.substring(1).split("\\|")) if (!m.isBlank()) q.mods.add(m.toLowerCase(Locale.ROOT)); continue; }
                boolean neg = tok.startsWith("-") || tok.startsWith("!");
                if (neg) tok = tok.substring(1);
                List<String> alts = new ArrayList<>();
                for (String a : tok.split("\\|")) { a = norm(a); if (!a.isEmpty()) alts.add(a); }
                if (alts.isEmpty()) continue;
                if (neg) q.not.addAll(alts);
                else if (orNext) q.all.get(q.all.size() - 1).addAll(alts);
                else q.all.add(alts);
                orNext = false;
            }
            return q;
        }
        /** only the NOT part (used when no include words are given) */
        boolean matchesNot(Item it, ResourceLocation rl, boolean names) {
            String path = rl.getPath().toLowerCase(Locale.ROOT), id = rl.toString().toLowerCase(Locale.ROOT);
            String name = null;
            if (names && !not.isEmpty()) { try { name = norm(it.getDescription().getString()); } catch (Throwable ignored) {} }
            for (String t : not) if (hit(t, path, id, name)) return false;
            return true;
        }
        public boolean isEmpty() { return all.isEmpty() && not.isEmpty() && mods.isEmpty(); }
        boolean hit(String term, String path, String id, String name) {
            if (term.indexOf(':') >= 0) return id.contains(term);
            return path.contains(term) || (name != null && name.contains(term));
        }
        public boolean matches(Item it, ResourceLocation rl, boolean names) {
            if (isEmpty()) return false;
            if (!mods.isEmpty() && !mods.contains(rl.getNamespace())) return false;
            String path = rl.getPath().toLowerCase(Locale.ROOT), id = rl.toString().toLowerCase(Locale.ROOT);
            String name = null;
            if (names) { try { name = norm(it.getDescription().getString()); } catch (Throwable ignored) {} }
            for (List<String> or : all) {
                boolean any = false;
                for (String t : or) if (hit(t, path, id, name)) { any = true; break; }
                if (!any) return false;
            }
            for (String t : not) if (hit(t, path, id, name)) return false;
            return true;
        }
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
            WheelConfig.ItemGroup g = new WheelConfig.ItemGroup(); g.mod = ns; g.kind = "query"; g.value = "$" + e.getKey();
            int n = count(g, items);
            if (n >= 2) out.add(new Suggestion("query", "$" + e.getKey(), cap(e.getKey()), n));
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
        g.value = s.kind().equals("pattern") && s.value().contains(":") ? s.value().substring(s.value().indexOf(':') + 1) : s.value();
        g.name = s.name();
        return g;
    }
}
