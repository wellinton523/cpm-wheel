package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.6.0 "Condições da regra": when a trigger rule turns on. One row per condition: [NÃO] [type] [value] [✖],
 * TODAS (E) / QUALQUER (OU) on top and a live "agora: sim / não" next to every row.
 */
public class CondEditScreen extends Screen {
    private final Screen parent;
    private final WheelConfig cfg;
    private final List<TriggerLogic.Cond> conds;
    private final java.util.function.Supplier<String> getCombine;
    private final java.util.function.Consumer<String> setCombine;
    private final java.util.function.Supplier<String> label;
    /** 1.7.2 "Desativar quando" mode: different texts + optional fade-time slider */
    private boolean suspend = false;
    private java.util.function.IntSupplier fadeGet; private java.util.function.IntConsumer fadeSet;
    private int page = 0;
    private int listY = 0, per = 1;

    public CondEditScreen(Screen parent, WheelConfig cfg, WheelConfig.EffectRule rule) {
        this(parent, cfg, Component.translatable("cpmwheel.cond.title"), rule.conds, () -> rule.combine, v -> rule.combine = v, () -> EffectTriggers.ruleLabel(rule));
    }
    public CondEditScreen(Screen parent, WheelConfig cfg, Component title, List<TriggerLogic.Cond> conds, java.util.function.Supplier<String> getCombine,
                          java.util.function.Consumer<String> setCombine, java.util.function.Supplier<String> label) {
        super(title); this.parent = parent; this.cfg = cfg; this.conds = conds; this.getCombine = getCombine; this.setCombine = setCombine; this.label = label;
    }
    /** 1.7.2: conditions that SUSPEND a physics chain / link; fade == null for links (they use the chain's fade time) */
    public CondEditScreen suspendMode(java.util.function.IntSupplier fadeGet, java.util.function.IntConsumer fadeSet) {
        suspend = true; this.fadeGet = fadeGet; this.fadeSet = fadeSet; return this;
    }

    private static Tooltip tip(String k) { return Tooltip.create(Component.translatable(k)); }

    @Override
    protected void init() {
        int w = Math.min(420, width - 16), x = width / 2 - w / 2, y = 40;
        boolean or = "or".equals(getCombine.get());
        String sfx = suspend ? ".susp" : "";
        if (fadeSet != null) {
            int hw = (w - 4) / 2;
            addRenderableWidget(Button.builder(Component.translatable((or ? "cpmwheel.cond.combine.or" : "cpmwheel.cond.combine.and") + sfx + ".short"), b -> {
                setCombine.accept("or".equals(getCombine.get()) ? "and" : "or"); rebuildWidgets(); })
                .tooltip(tip("cpmwheel.cond.combine" + sfx + ".help")).bounds(x, y, hw, 20).build());
            addRenderableWidget(new net.minecraft.client.gui.components.AbstractSliderButton(x + w - hw, y, hw, 20, fadeMsg(fadeGet.getAsInt()), fadeGet.getAsInt() / 3000.0) {
                @Override protected void updateMessage() { setMessage(fadeMsg(fadeGet.getAsInt())); }
                @Override protected void applyValue() { fadeSet.accept((int) Math.round(value * 60) * 50); }
            }).setTooltip(tip("cpmwheel.l2d.fade.help"));
        } else
        addRenderableWidget(Button.builder(Component.translatable((or ? "cpmwheel.cond.combine.or" : "cpmwheel.cond.combine.and") + sfx), b -> {
            setCombine.accept("or".equals(getCombine.get()) ? "and" : "or"); rebuildWidgets(); })
            .tooltip(tip("cpmwheel.cond.combine" + sfx + ".help")).bounds(x, y, w, 20).build());
        y += 26; listY = y;
        per = Math.max(2, (height - 64 - y) / 22);
        List<TriggerLogic.Cond> cs = conds;
        int pages = Math.max(1, (cs.size() + per - 1) / per);
        page = page >= 999 ? pages - 1 : Math.floorMod(page, pages);
        int vx = x + 36 + 102, vw = w - 36 - 102 - 24 - 34; // value area (34 px at the right for the live state)
        for (int i = page * per; i < Math.min(cs.size(), (page + 1) * per); i++) {
            final TriggerLogic.Cond c = cs.get(i);
            addRenderableWidget(Button.builder(Component.translatable(c.not ? "cpmwheel.cond.not" : "cpmwheel.cond.is"), b -> { c.not = !c.not; rebuildWidgets(); })
                    .tooltip(tip("cpmwheel.cond.not.help")).bounds(x, y, 34, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.t." + c.type), b -> minecraft.setScreen(new CondTypeScreen(this, cfg, t -> {
                        if (!t.equals(c.type)) { c.type = t; c.value = defValue(t); c.num = defNum(t); } })))
                    .tooltip(tip("cpmwheel.cond.t." + c.type + ".help")).bounds(x + 36, y, 100, 20).build());
            value(c, vx, y, vw);
            addRenderableWidget(Button.builder(Component.literal("\u2716"), b -> { cs.remove(c); rebuildWidgets(); }).bounds(vx + vw + 2, y, 20, 20).build());
            y += 22;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> {}).bounds(x + 22, y, 40, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 64, y, 20, 18).build());
            y += 20;
        }
        if (cs.size() < 12)
            addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.add"), b -> minecraft.setScreen(new CondTypeScreen(this, cfg, t -> {
                    cs.add(new TriggerLogic.Cond(t, defValue(t), defNum(t))); page = 999; })))
                .tooltip(tip("cpmwheel.cond.add.help")).bounds(x, y + 2, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
    }

    private static Component fadeMsg(int ms) { return Component.translatable("cpmwheel.l2d.fade", String.format(java.util.Locale.ROOT, "%.2f", ms / 1000f)); }

    static String defValue(String t) {
        return switch (t) { case "effect" -> "minecraft:poison"; case "dimension" -> "the_nether"; case "biome" -> "desert"; default -> ""; };
    }
    static int defNum(String t) {
        return switch (t) { case "health" -> 6; case "damage" -> 3; case "anim" -> 1; default -> 0; };
    }

    private String here(boolean dim) {
        var p = minecraft.player;
        if (p == null) return "";
        if (dim) return p.level().dimension().location().toString();
        return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    /** the value editor of one condition */
    private void value(TriggerLogic.Cond c, int x, int y, int w) {
        switch (c.type) {
            case "effect" -> {
                addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(EffectTriggers.effectName(c.value), w - 50)),
                        b -> minecraft.setScreen(new EffectPickerScreen(this, cfg, c.value, v -> c.value = v)))
                        .tooltip(Tooltip.create(Component.translatable("cpmwheel.eff.effect.help").append("\n" + c.value))).bounds(x, y, w - 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal(c.num <= 0 ? Component.translatable("cpmwheel.cond.lvl.any").getString() : EffectTriggersScreen.roman(c.num + 1) + "+"),
                        b -> { c.num = (c.num + 1) % 10; rebuildWidgets(); })
                        .tooltip(tip("cpmwheel.cond.lvl.help")).bounds(x + w - 46, y, 46, 20).build());
            }
            case "emote" -> { // 1.8.1: an Emotecraft emote is playing (any or a named one)
                Button b = Button.builder(Component.literal(font.plainSubstrByWidth(EmoteLogic.isAny(c.value) ? Component.translatable("cpmwheel.ec.any").getString() : c.value, w - 4)),
                        bt -> minecraft.setScreen(new EmotePickerScreen(this, true, e -> c.value = e == null ? "" : e.name())))
                        .tooltip(Tooltip.create(Component.translatable("cpmwheel.cond.t.emote.help").append(EmoteBridge.active(cfg) ? Component.empty()
                                : Component.literal("\n").append(Component.translatable(EmoteBridge.present() ? "cpmwheel.ec.offnote" : "cpmwheel.ec.missing"))))).bounds(x, y, w, 20).build();
                addRenderableWidget(b);
            }
            case "anim" -> { // 1.7.2: a CPM / wheel animation is playing (value >= N)
                addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(c.value.isBlank() ? "?" : c.value, w - 50)),
                        b -> minecraft.setScreen(new TargetPickerScreen(this, cfg, v -> c.value = v)))
                        .tooltip(Tooltip.create(Component.translatable("cpmwheel.cond.t.anim.help").append("\n" + c.value))).bounds(x, y, w - 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal("\u2265" + Math.max(1, c.num)), b -> {
                        int[] steps = {1, 2, 5, 10, 25, 50, 100, 128, 200, 255}; int n = 0;
                        for (int k = 0; k < steps.length; k++) if (steps[k] == Math.max(1, c.num)) n = k + 1;
                        c.num = steps[n % steps.length]; rebuildWidgets(); })
                        .tooltip(tip("cpmwheel.cond.anim.min.help")).bounds(x + w - 46, y, 46, 20).build());
            }
            case "health" -> slider(x, y, w, c, 1, 40, n -> Component.translatable("cpmwheel.cond.health.s", String.format(java.util.Locale.ROOT, "%.1f", n / 2f)), "cpmwheel.cond.t.health.help");
            case "damage" -> slider(x, y, w, c, 1, 30, n -> Component.translatable("cpmwheel.cond.damage.s", n), "cpmwheel.cond.t.damage.help");
            case "biome", "dimension" -> {
                boolean dim = c.type.equals("dimension");
                EditBox e = new EditBox(font, x + 1, y + 1, w - 50, 18, Component.literal(""));
                e.setMaxLength(120); e.setValue(c.value);
                e.setHint(Component.translatable(dim ? "cpmwheel.cond.dim.hint" : "cpmwheel.cond.biome.hint"));
                e.setResponder(v -> c.value = v.trim());
                e.setTooltip(tip(dim ? "cpmwheel.cond.t.dimension.help" : "cpmwheel.cond.t.biome.help"));
                addRenderableWidget(e);
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.here"), b -> { String h = here(dim); if (!h.isEmpty()) { c.value = h; rebuildWidgets(); } })
                        .tooltip(tip("cpmwheel.cond.here.help")).bounds(x + w - 46, y, 46, 20).build());
            }
            case "hold" -> {
                EditBox e = new EditBox(font, x + 1, y + 1, w - 96, 18, Component.literal(""));
                e.setMaxLength(200); e.setValue(c.value);
                e.setHint(Component.translatable("cpmwheel.cond.hold.hint"));
                e.setResponder(v -> c.value = v.trim());
                e.setTooltip(tip("cpmwheel.cond.t.hold.help"));
                addRenderableWidget(e);
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.group"), b -> {   // cycle through "Criar grupo" groups
                    List<WheelConfig.ItemGroup> gs = cfg.haGroups;
                    if (gs == null || gs.isEmpty()) return;
                    int i = -1;
                    for (int k = 0; k < gs.size(); k++) if (("grp:" + gs.get(k).id).equals(c.value)) i = k;
                    c.value = "grp:" + gs.get((i + 1) % gs.size()).id; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.translatable("cpmwheel.cond.group.help").append(c.value.startsWith("grp:") ? "\n= " + EffectTriggers.holdName(cfg, c.value) : "")))
                    .bounds(x + w - 92, y, 46, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("cpmwheel.cond.hand." + Math.min(2, Math.max(0, c.num))), b -> { c.num = (c.num + 1) % 3; rebuildWidgets(); })
                        .tooltip(tip("cpmwheel.cond.hand.help")).bounds(x + w - 44, y, 44, 20).build());
            }
            default -> {
                Button b = Button.builder(Component.translatable("cpmwheel.cond.novalue"), bt -> {}).tooltip(tip("cpmwheel.cond.t." + c.type + ".help")).bounds(x, y, w, 20).build();
                b.active = false;
                addRenderableWidget(b);
            }
        }
    }

    private void slider(int x, int y, int w, TriggerLogic.Cond c, int min, int max, java.util.function.IntFunction<Component> msg, String help) {
        c.num = Math.max(min, Math.min(max, c.num));
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, msg.apply(c.num), (c.num - min) / (double) (max - min)) {
            @Override protected void updateMessage() { setMessage(msg.apply(c.num)); }
            @Override protected void applyValue() { c.num = min + (int) Math.round(value * (max - min)); }
        }).setTooltip(tip(help));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0050A12);
        g.drawCenteredString(font, title, width / 2, 10, Style.accent(cfg));
        g.drawCenteredString(font, Component.literal(label.get()), width / 2, 24, 0xFFFFFF);
        super.render(g, mx, my, pt);
        // live state: is every condition true right now?
        var p = minecraft.player;
        int w = Math.min(420, width - 16), x = width / 2 - w / 2;
        if (p != null) {
            TriggerLogic.Snapshot sn = EffectTriggers.peek(p, cfg);
            int y = listY;
            for (int i = page * per; i < Math.min(conds.size(), (page + 1) * per); i++) {
                boolean v = TriggerLogic.test(conds.get(i), sn);
                g.drawString(font, Component.translatable(v ? "cpmwheel.cond.yes" : "cpmwheel.cond.no"), x + w - 30, y + 6, v ? 0x3BFF9D : 0xFF5A5A);
                y += 22;
            }
            boolean all = TriggerLogic.eval(conds, getCombine.get(), sn);
            String sfx = suspend ? ".susp" : "";
            g.drawCenteredString(font, Component.translatable(conds.isEmpty() ? "cpmwheel.cond.empty" + sfx : all ? "cpmwheel.cond.now.on" + sfx : "cpmwheel.cond.now.off" + sfx),
                    width / 2, height - 40, conds.isEmpty() ? (suspend ? 0x7FA8B8 : 0xFFC04A) : all ? (suspend ? 0xFFC04A : 0x3BFF9D) : (suspend ? 0x3BFF9D : 0x7FA8B8));
        }
    }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
