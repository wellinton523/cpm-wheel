package com.welli.cpmwheel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.9.0 profile export / import. A profile is the whole CPM Wheel config of a model (wheel pages, triggers, hands,
 * Física Live2D, look-at, blink, extras ...). Export writes config/cpmwheel/export/<name>.json; import reads
 * config/cpmwheel/import/ (and export/), validates it (format + a full parse with the normal migrations) and saves it as
 * the named profile config/cpmwheel/profiles/<name>.json. An existing profile is never replaced without an explicit
 * confirmation, and its old file is first copied to profiles/_backup/.
 */
public final class ProfileIO {
    private ProfileIO() {}

    public static final String FORMAT = "cpmwheel-profile";

    public static Path exportDir() { return WheelConfig.cfgDir().resolve("export"); }
    public static Path importDir() { return WheelConfig.cfgDir().resolve("import"); }
    public static Path backupDir() { return WheelConfig.cfgDir().resolve("profiles").resolve("_backup"); }

    /** writes the active config to export/<name>.json (never overwrites: name-2, name-3 ...); returns the file */
    public static Path export(String name) throws Exception {
        WheelConfig c = WheelConfig.get();
        c.save();
        Files.createDirectories(exportDir());
        Files.createDirectories(importDir());
        String base = ProfileLogic.clean(name == null || name.isBlank() ? defaultName() : name);
        String n = ProfileLogic.unique(base, k -> Files.exists(exportDir().resolve(k + ".json")));
        JsonObject env = new JsonObject();
        env.addProperty("format", FORMAT);
        env.addProperty("formatVersion", 1);
        env.addProperty("configVersion", c.configVersion);
        env.addProperty("name", n);
        env.addProperty("model", CpmBridge.modelKey());
        env.addProperty("exported", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        env.add("config", WheelConfig.gson().toJsonTree(c));
        Path f = exportDir().resolve(n + ".json");
        Files.writeString(f, WheelConfig.gson().toJson(env), StandardCharsets.UTF_8);
        return f;
    }

    static String defaultName() {
        String k = WheelConfig.activeKey();
        if (k == null) k = CpmBridge.modelKey();
        return ProfileLogic.isProfile(k) ? k.substring(1) : k;
    }

    /** importable files: import/ first, then export/ */
    public static List<Path> files() {
        List<Path> l = new ArrayList<>();
        for (Path d : new Path[]{importDir(), exportDir()}) {
            try (var st = Files.list(d)) {
                st.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().forEach(l::add);
            } catch (Exception ignored) {}
        }
        return l;
    }

    /** finds an importable file by its name */
    public static Path byName(String file) {
        for (Path f : files()) if (f.getFileName().toString().equals(file) || f.getFileName().toString().equals(file + ".json")) return f;
        return null;
    }

    /** result of a check / import: ok, message key + arg, suggested name, the parsed config */
    public static final class Result {
        public boolean ok, exists; public String key = "", arg = "", name = ""; WheelConfig cfg;
        Result fail(String k, String a) { ok = false; key = k; arg = a == null ? "" : a; return this; }
    }

    /** validates a file without writing anything */
    public static Result check(Path f) {
        Result r = new Result();
        if (f == null || !Files.isRegularFile(f)) return r.fail("cpmwheel.pio.err.nofile", f == null ? "" : f.getFileName().toString());
        try {
            if (Files.size(f) > 8L * 1024 * 1024) return r.fail("cpmwheel.pio.err.big", "");
            JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return r.fail("cpmwheel.pio.err.format", "");
            JsonObject o = root.getAsJsonObject();
            JsonElement cfg = o;
            String suggested = ProfileLogic.nameFromFile(f.getFileName().toString());
            if (o.has("format")) {
                if (!FORMAT.equals(o.get("format").getAsString())) return r.fail("cpmwheel.pio.err.format", o.get("format").getAsString());
                if (!o.has("config") || !o.get("config").isJsonObject()) return r.fail("cpmwheel.pio.err.format", "config");
                cfg = o.get("config");
                if (o.has("name")) { String nn = ProfileLogic.clean(o.get("name").getAsString()); if (!nn.isEmpty()) suggested = nn; }
            }
            JsonObject co = cfg.getAsJsonObject();
            int ver = co.has("configVersion") ? co.get("configVersion").getAsInt() : 0;
            if (ver > new WheelConfig().configVersion) return r.fail("cpmwheel.pio.err.newer", String.valueOf(ver));
            WheelConfig parsed = WheelConfig.parse(cfg.toString());
            if (parsed == null) return r.fail("cpmwheel.pio.err.invalid", "");
            r.ok = true; r.cfg = parsed; r.name = suggested;
            r.exists = Files.exists(WheelConfig.profileFile(suggested));
            r.key = "cpmwheel.pio.ok"; r.arg = String.valueOf(parsed.pages.size());
            return r;
        } catch (Exception e) {
            return r.fail("cpmwheel.pio.err.read", e.getClass().getSimpleName());
        }
    }

    /** imports as profile "name"; an existing profile needs replace = true and is backed up first */
    public static Result importAs(Path f, String name, boolean replace) {
        Result r = check(f);
        if (!r.ok) return r;
        String n = ProfileLogic.clean(name == null || name.isBlank() ? r.name : name);
        if (n.isEmpty()) return r.fail("cpmwheel.l2d.io.noname", "");
        r.name = n;
        Path target = WheelConfig.profileFile(n);
        r.exists = Files.exists(target);
        if (r.exists && !replace) { r.ok = false; r.key = "cpmwheel.pio.err.exists"; r.arg = n; return r; }
        try {
            Files.createDirectories(target.getParent());
            boolean active = ("@" + n).equals(WheelConfig.activeKey());
            if (active) WheelConfig.get().save();   // keep the newest copy in the backup
            if (r.exists) {
                Files.createDirectories(backupDir());
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Files.copy(target, backupDir().resolve(ProfileLogic.backupName(n, stamp)), StandardCopyOption.REPLACE_EXISTING);
            }
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, WheelConfig.gson().toJson(r.cfg), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (active) { WheelConfig.dropCurrent(); WheelConfig.get(); }
            r.ok = true; r.key = r.exists ? "cpmwheel.pio.done.replaced" : "cpmwheel.pio.done"; r.arg = n;
            return r;
        } catch (Exception e) {
            CpmWheel.LOG.warn("[CPM Wheel] profile import failed", e);
            return r.fail("cpmwheel.pio.err.write", e.getClass().getSimpleName());
        }
    }

    /** links the current CPM model to profile "name" and turns automatic profiles on */
    public static void useForModel(String name) {
        String n = ProfileLogic.clean(name);
        if (n.isEmpty()) return;
        WheelConfig.get().save();
        var pr = WheelConfig.profiles();
        pr.links.put(CpmBridge.modelKey(), n); pr.autoOn = true;
        WheelConfig.saveProfiles(); WheelConfig.reloadProfile(); WheelConfig.get();
    }
}
