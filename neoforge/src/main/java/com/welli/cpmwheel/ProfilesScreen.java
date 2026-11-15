package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.8.0 "Perfis automáticos por modelo": link CPM models to a named profile (config/cpmwheel/profiles/<name>.json).
 * With the switch on, changing model in CPM loads its linked profile (wheel, triggers, physics, hands = the whole config);
 * unlinked models keep their own file as before. Several models can share one profile.
 */
public class ProfilesScreen extends Screen {
    private final Screen parent;
    private EditBox name;
    private String status = "";
    public ProfilesScreen(Screen parent) { super(Component.translatable("cpmwheel.prof.title")); this.parent = parent; }

    @Override
    protected void init() {
        WheelConfig.Profiles pr = WheelConfig.profiles();
        String model = CpmBridge.modelKey();
        int x = 8, y = 40, w = 200;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("cpmwheel.prof.auto", MiniScreen.onoff(pr.autoOn)), b -> {
            WheelConfig.get().save(); pr.autoOn = !pr.autoOn; WheelConfig.saveProfiles(); WheelConfig.reloadProfile(); WheelConfig.get(); rebuildWidgets(); })
                .tooltip(MiniScreen.tip("cpmwheel.prof.help")).bounds(x, y, w, 20).build()); y += 22;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("cpmwheel.prof.notify", MiniScreen.onoff(pr.notify)), b -> {
            pr.notify = !pr.notify; WheelConfig.saveProfiles(); rebuildWidgets(); }).bounds(x, y, w, 20).build()); y += 30;
        name = new EditBox(font, x + 1, y + 1, w - 78, 18, Component.literal(""));
        name.setMaxLength(32); name.setHint(Component.translatable("cpmwheel.prof.name"));
        String linked = pr.links.get(model);
        if (linked != null) name.setValue(linked);
        addRenderableWidget(name);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("cpmwheel.prof.link"), b -> {
            String n = ProfileLogic.clean(name.getValue());
            if (n.isEmpty()) { status = Component.translatable("cpmwheel.l2d.io.noname").getString(); return; }
            WheelConfig.get().save(); pr.links.put(model, n); WheelConfig.saveProfiles(); WheelConfig.reloadProfile(); WheelConfig.get().save();
            status = "\u2714 " + n; rebuildWidgets(); }).tooltip(MiniScreen.tip("cpmwheel.prof.link.help")).bounds(x + w - 76, y, 76, 20).build()); y += 22;
        var un = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("cpmwheel.prof.unlink"), b -> {
            WheelConfig.get().save(); pr.links.remove(model); WheelConfig.saveProfiles(); WheelConfig.reloadProfile(); WheelConfig.get();
            status = "\u2714"; rebuildWidgets(); }).bounds(x, y, w, 20).build());
        un.active = linked != null; y += 26;
        List<String> names = WheelConfig.profileNames();
        int x2 = x + w + 12;
        int yy = 40;
        for (String n : names) {
            if (yy > height - 60) break;
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(n), b -> name.setValue(n)).bounds(x2, yy, 150, 18).build());
            yy += 20;
        }
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 26, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 10, 0x2EE6FF);
        WheelConfig.Profiles pr = WheelConfig.profiles();
        String model = CpmBridge.modelKey();
        String linked = pr.links.get(model);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("cpmwheel.prof.model", model, linked == null ? Component.translatable("cpmwheel.prof.own").getString() : linked).getString(), width - 16), 8, 26, 0xCFE8F0);
        if (!WheelConfig.profileNames().isEmpty()) g.drawString(font, Component.translatable("cpmwheel.prof.list"), 220, 30, 0x7FA8B8);
        if (!status.isEmpty()) g.drawString(font, status, 8, height - 40, 0xFFC04A);
        super.render(g, mx, my, pt);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
