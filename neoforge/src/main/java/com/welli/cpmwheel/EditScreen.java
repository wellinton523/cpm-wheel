package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** In-game organizer: drag animations from the list onto wheel slots, edit label/icon/color/value. */
public class EditScreen extends Screen {
    private static final int LIST_W = 150, ROW = 12;
    private final Screen parent;
    private WheelConfig cfg;
    private int page = 0, selected = -1, scroll = 0;
    private List<String> anims = new ArrayList<>();
    private String dragAnim;          // from list
    private int dragSlot = -1;        // from wheel
    private double dragX, dragY;
    private EditBox label, icon, value, pageName, custom;

    public EditScreen(Screen parent) {
        super(Component.translatable("cpmwheel.edit.title"));
        this.parent = parent;
    }

    private final List<WheelConfig.Slot> path = new ArrayList<>();
    private WheelConfig.Page cur() { return cfg.pages.get(page); }
    private List<WheelConfig.Slot> slots() { return path.isEmpty() ? cur().slots : path.get(path.size() - 1).children; }
    private WheelConfig.Slot sel() { return selected >= 0 && selected < slots().size() ? slots().get(selected) : null; }

    @Override
    protected void init() {
        if (cfg == null) {
            cfg = WheelConfig.get();
            page = Math.max(0, Math.min(cfg.lastPage - (cfg.viewPages().size() - cfg.pages.size()), cfg.pages.size() - 1));
        }
        valueLabels = -1;
        anims = new ArrayList<>();
        anims.add(WheelConfig.RESET);
        anims.addAll(CpmBridge.listAnimations().keySet());
        for (WheelConfig.Slot s : cfg.allSlots())
            if (!s.empty() && !anims.contains(s.anim)) anims.add(s.anim);

        custom = new EditBox(font, 8, height - 50, LIST_W - 26, 16, Component.literal(""));
        custom.setHint(Component.translatable("cpmwheel.edit.custom"));
        addRenderableWidget(custom);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            String v = custom.getValue().trim();
            if (!v.isEmpty() && !anims.contains(v)) anims.add(v);
            custom.setValue("");
        }).bounds(8 + LIST_W - 22, height - 50, 22, 16).build());

        int px = width - 170, y = 30;
        pageName = new EditBox(font, px, y, 160, 16, Component.literal(""));
        pageName.setValue(cur().name);
        pageName.setResponder(v -> cur().name = v);
        addRenderableWidget(pageName); y += 20;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> setPage(page - 1)).bounds(px, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> setPage(page + 1)).bounds(px + 22, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.newpage"), b -> {
            cfg.pages.add(new WheelConfig.Page("Page " + (cfg.pages.size() + 1), 8)); setPage(cfg.pages.size() - 1);
        }).bounds(px + 44, y, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.delpage"), b -> {
            if (cfg.pages.size() > 1) { cfg.pages.remove(page); setPage(Math.min(page, cfg.pages.size() - 1)); }
        }).bounds(px + 102, y, 58, 18).build()); y += 22;
        if (!path.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.folder.back"), b -> {
                path.remove(path.size() - 1); selected = -1; rebuildWidgets(); }).bounds(LIST_W + 16, 22, 70, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.animations", onoff(cfg.animations)), b -> {
            cfg.animations = !cfg.animations; rebuildWidgets(); }).bounds(px, height - 158, 160, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.center", Component.translatable("cpmwheel.center." + cfg.centerMode)), b -> {
            String[] o = {"player", "icon", "none"};
            int i = java.util.Arrays.asList(o).indexOf(cfg.centerMode);
            cfg.centerMode = o[(i + 1) % o.length]; rebuildWidgets(); }).bounds(px, height - 92, 160, 18).build());
        addRenderableWidget(new net.minecraft.client.gui.components.AbstractSliderButton(px, height - 136, 160, 18,
                Component.translatable("cpmwheel.edit.centersize", cfg.centerSize), (cfg.centerSize - 50) / 150.0) {
            @Override protected void updateMessage() { setMessage(Component.translatable("cpmwheel.edit.centersize", cfg.centerSize)); }
            @Override protected void applyValue() { cfg.centerSize = 50 + (int) Math.round(value * 150 / 5) * 5; }
        });
        addRenderableWidget(Button.builder(Component.literal("+ slot"), b -> { if (slots().size() < 12) slots().add(new WheelConfig.Slot()); })
                .bounds(px, y, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("- slot"), b -> {
            if (slots().size() > 2) { slots().remove(slots().size() - 1); selected = -1; rebuildWidgets(); }
        }).bounds(px + 82, y, 78, 18).build()); y += 28;

        WheelConfig.Slot s = sel();
        if (s != null) {
            label = new EditBox(font, px, y + 10, 160, 16, Component.literal(""));
            label.setValue(s.label == null ? "" : s.label); label.setHint(Component.literal(s.anim));
            label.setResponder(v -> s.label = v); addRenderableWidget(label); y += 30;
            icon = new EditBox(font, px, y + 10, 104, 16, Component.literal(""));
            icon.setMaxLength(256);
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.pickicon"), b ->
                    minecraft.setScreen(new IconPickerScreen(this, v -> s.icon = v))).bounds(px + 106, y + 9, 54, 18).build());
            icon.setValue(s.icon == null ? "" : s.icon); icon.setHint(Component.literal("minecraft:diamond"));
            icon.setResponder(v -> s.icon = v.trim()); addRenderableWidget(icon); y += 30;
            value = new EditBox(font, px, y + 10, 50, 16, Component.literal(""));
            value.setValue(s.value < 0 ? "" : Integer.toString(s.value)); value.setHint(Component.literal("auto"));
            value.setFilter(v -> v.matches("\\d{0,3}"));
            value.setResponder(v -> s.value = v.isEmpty() ? -1 : Integer.parseInt(v)); addRenderableWidget(value);
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.color"), b -> {
                int i = 0; for (; i < WheelConfig.PALETTE.length; i++) if (WheelConfig.PALETTE[i] == s.color) break;
                s.color = WheelConfig.PALETTE[(i + 1) % WheelConfig.PALETTE.length];
            }).bounds(px + 54, y + 9, 50, 18).build());
            addRenderableWidget(Button.builder(Component.literal(s.favorite ? "\u2605" : "\u2606"), b -> { s.favorite = !s.favorite; rebuildWidgets(); })
                    .bounds(px + 108, y + 9, 24, 18).build());
            addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> {
                slots().set(selected, new WheelConfig.Slot()); rebuildWidgets();
            }).bounds(px + 136, y + 9, 24, 18).build()); y += 32;
            if (s.isFolder()) {
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.folder.open"), b -> {
                    path.add(s); selected = -1; rebuildWidgets(); }).bounds(px, y, 160, 18).build());
            } else {
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.test"), b -> WheelConfig.run(s)).bounds(px, y, 78, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.folder.new"), b -> {
                    s.children = new ArrayList<>();
                    for (int i = 0; i < 8; i++) s.children.add(new WheelConfig.Slot());
                    if (!s.anim.isBlank()) { WheelConfig.Slot moved = s.children.get(0); moved.anim = s.anim; moved.label = s.label; moved.icon = s.icon; moved.color = s.color; moved.value = s.value; }
                    s.anim = ""; s.value = -1; if (s.label == null || s.label.isBlank()) s.label = "Pasta";
                    rebuildWidgets();
                }).bounds(px + 82, y, 78, 18).build());
                y += 22;
                String m = s.mode == null ? "" : s.mode;
                Component mc = m.isEmpty() ? Component.translatable("cpmwheel.edit.mode.auto", s.effectiveMode())
                        : Component.translatable(m.equals("toggle") ? "cpmwheel.edit.mode.toggle_s" : "cpmwheel.edit.mode." + m);
                addRenderableWidget(Button.builder(mc, b -> {
                    String[] order = {"", "toggle", "slider", "presets", "frames"};
                    int i = java.util.Arrays.asList(order).indexOf(s.mode == null ? "" : s.mode);
                    s.mode = order[(i + 1) % order.length]; rebuildWidgets();
                }).bounds(px, y, 160, 18).build());
                y += 20;
                if (!"toggle".equals(s.effectiveMode()) && !WheelConfig.RESET.equals(s.anim)) {
                    EditBox mn = num(px, y + 10, s.min <= 0 ? "" : "" + s.min, "0", v -> s.min = v);
                    EditBox mxb = num(px + 54, y + 10, s.max <= 0 ? "" : "" + s.max, "" + s.maxV(), v -> s.max = v);
                    EditBox st = num(px + 108, y + 10, s.step <= 0 ? "" : "" + s.step, "" + s.stepV(), v -> s.step = v);
                    y += 30;
                    if ("frames".equals(s.effectiveMode())) {
                        s.ensureFrames();
                        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.frames", s.frames.size()),
                                b -> minecraft.setScreen(new FramesScreen(this, s))).bounds(px, y + 9, 160, 18).build());
                    }
                    if ("presets".equals(s.effectiveMode())) {
                        EditBox pr = new EditBox(font, px, y + 10, 160, 16, Component.literal(""));
                        pr.setValue(s.presets == null ? "" : s.presets);
                        pr.setResponder(v -> s.presets = v); addRenderableWidget(pr);
                        valueLabels = y;
                    }
                } else if (!WheelConfig.RESET.equals(s.anim)) { // timed animation
                    EditBox du = new EditBox(font, px, y + 2, 160, 16, Component.literal(""));
                    du.setValue(s.duration <= 0 ? "" : Integer.toString(s.duration));
                    du.setHint(Component.translatable("cpmwheel.edit.duration"));
                    du.setFilter(v -> v.matches("\\d{0,4}"));
                    du.setResponder(v -> s.duration = v.isEmpty() ? 0 : Integer.parseInt(v));
                    addRenderableWidget(du);
                }
            }
        }

        addRenderableWidget(Button.builder(Component.translatable(cfg.holdToOpen ? "cpmwheel.edit.mode.hold" : "cpmwheel.edit.mode.toggle"),
                b -> { cfg.holdToOpen = !cfg.holdToOpen; rebuildWidgets(); }).bounds(px, height - 70, 160, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit.autofill"), b -> {
            List<String> have = new ArrayList<>();
            for (WheelConfig.Slot sl : cfg.allSlots()) have.add(sl.anim);
            for (String a : CpmBridge.listAnimations().keySet()) if (!have.contains(a)) place(a);
            rebuildWidgets();
        }).bounds(px, height - 48, 160, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(px, height - 26, 160, 20).build());
    }

    private int valueLabels = -1;

    private EditBox num(int x, int y, String val, String hint, java.util.function.IntConsumer set) {
        EditBox b = new EditBox(font, x, y, 50, 16, Component.literal(""));
        b.setValue(val); b.setHint(Component.literal(hint));
        b.setFilter(v -> v.matches("\\d{0,4}"));
        b.setResponder(v -> set.accept(v.isEmpty() ? -1 : Integer.parseInt(v)));
        addRenderableWidget(b);
        return b;
    }

    /** Put an animation in the first free slot, adding pages as needed. */
    private void place(String a) {
        for (WheelConfig.Page p : cfg.pages) for (WheelConfig.Slot s : p.slots) if (s.empty()) { s.anim = a; return; }
        WheelConfig.Page p = new WheelConfig.Page("Page " + (cfg.pages.size() + 1), 8);
        p.slots.get(0).anim = a; cfg.pages.add(p);
    }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }

    private void setPage(int p) { path.clear(); page = Math.floorMod(p, cfg.pages.size()); selected = -1; rebuildWidgets(); }

    private float cx() { return (LIST_W + 16 + width - 178) / 2f; }
    private float cy() { return height / 2f + 6; }
    private float R() { return Math.min((width - 178 - LIST_W - 16) / 2f - 10, height / 2f - 30); }

    private int slotAt(double mx, double my) {
        int n = slots().size();
        double dx = mx - cx(), dy = my - cy(), d2 = dx * dx + dy * dy, R = R();
        if (d2 < (R * 0.35) * (R * 0.35) || d2 > (R + 8) * (R + 8)) return -1;
        double seg = Math.PI * 2 / n, ang = Math.atan2(dx, -dy);
        if (ang < 0) ang += Math.PI * 2;
        return (int) Math.floor((ang + seg / 2) / seg) % n;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 8, 0x2EE6FF);
        // list
        g.fill(6, 22, 8 + LIST_W + 2, height - 54, 0xA0101C28);
        int rows = (height - 80) / ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, anims.size() - rows)));
        for (int i = 0; i < rows && i + scroll < anims.size(); i++) {
            String a = anims.get(i + scroll);
            int y = 24 + i * ROW;
            boolean hv = mx >= 8 && mx < 8 + LIST_W && my >= y && my < y + ROW;
            if (hv) g.fill(8, y, 8 + LIST_W, y + ROW, 0x402EE6FF);
            g.drawString(font, font.plainSubstrByWidth(WheelConfig.RESET.equals(a) ? "\u21BA Reset" : a, LIST_W - 4), 10, y + 2,
                    CpmBridge.playing(a) > 0 ? 0x3BFF9D : 0xC8E8F0, false);
        }
        // wheel
        float cx = cx(), cy = cy(), R = R(), r0 = R * 0.35f;
        List<WheelConfig.Slot> slots = slots();
        int n = slots.size(), hv = slotAt(mx, my);
        double seg = Math.PI * 2 / n;
        for (int i = 0; i < n; i++) {
            WheelConfig.Slot s = slots.get(i);
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            int bg = i == selected ? 0xD0163848 : (i == hv ? 0xC0102838 : 0xA00A1420);
            Draw.sector(g, cx, cy, r0, R, a0, a1, bg);
            Draw.sector(g, cx, cy, R - 3, R, a0, a1, Draw.argb(s.empty() ? 0x334455 : s.color, 230));
            float tx = cx + (float) Math.sin(i * seg) * (r0 + R) / 2, ty = cy - (float) Math.cos(i * seg) * (r0 + R) / 2;
            int textY = (int) ty - 4;
            if (Icons.has(s.icon)) { Icons.draw(g, s.icon, (int) tx, (int) ty - 6, 16); textY = (int) ty + 4; }
            String l = s.empty() ? (i + 1) + "" : (s.isFolder() ? "\u25B8 " : "") + s.display();
            g.drawCenteredString(font, font.plainSubstrByWidth(l, (int) (R * 0.7f)), (int) tx, textY, s.empty() ? 0x55667A : 0xFFFFFF);
            if (s.favorite) g.drawString(font, "\u2605", (int) tx - 3, textY + 10, 0xFFD23B);
        }
        Draw.sector(g, cx, cy, 0, r0 - 3, 0, Math.PI * 2, 0xD0060C14);
        g.drawCenteredString(font, path.isEmpty() ? cur().name : path.get(path.size() - 1).display(), (int) cx, (int) cy - 8, 0x2EE6FF);
        if (!path.isEmpty()) {
            StringBuilder sb = new StringBuilder(cur().name);
            for (WheelConfig.Slot f : path) sb.append(" \u203A ").append(f.display());
            g.drawString(font, sb.toString(), LIST_W + 90, 27, 0x2EE6FF);
        }
        g.drawCenteredString(font, (page + 1) + "/" + cfg.pages.size(), (int) cx, (int) cy + 4, 0x7FA8B8);
        g.drawString(font, Component.translatable("cpmwheel.edit.help"), LIST_W + 16, height - 14, 0x7FA8B8);

        int px = width - 170;
        g.fill(px - 6, 22, width - 4, height - 4, 0x80101C28);
        WheelConfig.Slot s = sel();
        if (s != null) {
            int y = 102;
            g.drawString(font, Component.translatable("cpmwheel.edit.label"), px, y, 0x7FA8B8, false); y += 30;
            g.drawString(font, Component.translatable("cpmwheel.edit.icon"), px, y, 0x7FA8B8, false); y += 30;
            g.drawString(font, Component.translatable("cpmwheel.edit.value"), px, y, 0x7FA8B8, false);
            g.fill(px + 54, y, px + 104, y + 6, Draw.argb(s.color, 255));
        } else {
            g.drawWordWrap(font, Component.translatable("cpmwheel.edit.select"), px, 110, 160, 0x7FA8B8);
        }
        super.render(g, mx, my, pt);

        String drag = dragAnim != null ? dragAnim : (dragSlot >= 0 && dragSlot < n ? slots.get(dragSlot).display() : null);
        if (drag != null) {
            int w = font.width(drag) + 8;
            g.pose().pushPose(); g.pose().translate(0, 0, 400);
            g.fill(mx + 6, my + 6, mx + 6 + w, my + 20, 0xE02EE6FF);
            g.drawString(font, drag, mx + 10, my + 9, 0x041018, false);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (mx >= 8 && mx < 8 + LIST_W && my >= 24 && my < height - 54) {
            int i = (int) ((my - 24) / ROW) + scroll;
            if (i < anims.size()) {
                if (btn == 0) { dragAnim = anims.get(i); dragX = mx; dragY = my; }
                else if (btn == 1) WheelConfig.run(new WheelConfig.Slot(anims.get(i)));
            }
            return true;
        }
        int sl = slotAt(mx, my);
        if (sl >= 0) {
            if (btn == 1) { slots().get(sl).favorite = !slots().get(sl).favorite; return true; }
            selected = sl; dragSlot = sl; dragX = mx; dragY = my; rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        int sl = slotAt(mx, my);
        if (dragAnim != null) {
            if (sl >= 0) {
                WheelConfig.Slot s = slots().get(sl);
                if (s.isFolder()) {
                    WheelConfig.Slot free = null;
                    for (WheelConfig.Slot c : s.children) if (c.empty()) { free = c; break; }
                    if (free == null && s.children.size() < 12) { free = new WheelConfig.Slot(); s.children.add(free); }
                    if (free != null) { free.anim = dragAnim; if (WheelConfig.RESET.equals(dragAnim)) free.color = 0xFF3B5C; }
                    dragAnim = null; return true;
                }
                s.anim = dragAnim; s.label = ""; s.value = -1;
                if (WheelConfig.RESET.equals(dragAnim)) s.color = 0xFF3B5C;
                selected = sl; rebuildWidgets();
            }
            dragAnim = null; return true;
        }
        if (dragSlot >= 0) {
            if (sl >= 0 && sl != dragSlot) {
                var l = slots(); var t = l.get(sl); l.set(sl, l.get(dragSlot)); l.set(dragSlot, t);
                selected = sl; rebuildWidgets();
            } else if (sl < 0 && (mx < 8 + LIST_W + 2) && Math.abs(mx - dragX) > 6) { // dragged back to list = clear
                slots().set(dragSlot, new WheelConfig.Slot()); selected = -1; rebuildWidgets();
            }
            dragSlot = -1; return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double d) {
        if (mx < 8 + LIST_W) { scroll -= (int) Math.signum(d) * 3; return true; }
        if (slotAt(mx, my) >= 0 || Math.hypot(mx - cx(), my - cy()) < R()) { setPage(page + (d > 0 ? -1 : 1)); return true; }
        return super.mouseScrolled(mx, my, sx, d);
    }

    @Override
    public void onClose() {
        cfg.save();
        minecraft.setScreen(parent instanceof WheelScreen ? new WheelScreen(false) : null);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
