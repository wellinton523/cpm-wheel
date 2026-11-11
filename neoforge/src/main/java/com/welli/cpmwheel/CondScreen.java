package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Editor for "Condicional" slots: ordered rules on the held item, first match wins. */
public class CondScreen extends Screen {
    private final Screen parent;
    private final WheelConfig.Slot slot;
    private int sel = 0;
    private List<String> anims;

    public CondScreen(Screen parent, WheelConfig.Slot slot) {
        super(Component.translatable("cpmwheel.cond.title", slot.display()));
        this.parent = parent; this.slot = slot;
        if (slot.rules == null) slot.rules = new ArrayList<>();
        anims = new ArrayList<>(CpmBridge.listAnimations().keySet());
    }

    private WheelConfig.Rule rule() { List<WheelConfig.Rule> l = slot.ruleList(); return sel >= 0 && sel < l.size() ? l.get(sel) : null; }

    private EditBox box(int x, int y, int w, String val, String hint, Consumer<String> set) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.literal(""));
        b.setMaxLength(128);
        b.setValue(val == null ? "" : val);
        b.setHint(Component.literal(hint));
        b.setResponder(set);
        addRenderableWidget(b);
        return b;
    }

    private static double num(String v) { try { return v.isBlank() ? -1 : Double.parseDouble(v.replace(',', '.')); } catch (Exception e) { return -1; } }
    private static String fmt(double d) { return d < 0 ? "" : (d == Math.floor(d) ? Integer.toString((int) d) : Double.toString(d)); }

    private static String next(String[] o, String cur, int dir) { int i = java.util.Arrays.asList(o).indexOf(cur == null ? "" : cur); return o[Math.floorMod(i + dir, o.length)]; }

    @Override
    protected void init() {
        int lx = 8, y = 26;
        List<WheelConfig.Rule> l = slot.ruleList();
        sel = l.isEmpty() ? -1 : Math.max(0, Math.min(sel, l.size() - 1));
        for (int i = 0; i < l.size() && y < height - 110; i++, y += 20) {
            int idx = i;
            WheelConfig.Rule r = l.get(i);
            String d = "#" + (i + 1) + " " + summary(r) + " \u2192 " + (r.anim == null || r.anim.isBlank() ? "?" : r.anim);
            addRenderableWidget(Button.builder(Component.literal((i == sel ? "\u25B6 " : "") + font.plainSubstrByWidth(d, 130)), b -> { sel = idx; rebuildWidgets(); })
                    .bounds(lx, y, 150, 18).build());
        }
        int by = height - 104;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.add"), b -> { slot.rules.add(new WheelConfig.Rule()); sel = slot.rules.size() - 1; rebuildWidgets(); }).bounds(lx, by, 73, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.del"), b -> { if (rule() != null) { slot.rules.remove(rule()); sel--; } rebuildWidgets(); }).bounds(lx + 77, by, 73, 18).build());
        addRenderableWidget(Button.builder(Component.literal("\u2191"), b -> move(-1)).bounds(lx, by + 20, 73, 18).build());
        addRenderableWidget(Button.builder(Component.literal("\u2193"), b -> move(1)).bounds(lx + 77, by + 20, 73, 18).build());
        box(lx, by + 46, 150, slot.fallback, "fallback", v -> slot.fallback = v.trim());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.auto", slot.condAuto ? "ON" : "OFF"), b -> {
            slot.condAuto = !slot.condAuto; if (!slot.condAuto) Conditions.autoOff(slot); rebuildWidgets(); }).bounds(lx, by + 66, 150, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(lx, height - 16 - 4, 150, 16).build());

        WheelConfig.Rule r = rule();
        if (r == null) return;
        int x = 170; y = 26;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.hand", Component.translatable("cpmwheel.cond.hand." + (r.hand == null ? "main" : r.hand))),
                b -> { r.hand = next(Conditions.HANDS, r.hand, 1); rebuildWidgets(); }).bounds(x, y, 160, 18).build()); y += 22;
        box(x, y + 10, 100, r.item, "minecraft:diamond_sword", v -> r.item = v.trim());
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.usehand"), b -> {
            if (minecraft.player != null) {
                ItemStack st = "off".equals(r.hand) ? minecraft.player.getOffhandItem() : minecraft.player.getMainHandItem();
                r.item = Conditions.itemId(st); rebuildWidgets();
            }
        }).bounds(x + 102, y + 9, 58, 18).build()); y += 30;
        box(x, y + 10, 160, r.tag, "#minecraft:swords", v -> r.tag = v.trim()); y += 30;
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.cat", Component.translatable("cpmwheel.cond.cat." + (r.category == null || r.category.isBlank() ? "any" : r.category))),
                b -> { r.category = next(Conditions.CATEGORIES, r.category, 1); rebuildWidgets(); }).bounds(x, y, 160, 18).build()); y += 22;
        box(x, y + 10, 160, r.name, "nome contém...", v -> r.name = v); y += 30;
        box(x, y + 10, 78, fmt(r.dmgMin), "dano \u2265", v -> r.dmgMin = num(v));
        box(x + 82, y + 10, 78, fmt(r.dmgMax), "dano \u2264", v -> r.dmgMax = num(v)); y += 30;
        EditBox an = box(x, y + 10, 136, r.anim, "animação", v -> r.anim = v.trim());
        addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> {
            if (anims.isEmpty()) return;
            int i = anims.indexOf(r.anim);
            r.anim = anims.get(Math.floorMod(i + 1, anims.size())); an.setValue(r.anim);
        }).bounds(x + 138, y + 9, 22, 18).build()); y += 30;
        EditBox vb = box(x, y + 10, 60, r.value < 0 ? "" : Integer.toString(r.value), "valor", v -> { try { r.value = v.isBlank() ? -1 : Integer.parseInt(v.trim()); } catch (Exception e) { r.value = -1; } });
        vb.setFilter(v -> v.matches("\\d{0,3}"));
    }

    private void move(int dir) {
        WheelConfig.Rule r = rule();
        if (r == null) return;
        int i = slot.rules.indexOf(r), j = i + dir;
        if (i < 0 || j < 0 || j >= slot.rules.size()) return;
        slot.rules.set(i, slot.rules.get(j)); slot.rules.set(j, r); sel = j; rebuildWidgets();
    }

    private String summary(WheelConfig.Rule r) {
        List<String> p = new ArrayList<>();
        if (r.category != null && !r.category.isBlank()) p.add(Component.translatable("cpmwheel.cond.cat." + r.category).getString());
        if (r.item != null && !r.item.isBlank()) p.add(r.item.replace("minecraft:", ""));
        if (r.tag != null && !r.tag.isBlank()) p.add(r.tag);
        if (r.name != null && !r.name.isBlank()) p.add("\"" + r.name + "\"");
        if (r.dmgMin >= 0) p.add("\u2265" + fmt(r.dmgMin));
        if (r.dmgMax >= 0) p.add("\u2264" + fmt(r.dmgMax));
        return p.isEmpty() ? "*" : String.join(" ", p);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, 0x2EE6FF);
        WheelConfig.Rule r = rule();
        if (r != null) {
            int x = 170, y = 48;
            String[] labels = {"cpmwheel.cond.l.item", "cpmwheel.cond.l.tag", null, "cpmwheel.cond.l.name", "cpmwheel.cond.l.dmg", "cpmwheel.cond.l.anim", "cpmwheel.cond.l.value"};
            int[] ys = {y, y + 30, -1, y + 52, y + 82, y + 112, y + 142};
            for (int i = 0; i < labels.length; i++) if (labels[i] != null) g.drawString(font, Component.translatable(labels[i]), x, ys[i], 0x7FA8B8, false);
        }
        // what is in hand right now + which rule would match (editor only)
        if (minecraft.player != null) {
            ItemStack m = minecraft.player.getMainHandItem(), o = minecraft.player.getOffhandItem();
            int iy = height - 40, ix = 340;
            if (width - ix > 100) {
                g.drawString(font, Component.translatable("cpmwheel.cond.inhand", Conditions.itemId(m), fmt(Math.round(ItemInfo.attackDamage(m) * 10) / 10.0), Conditions.itemId(o)), ix, iy, 0xC8E8F0, false);
                int k = Conditions.evaluate(slot);
                g.drawString(font, Component.translatable("cpmwheel.cond.match", k < 0 ? "-" : "#" + (k + 1)), ix, iy + 12, k < 0 ? 0xFF3B5C : 0x3BFF9D, false);
            }
        }
        g.drawString(font, Component.translatable("cpmwheel.cond.l.fallback"), 8, height - 104 + 36, 0x7FA8B8, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void onClose() { Actions.invalidate(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
