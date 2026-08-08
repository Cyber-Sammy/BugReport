package com.cybersammy.bugreport.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Builds the platform-neutral Brigadier tree used by the physical-client adapter. */
public final class BugReportCommandTree {
    private BugReportCommandTree() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            BugReportCommandService commands) {
        dispatcher.register(commandRoot(commands));
    }

    public static boolean registrationReadyForSmoke(BugReportCommandService commands) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        register(dispatcher, commands);
        return dispatcher.getRoot().getChild("bugreport") != null
                && dispatcher.getRoot().getChild("bugreport").getChild("list") != null
                && dispatcher.getRoot().getChild("bugreport").getChild("create") != null
                && dispatcher.parse("bugreport list", (CommandSourceStack) null)
                        .getReader().getRemainingLength() == 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandRoot(
            BugReportCommandService commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bugreport")
                .executes(context -> respond(context.getSource(), commands.help()));
        root.then(Commands.literal("list")
                .executes(context -> respond(context.getSource(), commands.listProviders())));
        root.then(Commands.literal("create").then(Commands.argument("mod-id", StringArgumentType.word())
                .executes(context -> respond(context.getSource(), commands.create(
                        StringArgumentType.getString(context, "mod-id"), null)))
                .then(Commands.argument("category-id", StringArgumentType.word())
                        .executes(context -> respond(context.getSource(), commands.create(
                                StringArgumentType.getString(context, "mod-id"),
                                StringArgumentType.getString(context, "category-id")))))));
        root.then(Commands.literal("open").then(Commands.argument("report-id", StringArgumentType.word())
                .executes(context -> respond(context.getSource(), commands.open(
                        StringArgumentType.getString(context, "report-id"))))));
        root.then(Commands.literal("discard").then(Commands.argument("report-id", StringArgumentType.word())
                .executes(context -> respond(context.getSource(), commands.discard(
                        StringArgumentType.getString(context, "report-id"))))));
        return root;
    }

    private static int respond(CommandSourceStack source, java.util.List<BugReportCommandService.Message> messages) {
        messages.forEach(message -> source.sendSuccess(
                () -> net.minecraft.network.chat.Component.translatable(
                        message.translationKey(), message.arguments()), false));
        return messages.size();
    }
}
