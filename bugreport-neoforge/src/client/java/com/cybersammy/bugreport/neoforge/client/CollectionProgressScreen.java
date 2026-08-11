package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.workspace.CollectionProgressSnapshot;
import com.cybersammy.bugreport.core.workspace.CategoryCollectionCoordinator;
import com.cybersammy.bugreport.core.workspace.CategoryCollectionResult;
import com.cybersammy.bugreport.core.workspace.CategoryCollectionRunControl;
import com.cybersammy.bugreport.core.workspace.FileReportWorkspaceStore;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Executes an accepted reviewed plan outside the render thread and exposes safe progress. */
final class CollectionProgressScreen extends Screen {
    private final BugReportCommandService commands;
    private final BugReportCommandService.CollectionExecutionRequest request;
    private final CategoryCollectionRunControl control = new CategoryCollectionRunControl();
    private volatile boolean visible;
    private boolean started;
    private boolean terminal;
    private CategoryCollectionResult terminalResult;
    private Component status = Component.translatable("bugreport.screen.collection.starting");

    CollectionProgressScreen(
            BugReportCommandService commands,
            BugReportCommandService.CollectionExecutionRequest request) {
        super(Component.translatable("bugreport.screen.collection.title"));
        this.commands = commands;
        this.request = request;
    }

    @Override
    protected void init() {
        visible = true;
        Button cancel = Button.builder(Component.translatable("gui.cancel"), ignored -> requestCancellation())
                .bounds(width / 2 - 58, height - 32, 116, 20).build();
        cancel.active = !terminal;
        addRenderableWidget(cancel);
        if (terminalResult != null
                && (terminalResult.status() == CategoryCollectionResult.Status.COMPLETE
                        || terminalResult.status() == CategoryCollectionResult.Status.PARTIAL)) {
            addRenderableWidget(Button.builder(
                            Component.translatable("bugreport.screen.collection.review"),
                            ignored -> continueToReview())
                    .bounds(width / 2 - 58, height - 56, 116, 20).build());
        }
        if (!started) {
            started = true;
            startCollection(Minecraft.getInstance().gameDirectory.toPath());
        }
    }

    private void startCollection(Path gameDirectory) {
        Thread.ofVirtual().name("bugreport-file-collection").start(() -> {
            try {
                Path absoluteGameDirectory = gameDirectory.toAbsolutePath();
                ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(absoluteGameDirectory);
                FileReportWorkspaceStore store = new FileReportWorkspaceStore(
                        absoluteGameDirectory.resolve("bugreport").resolve("workspaces"));
                ReportWorkspace workspace = store.create(request.sessionId());
                CategoryCollectionResult result = CategoryCollectionCoordinator.collect(
                        BugReportMod.providerRegistry(),
                        request.reviewedPlan(),
                        request.screenshots(),
                        roots,
                        SupportedSide.PHYSICAL_CLIENT,
                        workspace,
                        control,
                        NeoForgeGameThreadDispatchers.shared()
                                .dispatcher(SupportedSide.PHYSICAL_CLIENT),
                        absoluteGameDirectory.resolve("screenshots"));
                boolean accepted = commands.acceptCollectionResult(request, result, workspace);
                Minecraft.getInstance().execute(() -> presentResult(result, accepted));
            } catch (RuntimeException exception) {
                commands.failCollectionSetup(request);
                Minecraft.getInstance().execute(this::presentFailure);
            } catch (Error fatal) {
                commands.failCollectionSetup(request);
                Minecraft.getInstance().execute(this::presentFailure);
                throw fatal;
            }
        });
    }

    private void requestCancellation() {
        if (control.requestCancellation()) {
            status = Component.translatable("bugreport.screen.collection.cancelling");
        }
    }

    private void presentResult(CategoryCollectionResult result, boolean accepted) {
        if (!visible || !accepted) {
            return;
        }
        terminal = true;
        terminalResult = result;
        status = Component.translatable(
                "bugreport.screen.collection.result",
                result.status().name(),
                result.files().progress().successfulFiles(),
                result.files().progress().failedFiles(),
                result.files().progress().cancelledFiles());
        rebuildWidgets();
    }

    private void continueToReview() {
        commands.beginSanitization(request.sessionId().toString()).ifPresentOrElse(
                sanitization -> {
                    visible = false;
                    minecraft.setScreen(new SanitizationReviewScreen(commands, sanitization));
                }, this::presentFailure);
    }

    private void presentFailure() {
        if (!visible) {
            return;
        }
        terminal = true;
        status = Component.translatable("bugreport.screen.collection.failed");
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (!terminal) {
            control.requestCancellation();
        }
        visible = false;
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        var combined = control.progress();
        CollectionProgressSnapshot progress = combined.fileProgress();
        Component detail = Component.translatable(
                "bugreport.screen.collection.progress",
                combined.phase().name(),
                progress.completedFiles() + combined.completedGenerators(),
                progress.totalFiles() + combined.totalGenerators(),
                progress.processedBytes(), progress.plannedBytes());
        graphics.drawCenteredString(font, detail, width / 2, 48, 0xE0E0E0);
        graphics.drawCenteredString(font, status, width / 2, 70,
                terminal ? 0x60FF60 : 0xFFCC66);
    }
}
