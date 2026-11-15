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

/** 1.8.1: pick one of the client's Emotecraft emotes (optionally "any emote"). pick gets null for "any". */
public class EmotePickerScreen extends Screen {
    private final Screen parent;
    private final boolean withAny;
    private final Consumer<EmoteBridge.Emote> pick;
    private final List<EmoteBridge.Emote> shown = new ArrayList<>();
    private String query = "";
    private int page = 0;

    public EmotePickerScreen(Screen parent, boolean withAny, Consumer<EmoteBridge.Emote> pick) {
        super(Component.translatable("cpmwheel.ec.pick.title")); this.parent = parent; this.withAny = withAny; this.pick = pick;
    }

    @Override
    protected void init() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (EmoteBridge.Emote e : EmoteBridge.list()) if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) shown.add(e);
        int x = 8, y = 26, w = Math.min(320, width - 16);
        EditBox s = new EditBox(font, x, y, 200, 18, Component.literal(""));
        s.setHint(Component.translatable("cpmwheel.eff.tpick.search"));
        s.setValue(query);
        s.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(s); s.setFocused(true); } });
        addRenderableWidget(s);
        y += 24;
        if (withAny) {
            addRenderableWidget(Button.builder(Component.literal("\u2605 ").append(Component.translatable("cpmwheel.ec.any")), b -> { pick.accept(null); minecraft.setScreen(parent); })
                    .bounds(x, y, w, 18).build());
            y += 22;
        }
        int per = Math.max(3, (height - 32 - y) / 20);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            EmoteBridge.Emote e = shown.get(i);
            addRenderableWidget(Button.builder(Component.literal(e.name()), b -> { pick.accept(e); minecraft.setScreen(parent); }).bounds(x, y, w, 18).build());
            y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, Style.accent(WheelConfig.get()));
        if (!EmoteBridge.present()) g.drawString(font, Component.translatable("cpmwheel.ec.missing"), 8, 74, 0xFF8080);
        else if (EmoteBridge.list().isEmpty()) g.drawString(font, Component.translatable("cpmwheel.ec.pick.none"), 8, 74, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
