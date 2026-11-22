package com.welli.cpmwheel;

import java.util.Map;

/** 1.8.0 "Perfis automáticos por modelo": which config file a CPM model uses (pure; checked by tools/Feature180Check.java). */
public final class ProfileLogic {
    private ProfileLogic() {}

    /** profile name -> safe file name ("" if nothing usable is left) */
    public static String clean(String name) {
        if (name == null) return "";
        String n = name.trim().replaceAll("[^A-Za-z0-9._ -]", "_").replace(' ', '_');
        while (n.startsWith(".")) n = n.substring(1);
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    /** config key for a model: "@<profile>" when auto profiles are on and the model is linked, else the model key itself */
    public static String resolve(boolean autoOn, Map<String, String> links, String modelKey) {
        if (modelKey == null || modelKey.isBlank()) modelKey = "default";
        if (!autoOn || links == null) return modelKey;
        String p = clean(links.get(modelKey));
        return p.isEmpty() ? modelKey : "@" + p;
    }

    public static boolean isProfile(String key) { return key != null && key.startsWith("@"); }

    // ---------- 1.9.0 export / import ----------
    /** first free name: "name", "name-2", "name-3", ... */
    public static String unique(String name, java.util.function.Predicate<String> exists) {
        String n = clean(name); if (n.isEmpty()) n = "perfil";
        if (!exists.test(n)) return n;
        for (int i = 2; i < 1000; i++) { String k = n + "-" + i; if (!exists.test(k)) return k; }
        return n + "-" + System.currentTimeMillis();
    }
    /** backup file name for a profile about to be replaced */
    public static String backupName(String name, String stamp) { return clean(name) + "-" + stamp.replaceAll("[^0-9A-Za-z_-]", "") + ".json"; }
    /** suggested profile name from an import file name ("x.cpmw.json" / "x.json" -> "x") */
    public static String nameFromFile(String file) {
        String n = file == null ? "" : file;
        if (n.endsWith(".json")) n = n.substring(0, n.length() - 5);
        if (n.endsWith(".cpmw")) n = n.substring(0, n.length() - 5);
        return clean(n);
    }
}
