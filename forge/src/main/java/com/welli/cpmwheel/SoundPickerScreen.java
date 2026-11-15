package com.welli.cpmwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.6.0: searchable list of every sound id (vanilla, mods, resource packs); ▶ = listen, click = choose. */
public class SoundPickerScreen extends Screen {
    private final Screen parent;
    private final String current;
    private final Consumer<String> done;
    private final int vol, pitch;
    private final List<String> all = Sounds.all();
    private final List<String> shown = new ArrayList<>();
    private String query = "";
    private int page = 0;
    private EditBox search;

    public SoundPickerScreen(Screen parent, String current, int vol, int pitch, Consumer<String> done) {
        super(Component.translatable("cpmwheel.snd.pick")); this.parent = parent; this.current = current == null ? "" : current; this.vol = vol; this.pitch = pitch; this.done = done;
    }

    @Override
    protected void init() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (String id : all) {
            if (q.isEmpty()) { shown.add(id); continue; }
            boolean ok = true;
            for (String w : q.split("\\s+")) if (!id.contains(w)) { ok = false; break; }
            if (ok) shown.add(id);
        }
        int x = 8, y = 26, w = Math.min(320, width - 16);
        if (search == null) {
            search = new EditBox(font, x, y, 200, 18, Component.literal(""));
            search.setHint(Component.translatable("cpmwheel.snd.search"));
            search.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); } });
        }
        search.setX(x); search.setY(y);
        addRenderableWidget(search); setInitialFocus(search);
        y += 24;
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            String id = shown.get(i);
            addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> Sounds.play(id, vol, pitch)).bounds(x, y, 20, 18).build());
            Button b = Button.builder(Component.literal(id), bt -> { done.accept(id); minecraft.setScreen(parent); }).bounds(x + 22, y, w - 22, 18).build();
            b.active = !id.equals(current);
            addRenderableWidget(b); y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12); g.drawString(font, title, 8, 10, 0x2EE6FF);
        g.drawString(font, Component.translatable("cpmwheel.snd.count", shown.size()), 216, 31, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
