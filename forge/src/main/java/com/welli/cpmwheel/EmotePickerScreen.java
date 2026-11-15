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
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.8.1 / 1.8.2: pick one of the client's Emotecraft emotes (optionally "any emote"); pick gets null for "any".
 * 1.8.2: every row shows the emote icon; clicking a row shows its details (big icon, name, author, description,
 * length, loop, format, song, server, NSFW) on the right; double click or "Escolher" uses it.
 */
public class EmotePickerScreen extends Screen {
    private final Screen parent;
    private final boolean withAny;
    private final Consumer<EmoteBridge.Emote> pick;
    private final List<EmoteBridge.Emote> shown = new ArrayList<>();
    private final List<EmoteBridge.Emote> rowEmote = new ArrayList<>();
    private final List<int[]> rowAt = new ArrayList<>();
    private String query = "";
    private int page = 0;
    private EmoteBridge.Emote sel = null;
    private EmoteBridge.Info selInfo = null;
    private long lastClick = 0;
    private int listW = 200;

    public EmotePickerScreen(Screen parent, boolean withAny, Consumer<EmoteBridge.Emote> pick) {
        super(Component.translatable("cpmwheel.ec.pick.title")); this.parent = parent; this.withAny = withAny; this.pick = pick;
    }

    private void choose(EmoteBridge.Emote e) { pick.accept(e); minecraft.setScreen(parent); }

    @Override
    protected void init() {
        shown.clear(); rowEmote.clear(); rowAt.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (EmoteBridge.Emote e : EmoteBridge.list()) if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) shown.add(e);
        listW = Math.max(140, Math.min(240, (width - 24) / 2));
        int x = 8, y = 26;
        EditBox s = new EditBox(font, x, y, listW, 18, Component.literal(""));
        s.setHint(Component.translatable("cpmwheel.eff.tpick.search"));
        s.setValue(query);
        s.setResponder(v -> { if (!v.equals(query)) { query = v; page = 0; rebuildWidgets(); setFocused(s); s.setFocused(true); } });
        addRenderableWidget(s);
        y += 24;
        if (withAny) {
            addRenderableWidget(Button.builder(Component.literal("\u2605 ").append(Component.translatable("cpmwheel.ec.any")), b -> choose(null)).bounds(x, y, listW, 20).build());
            y += 24;
        }
        int per = Math.max(3, (height - 34 - y) / 22);
        int pages = Math.max(1, (shown.size() + per - 1) / per);
        page = Math.floorMod(page, pages);
        for (int i = page * per; i < Math.min(shown.size(), (page + 1) * per); i++) {
            EmoteBridge.Emote e = shown.get(i);
            String nm = font.plainSubstrByWidth(e.name(), listW - 30);
            Button b = Button.builder(Component.literal(nm), bt -> {
                long now = System.currentTimeMillis();
                if (sel != null && sel.id().equals(e.id()) && now - lastClick < 400) { choose(e); return; } // double click
                lastClick = now; sel = e; selInfo = EmoteBridge.info(e); rebuildWidgets();
            }).bounds(x, y, listW, 20).build();
            addRenderableWidget(b);
            rowEmote.add(e); rowAt.add(new int[]{x, y});
            y += 22;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, height - 26, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 84, height - 26, 20, 20).build());
        }
        Button use = Button.builder(Component.translatable("cpmwheel.ec.info.pick"), b -> { if (sel != null) choose(sel); }).bounds(width - 214, height - 26, 100, 20).build();
        use.active = sel != null;
        addRenderableWidget(use);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width - 108, height - 26, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        int accent = Style.accent(WheelConfig.get());
        g.drawString(font, title, 8, 10, accent);
        if (!EmoteBridge.present()) g.drawString(font, Component.translatable("cpmwheel.ec.missing"), 8, 74, 0xFF8080);
        else if (EmoteBridge.list().isEmpty()) g.drawString(font, Component.translatable("cpmwheel.ec.pick.none"), 8, 74, 0x7FA8B8);
        super.render(g, mx, my, pt);
        for (int i = 0; i < rowEmote.size(); i++) { // icons on top of the row buttons
            int[] p = rowAt.get(i);
            EmoteBridge.Emote e = rowEmote.get(i);
            if (sel != null && sel.id().equals(e.id())) g.renderOutline(p[0], p[1], listW, 20, 0xFF000000 | accent);
            EmoteIcons.draw(g, e.id().toString(), p[0] + 11, p[1] + 10, 16);
        }
        details(g, listW + 20, 26, width - listW - 28);
    }

    /** right panel: big icon + information */
    private void details(GuiGraphics g, int x, int y, int w) {
        if (w < 80) return;
        g.fill(x - 4, y - 4, x + w, height - 32, 0x80101C28);
        if (sel == null || selInfo == null) {
            int ly = y + 4;
            for (FormattedCharSequence l : font.split(Component.translatable("cpmwheel.ec.info.hint"), w - 8)) { g.drawString(font, l, x + 2, ly, 0x7FA8B8); ly += 10; }
            return;
        }
        EmoteBridge.Info in = selInfo;
        int big = Math.min(64, w / 3);
        EmoteIcons.draw(g, sel.id().toString(), x + 2 + big / 2, y + 2 + big / 2, big);
        int tx = x + big + 10, tw = w - big - 14;
        int ly = y + 2;
        g.drawString(font, font.plainSubstrByWidth(in.name(), tw), tx, ly, 0xFFFFFF); ly += 12;
        g.drawString(font, font.plainSubstrByWidth(in.author().isBlank() ? Component.translatable("cpmwheel.ec.info.noauthor").getString()
                : Component.translatable("cpmwheel.ec.info.author", in.author()).getString(), tw), tx, ly, 0xA8C4D0); ly += 12;
        if (!EmoteIcons.hasOwn(sel.id().toString())) { g.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.ec.info.noicon").getString(), tw), tx, ly, 0x7FA8B8); ly += 12; }
        ly = Math.max(ly, y + big + 8);
        List<Component> facts = new ArrayList<>();
        if (in.lengthTicks() > 0) facts.add(Component.translatable("cpmwheel.ec.info.length", EmoteLogic.seconds(in.lengthTicks())));
        facts.add(in.loop() ? (in.loopTicks() > 0 ? Component.translatable("cpmwheel.ec.info.loop.span", EmoteLogic.seconds(in.loopTicks())) : Component.translatable("cpmwheel.ec.info.loop.yes"))
                : Component.translatable("cpmwheel.ec.info.loop.no"));
        if (!in.format().isBlank()) facts.add(Component.translatable("cpmwheel.ec.info.format", Component.translatable("cpmwheel.ec.fmt." + EmoteLogic.formatKey(in.format()))));
        if (in.song()) facts.add(Component.translatable("cpmwheel.ec.info.song"));
        if (in.fromServer()) facts.add(Component.translatable("cpmwheel.ec.info.server"));
        if (in.nsfw()) facts.add(Component.translatable("cpmwheel.ec.info.nsfw"));
        for (Component c : facts) { g.drawString(font, font.plainSubstrByWidth(c.getString(), w - 6), x + 2, ly, 0xC8E8F0); ly += 11; }
        ly += 4;
        if (!in.description().isBlank()) {
            int max = Math.max(1, (height - 36 - ly) / 10);
            List<FormattedCharSequence> lines = font.split(Component.literal(in.description()), w - 8);
            for (int k = 0; k < Math.min(max, lines.size()); k++) { g.drawString(font, lines.get(k), x + 2, ly, 0xDDE8EE); ly += 10; }
        } else g.drawString(font, Component.translatable("cpmwheel.ec.info.nodesc"), x + 2, ly, 0x7FA8B8);
    }

    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
