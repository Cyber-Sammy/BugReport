package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandTree;
import com.cybersammy.bugreport.neoforge.command.FileReportHistoryRecorder;
import com.cybersammy.bugreport.neoforge.command.FileReportDraftPersistence;
import com.cybersammy.bugreport.core.draft.FileDraftStore;
import com.cybersammy.bugreport.core.history.FileReportHistoryStore;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Physical-client entrypoint that installs the render-thread handoff adapter. */
@Mod(value = BugReportMod.MOD_ID, dist = Dist.CLIENT)
public final class BugReportClientMod {
    private final BugReportCommandService commands =
            new BugReportCommandService(
                    BugReportMod::providerRegistry, historyRecorder(), draftPersistence());

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

    private static BugReportCommandService.ReportHistoryRecorder historyRecorder() {
        return productDirectory("bugreport-history")
                .<BugReportCommandService.ReportHistoryRecorder>map(
                        directory ->
                                new FileReportHistoryRecorder(
                                        new FileReportHistoryStore(directory)))
                .orElseGet(BugReportCommandService.ReportHistoryRecorder::empty);
    }

    private static BugReportCommandService.ReportDraftPersistence draftPersistence() {
        return productDirectory("bugreport-drafts")
                .<BugReportCommandService.ReportDraftPersistence>map(
                        directory ->
                                new FileReportDraftPersistence(new FileDraftStore(directory)))
                .orElseGet(BugReportCommandService.ReportDraftPersistence::empty);
    }

    private static Optional<Path> productDirectory(String name) {
        try {
            Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath()
                    .toAbsolutePath().normalize();
            if (!Files.isDirectory(gameDirectory, LinkOption.NOFOLLOW_LINKS)
                    || !gameDirectory.equals(gameDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS))
                    || !gameDirectory.equals(gameDirectory.toRealPath())) {
                return Optional.empty();
            }
            Path directory = gameDirectory.resolve(name);
            try {
                Files.createDirectory(directory);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // The bounded store revalidates this existing direct child.
            }
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.equals(directory.toRealPath(LinkOption.NOFOLLOW_LINKS))
                    || !directory.equals(directory.toRealPath())) {
                return Optional.empty();
            }
            return Optional.of(directory);
        } catch (RuntimeException | java.io.IOException failure) {
            return Optional.empty();
        }
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BugReportCommandTree.register(event.getDispatcher(), commands,
                new BugReportCommandTree.ProviderSelector() {
                    @Override public void open() {
                        Minecraft.getInstance().setScreen(new ProviderCategoryScreen(commands));
                    }

                    @Override public BugReportCommandTree.SelectionResult open(
                            com.cybersammy.bugreport.api.identifier.ProviderId providerId) {
                        return commands.providerChoice(providerId).map(provider -> {
                            if (provider.supportState() == ProviderSupportState.DISABLED) {
                                return BugReportCommandTree.SelectionResult.UNAVAILABLE;
                            }
                            Minecraft.getInstance().setScreen(
                                    new ProviderCategoryScreen(commands, provider));
                            return BugReportCommandTree.SelectionResult.OPENED;
                        }).orElse(BugReportCommandTree.SelectionResult.UNKNOWN);
                    }
                });
    }
}
