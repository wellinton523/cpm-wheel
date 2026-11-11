package com.welli.cpmwheel;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Grid picker: custom PNGs from config/cpmwheel/icons/ first, then items (searchable). */
public class IconPickerScreen extends Screen {
    private static final int CELL = 24;
    private final Screen parent;
    private final Consumer<String> result;
    private EditBox search;
    private final List<String> entries = new ArrayList<>();
    private int scroll = 0, lastFileCount = -1;
    private String lastQuery = null;

    public IconPickerScreen(Screen parent, Consumer<String> result) {
        super(Component.translatable("cpmwheel.icons.title"));
        this.parent = parent; this.result = result;
    }

    @Override
    protected void init() {
        search = new EditBox(font, 8, 22, Math.min(220, width - 16), 16, Component.literal(""));
        search.setHint(Component.translatable("cpmwheel.icons.search"));
        if (lastQuery != null) search.setValue(lastQuery);
        addRenderableWidget(search);
        setInitialFocus(search);
        int bx = 8, by = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.icons.open"), b -> Util.getPlatform().openFile(Icons.dir().toFile()))
                .bounds(bx, by, 130, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.icons.reload"), b -> { Icons.reload(); refresh(true); })
                .bounds(bx + 134, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.icons.none"), b -> pick(""))
                .bounds(bx + 228, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width - 78, by, 70, 20).build());
        refresh(true);
    }

    private void refresh(boolean force) {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> fl = Icons.files();
        if (!force && q.equals(lastQuery) && fl.size() == lastFileCount) return;
        lastQuery = q; lastFileCount = fl.size();
        entries.clear();
        for (String f : fl) if (q.isEmpty() || f.toLowerCase(Locale.ROOT).contains(q)) entries.add(Icons.FILE + f);
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            String id = rl.toString();
            if (id.equals("minecraft:air")) continue;
            if (q.isEmpty() || id.contains(q)) entries.add(id);
        }
        scroll = Math.min(scroll, Math.max(0, rows() - visibleRows()));
    }

    private int cols() { return Math.max(1, (width - 16) / CELL); }
    private int rows() { return (entries.size() + cols() - 1) / cols(); }
    private int visibleRows() { return Math.max(1, (height - 44 - 32) / CELL); }

    private int indexAt(double mx, double my) {
        if (mx < 8 || my < 44 || my >= 44 + visibleRows() * CELL) return -1;
        int c = (int) ((mx - 8) / CELL), r = (int) ((my - 44) / CELL) + scroll;
        if (c >= cols()) return -1;
        int i = r * cols() + c;
        return i < entries.size() ? i : -1;
    }

    private void pick(String icon) { result.accept(icon); onClose(); }

    @Override
    public void tick() { refresh(false); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xD0050A12);
        g.drawString(font, title, 8, 8, 0x2EE6FF);
        g.drawString(font, Component.translatable("cpmwheel.icons.folder", Icons.files().size()), 240, 26, 0x7FA8B8);
        int cols = cols(), hv = indexAt(mx, my);
        for (int r = 0; r < visibleRows(); r++) for (int c = 0; c < cols; c++) {
            int i = (r + scroll) * cols + c;
            if (i >= entries.size()) break;
            int x = 8 + c * CELL, y = 44 + r * CELL;
            boolean file = entries.get(i).startsWith(Icons.FILE);
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, i == hv ? 0xC0163848 : (file ? 0xA0182A3A : 0xA00A1420));
            if (file) g.fill(x + 1, y + CELL - 2, x + CELL - 1, y + CELL - 1, 0xFF2EE6FF);
            Icons.draw(g, entries.get(i), x + CELL / 2, y + CELL / 2, CELL - 6);
        }
        super.render(g, mx, my, pt);
        if (hv >= 0) {
            String id = entries.get(hv);
            g.pose().pushPose(); g.pose().translate(0, 0, 400);
            int w = font.width(id) + 8;
            g.fill(mx + 8, my + 8, mx + 8 + w, my + 22, 0xF0060C14);
            g.drawString(font, id, mx + 12, my + 11, 0xFFFFFF, false);
            g.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        int i = indexAt(mx, my);
        if (i >= 0) { pick(entries.get(i)); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double d) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(d) * 2, Math.max(0, rows() - visibleRows())));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
