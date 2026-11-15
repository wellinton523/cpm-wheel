package com.welli.cpmwheel;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 1.8.0 optional Simple Voice Chat link, by reflection only (no compile-time dependency; nothing happens without the mod).
 * Reads de.maxhenkel.voicechat.voice.client.ClientManager.getClient().getMicThread().isTalking() for your own microphone,
 * and TalkCache.getPlayerAudioLevel(uuid) (dB, newer versions) / isTalking(uuid) as a fallback.
 * Simple Voice Chat does not keep the level of your OWN microphone in the talk cache, so for yourself the mouth follows
 * talking / silent (Live2d turns it into a flap); a real loudness would need the official plugin API (compile-time dependency).
 */
public final class VoiceBridge {
    private VoiceBridge() {}

    private static boolean tried = false, present = false, failed = false;
    private static Method getClient, getMic, micTalking, getTalk, talkUuid, levelUuid;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            getClient = cm.getMethod("getClient");
            Class<?> cv = getClient.getReturnType();
            try { getMic = cv.getMethod("getMicThread"); micTalking = getMic.getReturnType().getMethod("isTalking"); } catch (Throwable ignored) {}
            try {
                getTalk = cv.getMethod("getTalkCache");
                Class<?> tc = getTalk.getReturnType();
                try { talkUuid = tc.getMethod("isTalking", UUID.class); } catch (Throwable ignored) {}
                try { levelUuid = tc.getMethod("getPlayerAudioLevel", UUID.class); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            present = micTalking != null || talkUuid != null;
            if (present) CpmWheel.LOG.info("[CPM Wheel] Simple Voice Chat found (mic: {}, talk cache: {}, level: {})", micTalking != null, talkUuid != null, levelUuid != null);
        } catch (Throwable t) { present = false; }
    }

    public static boolean present() { init(); return present && !failed; }
    /** "none" (not installed), "ok", "error" */
    public static String status() { init(); return failed ? "error" : present ? "ok" : "none"; }

    /** 0..1 loudness when known, -1 = talking without a level, 0 = silent / unavailable */
    public static float level(UUID id) {
        if (!present()) return 0;
        try {
            Object client = getClient.invoke(null);
            if (client == null) return 0;
            boolean talking = false;
            if (getMic != null) { Object mic = getMic.invoke(client); if (mic != null) talking = Boolean.TRUE.equals(micTalking.invoke(mic)); }
            Object tc = getTalk == null ? null : getTalk.invoke(client);
            if (tc != null && levelUuid != null) {
                double db = ((Number) levelUuid.invoke(tc, id)).doubleValue();
                if (db > -120) return (float) Math.max(0, Math.min(1, (db + 60) / 50)); // −60 dB .. −10 dB
            }
            if (!talking && tc != null && talkUuid != null) talking = Boolean.TRUE.equals(talkUuid.invoke(tc, id));
            return talking ? -1 : 0;
        } catch (Throwable t) {
            failed = true;
            CpmWheel.LOG.warn("[CPM Wheel] Simple Voice Chat link disabled after an error", t);
            return 0;
        }
    }
}
