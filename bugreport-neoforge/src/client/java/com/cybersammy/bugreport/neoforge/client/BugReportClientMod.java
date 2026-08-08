package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Physical-client entrypoint that installs the render-thread handoff adapter. */
@Mod(value = BugReportMod.MOD_ID, dist = Dist.CLIENT)
public final class BugReportClientMod {
    private final BugReportCommandService commands =
            new BugReportCommandService(BugReportMod::providerRegistry);

    public BugReportClientMod(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        NeoForgeGameThreadDispatchers.shared().installClient(command -> {
            try {
                Minecraft.getInstance().execute(Objects.requireNonNull(command, "command"));
                return true;
            } catch (RejectedExecutionException failure) {
                return false;
            }
        });
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
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
        event.getDispatcher().register(root);
    }

    private static int respond(CommandSourceStack source, java.util.List<BugReportCommandService.Message> messages) {
        messages.forEach(message -> source.sendSuccess(
                () -> Component.translatable(message.translationKey(), message.arguments()), false));
        return messages.size();
    }
}
