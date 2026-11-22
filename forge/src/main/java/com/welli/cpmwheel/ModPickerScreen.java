package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 1.5.8: pick a mod (namespace) from a searchable list, first entry "todos os mods". */
public class ModPickerScreen extends Screen {
    private final Screen parent;
    private final String current;
    private final Consumer<String> done;
    private final Map<String, Integer> mods = new TreeMap<>();
    private final List<String> shown = new ArrayList<>();
    private String query = "";
    private int page = 0;
    private EditBox search;

    public ModPickerScreen(Screen parent, String current, Consumer<String> done) {
        super(Component.translatable("cpmwheel.grp.modpick")); this.parent = parent; this.current = current == null ? "" : current; this.done = done;
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) mods.merge(rl.getNamespace(), 1, Integer::sum);
    }

    static String modName(String ns) {
        try { return net.minecraftforge.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns); } catch (Throwable t) { return ns; }
    }

    @Override
    protected void init() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) shown.add("");
        for (String ns : mods.keySet()) if (q.isEmpty() || ns.contains(q) || modName(ns).toLowerCase(Locale.ROOT).contains(q)) shown.add(ns);
        int x = 8, y = 26, w = Math.min(300, width - 16);
        if (search == null) {
            search = new EditBox(font, x, y, 200, 18, Component.literal(""));
            search.setHint(Component.translatable("cpmwheel.grp.modpick.search"));
            search.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); } });
        }
        search.setX(x); search.setY(y);
        addRenderableWidget(search);
        setInitialFocus(search);
        y += 24;
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            String ns = shown.get(i);
            Component l = ns.isEmpty() ? Component.translatable("cpmwheel.grp.mod.all")
                    : Component.literal(modName(ns) + "  \u00A77" + ns + " (" + mods.get(ns) + ")");
            Button b = Button.builder(l, bt -> { done.accept(ns); minecraft.setScreen(parent); }).bounds(x, y, w, 18).build();
            b.active = !ns.equals(current);
            addRenderableWidget(b); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xC0050A12); g.drawString(font, title, 8, 10, 0x2EE6FF); super.render(g, mx, my, pt); }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
