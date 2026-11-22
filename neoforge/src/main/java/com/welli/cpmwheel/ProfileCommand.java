package com.welli.cpmwheel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * 1.9.0 client command:
 *   /cpmwheel perfil listar
 *   /cpmwheel perfil exportar [nome]   (the rest of the line, spaces allowed)
 *   /cpmwheel perfil importar <arquivo> [nome] [substituir]
 *   /cpmwheel perfil usar <nome>
 */
public final class ProfileCommand {
    private ProfileCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cpmwheel").then(Commands.literal("perfil")
                .then(Commands.literal("listar").executes(ProfileCommand::list))
                .then(Commands.literal("exportar").executes(ctx -> export(""))
                        .then(Commands.argument("nome", StringArgumentType.greedyString()).executes(ctx -> export(StringArgumentType.getString(ctx, "nome")))))
                .then(Commands.literal("importar")
                        .then(Commands.argument("arquivo", StringArgumentType.string())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ProfileIO.files().stream().map(f -> quote(f.getFileName().toString())), b))
                                .executes(ctx -> imp(StringArgumentType.getString(ctx, "arquivo"), "", false))
                                .then(Commands.argument("nome", StringArgumentType.string())
                                        .executes(ctx -> imp(StringArgumentType.getString(ctx, "arquivo"), StringArgumentType.getString(ctx, "nome"), false))
                                        .then(Commands.literal("substituir")
                                                .executes(ctx -> imp(StringArgumentType.getString(ctx, "arquivo"), StringArgumentType.getString(ctx, "nome"), true))))))
                .then(Commands.literal("usar")
                        .then(Commands.argument("nome", StringArgumentType.string())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(WheelConfig.profileNames().stream().map(ProfileCommand::quote), b))
                                .executes(ctx -> use(StringArgumentType.getString(ctx, "nome"))))));
    }

    static String quote(String s) { return s.matches("[A-Za-z0-9_.+-]+") ? s : "\"" + s.replace("\"", "") + "\""; }

    static void say(Component c) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, false);
    }

    static int list(CommandContext<CommandSourceStack> ctx) {
        say(Component.translatable("cpmwheel.pio.cmd.profiles", String.join(", ", WheelConfig.profileNames())));
        var files = ProfileIO.files();
        StringBuilder sb = new StringBuilder();
        for (var f : files) { if (sb.length() > 0) sb.append(", "); sb.append(f.getFileName()); }
        say(Component.translatable("cpmwheel.pio.cmd.files", sb.toString(), ProfileIO.importDir().toString()));
        return 1;
    }

    static int export(String name) {
        try { var f = ProfileIO.export(name); say(Component.translatable("cpmwheel.pio.exported", f.toString())); return 1; }
        catch (Exception e) { say(Component.translatable("cpmwheel.pio.err.write", e.getClass().getSimpleName())); return 0; }
    }

    static int imp(String file, String name, boolean replace) {
        var f = ProfileIO.byName(file);
        var r = ProfileIO.importAs(f, name, replace);
        say(Component.translatable(r.key, r.arg));
        if (!r.ok && "cpmwheel.pio.err.exists".equals(r.key)) say(Component.translatable("cpmwheel.pio.cmd.replacehint", quote(file), quote(r.name)));
        else if (r.ok) say(Component.translatable("cpmwheel.pio.cmd.usehint", quote(r.name)));
        return r.ok ? 1 : 0;
    }

    static int use(String name) {
        String n = ProfileLogic.clean(name);
        if (!WheelConfig.profileNames().contains(n)) { say(Component.translatable("cpmwheel.pio.err.noprofile", n)); return 0; }
        ProfileIO.useForModel(n);
        say(Component.translatable("cpmwheel.pio.used", n, CpmBridge.modelKey()));
        return 1;
    }
}
