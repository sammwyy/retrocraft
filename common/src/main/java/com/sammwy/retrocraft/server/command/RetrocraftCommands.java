package com.sammwy.retrocraft.server.command;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sammwy.retrocraft.server.InstanceManager;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.config.RetrocraftConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /retrocraft} (alias {@code /rc}): gui, scan [onlynews], reload and config. */
public final class RetrocraftCommands {
    private final Supplier<InstanceManager> instances;

    private RetrocraftCommands(Supplier<InstanceManager> instances) {
        this.instances = instances;
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher, Supplier<InstanceManager> instances) {
        var commands = new RetrocraftCommands(instances);
        for (String name : List.of("retrocraft", "rc")) dispatcher.register(commands.tree(name));
    }

    private LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .then(Commands.literal("gui").executes(this::gui))
                .then(
                        Commands.literal("scan")
                                .requires(
                                        source ->
                                                com.sammwy.retrocraft.compat.Access.operator(
                                                        source))
                                .executes(context -> scan(context, false))
                                .then(
                                        Commands.literal("onlynews")
                                                .executes(context -> scan(context, true))))
                .then(
                        Commands.literal("reload")
                                .requires(
                                        source ->
                                                com.sammwy.retrocraft.compat.Access.operator(
                                                        source))
                                .executes(this::reload))
                .then(
                        Commands.literal("config")
                                .requires(
                                        source ->
                                                com.sammwy.retrocraft.compat.Access.operator(
                                                        source))
                                .executes(this::listConfig)
                                .then(
                                        Commands.argument("key", StringArgumentType.word())
                                                .suggests(
                                                        (context, builder) ->
                                                                suggest(
                                                                        builder,
                                                                        ConfigKey.keys().sorted()))
                                                .executes(this::getConfig)
                                                .then(
                                                        Commands.argument(
                                                                        "value",
                                                                        StringArgumentType.string())
                                                                .suggests(this::suggestValues)
                                                                .executes(this::setConfig))));
    }

    private int gui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        manager.openSettings(context.getSource().getPlayerOrException());
        return 1;
    }

    private int scan(CommandContext<CommandSourceStack> context, boolean onlyNew) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        if (manager.reloadLibrary(context.getSource(), onlyNew)) return 1;
        context.getSource().sendFailure(Component.translatable("message.retrocraft.scan_busy"));
        return 0;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        var source = context.getSource();
        if (manager.reloadConfig(source)) {
            source.sendSuccess(() -> Component.literal("Retrocraft config reloaded"), true);
            return 1;
        }
        source.sendFailure(Component.literal("Retrocraft config reload failed; see log"));
        return 0;
    }

    private int listConfig(CommandContext<CommandSourceStack> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        var source = context.getSource();
        ConfigKey.keys()
                .sorted()
                .forEach(
                        key ->
                                source.sendSuccess(
                                        () -> line(key, ConfigKey.get(manager.config(), key)),
                                        false));
        return 1;
    }

    private int getConfig(CommandContext<CommandSourceStack> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        String key = StringArgumentType.getString(context, "key");
        String value = ConfigKey.get(manager.config(), key);
        if (value == null) {
            context.getSource().sendFailure(Component.literal("Unknown config key " + key));
            return 0;
        }
        context.getSource().sendSuccess(() -> line(key, value), false);
        return 1;
    }

    private int setConfig(CommandContext<CommandSourceStack> context) {
        InstanceManager manager = instances.get();
        if (manager == null) return 0;
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        try {
            ConfigKey.set(manager.config(), key, value);
            manager.config().save();
            manager.applyConfig();
            context.getSource()
                    .sendSuccess(() -> line(key, ConfigKey.get(manager.config(), key)), true);
            return 1;
        } catch (IllegalArgumentException | IOException ex) {
            context.getSource().sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private CompletableFuture<Suggestions> suggestValues(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        InstanceManager manager = instances.get();
        String key = StringArgumentType.getString(context, "key");
        return suggest(
                builder,
                ConfigKey.valueSuggestions(
                        manager == null ? new RetrocraftConfig() : manager.config(), key));
    }

    private static CompletableFuture<Suggestions> suggest(
            SuggestionsBuilder builder, Stream<String> candidates) {
        String typed = builder.getRemainingLowerCase();
        candidates.filter(value -> value.toLowerCase().startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Component line(String key, String value) {
        return Component.literal(key + " = " + value);
    }
}
