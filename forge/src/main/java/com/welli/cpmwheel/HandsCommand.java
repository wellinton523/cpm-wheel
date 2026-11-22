package com.welli.cpmwheel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.9.1 client command:
 *   /cpmwheel mod <modid> maos [on|off|principal|secundaria]
 *   /cpmwheel item <item id> maos [on|off|principal|secundaria|herdar]
 *   /cpmwheel maos lista
 * "off" = CPM Wheel hand animations off in both hands while holding an item of that mod; principal / secundaria = only that hand.
 */
public final class HandsCommand {
    private HandsCommand() {}

    static final List<String> MOD_WORDS = List.of("on", "off", "principal", "secundaria");
    static final List<String> ITEM_WORDS = List.of("on", "off", "principal", "secundaria", "herdar");

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cpmwheel")
                .then(Commands.literal("mod").then(Commands.argument("modid", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(namespaces(), b))
                        .then(Commands.literal("maos").executes(c -> show(HandsOff.modKey(StringArgumentType.getString(c, "modid"))))
                                .then(Commands.argument("estado", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(MOD_WORDS, b))
                                        .executes(c -> set(HandsOff.modKey(StringArgumentType.getString(c, "modid")), StringArgumentType.getString(c, "estado")))))))
                .then(Commands.literal("item").then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .suggests((c, b) -> { heldIds().forEach(b::suggest); return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b); })
                        .then(Commands.literal("maos").executes(c -> show(itemKey(net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id").toString())))
                                .then(Commands.argument("estado", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ITEM_WORDS, b))
                                        .executes(c -> set(itemKey(net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id").toString()), StringArgumentType.getString(c, "estado")))))))
                .then(Commands.literal("maos").then(Commands.literal("lista").executes(c -> list())));
    }

    static TreeSet<String> namespaces() { TreeSet<String> s = new TreeSet<>(); for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) s.add(rl.getNamespace()); return s; }
    static List<String> heldIds() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return List.of();
        TreeSet<String> s = new TreeSet<>();
        for (var st : new net.minecraft.world.item.ItemStack[]{p.getMainHandItem(), p.getOffhandItem()})
            if (!st.isEmpty()) s.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
        return List.copyOf(s);
    }
    static String itemKey(String id) { ResourceLocation rl = ResourceLocation.tryParse(id.trim()); return rl == null ? id : rl.toString(); }

    static void say(Component c) { var mc = net.minecraft.client.Minecraft.getInstance(); if (mc.player != null) mc.player.displayClientMessage(c, false); }
    static Component name(String key) { return Component.literal(key.startsWith("mod:") ? key.substring(4) : key); }

    /** state text; an item that inherits shows its mod's state */
    static Component stateText(WheelConfig c, String key) {
        int st = HandsOff.state(c.haHandsOff, key);
        if (st != -1) return Component.translatable("cpmwheel.hoff.long." + HandsOff.word(st), "");
        int i = key.indexOf(':');
        int ms = i < 0 ? 0 : HandsOff.state(c.haHandsOff, HandsOff.modKey(key.substring(0, i)));
        return Component.translatable("cpmwheel.hoff.long.inherit", Component.translatable("cpmwheel.hoff.long." + HandsOff.word(ms)));
    }

    static int show(String key) {
        WheelConfig c = WheelConfig.get();
        if (!HandsOff.validKey(key)) { say(Component.translatable("cpmwheel.hoff.cmd.bad", key)); return 0; }
        say(Component.translatable("cpmwheel.hoff.cmd.state", name(key), stateText(c, key)));
        return 1;
    }

    static int set(String key, String word) {
        if (!HandsOff.validKey(key)) { say(Component.translatable("cpmwheel.hoff.cmd.bad", key)); return 0; }
        boolean isMod = key.startsWith("mod:");
        Integer st = HandsOff.parse(word);
        if (st == null || (isMod && st < 0)) { say(Component.translatable("cpmwheel.hoff.cmd.word", String.join(" | ", isMod ? MOD_WORDS : ITEM_WORDS))); return 0; }
        if (st != 0 && isMod && !namespaces().contains(key.substring(4))) say(Component.translatable("cpmwheel.hoff.cmd.nomod", key.substring(4)));
        if (st != 0 && !isMod && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(key))) say(Component.translatable("cpmwheel.hoff.cmd.noitem", key));
        WheelConfig c = WheelConfig.get();
        HandsOff.set(c.haHandsOff, key, st);
        c.fixup(); c.save(); HandAnim.load(c);
        say(Component.translatable("cpmwheel.hoff.cmd.state", name(key), stateText(c, key)));
        return 1;
    }

    static int list() {
        WheelConfig c = WheelConfig.get();
        if (c.haHandsOff.isEmpty()) { say(Component.translatable("cpmwheel.hoff.cmd.none")); return 1; }
        for (var e : c.haHandsOff.entrySet())
            say(Component.literal(" - ").append(name(e.getKey())).append(": ").append(Component.translatable("cpmwheel.hoff.long." + HandsOff.word(e.getValue()), "")));
        return 1;
    }
}
