package com.cybersammy.bugreport.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.cybersammy.bugreport.api.identifier.ProviderId;

/** Builds the client-independent Minecraft command tree used by the NeoForge client adapter. */
public final class BugReportCommandTree {
    private BugReportCommandTree() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            BugReportCommandService commands) {
        register(dispatcher, commands, ProviderSelector.none(), SessionOpener.forService(commands));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            BugReportCommandService commands, ProviderSelector selector) {
        register(dispatcher, commands, selector, SessionOpener.forService(commands));
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            BugReportCommandService commands,
            ProviderSelector selector,
            SessionOpener sessionOpener) {
        dispatcher.register(commandRoot(commands, selector, sessionOpener));
    }

    public static boolean registrationReadyForSmoke(BugReportCommandService commands) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        register(dispatcher, commands);
        return dispatcher.getRoot().getChild("bugreport") != null
                && dispatcher.getRoot().getChild("bugreport").getChild("list") != null
                && dispatcher.getRoot().getChild("bugreport").getChild("create") != null
                && dispatcher.parse("bugreport list", (CommandSourceStack) null)
                        .getReader().getRemainingLength() == 0
                && dispatcher.parse("bugreport open", (CommandSourceStack) null)
                        .getReader().getRemainingLength() == 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandRoot(
            BugReportCommandService commands,
            ProviderSelector selector,
            SessionOpener sessionOpener) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bugreport")
                .executes(context -> {
                    selector.open();
                    return 1;
                });
        root.then(Commands.literal("list")
                .executes(context -> respond(context.getSource(), commands.listProviders())));
        root.then(Commands.literal("create").then(Commands.argument("mod-id", StringArgumentType.word())
                .executes(context -> openProviderSelector(context.getSource(), selector,
                        StringArgumentType.getString(context, "mod-id")))
                .then(Commands.argument("category-id", StringArgumentType.word())
                        .executes(context -> respond(context.getSource(), commands.create(
                                StringArgumentType.getString(context, "mod-id"),
                                StringArgumentType.getString(context, "category-id")))))));
        root.then(Commands.literal("open")
                .executes(context -> respond(context.getSource(), sessionOpener.openLatest()))
                .then(Commands.argument("report-id", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(commands.activeSessionIds(), builder))
                        .executes(context -> respond(context.getSource(), sessionOpener.open(
                                StringArgumentType.getString(context, "report-id"))))));
        root.then(Commands.literal("discard").then(Commands.argument("report-id", StringArgumentType.word())
                .executes(context -> respond(context.getSource(), commands.discard(
                        StringArgumentType.getString(context, "report-id"))))));
        return root;
    }

    private static int openProviderSelector(CommandSourceStack source, ProviderSelector selector,
            String providerValue) {
        final ProviderId providerId;
        try {
            providerId = ProviderId.parse(providerValue);
        } catch (IllegalArgumentException exception) {
            return respond(source, java.util.List.of(
                    new BugReportCommandService.Message("bugreport.command.error.invalid_provider")));
        }
        return switch (selector.open(providerId)) {
            case OPENED -> 1;
            case UNKNOWN -> respond(source, java.util.List.of(new BugReportCommandService.Message(
                    "bugreport.command.error.unknown_provider", providerId.toString())));
            case UNAVAILABLE -> respond(source, java.util.List.of(new BugReportCommandService.Message(
                    "bugreport.command.error.provider_unavailable", providerId.toString())));
        };
    }

    private static int respond(CommandSourceStack source, java.util.List<BugReportCommandService.Message> messages) {
        messages.forEach(message -> source.sendSuccess(
                () -> net.minecraft.network.chat.Component.translatable(
                        message.translationKey(), message.arguments()), false));
        return messages.size();
    }

    public interface ProviderSelector {
        void open();

        SelectionResult open(ProviderId providerId);

        static ProviderSelector none() {
            return new ProviderSelector() {
                @Override public void open() {}
                @Override public SelectionResult open(ProviderId providerId) { return SelectionResult.UNKNOWN; }
            };
        }
    }

    public interface SessionOpener {
        java.util.List<BugReportCommandService.Message> openLatest();

        java.util.List<BugReportCommandService.Message> open(String sessionId);

        static SessionOpener forService(BugReportCommandService commands) {
            return new SessionOpener() {
                @Override
                public java.util.List<BugReportCommandService.Message> openLatest() {
                    return commands.openLatest();
                }

                @Override
                public java.util.List<BugReportCommandService.Message> open(String sessionId) {
                    return commands.open(sessionId);
                }
            };
        }
    }

    public enum SelectionResult { OPENED, UNKNOWN, UNAVAILABLE }
}
