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
        if (slot.groupMembers == null) slot.groupMembers = new ArrayList<>();
        all.addAll(CpmBridge.listAnimations().keySet());
        for (WheelConfig.Member m : slot.groupList()) if (!all.contains(m.anim)) all.add(m.anim); // keep members missing from the model
    }

    private int listX() { return width / 2 - 131; }
    private static final int W = 262, AX = 152, VX = 214;
    private static final String[] ACTIONS = {"on", "off", "toggle", "value"};
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
        search = new EditBox(font, listX(), 30, W, 16, Component.literal(""));
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
        g.fill(x - 2, top - 2, x + W + 2, bottom + 2, 0x80101C28);
        int rows = (bottom - top) / ROW;
        for (int i = 0; i < rows && i + scroll < shown.size(); i++) {
            String a = shown.get(i + scroll);
            int y = top + i * ROW;
            WheelConfig.Member m = slot.member(a);
            boolean on = m != null, hv = mx >= x && mx < x + W && my >= y && my < y + ROW;
            if (hv) g.fill(x, y, x + W, y + ROW, 0x402EE6FF);
            g.drawString(font, on ? "\u2611" : "\u2610", x + 3, y + 2, on ? 0x3BFF9D : 0x7FA8B8, false);
            g.drawString(font, font.plainSubstrByWidth(a, AX - 18), x + 16, y + 2, on ? 0xFFFFFF : 0xC8E8F0, false);
            if (on) {
                g.fill(x + AX, y + 1, x + VX - 2, y + ROW - 1, 0x60203040);
                g.drawString(font, Component.translatable("cpmwheel.group.act." + m.action), x + AX + 3, y + 2, 0xFFD23B, false);
                if ("value".equals(m.action)) {
                    g.fill(x + VX, y + 1, x + W, y + ROW - 1, 0x60203040);
                    String v = m.frame != null && !m.frame.isBlank() ? m.frame : Integer.toString(Math.max(0, m.value));
                    g.drawString(font, font.plainSubstrByWidth(v, W - VX - 4), x + VX + 3, y + 2, 0x2EE6FF, false);
                }
            }
        }
        g.drawString(font, Component.translatable("cpmwheel.group.selected", slot.groupList().size()), x, bottom + 4, 0x2EE6FF, false);
        g.drawCenteredString(font, Component.translatable("cpmwheel.group.help"), width / 2, 20, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    private WheelConfig.Slot framesOf(String anim) {
        for (WheelConfig.Slot o : WheelConfig.get().allSlots()) if (anim.equals(o.anim) && o.frames != null && !o.frames.isEmpty()) return o;
        return null;
    }

    private int rowAt(double mx, double my) {
        int x = listX();
        if (mx < x || mx >= x + W || my < listTop() || my >= listBottom()) return -1;
        int i = (int) ((my - listTop()) / ROW) + scroll;
        return i >= 0 && i < shown.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        int i = rowAt(mx, my);
        if (i < 0) return false;
        String a = shown.get(i);
        WheelConfig.Member m = slot.member(a);
        double rx = mx - listX();
        if (m != null && rx >= AX && rx < VX) { // cycle action
            int k = java.util.Arrays.asList(ACTIONS).indexOf(m.action);
            m.action = ACTIONS[Math.floorMod(k + (btn == 1 ? -1 : 1), ACTIONS.length)];
        } else if (m != null && rx >= VX && "value".equals(m.action)) { // cycle frames by name, or step value
            WheelConfig.Slot fs = framesOf(a);
            if (fs != null) {
                int k = -1; for (int f = 0; f < fs.frames.size(); f++) if (fs.frameName(f).equals(m.frame)) k = f;
                k = Math.floorMod(k + (btn == 1 ? -1 : 1), fs.frames.size());
                m.frame = fs.frameName(k);
            } else m.value = Math.max(0, Math.max(0, m.value) + (btn == 1 ? -1 : 1) * (hasShiftDown() ? 10 : 1));
        } else if (m != null) slot.groupMembers.removeIf(x -> x == null || a.equals(x.anim));
        else slot.groupMembers.add(new WheelConfig.Member(a));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int i = rowAt(mx, my);
        if (i >= 0 && mx - listX() >= VX) { // scroll on the value: +-1 (Shift 10)
            WheelConfig.Member m = slot.member(shown.get(i));
            if (m != null && "value".equals(m.action) && (m.frame == null || m.frame.isBlank())) {
                m.value = Math.max(0, Math.max(0, m.value) + (d > 0 ? 1 : -1) * (hasShiftDown() ? 10 : 1));
                return true;
            }
        }
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
