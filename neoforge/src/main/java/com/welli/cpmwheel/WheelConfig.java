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

    /** 1.6.0 optional sound: on activate (id) and optionally on restore / turning off (endId); volume and pitch in % */
    public static class SoundSpec {
        public String id = "", endId = "";
        public int vol = 100, pitch = 100;
        public boolean empty() { return (id == null || id.isBlank()) && (endId == null || endId.isBlank()); }
    }

    public static class Slot {
        /** 1.6.0: optional sound for this button (null = none) */
        public SoundSpec sound = null;
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
        /** 1.8.1 "Emote do Emotecraft": plays / stops an Emotecraft emote (ecId = emote UUID, ecName = its name, ecAction toggle | play | stop) */
        public String ecId = "", ecName = "", ecAction = "toggle";
        public boolean isEc() { return "ec".equals(mode); }
        /** 1.8.2: Emotecraft buttons show the emote's icon (or the generic one) unless an icon is set or this is off */
        public boolean ecIcon = true;
        /** icon actually drawn: the set icon, else the Emotecraft emote icon for Emotecraft buttons */
        public String iconKey() {
            if ((icon == null || icon.isBlank()) && isEc() && ecIcon) return "stop".equals(ecAction) || ecId == null || ecId.isBlank() ? "ec:-" : EmoteLogic.iconId(ecId);
            return icon;
        }
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
        public String display() { return label != null && !label.isBlank() ? label : (isFolder() ? "..." : isGroup() ? "Grupo" : isCond() ? "Condicional" : isEc() ? ecDisplay() : RESET.equals(anim) ? "Reset" : anim); }
        public boolean empty() { return !isFolder() && !isGroup() && !isCond() && !isEc() && (anim == null || anim.isBlank()); }
        public String ecDisplay() { return "stop".equals(ecAction) || ecName == null || ecName.isBlank() ? "\u25A0 Emotecraft" : ecName; }
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

    /** 1.5.8: kind "query" ($backpack -$upgrade @mod, see ItemGroups.Query). 1.5.7 item group: kind "pattern" (wildcard * ? or re:regex on the id), "class" (item class incl. subclasses), "tag" (item tag), "list" (only the listed ids) */
    public static class ItemGroup {
        public String id = "";
        public String name = "";
        /** mod (namespace) the group belongs to; "" = any mod */
        public String mod = "";
        public String kind = "query";
        public String value = "";
        /** extra items always in the group (and the only members for kind "list") */
        public List<String> items = new ArrayList<>();
        /** 1.5.8 kind "query": $terms also match the item's display name (game language) */
        public boolean names = true;
        /** 1.5.8: "Não incluir" words (query kind), items clicked off in the preview, group colour */
        public String notq = "";
        public List<String> exclude = new ArrayList<>();
        public int color = 0xFFD23B;
        public String label() { return name == null || name.isBlank() ? (value == null || value.isBlank() ? id : value) : name; }
    }

    /** 1.5.7 effect trigger: while the effect is active, run the actions; when it ends, restore (or keep) */
    public static class EffectRule {
        public String id = "";
        public String name = "";
        public boolean on = true;
        public String effect = "minecraft:poison";
        /** minimum amplifier (0 = level I = any) */
        public int minAmp = 0;
        public List<EffectAction> actions = new ArrayList<>();
        /** "restore" = back to the previous state, "none" = do nothing */
        public String onEnd = "restore";
        /** debounce in ms: condition must hold this long before starting / ending */
        public int delayStart = 0, delayEnd = 1000;
        public boolean notify = true;
        /** 1.5.8: while active, the wheel buttons of the animations it holds are locked (no manual change; restore = pre-trigger state) */
        public boolean lock = true;
        /** 1.6.0: conditions (effect, health, night, rain, biome, ...) combined with "and" / "or"; replaces effect/minAmp (kept for the icon) */
        public List<TriggerLogic.Cond> conds = new ArrayList<>();
        public String combine = "and";
        /** 1.6.0 timed transition: animation played for transMs before switching (and optionally before restoring) */
        public String transAnim = "";
        public int transMs = 1000;
        public boolean transOnEnd = false;
        /** 1.6.0: optional sound when the rule starts (id) / restores (endId) */
        public SoundSpec sound = null;
    }

    /** 1.5.8 movable HUD element: anchor (tl tc tr ml mc mr bl bc br) + offset in GUI pixels + scale % */
    public static class HudPos {
        public String anchor = "tl";
        public int dx = 4, dy = 4, scale = 100;
        public HudPos() {}
        public HudPos(String a, int x, int y, int sc) { anchor = a; dx = x; dy = y; scale = sc; }
    }

    /** target "anim:<cpm animation>" or "group:<group button name>"; op "on" (ATIVAR), "off" (DESATIVAR), "toggle" (ALTERNAR); value for ATIVAR on value animations (-1 = button value / max) */
    public static class EffectAction {
        public String target = "";
        public String op = "off";
        public int value = -1;
        public EffectAction() {}
        public EffectAction(String t, String o) { target = t; op = o; }
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

    /** 1.8.0 default item positions per type {x,y,z,scale,rx,ry,rz} (the author's tuning); types not listed default to 0 */
    static final java.util.Map<String, int[]> DEF_ADJ = java.util.Map.of(
            "shield", new int[]{6, -1, -7, 0, 0, 30, 35}, "sword", new int[]{-1, 6, 1, 0, 30, 60, 30}, "other", new int[]{2, -3, 4, 0, 10, 35, 35},
            "trident", new int[]{-1, -2, 14, 0, 65, -140, -40}, "crossbow", new int[]{0, 0, 0, 0, 0, 0, 0}, "bow", new int[]{-5, 2, 0, 0, 20, 20, 0},
            "tool", new int[]{0, 0, 0, 0, 15, 35, 5}, "axe", new int[]{0, 0, 0, 0, 15, 35, 5}, "lantern", new int[]{12, -9, 0, 40, 0, 0, 0},
            "flatblock", new int[]{2, -8, 6, 0, 0, 0, 0});
    /** 1.8.0 default arm adjust per hand */
    static final java.util.Map<String, int[]> DEF_ARM = java.util.Map.of("main", new int[]{30, 0, 0, 0, 0, 0, 0}, "off", new int[]{30, 0, 0, 0, 0, 0, 0});
    public static int[] defAdj(String cat) { int[] v = DEF_ADJ.get(cat); return v == null ? new int[7] : v.clone(); }
    public static int[] defArm(String hand) { int[] v = DEF_ARM.get(hand); return v == null ? new int[7] : v.clone(); }
    /** value in use for a type / hand: own entry, else the default */
    public int[] adj(String cat) { int[] v = haAdj.get(cat); return v != null && v.length == 7 ? v : defAdj(cat); }
    public int[] arm(String hand) { int[] v = haArm.get(hand); return v != null && v.length == 7 ? v : defArm(hand); }

    public boolean holdToOpen = true;
    public boolean animations = true;
    public boolean livePreview = true;
    /** Hover preview really plays the animation on your model (CPM has no local-only preview). Off by default. */
    public boolean previewOnHover = false;
    /** center of the wheel: "player", "icon" or "none" */
    public String centerMode = "player";
    /** center size in percent (50-200) */
    public int centerSize = 100;
    public int configVersion = 26;
    /** 1.6.0 global switches: extra trigger conditions, timed transitions, custom sounds (per-button / per-rule sounds) */
    public boolean trigCondsOn = true, trigTransOn = true, customSounds = true;
    /** 1.7.0 "Física Live2D": chains of CPM value layers driven by spring physics */
    public boolean l2dOn = false, l2dSync = true, l2dPauseTrig = true;
    public List<Live2dMath.Chain> l2dChains = new ArrayList<>();
    /** 1.7.1: layer -> how links sharing it combine ("sum" / "avg" / "max"); missing = sum */
    public java.util.Map<String, String> l2dModes = new java.util.HashMap<>();
    /** 1.8.0 Física Live2D extras, all off until turned on: wind, damage / landing impulses, look-at, voice (Simple Voice Chat),
     *  "Camada/animação (movimento)" input, breathing / blinking generators */
    public boolean l2dWindOn = false, l2dImpactOn = false, l2dLookOn = false, l2dVoiceOn = false, l2dLayerOn = false, l2dGenOn = false;
    public int l2dWindPct = 100, l2dImpactPct = 100, l2dLookRadius = 8, l2dVoicePct = 100;
    public boolean l2dLookPlayers = true;
    /** 1.8.3 "Olhar": who can be a target ("players" / "mobs" / "both" / "off"), how it is chosen ("screen" = area around the
     *  centre of your own view, default; "nearest" = the 1.8.0 way), and the privacy filters (all on by default) */
    public String l2dLookTargets = "players", l2dLookMode = "screen";
    public boolean l2dLookSafeCam = true, l2dLookHideHidden = true, l2dLookVanishTag = true, l2dLookLos = true;
    public int l2dLookFov = 160, l2dLookMaxYaw = 60;
    /** screen area: "circle" (radius % of the screen height) or "rect" (width / height % of the screen), offset % from the centre */
    public String l2dLookShape = "circle";
    public int l2dLookAreaR = 20, l2dLookAreaW = 40, l2dLookAreaH = 30, l2dLookOffX = 0, l2dLookOffY = 0;
    /** stickiness so it doesn't flick between two targets; debug overlay (area outline + target marker on the HUD) */
    public boolean l2dLookSticky = true, l2dLookDebug = false;
    public int l2dLookStickPct = 25;
    /** eye smoothing: "spring" (critically damped) / "lerp" / "off", response (ms), max speed (°/s, 0 = no limit), saccades (%),
     *  return-to-centre delay (ms), softer move after a target change (ms) */
    public String l2dLookSmooth = "spring";
    public int l2dLookRespMs = 150, l2dLookMaxSpeed = 720, l2dLookSaccade = 0, l2dLookReturnMs = 600, l2dLookSwitchMs = 250;
    public List<Live2dMath.Gen> l2dGens = new ArrayList<>();
    /** 1.8.0 "Teclas de emotes": animation per key slot (key.cpmwheel.emote1..8, set in Controls); off by default */
    public boolean emoteKeysOn = false;
    public List<String> emoteKeys = new ArrayList<>();
    /** 1.8.0 "Encadear animações": when an animation ends / starts, play another; off by default */
    public boolean chainTrigOn = false;
    public List<ChainLogic.Rule> chainRules = new ArrayList<>();
    /** 1.8.1 Emotecraft bridge (optional mod, reflection): off by default. ecL2d: "none" | "off" (physics off while an emote plays) | "kick" */
    public boolean ecOn = false;
    public String ecL2d = "none";
    public int ecKick = 30;
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
    /** 1.5.5: per-item ("ns:id|main/off") and per-mod ("mod:ns|main/off") overrides {x,y,z,scale,rx,ry,rz}; priority item > mod > category */
    public java.util.Map<String, int[]> haItem = new java.util.HashMap<>();
    public boolean haItemOn = true;
    /** 1.5.8 "Física de pendurado" per item/group/mod/type and hand: {on, pivot, intensity, stiffness, damping x10, weight, max angle, react} */
    public java.util.Map<String, int[]> haPhys = new java.util.HashMap<>();
    /** 1.5.9 "Igual nas duas mãos": targets ("ns:id", "grp:<id>", "mod:<ns>", "cat:<type>") whose item/arm/physics values are edited once for both hands */
    public java.util.Set<String> haSame = new java.util.HashSet<>();
    /** 1.5.4: categories drawn 2D (flat item) instead of 3D (lantern, torch, block each separate); arm adjust per hand */
    public List<String> haFlat = new ArrayList<>();
    public java.util.Map<String, int[]> haArm = new java.util.HashMap<>();
    /** 1.5.7: draw the first-person arm in the world entity pass like Better Combat ("on", "auto" = only with a shader pack, "off"), depth % toward the camera */
    public String haWorldArm = "on";
    public int haWorldDepth = 40;
    /** 1.5.7: item groups inside a mod (overrides stored in haItem as "grp:<id>|main/off" and "arm/grp:<id>|..."); priority item > group > mod > category */
    public List<ItemGroup> haGroups = new ArrayList<>();
    /** 1.5.7: effect triggers ("Gatilhos de efeito") */
    public boolean effOn = true;
    public List<EffectRule> effRules = new ArrayList<>();
    public int haIntensity = 100, haIdle = 100, haBob = 100, haInertia = 100, haPose = 100, haSwing = 100;
    public boolean frameSegments = true;
    public boolean hudEnabled = true;
    public String hudCorner = "top_left";
    public int hudScale = 100;
    public int hudOpacity = 60;
    public boolean hudOnlyNonDefault = true;
    /** 1.5.8: positions of every HUD element ("list" = active items, "toasts" = trigger notifications) */
    public java.util.Map<String, HudPos> hudPos = new java.util.HashMap<>();
    public boolean hudSnap = true;
    /** 1.5.8 trigger toasts: on, seconds on screen, background opacity %, compact row with timer while the effect lasts */
    public boolean toastOn = true;
    public int toastSecs = 4, toastOpacity = 85;
    public boolean toastMini = true;
    public boolean showFavoritesPage = true;
    public int lastPage = 0;
    public List<Page> pages = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WheelConfig current;
    private static String currentKey;
    /** 1.8.0: the file this object belongs to (a screen still holding an old object after a profile switch saves to its own file) */
    private transient String myKey;

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("cpmwheel"); }

    private static Path file(String key) {
        if (ProfileLogic.isProfile(key)) return dir().resolve("profiles").resolve(ProfileLogic.clean(key.substring(1)) + ".json");
        return dir().resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    // ---------- 1.8.0 "Perfis automáticos por modelo" (config/cpmwheel/_profiles.json, shared by every model) ----------
    public static class Profiles {
        public boolean autoOn = false;
        public boolean notify = true;
        /** CPM model key -> profile name (config/cpmwheel/profiles/<name>.json) */
        public java.util.Map<String, String> links = new java.util.HashMap<>();
    }
    private static Profiles profiles;
    public static Profiles profiles() {
        if (profiles == null) {
            try {
                Path f = dir().resolve("_profiles.json");
                if (Files.exists(f)) profiles = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Profiles.class);
            } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] profiles load failed", e); }
            if (profiles == null) profiles = new Profiles();
            if (profiles.links == null) profiles.links = new java.util.HashMap<>();
        }
        return profiles;
    }
    public static void saveProfiles() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("_profiles.json"), GSON.toJson(profiles()), StandardCharsets.UTF_8);
        } catch (Exception e) { CpmWheel.LOG.warn("[CPM Wheel] profiles save failed", e); }
    }
    private static String currentModel = "default";
    public static String currentModel() { return currentModel; }
    /** after linking / unlinking: reload on the next get() */
    public static void reloadProfile() { if (current != null) current.save(); current = null; }
    public static List<String> profileNames() {
        List<String> l = new ArrayList<>();
        try (var st = Files.list(dir().resolve("profiles"))) {
            st.forEach(f -> { String n = f.getFileName().toString(); if (n.endsWith(".json")) l.add(n.substring(0, n.length() - 5)); });
        } catch (Exception ignored) {}
        Collections.sort(l);
        return l;
    }

    public static WheelConfig get() {
        String model = CpmBridge.modelKey();
        Profiles pr = profiles();
        String key = ProfileLogic.resolve(pr.autoOn, pr.links, model);
        if (current == null || !key.equals(currentKey)) {
            boolean switching = current != null && currentKey != null;
            current = load(key, model); current.myKey = key; currentKey = key; currentModel = model;
            if (switching && pr.autoOn && pr.notify) {
                try {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cpmwheel.prof.switched",
                            ProfileLogic.isProfile(key) ? key.substring(1) : net.minecraft.network.chat.Component.translatable("cpmwheel.prof.own").getString()), true);
                } catch (Throwable ignored) {}
            }
            try { Live2d.reset(); Actions.invalidate(); } catch (Throwable ignored) {}
        }
        currentModel = model;
        return current;
    }

    private static WheelConfig load(String key) { return load(key, key); }
    private static WheelConfig load(String key, String model) {
        try {
            Path f = file(key);
            if (!Files.exists(f) && ProfileLogic.isProfile(key)) f = file(model);   // a new profile starts from this model's own settings
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
                    if (c.configVersion < 15) c.configVersion = 15; // 1.5.6: arm overrides per item/mod stored in haItem as "arm/<key>"
                    if (c.configVersion < 14) { c.configVersion = 14; c.haItemOn = true; } // 1.5.5: per-item/per-mod overrides + flatblock category
                    if (c.configVersion < 13) c.configVersion = 13; // 1.5.4: haFlat + haArm (new fields, empty = defaults)
                    if (c.configVersion < 12) { c.configVersion = 12; c.haTwoHandBlock = false; } // 1.5.3: blocks held with one hand by default
                    if (c.configVersion < 11) c.configVersion = 11; // 1.5.0: hand animations (new fields, defaults on, set in fixup)
                    if (c.configVersion < 10) c.configVersion = 10; // 1.4.7: handGlow (default auto, set in fixup) // 1.4.6: group members -> actions (fixup), keybinds, conditional slots // 1.4.5: group slots (new fields, default none)
                    if (c.configVersion < 6) c.configVersion = 6; // 1.4.2: design id rename + per-slot displayMode (defaults to "") handled in fixup()
                    if (c.configVersion < 16) { c.configVersion = 16; c.haWorldArm = "on"; c.haWorldDepth = 40; c.effOn = true; } // 1.5.7: world-pass arm (Better Combat method) on, item groups, effect triggers
                    if (c.configVersion < 17) { c.configVersion = 17; c.toastOn = true; c.toastSecs = 4; c.toastOpacity = 85; c.toastMini = true; c.hudSnap = true;
                        if (c.effRules != null) for (EffectRule r : c.effRules) if (r != null) r.lock = true; } // 1.5.8: query groups, physics, movable HUD (list position from the old corner), toasts, trigger lock
                    if (c.configVersion < 18) c.configVersion = 18; // 1.5.9: physics with gravity (old arrays upgraded in fixup), "Igual nas duas mãos"
                    if (c.configVersion < 19) { c.configVersion = 19; c.trigCondsOn = true; c.trigTransOn = true; c.customSounds = true; // 1.6.0: effect rules -> condition lists
                        if (c.effRules != null) for (EffectRule r : c.effRules) if (r != null) migrateRule(r); }
                    if (c.configVersion < 20) { c.configVersion = 20; c.l2dOn = false; c.l2dSync = true; c.l2dPauseTrig = true; } // 1.7.0: Física Live2D (off by default)
                    if (c.configVersion < 21) { c.configVersion = 21; // 1.7.1: one input per link -> weighted input list (100 %)
                        if (c.l2dChains != null) for (Live2dMath.Chain ch : c.l2dChains) if (ch != null && ch.links != null) for (Live2dMath.Link l : ch.links) if (l != null) Live2dMath.migrateLink(l); }
                    if (c.configVersion < 22) { c.configVersion = 22; // 1.7.2: "Desativar quando" (empty = never) + fade 0.3 s
                        if (c.l2dChains != null) for (Live2dMath.Chain ch : c.l2dChains) if (ch != null) { if (ch.off == null) ch.off = new ArrayList<>(); if (ch.fadeMs <= 0) ch.fadeMs = 300; } }
                    if (c.configVersion < 23) { c.configVersion = 23; // 1.7.3: velocity inputs hold the link displaced (75 %), angles unchanged
                        if (c.l2dChains != null) for (Live2dMath.Chain ch : c.l2dChains) if (ch != null && ch.links != null) for (Live2dMath.Link l : ch.links) Live2dMath.migrateHold(l); }
                    if (c.configVersion < 24) { c.configVersion = 24; // 1.8.0: new extras off; item / arm positions still on the old defaults (0) use the new ones
                        c.l2dWindOn = c.l2dImpactOn = c.l2dLookOn = c.l2dVoiceOn = c.l2dLayerOn = c.l2dGenOn = false; c.emoteKeysOn = false; c.chainTrigOn = false;
                        if (c.haAdj != null) c.haAdj.entrySet().removeIf(e -> DEF_ADJ.containsKey(e.getKey()) && allZero(e.getValue()));
                        if (c.haArm != null) c.haArm.entrySet().removeIf(e -> DEF_ARM.containsKey(e.getKey()) && allZero(e.getValue())); }
                    if (c.configVersion < 25) { c.configVersion = 25; // 1.8.1: Emotecraft bridge, off until turned on
                        c.ecOn = false; c.ecL2d = "none"; c.ecKick = 30; }
                    if (c.configVersion < 26) { c.configVersion = 26; // 1.8.3: look-at targets keep the old choice, privacy filters on, screen-area mode
                        c.l2dLookTargets = c.l2dLookPlayers ? "players" : "both"; c.l2dLookMode = "screen";
                        c.l2dLookSafeCam = c.l2dLookHideHidden = c.l2dLookVanishTag = c.l2dLookLos = true; c.l2dLookDebug = false; }
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

    /** 1.6.0: a 1.5.x rule (one effect + min level) becomes a rule with one "effect" condition; nothing else changes */
    static void migrateRule(EffectRule r) {
        if (r.conds == null) r.conds = new ArrayList<>();
        if (r.conds.isEmpty() && r.effect != null && !r.effect.isBlank()) r.conds.add(new TriggerLogic.Cond("effect", r.effect, Math.max(0, r.minAmp)));
        if (r.combine == null) r.combine = "and";
    }

    /** 1.7.2: null-safe condition list with known types */
    static List<TriggerLogic.Cond> fixConds(List<TriggerLogic.Cond> l) {
        if (l == null) l = new ArrayList<>();
        l.removeIf(c -> c == null || !java.util.Arrays.asList(TriggerLogic.TYPES).contains(c.type));
        for (TriggerLogic.Cond c : l) { if (c.value == null) c.value = ""; c.num = Math.max(0, Math.min(255, c.num)); }
        while (l.size() > 12) l.remove(l.size() - 1);
        return l;
    }

    static void fixSound(SoundSpec sp) {
        if (sp == null) return;
        if (sp.id == null) sp.id = ""; if (sp.endId == null) sp.endId = "";
        sp.vol = clamp(sp.vol, 0, 200); sp.pitch = clamp(sp.pitch <= 0 ? 100 : sp.pitch, 50, 200);
    }

    static boolean allZero(int[] v) { if (v == null) return true; for (int x : v) if (x != 0) return false; return true; }

    void fixup() {
        if (LEGACY_DESIGN.equals(design)) design = "terminal"; // 1.4.0/1.4.1 id of the Terminal design
        for (Page pg : pages) { if (pg.slots == null) pg.slots = new ArrayList<>(); if (pg.color == 0) pg.color = 0x2EE6FF; }
        if (Style.idx(Style.DESIGNS, design) < 0) design = "classic";
        if (Style.idx(Style.THEMES, theme) < 0) theme = "auto";
        if (!java.util.Arrays.asList(FpState.MODES).contains(fpAnimations)) fpAnimations = "layers";
        if (!java.util.Arrays.asList(HandGlow.MODES).contains(handGlow)) handGlow = "auto";
        if (!java.util.Arrays.asList(WorldArm.MODES).contains(haWorldArm)) haWorldArm = "on";
        haWorldDepth = haWorldDepth <= 0 ? 40 : Math.max(20, Math.min(100, haWorldDepth));
        if (handAnimOff == null) handAnimOff = new ArrayList<>();
        if (haIgnore == null) haIgnore = new ArrayList<>();
        if (haAdj == null) haAdj = new java.util.HashMap<>();
        if (haFlat == null) haFlat = new ArrayList<>();
        if (haItem == null) haItem = new java.util.HashMap<>();
        haItem.entrySet().removeIf(e -> e.getKey() == null || !(e.getKey().endsWith("|main") || e.getKey().endsWith("|off")) || e.getValue() == null || e.getValue().length != 7);
        if (hudPos == null) hudPos = new java.util.HashMap<>();
        // 1.8.0
        l2dWindPct = clamp(l2dWindPct, 0, 300); l2dImpactPct = clamp(l2dImpactPct, 0, 300); l2dVoicePct = clamp(l2dVoicePct, 0, 300);
        l2dLookRadius = clamp(l2dLookRadius <= 0 ? 8 : l2dLookRadius, 2, 32);
        if (!java.util.List.of("players", "mobs", "both", "off").contains(l2dLookTargets)) l2dLookTargets = "players";
        if (!"nearest".equals(l2dLookMode)) l2dLookMode = "screen";
        if (!"rect".equals(l2dLookShape)) l2dLookShape = "circle";
        if (!java.util.List.of("spring", "lerp", "off").contains(l2dLookSmooth)) l2dLookSmooth = "spring";
        l2dLookPlayers = "players".equals(l2dLookTargets);
        l2dLookFov = clamp(l2dLookFov, 20, 360); l2dLookMaxYaw = clamp(l2dLookMaxYaw, 5, 90);
        l2dLookAreaR = clamp(l2dLookAreaR, 2, 100); l2dLookAreaW = clamp(l2dLookAreaW, 2, 100); l2dLookAreaH = clamp(l2dLookAreaH, 2, 100);
        l2dLookOffX = clamp(l2dLookOffX, -50, 50); l2dLookOffY = clamp(l2dLookOffY, -50, 50); l2dLookStickPct = clamp(l2dLookStickPct, 0, 100);
        l2dLookRespMs = clamp(l2dLookRespMs, 0, 2000); l2dLookMaxSpeed = clamp(l2dLookMaxSpeed, 0, 3000); l2dLookSaccade = clamp(l2dLookSaccade, 0, 100);
        l2dLookReturnMs = clamp(l2dLookReturnMs, 0, 5000); l2dLookSwitchMs = clamp(l2dLookSwitchMs, 0, 2000);
        if (l2dGens == null) l2dGens = new ArrayList<>();
        l2dGens.removeIf(java.util.Objects::isNull);
        while (l2dGens.size() > 8) l2dGens.remove(l2dGens.size() - 1);
        for (Live2dMath.Gen g : l2dGens) {
            if (g.id == null || g.id.isBlank()) g.id = newId("g");
            if (g.name == null) g.name = ""; if (g.anim == null) g.anim = "";
            if (!"blink".equals(g.kind)) g.kind = "breath";
            g.periodMs = clamp(g.periodMs, 200, 30000); g.lo = clamp(g.lo, 0, 100); g.hi = clamp(g.hi, 0, 100);
            g.rand = clamp(g.rand, 0, 100); g.pulseMs = clamp(g.pulseMs, 50, 2000);
            // 1.8.3 smooth blink (blinks from before 1.8.3 keep their old timing, now with the smooth curve)
            if (g.closeMs < 0 || g.holdMs < 0 || g.openMs < 0) { int[] t = Live2dMath.blinkTimes(g); g.closeMs = t[0]; g.holdMs = t[1]; g.openMs = t[2]; }
            g.closeMs = clamp(g.closeMs, 0, 2000); g.holdMs = clamp(g.holdMs, 0, 2000); g.openMs = clamp(g.openMs, 0, 2000);
            if (g.closeMs + g.holdMs + g.openMs < 50) g.holdMs = 50 - g.closeMs - g.openMs;
            g.curve = Live2dMath.curve(g.curve); g.doubleChance = clamp(g.doubleChance, 0, 100);
            if (g.more == null) g.more = new ArrayList<>();
            g.more.removeIf(m -> m == null || m.isBlank() || m.equals(g.anim));
            while (g.more.size() > 4) g.more.remove(g.more.size() - 1);
        }
        if (emoteKeys == null) emoteKeys = new ArrayList<>();
        emoteKeys.replaceAll(k -> k == null ? "" : k);
        while (emoteKeys.size() < CpmWheel.EMOTE_KEYS) emoteKeys.add("");
        while (emoteKeys.size() > CpmWheel.EMOTE_KEYS) emoteKeys.remove(emoteKeys.size() - 1);
        ecL2d = EmoteLogic.l2dMode(ecL2d);
        ecKick = Math.max(0, Math.min(90, ecKick));
        if (chainRules == null) chainRules = new ArrayList<>();
        chainRules.removeIf(java.util.Objects::isNull);
        while (chainRules.size() > 32) chainRules.remove(chainRules.size() - 1);
        for (ChainLogic.Rule r : chainRules) {
            if (r.id == null || r.id.isBlank()) r.id = newId("t");
            if (r.from == null) r.from = ""; if (r.to == null) r.to = "";
            if (!"start".equals(r.when)) r.when = "end";
            if (!"off".equals(r.op) && !"toggle".equals(r.op)) r.op = "on";
            r.delayMs = clamp(r.delayMs, 0, 60000); r.value = clamp(r.value, -1, 255);
        }
        hudPos.values().removeIf(java.util.Objects::isNull);
        if (!hudPos.containsKey("list")) hudPos.put("list", Hud.defaultPos("list", this));
        if (!hudPos.containsKey("toasts")) hudPos.put("toasts", Hud.defaultPos("toasts", this));
        for (HudPos hp : hudPos.values()) { if (!java.util.Arrays.asList(Hud.ANCHORS).contains(hp.anchor)) hp.anchor = "tl"; hp.scale = Math.max(50, Math.min(300, hp.scale <= 0 ? 100 : hp.scale)); }
        toastSecs = Math.max(1, Math.min(15, toastSecs <= 0 ? 4 : toastSecs));
        toastOpacity = Math.max(0, Math.min(100, toastOpacity));
        if (haPhys == null) haPhys = new java.util.HashMap<>();
        for (var e : haPhys.entrySet()) e.setValue(HangPhysics.upgrade(e.getValue())); // 1.5.9: 8 -> 11 values (gravity, raise limit, invert)
        haPhys.entrySet().removeIf(e -> e.getKey() == null || !(e.getKey().endsWith("|main") || e.getKey().endsWith("|off")) || e.getValue() == null || e.getValue().length != PendulumMath.LEN);
        if (haSame == null) haSame = new java.util.HashSet<>();
        haSame.removeIf(java.util.Objects::isNull);
        haFlat.removeIf(k -> k == null || java.util.Arrays.asList(HandAnim.CATS).indexOf(k) < 0);
        if (haGroups == null) haGroups = new ArrayList<>();
        haGroups.removeIf(java.util.Objects::isNull);
        // 1.7.0 Física Live2D
        if (l2dChains == null) l2dChains = new ArrayList<>();
        l2dChains.removeIf(java.util.Objects::isNull);
        java.util.Set<String> cids = new java.util.HashSet<>();
        for (Live2dMath.Chain ch : l2dChains) {
            if (ch.id == null || ch.id.isBlank() || !cids.add(ch.id)) { ch.id = newId("c"); cids.add(ch.id); }
            if (ch.name == null) ch.name = "";
            if (ch.links == null) ch.links = new ArrayList<>();
            ch.links.removeIf(java.util.Objects::isNull);
            while (ch.links.size() > 8) ch.links.remove(ch.links.size() - 1);
            ch.off = fixConds(ch.off); if (!"and".equals(ch.offCombine)) ch.offCombine = "or";
            ch.fadeMs = Math.max(0, Math.min(3000, ch.fadeMs));
            for (Live2dMath.Link l : ch.links) {
                l.off = fixConds(l.off); if (!"and".equals(l.offCombine)) l.offCombine = "or";
                if (l.anim == null) l.anim = "";
                Live2dMath.migrateLink(l);
                l.inputs.removeIf(in -> in == null || !java.util.Arrays.asList(Live2dMath.INPUTS).contains(in.src));
                for (Live2dMath.Input in : l.inputs) { in.weight = Math.max(0, Math.min(300, in.weight)); in.hold = Math.max(0, Math.min(100, in.hold));
                    if (in.ref == null) in.ref = ""; if (!java.util.Arrays.asList(Live2dMath.REF_MODES).contains(in.refMode)) in.refMode = "rate";
                    in.refHz10 = Math.max(1, Math.min(200, in.refHz10 <= 0 ? 40 : in.refHz10)); }
                l.limLo = Math.max(-180, Math.min(180, l.limLo)); l.limHi = Math.max(-180, Math.min(180, l.limHi));
                while (l.inputs.size() > 4) l.inputs.remove(l.inputs.size() - 1);
                l.len = Math.max(10, Math.min(400, l.len)); l.weight = Math.max(10, Math.min(500, l.weight));
                l.damp = Math.max(0, Math.min(100, l.damp)); l.gain = Math.max(0, Math.min(300, l.gain));
                l.min = Math.max(-180, Math.min(180, l.min)); l.max = Math.max(-180, Math.min(180, l.max));
            }
        }
        while (l2dChains.size() > 16) l2dChains.remove(l2dChains.size() - 1);
        if (l2dModes == null) l2dModes = new java.util.HashMap<>();
        l2dModes.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || !java.util.Arrays.asList(Live2dMath.MODES).contains(e.getValue()) || "sum".equals(e.getValue()));
        java.util.Set<String> gids = new java.util.HashSet<>();
        for (ItemGroup g : haGroups) {
            if (g.id == null || g.id.isBlank() || !gids.add(g.id)) { g.id = newId("g"); gids.add(g.id); }
            if (g.name == null) g.name = ""; if (g.mod == null) g.mod = ""; if (g.value == null) g.value = "";
            if (!java.util.Arrays.asList("query", "pattern", "class", "tag", "list").contains(g.kind)) g.kind = "pattern";
            if (g.items == null) g.items = new ArrayList<>();
            if (g.notq == null) g.notq = "";
            if (g.exclude == null) g.exclude = new ArrayList<>();
            g.exclude.removeIf(i -> i == null || i.isBlank());
            if (g.color == 0) g.color = 0xFFD23B;
            g.items.removeIf(i -> i == null || i.isBlank());
        }
        if (effRules == null) effRules = new ArrayList<>();
        effRules.removeIf(java.util.Objects::isNull);
        java.util.Set<String> rids = new java.util.HashSet<>();
        for (EffectRule r : effRules) {
            if (r.id == null || r.id.isBlank() || !rids.add(r.id)) { r.id = newId("r"); rids.add(r.id); }
            if (r.name == null) r.name = "";
            if (r.effect == null) r.effect = "";
            r.minAmp = clamp(r.minAmp, 0, 255);
            if (!"none".equals(r.onEnd)) r.onEnd = "restore";
            r.delayStart = clamp(r.delayStart, 0, 10000); r.delayEnd = clamp(r.delayEnd, 0, 10000);
            if (r.actions == null) r.actions = new ArrayList<>();
            r.actions.removeIf(a -> a == null || a.target == null || a.target.isBlank());
            for (EffectAction a : r.actions) if (!java.util.Arrays.asList("on", "off", "toggle").contains(a.op)) a.op = "off";
            if (r.conds == null) r.conds = new ArrayList<>();
            r.conds.removeIf(c -> c == null || !java.util.Arrays.asList(TriggerLogic.TYPES).contains(c.type));
            for (TriggerLogic.Cond c : r.conds) { if (c.value == null) c.value = ""; c.num = clamp(c.num, 0, 255); }
            if (!"or".equals(r.combine)) r.combine = "and";
            for (TriggerLogic.Cond c : r.conds) if ("effect".equals(c.type) && !c.value.isBlank()) { r.effect = c.value; r.minAmp = c.num; break; } // icon / old readers
            if (r.transAnim == null) r.transAnim = "";
            r.transMs = clamp(r.transMs <= 0 ? 1000 : r.transMs, 100, 10000);
            fixSound(r.sound);
        }
        for (Slot sl : allSlots()) fixSound(sl.sound);
        if (haArm == null) haArm = new java.util.HashMap<>();
        haArm.entrySet().removeIf(e -> !("main".equals(e.getKey()) || "off".equals(e.getKey())) || e.getValue() == null || e.getValue().length != 7);
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
    public static String newId(String prefix) { return prefix + Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, 36) + Integer.toString((int) (Math.random() * 1296), 36); }
    public ItemGroup group(String id) { if (haGroups != null) for (ItemGroup g : haGroups) if (g.id.equals(id)) return g; return null; }
    /** group button by its shown name (or animation field) */
    public Slot groupSlot(String name) {
        for (Slot s : allSlots()) if (s.isGroup() && (s.display().equals(name))) return s;
        for (Slot s : allSlots()) if (s.isGroup() && name.equals(s.anim)) return s;
        return null;
    }
    static int clampI(int v, int a, int b) { return clamp(v, a, b); }
    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    public String profileKey() { return myKey != null ? myKey : currentKey == null ? "default" : currentKey; }

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
            Path f = file(profileKey());
            Files.createDirectories(f.getParent());
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
        if (s.isEc()) { EmoteBridge.click(get(), s); return; }
        if (RESET.equals(s.anim)) CpmBridge.resetAll();
        else CpmBridge.play(s.anim, s.value);
    }
}
