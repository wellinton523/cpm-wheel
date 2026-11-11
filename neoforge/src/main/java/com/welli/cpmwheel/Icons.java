package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/** Slot icons: item ids ("minecraft:diamond") or user PNGs ("file:name.png" from config/cpmwheel/icons/). */
public final class Icons {
    public static final String FILE = "file:";
    private record Tex(ResourceLocation rl, int w, int h, long mtime, DynamicTexture tex) {}
    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static List<String> files = null;
    private static long lastScan = 0;
    private static int counter = 0;

    private Icons() {}

    public static Path dir() {
        Path d = FMLPaths.CONFIGDIR.get().resolve("cpmwheel").resolve("icons");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    /** PNG file names in the icons folder (rescanned at most once per second = hot reload). */
    public static List<String> files() {
        long now = System.currentTimeMillis();
        if (files == null || now - lastScan > 1000) {
            lastScan = now;
            List<String> l = new ArrayList<>();
            try (var s = Files.list(dir())) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                 .forEach(p -> l.add(p.getFileName().toString()));
            } catch (Exception ignored) {}
            Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
            files = l;
        }
        return files;
    }

    public static void reload() {
        for (Tex t : CACHE.values()) release(t);
        CACHE.clear(); FAILED.clear(); files = null;
    }

    private static void release(Tex t) {
        try { Minecraft.getInstance().getTextureManager().release(t.rl()); } catch (Throwable ignored) {}
    }

    private static Tex custom(String name) {
        Path p = dir().resolve(name).normalize();
        if (!p.startsWith(dir())) return null;
        long mt;
        try { mt = Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return null; }
        Tex t = CACHE.get(name);
        if (t != null && t.mtime() == mt) return t;
        if (t == null && FAILED.contains(name + "@" + mt)) return null;
        if (t != null) { release(t); CACHE.remove(name); }
        try (InputStream in = Files.newInputStream(p)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture dt = new DynamicTexture(img);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(CpmWheel.MODID, "icons/custom_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(rl, dt);
            t = new Tex(rl, img.getWidth(), img.getHeight(), mt, dt);
            CACHE.put(name, t);
            return t;
        } catch (Exception e) {
            CpmWheel.LOG.warn("[CPM Wheel] could not load icon {}: {}", name, e.toString());
            FAILED.add(name + "@" + mt);
            return null;
        }
    }

    public static ItemStack item(String icon) {
        if (icon == null || icon.isBlank() || icon.startsWith(FILE)) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(icon.contains(":") ? icon : "minecraft:" + icon);
        if (rl == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static boolean has(String icon) {
        if (icon == null || icon.isBlank()) return false;
        if (icon.startsWith(FILE)) return custom(icon.substring(FILE.length())) != null;
        return !item(icon).isEmpty();
    }

    /** Draws icon centered at (cx, cy) fitting in a size x size box. */
    public static void draw(GuiGraphics g, String icon, int cx, int cy, int size) {
        if (icon == null || icon.isBlank()) return;
        if (icon.startsWith(FILE)) {
            Tex t = custom(icon.substring(FILE.length()));
            if (t == null) return;
            float sc = Math.min(size / (float) t.w(), size / (float) t.h());
            int w = Math.max(1, Math.round(t.w() * sc)), h = Math.max(1, Math.round(t.h() * sc));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(t.rl(), cx - w / 2, cy - h / 2, w, h, 0f, 0f, t.w(), t.h(), t.w(), t.h());
            RenderSystem.disableBlend();
        } else {
            ItemStack st = item(icon);
            if (st.isEmpty()) return;
            float sc = size / 16f;
            g.pose().pushPose();
            g.pose().translate(cx - 8 * sc, cy - 8 * sc, 0);
            g.pose().scale(sc, sc, 1);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
        }
    }
}
