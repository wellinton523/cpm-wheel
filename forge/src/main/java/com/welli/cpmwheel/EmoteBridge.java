package com.welli.cpmwheel;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * 1.8.1 optional Emotecraft link, by reflection only (no compile-time dependency; nothing is loaded without the mod).
 * Works with Emotecraft 2.4.x (NeoForge 1.21.1) and 2.2.7 (Forge 1.20.1, through PlayerAnimator):
 * <ul>
 * <li>emote list: io.github.kosmx.emotes.main.EmoteHolder.list (UUID -> holder; holder.name / holder.emote);</li>
 * <li>play: io.github.kosmx.emotes.api.events.client.ClientEmoteAPI.playEmote(KeyframeAnimation) (public API, both);</li>
 * <li>stop: ClientEmoteAPI.stopEmote() on 2.4.x; 2.2.7 has no stopEmote in the API, so
 *     io.github.kosmx.emotes.main.network.ClientEmotePlay.clientStopLocalEmote() (also sends the stop to the server);</li>
 * <li>state: the local player implements IEmotePlayerEntity (Emotecraft mixin): isPlayingEmote() and
 *     emotecraft$getEmote() (2.4.x) / getEmote() (2.2.7) -> IEmotePlayer.getData() -> KeyframeAnimation.</li>
 * </ul>
 * Any reflection error disables the bridge for the session (logged once); the game never crashes because of it.
 */
public final class EmoteBridge {
    private EmoteBridge() {}

    public record Emote(UUID id, String name, Object anim, Object holder) {}
    /** 1.8.2 details for the picker (-1 / "" when unknown) */
    public record Info(String name, String author, String description, int lengthTicks, boolean loop, int loopTicks,
                       boolean nsfw, String format, boolean song, boolean fromServer, boolean hasIcon) {}

    private static boolean tried = false, present = false, failed = false;
    private static EmoteApi A;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            A = EmoteApi.resolve(EmoteBridge.class.getClassLoader());
            present = true;
            CpmWheel.LOG.info("[CPM Wheel] Emotecraft found (API {}.x)", A.flavor);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            present = false; // not installed
        } catch (Throwable t) {
            present = false; failed = true;
            CpmWheel.LOG.warn("[CPM Wheel] Emotecraft found but its API is not the expected one; the Emotecraft bridge is off", t);
        }
    }

    private static void fail(Throwable t) {
        if (failed) return;
        failed = true;
        CpmWheel.LOG.warn("[CPM Wheel] Emotecraft bridge disabled after an error", t);
    }

    /** the mod is installed and its API was understood */
    public static boolean present() { init(); return present && !failed; }
    /** present and turned on in the settings */
    public static boolean active(WheelConfig c) { return c != null && c.ecOn && present(); }
    /** "none" (not installed), "ok", "error" */
    public static String status() { init(); return failed ? "error" : present ? "ok" : "none"; }
    /** "2.4" / "2.2" ("" when absent) */
    public static String flavor() { init(); return present ? A.flavor : ""; }

    // ---- emote list (cached 1 s) ----
    private static List<Emote> cache = List.of();
    private static long cacheAt = 0;

    public static List<Emote> list() {
        if (!present()) return List.of();
        long now = System.currentTimeMillis();
        if (now - cacheAt < 1000) return cache;
        cacheAt = now;
        try {
            Object l = A.list.get(null);
            Collection<?> vals = l instanceof Map<?, ?> m ? m.values() : List.of();
            List<Emote> out = new ArrayList<>();
            for (Object h : vals) {
                if (h == null) continue;
                Object a = A.emote.get(h);
                String n = text(A.name.get(h));
                if (n.isBlank()) n = extraName(a);
                UUID id = (UUID) A.holderUuid.invoke(h);
                if (n.isBlank()) n = String.valueOf(id).substring(0, 8);
                out.add(new Emote(id, n, a, h));
            }
            out.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
            cache = List.copyOf(out);
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Emote e : cache) ids.add(e.id().toString());
            EmoteIcons.retain(ids); // 1.8.2: free icons of emotes that are gone
        } catch (Throwable t) { fail(t); cache = List.of(); EmoteIcons.clear(); }
        return cache;
    }

    public static Emote find(String id, String name) {
        List<Emote> l = list();
        if (id != null && !id.isBlank()) for (Emote e : l) if (e.id().toString().equals(id)) return e;
        String n = EmoteLogic.norm(name);
        if (!n.isEmpty()) for (Emote e : l) if (EmoteLogic.norm(e.name()).equals(n)) return e;
        return null;
    }

    /** Component (2.4.x, NeoForge) or Emotecraft's own Text (2.2.7) -> string */
    private static String text(Object o) {
        if (o == null) return "";
        if (o instanceof net.minecraft.network.chat.Component c) return c.getString();
        try {
            if (A.textString != null && A.text.isInstance(o)) return EmoteLogic.clean(String.valueOf(A.textString.invoke(o)));
        } catch (Throwable ignored) {}
        try { return EmoteLogic.clean(String.valueOf(o.getClass().getMethod("getString").invoke(o))); }
        catch (Throwable t) { return EmoteLogic.clean(String.valueOf(o)); }
    }
    private static String extraName(Object anim) {
        try {
            if (anim == null || A.extra == null) return "";
            Object m = A.extra.get(anim);
            Object n = m instanceof Map<?, ?> mm ? mm.get("name") : null;
            return n == null ? "" : EmoteLogic.clean(String.valueOf(n));
        } catch (Throwable t) { return ""; }
    }

    // ---- 1.8.2 icon + details ----
    private static Map<?, ?> extra(Object anim) {
        try { Object m = anim == null || A.extra == null ? null : A.extra.get(anim); return m instanceof Map<?, ?> mm ? mm : null; } catch (Throwable t) { return null; }
    }
    /** PNG bytes of the emote icon (KeyframeAnimation.extraData "iconData"), null when it has none */
    public static byte[] iconBytes(String uuid) {
        if (!present() || uuid == null) return null;
        try {
            Object l = A.list.get(null);
            Object h = l instanceof Map<?, ?> m ? m.get(UUID.fromString(uuid)) : null;
            if (h == null) return null;
            Map<?, ?> x = extra(A.emote.get(h));
            byte[] b = x == null ? null : EmoteLogic.bytes(x.get("iconData"));
            return EmoteLogic.isPng(b) ? b : null;
        } catch (IllegalArgumentException e) { return null; } // not a UUID
        catch (Throwable t) { fail(t); return null; }
    }
    private static int i(Field f, Object o) { try { return f == null || o == null ? -1 : f.getInt(o); } catch (Throwable t) { return -1; } }
    private static boolean b(Field f, Object o) { try { return f != null && o != null && f.getBoolean(o); } catch (Throwable t) { return false; } }
    private static Object o(Field f, Object o) { try { return f == null || o == null ? null : f.get(o); } catch (Throwable t) { return null; } }

    public static Info info(Emote e) {
        if (e == null || !present()) return null;
        Object a = e.anim(), h = e.holder();
        Map<?, ?> x = extra(a);
        String au = text(o(A.author, h)), de = text(o(A.description, h));
        if (au.isBlank() && x != null && x.get("author") != null) au = EmoteLogic.clean(String.valueOf(x.get("author")));
        if (de.isBlank() && x != null && x.get("description") != null) de = EmoteLogic.clean(String.valueOf(x.get("description")));
        int end = i(A.endTick, a), stop = i(A.stopTick, a), ret = i(A.returnTick, a);
        boolean loop = b(A.infinite, a);
        Object fmt = o(A.format, a);
        return new Info(e.name(), au, de, EmoteLogic.length(end, stop), loop, EmoteLogic.loopSpan(loop, ret, end), b(A.nsfw, a),
                fmt == null ? "" : String.valueOf(fmt), x != null && x.get("song") != null, o(A.fromInstance, h) != null,
                x != null && EmoteLogic.isPng(EmoteLogic.bytes(x.get("iconData"))));
    }

    // ---- local player state (cached per 40 ms) ----
    private static boolean playing = false;
    private static String playingName = "";
    private static long stateAt = 0;

    private static void poll() {
        long now = System.currentTimeMillis();
        if (now - stateAt < 40) return;
        stateAt = now;
        playing = false; playingName = "";
        if (!present()) return;
        try {
            Object p = Minecraft.getInstance().player;
            if (p == null || !A.playerEnt.isInstance(p)) return;
            playing = Boolean.TRUE.equals(A.isPlaying.invoke(p));
            if (!playing) return;
            Object ep = A.getEmote.invoke(p);
            Object a = ep == null ? null : A.getData.invoke(ep);
            if (a == null) return;
            Object id = A.kaUuid.invoke(a);
            Object l = A.list.get(null);
            Object h = l instanceof Map<?, ?> m ? m.get(id) : null;
            String n = h == null ? "" : text(A.name.get(h));
            playingName = n.isBlank() ? extraName(a) : n;
        } catch (Throwable t) { fail(t); playing = false; playingName = ""; }
    }

    /** an Emotecraft emote is playing on the local player */
    public static boolean playing() { poll(); return playing; }
    public static String playingName() { poll(); return playingName; }
    /** "*" / "" = any emote, else that emote name */
    public static boolean matches(String want) { poll(); return EmoteLogic.matches(want, playing, playingName); }

    public static boolean play(Emote e) {
        if (e == null || !present()) return false;
        try { boolean ok = Boolean.TRUE.equals(A.play.invoke(null, e.anim())); stateAt = 0; return ok; }
        catch (Throwable t) { fail(t); return false; }
    }
    public static boolean stop() {
        if (!present()) return false;
        try { Object r = A.stop.invoke(null); stateAt = 0; return !(r instanceof Boolean b) || b; }
        catch (Throwable t) { fail(t); return false; }
    }

    /** wheel button "Emote do Emotecraft" */
    public static void click(WheelConfig cfg, WheelConfig.Slot s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!active(cfg)) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(present() ? "cpmwheel.ec.offmsg" : "cpmwheel.ec.missing"), true);
            return;
        }
        String what = EmoteLogic.click(s.ecAction, s.ecName, playing(), playingName());
        switch (what) {
            case "stop" -> stop();
            case "play" -> {
                Emote e = find(s.ecId, s.ecName);
                if (e == null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cpmwheel.ec.notfound", s.ecName), true);
                else if (!play(e)) mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cpmwheel.ec.refused", e.name()), true);
            }
            default -> {}
        }
        Hud.invalidate();
    }
}
