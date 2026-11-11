package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pick the toggle animations of a "Grupo" slot (checkbox list with search). */
public class GroupScreen extends Screen {
    private static final int ROW = 12;
    private final Screen parent;
    private final WheelConfig.Slot slot;
    private final List<String> all = new ArrayList<>();
    private List<String> shown = new ArrayList<>();
    private EditBox search;
    private int scroll = 0;

    public GroupScreen(Screen parent, WheelConfig.Slot slot) {
        super(Component.translatable("cpmwheel.group.title", slot.display()));
        this.parent = parent; this.slot = slot;
        if (slot.members == null) slot.members = new ArrayList<>();
        all.addAll(CpmBridge.listAnimations().keySet());
        for (String m : slot.memberList()) if (!all.contains(m)) all.add(m); // keep members missing from the model
    }

    private int listX() { return width / 2 - 120; }
    private int listTop() { return 52; }
    private int listBottom() { return height - 34; }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase();
        shown = new ArrayList<>();
        for (String a : all) if (q.isEmpty() || a.toLowerCase().contains(q)) shown.add(a);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - (listBottom() - listTop()) / ROW)));
    }

    @Override
    protected void init() {
        String q = search == null ? "" : search.getValue();
        search = new EditBox(font, listX(), 30, 240, 16, Component.literal(""));
        search.setHint(Component.translatable("cpmwheel.group.search"));
        search.setValue(q);
        search.setResponder(v -> refilter());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 80, height - 26, 160, 20).build());
        refilter();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 10, 0x2EE6FF);
        int x = listX(), top = listTop(), bottom = listBottom();
        g.fill(x - 2, top - 2, x + 242, bottom + 2, 0x80101C28);
        List<String> mem = slot.memberList();
        int rows = (bottom - top) / ROW;
        for (int i = 0; i < rows && i + scroll < shown.size(); i++) {
            String a = shown.get(i + scroll);
            int y = top + i * ROW;
            boolean on = mem.contains(a), hv = mx >= x && mx < x + 240 && my >= y && my < y + ROW;
            if (hv) g.fill(x, y, x + 240, y + ROW, 0x402EE6FF);
            g.drawString(font, on ? "\u2611" : "\u2610", x + 3, y + 2, on ? 0x3BFF9D : 0x7FA8B8, false);
            g.drawString(font, font.plainSubstrByWidth(a, 220), x + 16, y + 2, on ? 0xFFFFFF : 0xC8E8F0, false);
        }
        g.drawString(font, Component.translatable("cpmwheel.group.selected", mem.size()), x, bottom + 4, 0x2EE6FF, false);
        g.drawCenteredString(font, Component.translatable("cpmwheel.group.help"), width / 2, 20, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        int x = listX();
        if (mx >= x && mx < x + 240 && my >= listTop() && my < listBottom()) {
            int i = (int) ((my - listTop()) / ROW) + scroll;
            if (i >= 0 && i < shown.size()) {
                String a = shown.get(i);
                if (slot.members == null) slot.members = new ArrayList<>();
                if (slot.members.contains(a)) slot.members.removeIf(a::equals); else slot.members.add(a);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int max = Math.max(0, shown.size() - (listBottom() - listTop()) / ROW);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(d) * 3));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void onClose() { WheelConfig.get().save(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
