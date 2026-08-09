package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.core.workspace.WorkspaceReviewCoordinator;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sanitizes collected bytes off-thread and captures explicit per-artifact review decisions. */
final class SanitizationReviewScreen extends Screen {
    private final BugReportCommandService commands;
    private final BugReportCommandService.SanitizationExecutionRequest execution;
    private final Set<String> included = new HashSet<>();
    private final Set<String> explicitlyReviewed = new HashSet<>();
    private volatile boolean visible;
    private volatile boolean cancellationRequested;
    private boolean started;
    private boolean preparing;
    private boolean completed;
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
        for (WorkspaceReviewCoordinator.ArtifactReview artifact : request.batch().artifacts()) {
            if (artifact.status() != WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED
                    && artifact.inclusionDefault() == InclusionDefault.INCLUDED) {
                included.add(artifact.artifactName());
            }
        }
        status = Component.translatable(
                "bugreport.screen.review.ready", request.batch().artifacts().size());
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
            addArtifactControls(review.batch().artifacts());
            Button accept = Button.builder(
                            Component.translatable("bugreport.screen.review.accept"),
                            ignored -> prepareSelection())
                    .bounds(width / 2 - 120, height - 56, 240, 20).build();
            accept.active = !preparing && !completed && decisionsComplete();
            addRenderableWidget(accept);
        }
        Button cancel = Button.builder(Component.translatable("gui.cancel"), ignored -> cancel())
                .bounds(width / 2 - 58, height - 32, 116, 20).build();
        cancel.active = !completed;
        addRenderableWidget(cancel);
        if (completed) {
            addRenderableWidget(Button.builder(Component.translatable("bugreport.screen.review.export"),
                            ignored -> openExport())
                    .bounds(width / 2 - 110, height - 56, 220, 20).build());
        }
    }

    private void addArtifactControls(List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        int pageSize = 4;
        int pageCount = Math.max(1, (artifacts.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pageCount - 1);
        int first = page * pageSize;
        int last = Math.min(first + pageSize, artifacts.size());
        for (int index = first; index < last; index++) {
            WorkspaceReviewCoordinator.ArtifactReview artifact = artifacts.get(index);
            boolean failed = artifact.status() == WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED;
            Button include = Button.builder(
                            Component.translatable(
                                    included.contains(artifact.artifactName())
                                            ? "bugreport.screen.review.exclude"
                                            : "bugreport.screen.review.include",
                                    index + 1),
                            ignored -> toggleIncluded(artifact))
                    .bounds(width / 2 - 140, 62 + (index - first) * 40, 135, 20).build();
            include.active = !failed && !preparing && !completed;
            addRenderableWidget(include);
            if (artifact.explicitReviewRequired()) {
                Button confirm = Button.builder(
                                Component.translatable(
                                        explicitlyReviewed.contains(artifact.artifactName())
                                                ? "bugreport.screen.review.confirmed"
                                                : "bugreport.screen.review.confirm"),
                                ignored -> toggleReviewed(artifact))
                        .bounds(width / 2 + 5, 62 + (index - first) * 40, 135, 20).build();
                confirm.active = included.contains(artifact.artifactName())
                        && !preparing && !completed;
                addRenderableWidget(confirm);
            }
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
        return review.batch().artifacts().stream()
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
        cancellationRequested = true;
        visible = false;
        commands.discard(execution.sessionId().toString());
        minecraft.setScreen(null);
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 42,
                completed ? 0x60FF60 : 0xFFCC66);
        if (review != null) {
            renderArtifacts(graphics, review.batch().artifacts());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderArtifacts(
            GuiGraphics graphics, List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        int first = page * 4;
        int last = Math.min(first + 4, artifacts.size());
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
            graphics.drawString(font, row, width / 2 - 140, 86 + (index - first) * 40, 0xE0E0E0);
        }
    }
}
