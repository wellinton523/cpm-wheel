package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

public class WheelScreen extends Screen {
    private final boolean openedByKey;
    private WheelConfig cfg;
    private List<WheelConfig.Page> pages;
    private int page;
    private int hovered = -1;
    private long openedAt;

    public WheelScreen(boolean openedByKey) {
        super(Component.translatable("cpmwheel.title"));
        this.openedByKey = openedByKey;
    }

    @Override
    protected void init() {
        cfg = WheelConfig.get();
        pages = cfg.viewPages();
        page = Math.floorMod(cfg.lastPage, pages.size());
        openedAt = System.currentTimeMillis();
        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.edit"), b -> minecraft.setScreen(new EditScreen(this)))
                .bounds(width - 84, height - 26, 78, 20).build());
    }

    private float radius() { return Math.min(width, height) * 0.36f; }

    private WheelConfig.Page cur() { return pages.get(page); }

    @Override
    public void tick() {
        if (openedByKey && cfg.holdToOpen && System.currentTimeMillis() - openedAt > 120) {
            InputConstants.Key k = CpmWheel.KEY_WHEEL.getKey();
            boolean down = k.getType() == InputConstants.Type.MOUSE ? false
                    : InputConstants.isKeyDown(minecraft.getWindow().getWindow(), k.getValue());
            if (!down && k.getType() == InputConstants.Type.KEYSYM) activate();
        }
    }

    private void activate() {
        int h = hovered;
        onClose();
        if (h >= 0 && h < cur().slots.size()) WheelConfig.run(cur().slots.get(h));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x90050A12, 0xB0050A12);
        float cx = width / 2f, cy = height / 2f, R = radius(), r0 = R * 0.38f;
        List<WheelConfig.Slot> slots = cur().slots;
        int n = Math.max(1, slots.size());
        double seg = Math.PI * 2 / n;

        double dx = mx - cx, dy = my - cy;
        hovered = -1;
        if (dx * dx + dy * dy > r0 * r0 * 0.6) {
            double ang = Math.atan2(dx, -dy);
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.floor((ang + seg / 2) / seg) % n;
        }

        for (int i = 0; i < slots.size(); i++) {
            WheelConfig.Slot s = slots.get(i);
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            boolean hv = i == hovered;
            boolean on = !s.empty() && !WheelConfig.RESET.equals(s.anim) && CpmBridge.playing(s.anim) > 0;
            float ro = hv ? R + 6 : R;
            Draw.sector(g, cx, cy, r0, ro, a0, a1, hv ? 0xC0102838 : 0xA00A1420);
            Draw.sector(g, cx, cy, ro - 3, ro, a0, a1, Draw.argb(s.empty() ? 0x334455 : s.color, hv ? 255 : 170));
            if (on) Draw.sector(g, cx, cy, r0, r0 + 3, a0, a1, Draw.argb(s.color, 255));

            double mid = i * seg;
            float tx = cx + (float) Math.sin(mid) * (r0 + R) / 2, ty = cy - (float) Math.cos(mid) * (r0 + R) / 2;
            ItemStack icon = iconOf(s);
            int textY = (int) ty - 4;
            if (!icon.isEmpty()) { g.renderItem(icon, (int) tx - 8, (int) ty - 14); textY = (int) ty + 4; }
            String label = s.empty() ? "—" : s.display();
            label = font.plainSubstrByWidth(label, (int) (R * 0.75f));
            g.drawCenteredString(font, label, (int) tx, textY, s.empty() ? 0x55667A : (hv ? 0xFFFFFF : 0xC8E8F0));
            if (s.favorite) g.drawString(font, "\u2605", (int) tx + font.width(label) / 2 + 2, textY, 0xFFD23B);
        }

        Draw.sector(g, cx, cy, 0, r0 - 4, 0, Math.PI * 2, 0xD0060C14);
        Draw.sector(g, cx, cy, r0 - 6, r0 - 4, 0, Math.PI * 2, 0xFF2EE6FF);
        String center = hovered >= 0 && hovered < slots.size() && !slots.get(hovered).empty() ? slots.get(hovered).display() : cur().name;
        g.drawCenteredString(font, font.plainSubstrByWidth(center, (int) (r0 * 1.7f)), (int) cx, (int) cy - 8, 0x2EE6FF);
        g.drawCenteredString(font, (page + 1) + "/" + pages.size(), (int) cx, (int) cy + 4, 0x7FA8B8);

        // tabs
        int tx = 8;
        for (int i = 0; i < pages.size(); i++) {
            String nm = pages.get(i).name;
            int w = font.width(nm) + 10;
            g.fill(tx, 8, tx + w, 22, i == page ? 0xE02EE6FF : 0xA0101C28);
            g.drawString(font, nm, tx + 5, 11, i == page ? 0x041018 : 0xC8E8F0, false);
            tx += w + 3;
        }
        if (!CpmBridge.cpmPresent()) g.drawCenteredString(font, Component.translatable("cpmwheel.nocpm"), width / 2, height - 20, 0xFF3B5C);
        else g.drawString(font, Component.translatable("cpmwheel.hint"), 8, height - 20, 0x7FA8B8);
        super.render(g, mx, my, pt);
    }

    static ItemStack iconOf(WheelConfig.Slot s) {
        if (s.icon == null || s.icon.isBlank()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(s.icon.contains(":") ? s.icon : "minecraft:" + s.icon);
        if (rl == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (my < 24) {
            int tx = 8;
            for (int i = 0; i < pages.size(); i++) {
                int w = font.width(pages.get(i).name) + 10;
                if (mx >= tx && mx < tx + w) { setPage(i); return true; }
                tx += w + 3;
            }
        }
        if (btn == 0 && hovered >= 0) { activate(); return true; }
        if (btn == 1 && hovered >= 0 && hovered < cur().slots.size()) {
            WheelConfig.Slot s = cur().slots.get(hovered);
            if (!s.empty()) { s.favorite = !s.favorite; cfg.save(); pages = cfg.viewPages(); page = Math.min(page, pages.size() - 1); }
            return true;
        }
        return false;
    }

    private void setPage(int p) {
        page = Math.floorMod(p, pages.size());
        cfg.lastPage = page; cfg.save();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double d) {
        setPage(page + (d > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            int i = key - InputConstants.KEY_1;
            if (i < cur().slots.size()) { hovered = i; activate(); }
            return true;
        }
        if (key == InputConstants.KEY_TAB) { setPage(page + (hasShiftDown() ? -1 : 1)); return true; }
        if (!cfg.holdToOpen && CpmWheel.KEY_WHEEL.matches(key, sc) && System.currentTimeMillis() - openedAt > 150) { onClose(); return true; }
        return super.keyPressed(key, sc, mod);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
