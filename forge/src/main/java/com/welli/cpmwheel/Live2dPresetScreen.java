package com.welli.cpmwheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 1.8.0 "Exportar / importar" physics presets: files in config/cpmwheel/l2d/*.json and text through the clipboard
 * (CPMW-L2D1:<Base64 JSON>, plain JSON is accepted too). Importing always ADDS chains / generators with new ids.
 */
public class Live2dPresetScreen extends MiniScreen {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static class Pack {
        public String format = "cpmwheel-l2d";
        public int version = 1;
        public List<Live2dMath.Chain> chains = new ArrayList<>();
        public Map<String, String> modes = new HashMap<>();
        public List<Live2dMath.Gen> gens = new ArrayList<>();
    }

    private final String chainId;
    private EditBox name;
    private String status = "";
    private int page = 0;

    public Live2dPresetScreen(Screen parent, WheelConfig cfg, String chainId) { super("cpmwheel.l2d.io.title", parent, cfg); this.chainId = chainId; }

    static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("cpmwheel").resolve("l2d"); }
    static List<String> names() {
        List<String> l = new ArrayList<>();
        try (var st = Files.list(dir())) { st.forEach(f -> { String n = f.getFileName().toString(); if (n.endsWith(".json")) l.add(n.substring(0, n.length() - 5)); }); } catch (Exception ignored) {}
        Collections.sort(l);
        return l;
    }

    private Pack pack(boolean all) {
        Pack p = new Pack();
        for (Live2dMath.Chain c : cfg.l2dChains) if (all || c.id.equals(chainId)) {
            p.chains.add(c);
            for (Live2dMath.Link l : c.links) if (cfg.l2dModes.containsKey(l.anim)) p.modes.put(l.anim, cfg.l2dModes.get(l.anim));
        }
        if (all) p.gens.addAll(cfg.l2dGens);
        return p;
    }

    /** adds the pack's chains / generators with new ids; returns how many chains were added (-1 = not a preset) */
    int importPack(String json) {
        Pack p;
        try { p = GSON.fromJson(json, Pack.class); } catch (Exception e) { return -1; }
        if (p == null || !"cpmwheel-l2d".equals(p.format)) return -1;
        int n = 0;
        if (p.chains != null) for (Live2dMath.Chain c : p.chains) {
            if (c == null || cfg.l2dChains.size() >= 16) continue;
            Live2dMath.Chain k = Live2dMath.copy(c, WheelConfig.newId("c"));
            cfg.l2dChains.add(k); n++;
        }
        if (p.modes != null) for (var e : p.modes.entrySet()) if (e.getKey() != null && e.getValue() != null) cfg.l2dModes.putIfAbsent(e.getKey(), e.getValue());
        if (p.gens != null) for (Live2dMath.Gen g : p.gens) {
            if (g == null || cfg.l2dGens.size() >= 8) continue;
            g.id = WheelConfig.newId("g"); cfg.l2dGens.add(g);
        }
        cfg.fixup(); Live2d.reset();
        return n;
    }

    @Override
    protected void init() {
        int x = 8, y = 26, w = 150;
        name = new EditBox(font, x + 1, y + 1, w - 2, 18, Component.literal(""));
        name.setMaxLength(32); name.setHint(Component.translatable("cpmwheel.l2d.io.name"));
        addRenderableWidget(name); y += 22;
        btn("cpmwheel.l2d.io.save.one", b -> save(false), x, y, w, "cpmwheel.l2d.io.save.help"); y += 22;
        btn("cpmwheel.l2d.io.save.all", b -> save(true), x, y, w, "cpmwheel.l2d.io.save.help"); y += 26;
        btn("cpmwheel.l2d.io.copy.one", b -> copy(false), x, y, w, "cpmwheel.l2d.io.copy.help"); y += 22;
        btn("cpmwheel.l2d.io.copy.all", b -> copy(true), x, y, w, "cpmwheel.l2d.io.copy.help"); y += 26;
        btn("cpmwheel.l2d.io.paste", b -> {
            String js = Live2dMath.unwrapPreset(minecraft.keyboardHandler.getClipboard());
            int n = js == null ? -1 : importPack(js);
            status = n < 0 ? Component.translatable("cpmwheel.l2d.io.bad").getString() : Component.translatable("cpmwheel.l2d.io.added", n).getString();
        }, x, y, w, "cpmwheel.l2d.io.paste.help"); y += 22;

        int x2 = 166; y = 26;
        List<String> files = names();
        int per = Math.max(3, (height - 60 - y) / 20), pages = Math.max(1, (files.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(files.size(), (page + 1) * per); i++) {
            final String f = files.get(i);
            btn(Component.literal("\u2B07 " + f), b -> {
                int n;
                try { n = importPack(Files.readString(dir().resolve(f + ".json"), StandardCharsets.UTF_8)); } catch (Exception e) { n = -1; }
                status = n < 0 ? Component.translatable("cpmwheel.l2d.io.bad").getString() : Component.translatable("cpmwheel.l2d.io.added", n).getString();
            }, x2, y, 180, 18, "cpmwheel.l2d.io.load.help");
            y += 20;
        }
        if (pages > 1) {
            btn(Component.literal("<"), b -> { page--; rebuildWidgets(); }, x2, height - 50, 20, 20, null);
            btn(Component.literal(">"), b -> { page++; rebuildWidgets(); }, x2 + 160, height - 50, 20, 20, null);
        }
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    private void save(boolean all) {
        String n = ProfileLogic.clean(name.getValue());
        if (n.isEmpty()) { status = Component.translatable("cpmwheel.l2d.io.noname").getString(); return; }
        Pack p = pack(all);
        if (p.chains.isEmpty() && p.gens.isEmpty()) { status = Component.translatable("cpmwheel.l2d.io.empty").getString(); return; }
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(n + ".json"), GSON.toJson(p), StandardCharsets.UTF_8);
            status = "\u2714 config/cpmwheel/l2d/" + n + ".json";
        } catch (Exception e) { status = "\u2716 " + e.getMessage(); }
        rebuildWidgets();
    }

    private void copy(boolean all) {
        Pack p = pack(all);
        if (p.chains.isEmpty() && p.gens.isEmpty()) { status = Component.translatable("cpmwheel.l2d.io.empty").getString(); return; }
        minecraft.keyboardHandler.setClipboard(Live2dMath.wrapPreset(new Gson().toJson(p)));
        status = Component.translatable("cpmwheel.l2d.io.copied").getString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, Component.translatable("cpmwheel.l2d.io.files"), 166, 14, 0x7FA8B8);
        if (names().isEmpty()) g.drawString(font, Component.translatable("cpmwheel.l2d.io.nofiles"), 166, 30, 0x7FA8B8);
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, width - 16), 8, height - 40, 0xFFC04A);
    }
}
