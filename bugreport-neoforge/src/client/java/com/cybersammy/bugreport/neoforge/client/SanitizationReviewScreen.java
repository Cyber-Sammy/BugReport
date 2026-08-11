package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.core.workspace.WorkspaceReviewCoordinator;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sanitizes collected bytes off-thread and captures explicit per-artifact review decisions. */
final class SanitizationReviewScreen extends Screen {
    private static final int MAX_ARTIFACTS_PER_PAGE = 4;
    private static final int ARTIFACT_ROW_HEIGHT = 64;

    private final BugReportCommandService commands;
    private final BugReportCommandService.SanitizationExecutionRequest execution;
    private final Set<String> included = new HashSet<>();
    private final Set<String> explicitlyReviewed = new HashSet<>();
    private volatile boolean visible;
    private volatile boolean cancellationRequested;
    private boolean started;
    private boolean preparing;
    private boolean completed;
    private boolean openingArtifact;
    private boolean cancelInProgress;
    private int page;
    private BugReportCommandService.WorkspaceReviewRequest review;
    private Component status = Component.translatable("bugreport.screen.review.sanitizing");

    SanitizationReviewScreen(
            BugReportCommandService commands,
            BugReportCommandService.SanitizationExecutionRequest execution) {
        super(Component.translatable("bugreport.screen.review.title"));
        this.commands = commands;
        this.execution = execution;
    }

    @Override
    protected void init() {
        visible = true;
        rebuildReviewWidgets();
        if (!started) {
            started = true;
            startSanitization();
        }
    }

    private void startSanitization() {
        Thread.ofVirtual().name("bugreport-workspace-sanitization").start(() -> {
            try {
                var accepted = commands.executeSanitization(
                        execution, () -> cancellationRequested);
                Minecraft.getInstance().execute(() -> accepted.ifPresentOrElse(
                        this::presentReview, this::presentFailure));
            } catch (RuntimeException failure) {
                commands.failSanitization(execution);
                Minecraft.getInstance().execute(this::presentFailure);
            }
        });
    }

    private void presentReview(BugReportCommandService.WorkspaceReviewRequest request) {
        if (!visible) {
            return;
        }
        review = request;
        for (WorkspaceReviewCoordinator.ArtifactReview artifact : request.artifacts()) {
            if (artifact.status() != WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED
                    && artifact.inclusionDefault() == InclusionDefault.INCLUDED) {
                included.add(artifact.artifactName());
            }
        }
        status = Component.translatable(
                "bugreport.screen.review.ready", request.artifacts().size());
        rebuildReviewWidgets();
    }

    private void presentFailure() {
        if (!visible) {
            return;
        }
        status = Component.translatable("bugreport.screen.review.failed");
        rebuildReviewWidgets();
    }

    private void rebuildReviewWidgets() {
        clearWidgets();
        if (review != null) {
            addArtifactControls(review.artifacts());
            Button accept = Button.builder(
                            Component.translatable("bugreport.screen.review.accept"),
                            ignored -> prepareSelection())
                    .bounds(width / 2 - 120, height - 56, 240, 20).build();
            accept.active = !preparing && !completed && decisionsComplete();
            addRenderableWidget(accept);
        }
        Button cancel = Button.builder(Component.translatable("gui.cancel"), ignored -> cancel())
                .bounds(width / 2 - 58, height - 32, 116, 20).build();
        cancel.active = !completed && !cancelInProgress;
        addRenderableWidget(cancel);
        if (completed) {
            addRenderableWidget(Button.builder(Component.translatable("bugreport.screen.review.export"),
                            ignored -> openExport())
                    .bounds(width / 2 - 110, height - 56, 220, 20).build());
        }
    }

    private void addArtifactControls(List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        int pageSize = artifactsPerPage();
        int pageCount = Math.max(1, (artifacts.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pageCount - 1);
        int first = page * pageSize;
        int last = Math.min(first + pageSize, artifacts.size());
        for (int index = first; index < last; index++) {
            WorkspaceReviewCoordinator.ArtifactReview artifact = artifacts.get(index);
            boolean failed = artifact.status() == WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED;
            int rowTop = 62 + (index - first) * ARTIFACT_ROW_HEIGHT;
            Button include = Button.builder(
                            Component.translatable(
                                    included.contains(artifact.artifactName())
                                            ? "bugreport.screen.review.included"
                                            : "bugreport.screen.review.excluded"),
                            ignored -> toggleIncluded(artifact))
                    .bounds(width / 2 - 140, rowTop + 14, 135, 20)
                    .build();
            include.active = !failed && !preparing && !completed;
            addRenderableWidget(include);
            if (artifact.explicitReviewRequired()) {
                Button confirm = Button.builder(
                                Component.translatable(
                                        explicitlyReviewed.contains(artifact.artifactName())
                                                ? "bugreport.screen.review.confirmed"
                                                : "bugreport.screen.review.confirm"),
                                ignored -> toggleReviewed(artifact))
                        .bounds(width / 2 + 5, rowTop + 14, 135, 20)
                        .build();
                confirm.active = included.contains(artifact.artifactName())
                        && !preparing && !completed;
                addRenderableWidget(confirm);
            }
            addOpenControls(artifact, rowTop + 38);
        }
        if (pageCount > 1) {
            Button previous = Button.builder(Component.translatable("bugreport.screen.form.previous"),
                            ignored -> changePage(-1))
                    .bounds(width / 2 - 120, height - 82, 56, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.translatable("bugreport.screen.form.next"),
                            ignored -> changePage(1))
                    .bounds(width / 2 + 64, height - 82, 56, 20).build();
            next.active = page + 1 < pageCount;
            addRenderableWidget(next);
        }
    }

    private int artifactsPerPage() {
        int rowsAboveInstructions = Math.floorDiv(height - 244, ARTIFACT_ROW_HEIGHT) + 1;
        return Math.max(1, Math.min(MAX_ARTIFACTS_PER_PAGE, rowsAboveInstructions));
    }

    private void addOpenControls(
            WorkspaceReviewCoordinator.ArtifactReview artifact, int top) {
        if (artifact.status() == WorkspaceReviewCoordinator.ArtifactReviewStatus.SANITIZED) {
            Button original = Button.builder(
                            Component.translatable("bugreport.screen.review.open_original"),
                            ignored -> openArtifact(
                                    artifact,
                                    WorkspaceReviewCoordinator.ReviewArtifactVersion.ORIGINAL))
                    .bounds(width / 2 - 140, top, 135, 20)
                    .build();
            original.active = canOpenArtifact();
            addRenderableWidget(original);
            Button sanitized = Button.builder(
                            Component.translatable("bugreport.screen.review.open_sanitized"),
                            ignored -> openArtifact(
                                    artifact,
                                    WorkspaceReviewCoordinator.ReviewArtifactVersion.SANITIZED))
                    .bounds(width / 2 + 5, top, 135, 20)
                    .build();
            sanitized.active = canOpenArtifact();
            addRenderableWidget(sanitized);
            return;
        }
        Button artifactFile = Button.builder(
                        Component.translatable("bugreport.screen.review.open_artifact"),
                        ignored -> openArtifact(
                                artifact,
                                WorkspaceReviewCoordinator.ReviewArtifactVersion.ORIGINAL))
                .bounds(width / 2 - 140, top, 280, 20)
                .build();
        artifactFile.active = canOpenArtifact();
        addRenderableWidget(artifactFile);
    }

    private boolean canOpenArtifact() {
        return !preparing && !completed && !openingArtifact && !cancelInProgress;
    }

    private void openArtifact(
            WorkspaceReviewCoordinator.ArtifactReview artifact,
            WorkspaceReviewCoordinator.ReviewArtifactVersion version) {
        if (!canOpenArtifact()) {
            return;
        }
        openingArtifact = true;
        status = Component.translatable("bugreport.screen.review.opening");
        rebuildReviewWidgets();
        Thread.ofVirtual().name("bugreport-review-open").start(() -> {
            var file = commands.reviewArtifactFile(review, artifact.artifactName(), version);
            boolean opened = file.isPresent();
            if (opened) {
                Util.getPlatform().openPath(file.orElseThrow().path());
            }
            Minecraft.getInstance().execute(() -> finishOpen(opened, version));
        });
    }

    private void finishOpen(
            boolean opened, WorkspaceReviewCoordinator.ReviewArtifactVersion version) {
        if (!visible) {
            return;
        }
        openingArtifact = false;
        status = opened
                ? Component.translatable(
                        "bugreport.screen.review.opened",
                        Component.translatable(
                                version == WorkspaceReviewCoordinator.ReviewArtifactVersion.ORIGINAL
                                        ? "bugreport.screen.review.version.original"
                                        : "bugreport.screen.review.version.sanitized"))
                : Component.translatable("bugreport.screen.review.open_failed");
        rebuildReviewWidgets();
    }

    private void toggleIncluded(WorkspaceReviewCoordinator.ArtifactReview artifact) {
        if (!included.add(artifact.artifactName())) {
            included.remove(artifact.artifactName());
            explicitlyReviewed.remove(artifact.artifactName());
        }
        rebuildReviewWidgets();
    }

    private void toggleReviewed(WorkspaceReviewCoordinator.ArtifactReview artifact) {
        if (!explicitlyReviewed.add(artifact.artifactName())) {
            explicitlyReviewed.remove(artifact.artifactName());
        }
        rebuildReviewWidgets();
    }

    private boolean decisionsComplete() {
        return review.artifacts().stream()
                .filter(WorkspaceReviewCoordinator.ArtifactReview::explicitReviewRequired)
                .filter(artifact -> included.contains(artifact.artifactName()))
                .allMatch(artifact -> explicitlyReviewed.contains(artifact.artifactName()));
    }

    private void prepareSelection() {
        if (preparing || !decisionsComplete()) {
            return;
        }
        preparing = true;
        status = Component.translatable("bugreport.screen.review.preparing");
        rebuildReviewWidgets();
        Set<String> selected = Set.copyOf(included);
        Set<String> confirmations = Set.copyOf(explicitlyReviewed);
        Thread.ofVirtual().name("bugreport-workspace-review").start(() -> {
            try {
                var prepared = commands.confirmReview(
                        review,
                        new BugReportCommandService.ReviewDecision(selected, confirmations));
                Minecraft.getInstance().execute(() -> presentPrepared(prepared.isPresent()));
            } catch (RuntimeException failure) {
                Minecraft.getInstance().execute(this::presentPreparationFailure);
            }
        });
    }

    private void presentPrepared(boolean accepted) {
        if (!visible) {
            return;
        }
        preparing = false;
        completed = accepted;
        status = Component.translatable(
                accepted ? "bugreport.screen.review.accepted" : "bugreport.screen.review.failed");
        rebuildReviewWidgets();
    }

    private void presentPreparationFailure() {
        if (!visible) {
            return;
        }
        preparing = false;
        status = Component.translatable("bugreport.screen.review.failed");
        rebuildReviewWidgets();
    }

    private void changePage(int delta) {
        page += delta;
        rebuildReviewWidgets();
    }

    private void openExport() {
        commands.beginLocalExport(execution.sessionId().toString()).ifPresent(request ->
                minecraft.setScreen(new LocalExportScreen(commands, request, minecraft.gameDirectory.toPath())));
    }

    private void cancel() {
        if (cancelInProgress) {
            return;
        }
        cancellationRequested = true;
        cancelInProgress = true;
        status = Component.translatable("bugreport.screen.review.cancelling");
        rebuildReviewWidgets();
        Thread.ofVirtual().name("bugreport-review-cancel").start(() -> {
            boolean discarded = commands.discard(execution.sessionId().toString()).stream()
                    .anyMatch(message ->
                            "bugreport.command.discard.success".equals(message.translationKey()));
            Minecraft.getInstance().execute(() -> finishCancel(discarded));
        });
    }

    private void finishCancel(boolean discarded) {
        if (discarded) {
            visible = false;
            minecraft.setScreen(null);
            return;
        }
        cancellationRequested = false;
        cancelInProgress = false;
        status = Component.translatable("bugreport.screen.review.cancel_failed");
        rebuildReviewWidgets();
    }

    @Override
    public void onClose() {
        if (!completed) {
            cancel();
        } else {
            visible = false;
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 42,
                completed ? 0x60FF60 : 0xFFCC66);
        if (review != null) {
            renderArtifacts(graphics, review.artifacts());
            renderInstructions(graphics);
        }
    }

    private void renderInstructions(GuiGraphics graphics) {
        Component firstLine = Component.translatable(
                completed
                        ? "bugreport.screen.review.help.completed"
                        : "bugreport.screen.review.help.selection");
        graphics.drawCenteredString(font, firstLine, width / 2, height - 112, 0xD0D0D0);
        if (!completed) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("bugreport.screen.review.help.sensitive"),
                    width / 2,
                    height - 100,
                    0xFFCC66);
        }
    }

    private void renderArtifacts(
            GuiGraphics graphics, List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        int pageSize = artifactsPerPage();
        int first = page * pageSize;
        int last = Math.min(first + pageSize, artifacts.size());
        for (int index = first; index < last; index++) {
            var artifact = artifacts.get(index);
            Component row = Component.translatable(
                    "bugreport.screen.review.artifact",
                    index + 1,
                    Component.translatable(artifact.labelKey().value()),
                    artifact.contentType().name(),
                    artifact.privacy().name(),
                    artifact.byteCount(),
                    artifact.status().name(),
                    artifact.findingCount());
            graphics.drawString(
                    font,
                    row,
                    width / 2 - 140,
                    62 + (index - first) * ARTIFACT_ROW_HEIGHT,
                    0xE0E0E0);
        }
    }
}
