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
import java.util.List;
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
                        Minecraft.getInstance().setScreen(new ProviderCategoryScreen(
                                commands, BugReportClientMod.this::openLiveSessionTarget));
                    }

                    @Override public BugReportCommandTree.SelectionResult open(
                            com.cybersammy.bugreport.api.identifier.ProviderId providerId) {
                        return commands.providerChoice(providerId).map(provider -> {
                            if (provider.supportState() == ProviderSupportState.DISABLED) {
                                return BugReportCommandTree.SelectionResult.UNAVAILABLE;
                            }
                            Minecraft.getInstance().setScreen(
                                    new ProviderCategoryScreen(
                                            commands,
                                            provider,
                                            BugReportClientMod.this::openLiveSessionTarget));
                            return BugReportCommandTree.SelectionResult.OPENED;
                        }).orElse(BugReportCommandTree.SelectionResult.UNKNOWN);
                    }
                }, new BugReportCommandTree.SessionOpener() {
                    @Override
                    public List<BugReportCommandService.Message> openLatest() {
                        return openLatestLiveSession();
                    }

                    @Override
                    public List<BugReportCommandService.Message> open(String sessionId) {
                        return openLiveSession(sessionId);
                    }
                });
    }

    private List<BugReportCommandService.Message> openLatestLiveSession() {
        return commands.latestSessionIdForOpen()
                .map(this::openLiveSession)
                .orElseGet(() -> List.of(new BugReportCommandService.Message(
                        "bugreport.command.error.unknown_session")));
    }

    private List<BugReportCommandService.Message> openLiveSession(String sessionValue) {
        BugReportCommandService.SessionResumeStatus status = openLiveSessionTarget(sessionValue);
        if (status != BugReportCommandService.SessionResumeStatus.READY) {
            return List.of(new BugReportCommandService.Message(switch (status) {
                case UNKNOWN_SESSION -> "bugreport.command.error.unknown_session";
                case BUSY -> "bugreport.command.open.busy";
                case UNAVAILABLE -> "bugreport.command.open.unavailable";
                case TERMINAL -> "bugreport.command.open.terminal";
                case READY -> throw new IllegalStateException("Ready resume has no target");
            }));
        }
        return List.of(new BugReportCommandService.Message(
                "bugreport.command.open.success", sessionValue));
    }

    private BugReportCommandService.SessionResumeStatus openLiveSessionTarget(String sessionValue) {
        BugReportCommandService.SessionResumeResult resumed = commands.resumeSession(sessionValue);
        if (resumed.status() != BugReportCommandService.SessionResumeStatus.READY) {
            return resumed.status();
        }
        return openTarget(resumed.target().orElseThrow())
                ? BugReportCommandService.SessionResumeStatus.READY
                : BugReportCommandService.SessionResumeStatus.UNAVAILABLE;
    }

    private boolean openTarget(BugReportCommandService.SessionResumeTarget target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (target instanceof BugReportCommandService.FormResumeTarget form) {
            return attachForm(form.sessionId(), form.submission())
                    .map(screen -> {
                        minecraft.setScreen(screen);
                        return true;
                    })
                    .orElse(false);
        }
        if (target instanceof BugReportCommandService.CollectionPlanResumeTarget planning) {
            return attachForm(planning.request().sessionId(), planning.submission())
                    .map(form -> {
                        minecraft.setScreen(new CollectionPlanScreen(
                                commands,
                                planning.request(),
                                form,
                                planning.reviewedPlan().orElse(null)));
                        return true;
                    })
                    .orElse(false);
        }
        if (target instanceof BugReportCommandService.SanitizationResumeTarget sanitization) {
            minecraft.setScreen(new SanitizationReviewScreen(commands, sanitization.request()));
            return true;
        }
        if (target instanceof BugReportCommandService.ReviewResumeTarget review) {
            minecraft.setScreen(new SanitizationReviewScreen(commands, review.request()));
            return true;
        }
        if (target instanceof BugReportCommandService.ExportPreparationResumeTarget export) {
            minecraft.setScreen(new LocalExportScreen(
                    commands, export.request(), minecraft.gameDirectory.toPath()));
            return true;
        }
        if (target instanceof BugReportCommandService.ExportResumeTarget export) {
            minecraft.setScreen(new LocalExportScreen(
                    commands, export.request(), minecraft.gameDirectory.toPath()));
            return true;
        }
        return false;
    }

    private Optional<CategoryFormScreen> attachForm(
            com.cybersammy.bugreport.core.session.ReportSessionId sessionId,
            com.cybersammy.bugreport.core.form.FormSubmission submission) {
        return commands.form(sessionId.toString()).flatMap(form ->
                commands.providerChoice(form.providerId()).map(provider -> {
                    ProviderCategoryScreen selector =
                            new ProviderCategoryScreen(
                                    commands, provider, this::openLiveSessionTarget);
                    return selector.attachLiveForm(sessionId, provider, submission);
                }));
    }
}
