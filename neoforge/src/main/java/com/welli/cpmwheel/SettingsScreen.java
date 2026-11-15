package com.welli.cpmwheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 1.5.7 options screen, organised in categories (left tabs) with section headers, a search box that filters every option
 * by name/tooltip, a tooltip on each option and "restaurar padrões" per section. Same config fields as before (nothing renamed).
 */
public class SettingsScreen extends Screen {
    static final String[] CATS = {"wheel", "style", "hud", "fp", "items", "effects", "compat", "advanced"};
    private static final WheelConfig DEF = new WheelConfig();
    private static String cat = "wheel";
    private static String query = "";
    private static int scroll = 0;

    private final Screen parent;
    private WheelConfig cfg;
    private List<String> presets;
    private int presetIdx = 0;
    private String status = "";
    private EditBox presetName, searchBox;
    private int contentX, contentW, contentTop, contentBottom, totalH;
    private final List<Header> headers = new ArrayList<>();

    private record Header(int y, String text) {}

    /** one option row: category, section, widgets factory, reset to default (null = link / action) */
    private static final class Opt {
        final String cat, sec, tip;
        final RowMaker maker;
        final Runnable reset;
        String search = "";
        Opt(String cat, String sec, String tip, RowMaker maker, Runnable reset) { this.cat = cat; this.sec = sec; this.tip = tip; this.maker = maker; this.reset = reset; }
    }
    private interface RowMaker { List<AbstractWidget> make(int x, int y, int w); }

    public SettingsScreen(Screen parent) { super(Component.translatable("cpmwheel.set.title")); this.parent = parent; }
    /** open directly on a category */
    public SettingsScreen(Screen parent, String category) { this(parent); cat = category; query = ""; scroll = 0; }

    private static String onoff(boolean b) { return b ? "ON" : "OFF"; }
    private static String next(String[] o, String cur, int dir) {
        int i = Math.max(0, Style.idx(o, cur));
        return o[Math.floorMod(i + dir, o.length)];
    }

    // ---------- option builders ----------
    private final List<Opt> opts = new ArrayList<>();
    private String curCat, curSec;
    private void sec(String c, String s) { curCat = c; curSec = s; }

    private void toggle(String tip, Supplier<Component> label, Runnable flip, Runnable reset) {
        opts.add(new Opt(curCat, curSec, tip, (x, y, w) -> List.of(Button.builder(label.get(), b -> { flip.run(); apply(); rebuildWidgets(); }).bounds(x, y, w, 20).build()), reset));
    }
    private void bool(String key, String tip, BooleanSupplier get, Consumer<Boolean> set, boolean def) {
        toggle(tip, () -> Component.translatable(key, onoff(get.getAsBoolean())), () -> set.accept(!get.getAsBoolean()), () -> set.accept(def));
    }
    private void slider(String key, String tip, int min, int max, int stepv, IntSupplier get, IntConsumer set, int def) {
        opts.add(new Opt(curCat, curSec, tip, (x, y, w) -> {
            int val = get.getAsInt();
            return List.of(new AbstractSliderButton(x, y, w, 20, Component.translatable(key, val), (val - min) / (double) (max - min)) {
                int v = val;
                @Override protected void updateMessage() { setMessage(Component.translatable(key, v)); }
                @Override protected void applyValue() { v = min + (int) Math.round(value * (max - min) / stepv) * stepv; set.accept(v); apply(); }
            });
        }, () -> set.accept(def)));
    }
    private void link(String key, String tip, Supplier<Screen> open) {
        opts.add(new Opt(curCat, curSec, tip, (x, y, w) -> List.of(Button.builder(Component.translatable(key), b -> minecraft.setScreen(open.get())).bounds(x, y, w, 20).build()), null));
    }

    private void buildOpts() {
        opts.clear();
        // ---- Roda e botões ----
        sec("wheel", "open");
        toggle("cpmwheel.set.tip.hold", () -> Component.translatable(cfg.holdToOpen ? "cpmwheel.edit.mode.hold" : "cpmwheel.edit.mode.toggle"), () -> cfg.holdToOpen = !cfg.holdToOpen, () -> cfg.holdToOpen = DEF.holdToOpen);
        bool("cpmwheel.edit.animations", "cpmwheel.set.tip.animations", () -> cfg.animations, v -> cfg.animations = v, DEF.animations);
        toggle("cpmwheel.set.tip.preview", () -> Component.translatable(cfg.previewOnHover ? "cpmwheel.preview.on" : "cpmwheel.preview.off"), () -> cfg.previewOnHover = !cfg.previewOnHover, () -> cfg.previewOnHover = DEF.previewOnHover);
        bool("cpmwheel.set.favpage", "cpmwheel.set.tip.favpage", () -> cfg.showFavoritesPage, v -> cfg.showFavoritesPage = v, DEF.showFavoritesPage);
        bool("cpmwheel.set.ring", "cpmwheel.set.tip.ring", () -> cfg.innerRing, v -> cfg.innerRing = v, DEF.innerRing);
        bool("cpmwheel.set.alt", "cpmwheel.set.tip.alt", () -> cfg.altHotkeys, v -> cfg.altHotkeys = v, DEF.altHotkeys);
        bool("cpmwheel.set.framesegs", "cpmwheel.set.tip.framesegs", () -> cfg.frameSegments, v -> cfg.frameSegments = v, DEF.frameSegments);
        sec("wheel", "sound");
        toggle("cpmwheel.set.tip.sounds", () -> Component.translatable("cpmwheel.set.sounds", onoff(cfg.sounds)), () -> { cfg.sounds = !cfg.sounds; if (cfg.sounds) Actions.sound(cfg, 1.2f); }, () -> cfg.sounds = DEF.sounds);
        slider("cpmwheel.set.volume", "cpmwheel.set.tip.volume", 0, 100, 5, () -> cfg.soundVolume, v -> cfg.soundVolume = v, DEF.soundVolume);

        // ---- Estilo ----
        sec("style", "look");
        toggle("cpmwheel.set.tip.design", () -> Component.translatable("cpmwheel.set.design", Component.translatable("cpmwheel.design." + Style.design(cfg))), () -> cfg.design = next(Style.DESIGNS, cfg.design, 1), () -> cfg.design = DEF.design);
        toggle("cpmwheel.set.tip.theme", () -> Component.translatable("cpmwheel.set.theme", Component.translatable("cpmwheel.theme." + (Style.idx(Style.THEMES, cfg.theme) < 0 ? "auto" : cfg.theme))), () -> cfg.theme = next(Style.THEMES, cfg.theme, 1), () -> cfg.theme = DEF.theme);
        bool("cpmwheel.set.icononly", "cpmwheel.set.tip.icononly", () -> cfg.iconOnly, v -> cfg.iconOnly = v, DEF.iconOnly);
        slider("cpmwheel.set.iconsize", "cpmwheel.set.tip.iconsize", 50, 250, 5, () -> cfg.iconSize, v -> cfg.iconSize = v, DEF.iconSize);
        sec("style", "center");
        toggle("cpmwheel.set.tip.center", () -> Component.translatable("cpmwheel.edit.center", Component.translatable("cpmwheel.center." + cfg.centerMode)),
                () -> cfg.centerMode = next(new String[]{"player", "icon", "none"}, cfg.centerMode, 1), () -> cfg.centerMode = DEF.centerMode);
        slider("cpmwheel.edit.centersize", "cpmwheel.set.tip.centersize", 50, 200, 5, () -> cfg.centerSize, v -> cfg.centerSize = v, DEF.centerSize);
        bool("cpmwheel.set.centericononly", "cpmwheel.set.tip.centericononly", () -> cfg.centerIconOnly, v -> cfg.centerIconOnly = v, DEF.centerIconOnly);
        slider("cpmwheel.set.centericonsize", "cpmwheel.set.tip.centericonsize", 50, 250, 5, () -> cfg.centerIconSize, v -> cfg.centerIconSize = v, DEF.centerIconSize);

        // ---- HUD ----
        sec("hud", "hud");
        bool("cpmwheel.hud.enabled", "cpmwheel.set.tip.hudenabled", () -> cfg.hudEnabled, v -> cfg.hudEnabled = v, DEF.hudEnabled);
        toggle("cpmwheel.set.tip.hudcorner", () -> Component.translatable("cpmwheel.hud.corner", Component.translatable("cpmwheel.hud.corner." + cfg.hudCorner)),
                () -> cfg.hudCorner = next(new String[]{"top_left", "top_right", "bottom_left", "bottom_right"}, cfg.hudCorner, 1), () -> cfg.hudCorner = DEF.hudCorner);
        slider("cpmwheel.hud.scale", "cpmwheel.set.tip.hudscale", 50, 200, 5, () -> cfg.hudScale, v -> cfg.hudScale = v, DEF.hudScale);
        slider("cpmwheel.hud.opacity", "cpmwheel.set.tip.hudopacity", 0, 100, 5, () -> cfg.hudOpacity, v -> cfg.hudOpacity = v, DEF.hudOpacity);
        bool("cpmwheel.hud.onlynondefault", "cpmwheel.set.tip.hudnondefault", () -> cfg.hudOnlyNonDefault, v -> cfg.hudOnlyNonDefault = v, DEF.hudOnlyNonDefault);
        bool("cpmwheel.hud.grouped", "cpmwheel.set.tip.hudgrouped", () -> cfg.hudGrouped, v -> cfg.hudGrouped = v, DEF.hudGrouped);
        bool("cpmwheel.hud.showempty", "cpmwheel.set.tip.hudempty", () -> cfg.hudShowEmptyGroups, v -> cfg.hudShowEmptyGroups = v, DEF.hudShowEmptyGroups);

        // ---- Primeira pessoa ----
        sec("fp", "arm");
        toggle("cpmwheel.worldarm.help", () -> Component.translatable("cpmwheel.set.worldarm", Component.translatable("cpmwheel.worldarm." + cfg.haWorldArm)),
                () -> cfg.haWorldArm = next(WorldArm.MODES, cfg.haWorldArm, 1), () -> cfg.haWorldArm = DEF.haWorldArm);
        slider("cpmwheel.set.worlddepth", "cpmwheel.set.tip.worlddepth", 20, 100, 5, () -> cfg.haWorldDepth, v -> cfg.haWorldDepth = v, DEF.haWorldDepth);
        toggle("cpmwheel.glow.help", () -> Component.translatable("cpmwheel.set.glow", Component.translatable("cpmwheel.glow." + cfg.handGlow)),
                () -> cfg.handGlow = next(HandGlow.MODES, cfg.handGlow, 1), () -> cfg.handGlow = DEF.handGlow);
        toggle("cpmwheel.fp.help", () -> Component.translatable("cpmwheel.set.fp", Component.translatable("cpmwheel.fp." + cfg.fpAnimations)),
                () -> cfg.fpAnimations = next(FpState.MODES, cfg.fpAnimations, 1), () -> cfg.fpAnimations = DEF.fpAnimations);
        sec("fp", "anim");
        bool("cpmwheel.ha.master", "cpmwheel.set.tip.hamaster", () -> cfg.handAnim, v -> cfg.handAnim = v, DEF.handAnim);
        slider("cpmwheel.ha.int", "cpmwheel.set.tip.haint", 0, 200, 10, () -> cfg.haIntensity, v -> cfg.haIntensity = v, DEF.haIntensity);
        link("cpmwheel.set.link.handanim", "cpmwheel.set.tip.link.handanim", () -> new HandAnimScreen(this, cfg));
        link("cpmwheel.ha.adjust", "cpmwheel.set.tip.link.adjust", () -> new ItemAdjustScreen(this, cfg));
        link("cpmwheel.ha.armadj", "cpmwheel.set.tip.link.armadj", () -> new ArmAdjustScreen(this, cfg));

        // ---- Itens e mods ----
        sec("items", "items");
        bool("cpmwheel.io.enabled", "cpmwheel.set.tip.itemson", () -> cfg.haItemOn, v -> cfg.haItemOn = v, DEF.haItemOn);
        link("cpmwheel.ha.items", "cpmwheel.set.tip.link.items", () -> new ItemOverrideScreen(this, cfg));
        bool("cpmwheel.adj.vanilla", "cpmwheel.set.tip.vanilla", () -> cfg.haVanillaItem, v -> cfg.haVanillaItem = v, DEF.haVanillaItem);
        toggle("cpmwheel.set.tip.twohand", () -> Component.translatable(cfg.haTwoHandBlock ? "cpmwheel.adj.block2" : "cpmwheel.adj.block1"), () -> cfg.haTwoHandBlock = !cfg.haTwoHandBlock, () -> cfg.haTwoHandBlock = DEF.haTwoHandBlock);

        // ---- Gatilhos de efeito ----
        sec("effects", "effects");
        bool("cpmwheel.eff.master", "cpmwheel.eff.master.help", () -> cfg.effOn, v -> cfg.effOn = v, DEF.effOn);
        link("cpmwheel.set.link.effects", "cpmwheel.set.tip.link.effects", () -> new EffectTriggersScreen(this, cfg));

        // ---- Compatibilidade ----
        sec("compat", "compat");
        for (int m = 0; m < HandAnim.MODS.length; m++) {
            final String key = HandAnim.MODS[m][0], name = HandAnim.MODS[m][1];
            final boolean loaded = HandAnim.modLoaded[m];
            opts.add(new Opt(curCat, curSec, "cpmwheel.set.tip.ignore", (x, y, w) -> {
                Button b = Button.builder(Component.translatable("cpmwheel.ha.ignore", name, onoff(cfg.haIgnore.contains(key))).append(loaded ? Component.empty() : Component.translatable("cpmwheel.set.notinstalled")),
                        bt -> { if (!cfg.haIgnore.remove(key)) cfg.haIgnore.add(key); apply(); rebuildWidgets(); }).bounds(x, y, w, 20).build();
                b.active = loaded;
                return List.of(b);
            }, () -> cfg.haIgnore.remove(key)));
        }

        // ---- Avançado ----
        sec("advanced", "presets");
        opts.add(new Opt(curCat, curSec, "cpmwheel.set.tip.presetsave", (x, y, w) -> {
            presetName = new EditBox(font, x, y + 1, w - 76, 18, Component.literal(""));
            presetName.setHint(Component.translatable("cpmwheel.set.presetname"));
            presetName.setMaxLength(32);
            Button save = Button.builder(Component.translatable("cpmwheel.set.save"), b -> {
                String n = presetName.getValue().trim();
                status = cfg.savePreset(n) ? "\u2714 " + n : "\u2716";
                presets = WheelConfig.presetNames(); presetName.setValue("");
            }).bounds(x + w - 74, y, 74, 20).build();
            return List.of(presetName, save);
        }, null));
        opts.add(new Opt(curCat, curSec, "cpmwheel.set.tip.presetload", (x, y, w) -> {
            String pn = presets.isEmpty() ? "-" : presets.get(Math.floorMod(presetIdx, presets.size()));
            Button pick = Button.builder(Component.translatable("cpmwheel.set.preset", pn), b -> { presetIdx++; rebuildWidgets(); }).bounds(x, y, w - 76, 20).build();
            Button load = Button.builder(Component.translatable("cpmwheel.set.load"), b -> {
                if (!presets.isEmpty()) status = cfg.loadPreset(pn) ? "\u2714 " + pn : "\u2716";
            }).bounds(x + w - 74, y, 74, 20).build();
            return List.of(pick, load);
        }, null));
        opts.add(new Opt(curCat, curSec, "cpmwheel.set.tip.copydefault", (x, y, w) -> List.of(Button.builder(Component.translatable("cpmwheel.set.copydefault"), b -> {
            status = cfg.copyFromDefault() ? "\u2714" : "\u2716 (default)"; }).bounds(x, y, w, 20).build()), null));
        sec("advanced", "other");
        link("cpmwheel.set.link.hud", "cpmwheel.set.tip.link.hud", () -> new HudScreen(this));
    }

    /** push changed values to the runtime (same as the old screens did) */
    private void apply() {
        FpState.mode = cfg.fpAnimations;
        HandGlow.mode = cfg.handGlow;
        HandAnim.load(cfg);
        WorldArm.load(cfg);
        Hud.invalidate();
    }

    private void resetSection(String c, String s) {
        for (Opt o : opts) if (o.cat.equals(c) && o.sec.equals(s) && o.reset != null) o.reset.run();
        cfg.fixup(); apply();
        status = Component.translatable("cpmwheel.set.resetdone", Component.translatable("cpmwheel.set.sec." + s)).getString();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (cfg == null) { cfg = WheelConfig.get(); presets = WheelConfig.presetNames(); }
        buildOpts();
        headers.clear();
        int tabW = 112;
        contentX = 8 + tabW + 8;
        contentW = Math.max(150, Math.min(232, width - contentX - 8));
        contentTop = 30; contentBottom = height - 30;

        // tabs
        int ty = 30;
        for (String c : CATS) {
            Button b = Button.builder(Component.translatable("cpmwheel.set.cat." + c), bt -> { cat = c; query = ""; scroll = 0; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.set.cat." + c + ".help"))).bounds(8, ty, tabW, 20).build();
            b.active = !(query.isBlank() && c.equals(cat));
            addRenderableWidget(b);
            ty += 22;
        }
        // search
        searchBox = new EditBox(font, contentX, 8, contentW, 16, Component.literal(""));
        searchBox.setHint(Component.translatable("cpmwheel.set.search"));
        searchBox.setValue(query);
        searchBox.setResponder(v -> { if (!v.equals(query)) { query = v; scroll = 0; rebuildWidgets(); setFocused(searchBox); searchBox.setFocused(true); } });
        addRenderableWidget(searchBox);

        // visible options
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Opt> vis = new ArrayList<>();
        for (Opt o : opts) {
            if (q.isEmpty()) { if (o.cat.equals(cat)) vis.add(o); continue; }
            List<AbstractWidget> probe = o.maker.make(0, 0, 100);
            StringBuilder sb = new StringBuilder();
            for (AbstractWidget w : probe) sb.append(w.getMessage().getString()).append(' ');
            sb.append(Component.translatable(o.tip).getString()).append(' ')
              .append(Component.translatable("cpmwheel.set.sec." + o.sec).getString()).append(' ')
              .append(Component.translatable("cpmwheel.set.cat." + o.cat).getString());
            if (sb.toString().toLowerCase(Locale.ROOT).contains(q)) vis.add(o);
        }
        // layout with section headers
        int y = contentTop - scroll;
        String lastKey = "";
        int start = y;
        for (Opt o : vis) {
            String k = o.cat + "/" + o.sec;
            if (!k.equals(lastKey)) {
                if (!lastKey.isEmpty()) y += 6;
                lastKey = k;
                String head = (q.isEmpty() ? "" : Component.translatable("cpmwheel.set.cat." + o.cat).getString() + " \u203A ") + Component.translatable("cpmwheel.set.sec." + o.sec).getString();
                if (o.sec.equals("presets")) head += "  \u00A77" + Component.translatable("cpmwheel.set.profile", cfg.profileKey()).getString();
                boolean hasReset = false;
                for (Opt p : opts) if (p.cat.equals(o.cat) && p.sec.equals(o.sec) && p.reset != null) { hasReset = true; break; }
                if (y >= contentTop - 2 && y + 14 <= contentBottom) {
                    headers.add(new Header(y + 3, head));
                    if (hasReset) {
                        final String rc = o.cat, rs = o.sec;
                        addRenderableWidget(Button.builder(Component.translatable("cpmwheel.set.resetsec"), b -> resetSection(rc, rs))
                                .tooltip(Tooltip.create(Component.translatable("cpmwheel.set.resetsec.help"))).bounds(contentX + contentW - 70, y, 70, 14).build());
                    }
                }
                y += 18;
            }
            if (y >= contentTop - 2 && y + 20 <= contentBottom) {
                for (AbstractWidget w : o.maker.make(contentX, y, contentW)) {
                    if (!(w instanceof EditBox) && w.getTooltip() == null) w.setTooltip(Tooltip.create(Component.translatable(o.tip)));
                    addRenderableWidget(w);
                }
            }
            y += 22;
        }
        totalH = y - start;
        int maxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (scroll > maxScroll) { scroll = maxScroll; rebuildWidgets(); return; }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(8, height - 26, tabW, 20).build());
    }

    // scrolling: 1.20.1 and 1.21.1 have different mouseScrolled signatures; declare both (no @Override) so each line uses its own
    public boolean mouseScrolled(double mx, double my, double sx, double sy) { return scrollBy(mx, sy); }
    public boolean mouseScrolled(double mx, double my, double d) { return scrollBy(mx, d); }
    private boolean scrollBy(double mx, double d) {
        if (mx < contentX || mx > contentX + contentW || d == 0) return false;
        int maxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        int ns = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(d) * 22));
        if (ns != scroll) { scroll = ns; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawString(font, title, 8, 12, Style.accent(cfg));
        g.fill(contentX - 5, contentTop - 2, contentX - 4, contentBottom, 0x402EE6FF);
        for (Header h : headers) {
            g.drawString(font, font.plainSubstrByWidth(h.text(), contentW - 74), contentX, h.y(), Style.accent(cfg));
            g.fill(contentX, h.y() + 10, contentX + contentW - 74, h.y() + 11, 0x302EE6FF);
        }
        int maxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (maxScroll > 0) {
            int track = contentBottom - contentTop, bar = Math.max(16, track * track / Math.max(1, totalH));
            int by = contentTop + (track - bar) * scroll / maxScroll;
            g.fill(contentX + contentW + 2, by, contentX + contentW + 4, by + bar, 0x802EE6FF);
        }
        if (headers.isEmpty() && !query.isBlank()) g.drawString(font, Component.translatable("cpmwheel.set.nomatch"), contentX, contentTop + 4, 0x7FA8B8);
        if (!status.isEmpty()) g.drawString(font, status, contentX, height - 20, 0x3BFF9D);
        if (query.isBlank() && "style".equals(cat)) preview(g, contentX + contentW + 12);
        super.render(g, mx, my, pt);
    }

    /** live preview of the selected design with the real slots of the current page */
    private void preview(GuiGraphics g, int left) {
        if (width - left < 80) return;
        float cx = (left + width) / 2f, cy = height / 2f;
        float R = Math.min((width - left) / 2f - 14, height * 0.36f), r0 = R * 0.38f;
        g.enableScissor(left, 0, width, height);
        Style.background(g, cfg, width, height, 1f, cx, cy, R);
        g.disableScissor();
        List<WheelConfig.Page> pages = cfg.viewPages();
        List<WheelConfig.Slot> slots = pages.get(Math.floorMod(cfg.lastPage, pages.size())).slots;
        int n = Math.max(1, slots.size());
        double seg = Math.PI * 2 / n;
        int hv = (int) ((System.currentTimeMillis() / 900) % n);
        for (int i = 0; i < slots.size(); i++) {
            WheelConfig.Slot s = slots.get(i);
            boolean h = i == hv;
            double a0 = i * seg - seg / 2 + 0.02, a1 = i * seg + seg / 2 - 0.02;
            float ro = R + (h ? 7 : 0);
            Style.segment(g, cfg, cx, cy, r0, ro, a0, a1, s.color, h ? 1f : 0f, i == 0, s.empty(), 1f);
            float rr = (r0 + ro) / 2, tx = cx + (float) Math.sin(i * seg) * rr, ty = cy - (float) Math.cos(i * seg) * rr;
            int isz = Math.max(8, Math.round(Math.min((ro - r0) * 0.95f, 18 * cfg.iconSize / 100f)));
            if (s.iconOnly(cfg)) { if (!s.empty()) Style.icon(g, cfg, font, s, (int) tx, (int) ty, isz, s.color, 1f); }
            else {
                String l = Style.fit(cfg, font, s.empty() ? "\u2014" : s.display(), (int) (R * 0.75f));
                Style.text(g, cfg, font, l, (int) tx, (int) ty - 4, Style.textColor(cfg, h, s.empty()));
            }
        }
        Style.center(g, cfg, cx, cy, r0 - 4, 1f);
        WheelConfig.Slot hs = hv < slots.size() ? slots.get(hv) : null;
        int cis = Math.max(8, Math.round(Math.min(r0 * 1.5f, 28 * cfg.centerIconSize / 100f)));
        if (cfg.centerIconOnly) { if (hs != null && Icons.has(hs.icon)) Icons.draw(g, hs.icon, (int) cx, (int) cy, cis); }
        else if (hs != null) Style.text(g, cfg, font, Style.fit(cfg, font, hs.empty() ? "-" : hs.display(), (int) (r0 * 1.7f)), (int) cx, (int) cy - 4, Style.accent(cfg));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void onClose() { cfg.fixup(); cfg.save(); apply(); Hud.invalidate(); Actions.invalidate(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
