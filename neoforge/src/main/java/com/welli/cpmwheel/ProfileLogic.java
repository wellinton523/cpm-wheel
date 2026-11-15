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
}
