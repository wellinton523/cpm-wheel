package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.8.2 Emotecraft emote icons: the PNG in the emote ("iconData") is decoded once into our own DynamicTexture
 * (cpmwheel:ec_icon/N), cached per emote UUID (LRU, at most MAX), and released from the TextureManager when the emote
 * disappears, the cache overflows, the bridge fails or icons are reloaded. Emotes without an icon (or any error) use
 * the generic icon (assets/cpmwheel/textures/gui/ec_generic.png). Render thread only.
 */
public final class EmoteIcons {
    private EmoteIcons() {}
    private record Tex(ResourceLocation rl, int w, int h, DynamicTexture tex) {}
    private static final int MAX = 96;
    private static final LinkedHashMap<String, Tex> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /** emotes known to have no usable icon (retried after retain / clear) */
    private static final Set<String> NONE = new HashSet<>();
    private static int counter = 0;
    public static final ResourceLocation GENERIC = new ResourceLocation(CpmWheel.MODID, "textures/gui/ec_generic.png");

    private static void release(Tex t) {
        try { Minecraft.getInstance().getTextureManager().release(t.rl()); } catch (Throwable ignored) {} // release() also closes the texture
    }

    private static Tex get(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        Tex t = CACHE.get(uuid);
        if (t != null || NONE.contains(uuid)) return t;
        byte[] png = EmoteBridge.iconBytes(uuid);
        if (png == null) { NONE.add(uuid); return null; }
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture dt = new DynamicTexture(img);
            ResourceLocation rl = new ResourceLocation(CpmWheel.MODID, "ec_icon/" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, dt);
            t = new Tex(rl, img.getWidth(), img.getHeight(), dt);
            CACHE.put(uuid, t);
            while (CACHE.size() > MAX) { // least recently drawn goes first
                Iterator<Map.Entry<String, Tex>> it = CACHE.entrySet().iterator();
                release(it.next().getValue()); it.remove();
            }
            return t;
        } catch (Throwable e) {
            CpmWheel.LOG.debug("[CPM Wheel] Emotecraft icon of {} could not be read: {}", uuid, e.toString());
            NONE.add(uuid);
            return null;
        }
    }

    /** the emote has its own icon (false = the generic one is drawn) */
    public static boolean hasOwn(String uuid) { return get(uuid) != null; }
    public static int cached() { return CACHE.size(); }

    /** draws the emote icon (or the generic one) centered at (cx, cy), fitting size x size */
    public static void draw(GuiGraphics g, String uuid, int cx, int cy, int size) {
        Tex t = get(uuid);
        ResourceLocation rl = t == null ? GENERIC : t.rl();
        int tw = t == null ? 32 : t.w(), th = t == null ? 32 : t.h();
        float sc = Math.min(size / (float) tw, size / (float) th);
        int w = Math.max(1, Math.round(tw * sc)), h = Math.max(1, Math.round(th * sc));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(rl, cx - w / 2, cy - h / 2, w, h, 0f, 0f, tw, th, tw, th);
        RenderSystem.disableBlend();
    }

    /** keep only these emotes' icons (the others are released) */
    public static void retain(Set<String> uuids) {
        for (Iterator<Map.Entry<String, Tex>> it = CACHE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!uuids.contains(e.getKey())) { release(e.getValue()); it.remove(); }
        }
        NONE.retainAll(uuids);
    }

    public static void clear() {
        for (Tex t : CACHE.values()) release(t);
        CACHE.clear(); NONE.clear();
    }
}
