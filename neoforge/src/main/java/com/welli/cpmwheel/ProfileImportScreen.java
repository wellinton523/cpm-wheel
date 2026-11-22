package com.welli.cpmwheel;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.9.0 import a profile file (config/cpmwheel/import/ or export/): validate, choose the name, confirm before replacing. */
public class ProfileImportScreen extends MiniScreen {
    private static String selFile = "";
    private EditBox name;
    private String status = ""; private int statusCol = 0xCFE8F0;
    private boolean confirm = false; private String lastImported = "";
    private ProfileIO.Result check;

    public ProfileImportScreen(Screen parent, WheelConfig cfg) { super("cpmwheel.pio.title", parent, cfg); }

    @Override
    protected void init() {
        List<Path> files = ProfileIO.files();
        int x = 8, y = 26, w = 190;
        if (files.isEmpty()) status = Component.translatable("cpmwheel.pio.none", ProfileIO.importDir().toString()).getString();
        for (Path f : files) {
            if (y > height - 60) break;
            String fn = f.getFileName().toString();
            String dirTag = f.getParent().getFileName().toString();
            Button b = btn(Component.literal(font.plainSubstrByWidth(dirTag + "/" + fn, w - 6)), q -> { selFile = fn; confirm = false; check = null; name = null; lastImported = ""; status = ""; rebuildWidgets(); }, x, y, w, 18, null);
            b.active = !fn.equals(selFile);
            y += 20;
        }
        Path sel = selFile.isEmpty() ? null : ProfileIO.byName(selFile);
        int x2 = x + w + 10, w2 = Math.max(160, Math.min(220, width - x2 - 8)); y = 26;
        if (sel != null) {
            if (check == null) check = ProfileIO.check(sel);
            String suggested = check.ok ? check.name : ProfileLogic.nameFromFile(selFile);
            String prev = name == null ? suggested : name.getValue();
            name = new EditBox(font, x2 + 1, y + 1, w2 - 2, 18, Component.literal(""));
            name.setMaxLength(32); name.setHint(Component.translatable("cpmwheel.prof.name"));
            name.setValue(prev == null || prev.isEmpty() ? suggested : prev);
            name.setResponder(v -> { if (confirm) { confirm = false; rebuildWidgets(); } });
            addRenderableWidget(name); y += 24;
            boolean exists = WheelConfig.profileNames().contains(ProfileLogic.clean(name.getValue()));
            Button imp = btn(Component.translatable(confirm ? "cpmwheel.pio.replace" : exists ? "cpmwheel.pio.import.exists" : "cpmwheel.pio.import"), b -> doImport(sel), x2, y, w2, 20, "cpmwheel.pio.import.help");
            imp.active = check.ok; y += 22;
            if (!lastImported.isEmpty()) { btn(Component.translatable("cpmwheel.pio.use", lastImported), b -> { ProfileIO.useForModel(lastImported); status = Component.translatable("cpmwheel.pio.used", lastImported, CpmBridge.modelKey()).getString(); statusCol = 0x6CFF8F; rebuildWidgets(); }, x2, y, w2, 20, "cpmwheel.pio.use.help"); y += 22; }
        }
        btn(Component.translatable("cpmwheel.pio.openfolder"), b -> { try { java.nio.file.Files.createDirectories(ProfileIO.importDir()); net.minecraft.Util.getPlatform().openFile(ProfileIO.importDir().toFile()); } catch (Exception ignored) {} }, x2, height - 26, w2, 20, null);
        btn(Component.translatable("gui.done"), b -> onClose(), 8, height - 26, 150, 20, null);
    }

    private void doImport(Path sel) {
        String n = ProfileLogic.clean(name.getValue());
        boolean exists = WheelConfig.profileNames().contains(n);
        if (exists && !confirm) {   // first click on an existing name only asks
            confirm = true; status = Component.translatable("cpmwheel.pio.err.exists", n).getString(); statusCol = 0xFFC04A; rebuildWidgets(); return;
        }
        ProfileIO.Result r = ProfileIO.importAs(sel, n, confirm);
        confirm = false;
        status = Component.translatable(r.key, r.arg).getString(); statusCol = r.ok ? 0x6CFF8F : 0xFF6060;
        if (r.ok) lastImported = r.name;
        check = null; rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x2 = 208;
        if (check != null) {
            int yy = 100;
            for (var line : font.split(Component.translatable(check.key, check.arg), Math.max(60, width - x2 - 8))) { g.drawString(font, line, x2, yy, check.ok ? 0x9FE8B0 : 0xFF6060); yy += 10; }
        }
        if (!status.isEmpty()) { int yy = height - 40 - 10 * (font.split(Component.literal(status), width - 16).size() - 1);
            for (var line : font.split(Component.literal(status), width - 16)) { g.drawString(font, line, 8, yy, statusCol); yy += 10; } }
    }
    /** never saves the config object it was opened with: an import may have just replaced that file */
    @Override public void onClose() { minecraft.setScreen(parent); }
}
