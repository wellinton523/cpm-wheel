package com.welli.cpmwheel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 1.8.1 Emotecraft API handles found by reflection (no Minecraft imports, so tools/EmoteApiCheck.java can resolve them
 * offline against the real Emotecraft 2.4.x / 2.2.7 jars). Used by EmoteBridge.
 */
public final class EmoteApi {
    public Class<?> playerEnt, text;
    public Method isPlaying, getEmote, getData, play, stop, kaUuid, holderUuid, textString;
    public Field list, name, emote, extra;
    /** 1.8.2 optional details (null when a version lacks them): holder author / description / fromInstance (server emote),
     *  KeyframeAnimation beginTick / endTick / stopTick / isInfinite / returnToTick / nsfw / animationFormat */
    public Field author, description, fromInstance, beginTick, endTick, stopTick, infinite, returnTick, nsfw, format;
    /** "2.4" (ClientEmoteAPI.stopEmote) or "2.2" (ClientEmotePlay.clientStopLocalEmote) */
    public String flavor = "";

    private static Field opt(Class<?> k, String n) { try { return k.getField(n); } catch (Throwable t) { return null; } }

    private static Class<?> c(ClassLoader cl, String n) throws ClassNotFoundException { return Class.forName(n, false, cl); }

    /** throws ClassNotFoundException when Emotecraft is not installed, another exception when its API changed */
    public static EmoteApi resolve(ClassLoader cl) throws Exception {
        EmoteApi a = new EmoteApi();
        Class<?> api = c(cl, "io.github.kosmx.emotes.api.events.client.ClientEmoteAPI");
        Class<?> ka = c(cl, "dev.kosmx.playerAnim.core.data.KeyframeAnimation");
        Class<?> holder = c(cl, "io.github.kosmx.emotes.main.EmoteHolder");
        a.playerEnt = c(cl, "io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity");
        Class<?> emPlayer = c(cl, "io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer");
        a.play = api.getMethod("playEmote", ka);
        try { a.stop = api.getMethod("stopEmote"); a.flavor = "2.4"; }
        catch (NoSuchMethodException e) {
            a.stop = c(cl, "io.github.kosmx.emotes.main.network.ClientEmotePlay").getMethod("clientStopLocalEmote"); a.flavor = "2.2";
        }
        if (!Modifier.isStatic(a.play.getModifiers()) || !Modifier.isStatic(a.stop.getModifiers())) throw new IllegalStateException("unexpected Emotecraft API shape");
        a.isPlaying = a.playerEnt.getMethod("isPlayingEmote");
        try { a.getEmote = a.playerEnt.getMethod("emotecraft$getEmote"); } catch (NoSuchMethodException e) { a.getEmote = a.playerEnt.getMethod("getEmote"); }
        a.getData = emPlayer.getMethod("getData");
        a.kaUuid = ka.getMethod("getUuid");
        try { a.extra = ka.getField("extraData"); } catch (Throwable ignored) {}
        a.list = holder.getField("list");
        a.name = holder.getField("name");
        a.emote = holder.getField("emote");
        a.holderUuid = holder.getMethod("getUuid");
        a.author = opt(holder, "author"); a.description = opt(holder, "description"); a.fromInstance = opt(holder, "fromInstance");
        a.beginTick = opt(ka, "beginTick"); a.endTick = opt(ka, "endTick"); a.stopTick = opt(ka, "stopTick"); a.infinite = opt(ka, "isInfinite");
        a.returnTick = opt(ka, "returnToTick"); a.nsfw = opt(ka, "nsfw"); a.format = opt(ka, "animationFormat");
        try { a.text = c(cl, "io.github.kosmx.emotes.executor.dataTypes.Text"); a.textString = a.text.getMethod("getString"); } catch (Throwable ignored) {} // 2.2.x only
        return a;
    }
}
