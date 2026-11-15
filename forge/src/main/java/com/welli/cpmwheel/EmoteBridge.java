package com.welli.cpmwheel;

import java.util.ArrayList;
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

    public record Emote(UUID id, String name, Object anim) {}

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
                out.add(new Emote(id, n, a));
            }
            out.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
            cache = List.copyOf(out);
        } catch (Throwable t) { fail(t); cache = List.of(); }
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
