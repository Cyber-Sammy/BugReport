package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.core.workspace.WorkspaceReviewCoordinator;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sanitizes collected bytes off-thread and captures explicit per-artifact review decisions. */
final class SanitizationReviewScreen extends Screen {
    private static final int ARTIFACT_ROW_HEIGHT = 64;
    private static final int PANEL_MAX_WIDTH = 760;
    private static final int PANEL_SIDE_PADDING = 10;
    private static final int SCROLLBAR_WIDTH = 6;

    private final BugReportCommandService commands;
    private final BugReportCommandService.SanitizationExecutionRequest execution;
    private final ReportSessionId sessionId;
    private final Set<String> included = new HashSet<>();
    private final Set<String> explicitlyReviewed = new HashSet<>();
    private volatile boolean visible;
    private volatile boolean cancellationRequested;
    private boolean started;
    private boolean preparing;
    private boolean completed;
    private boolean openingArtifact;
    private boolean cancelInProgress;
    private boolean draggingScrollbar;
    private ReviewSection activeSection;
    private final Map<ReviewSection, Integer> scrollOffsets =
            new EnumMap<>(ReviewSection.class);
    private BugReportCommandService.WorkspaceReviewRequest review;
    private Component status = Component.translatable("bugreport.screen.review.sanitizing");

    SanitizationReviewScreen(
            BugReportCommandService commands,
            BugReportCommandService.SanitizationExecutionRequest execution) {
        super(Component.translatable("bugreport.screen.review.title"));
        this.commands = commands;
        this.execution = execution;
        sessionId = execution.sessionId();
    }

    SanitizationReviewScreen(
            BugReportCommandService commands,
            BugReportCommandService.WorkspaceReviewRequest review) {
        super(Component.translatable("bugreport.screen.review.title"));
        this.commands = commands;
        execution = null;
        sessionId = review.sessionId();
        started = true;
        installReview(review);
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
        installReview(request);
        rebuildReviewWidgets();
    }

    private void installReview(BugReportCommandService.WorkspaceReviewRequest request) {
        review = request;
        included.clear();
        explicitlyReviewed.clear();
        commands.reviewDecisionDraft(request).ifPresentOrElse(
                decision -> {
                    included.addAll(decision.includedArtifacts());
                    explicitlyReviewed.addAll(decision.explicitlyReviewedArtifacts());
                },
                () -> {
                    for (WorkspaceReviewCoordinator.ArtifactReview artifact : request.artifacts()) {
                        if (artifact.status()
                                        != WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED
                                && artifact.inclusionDefault() == InclusionDefault.INCLUDED) {
                            included.add(artifact.artifactName());
                        }
                    }
                    saveDecisionDraft();
                });
        status = Component.translatable(
                "bugreport.screen.review.ready", request.artifacts().size());
        ensureActiveSection(request.artifacts());
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
            List<ReviewSection> sections = availableSections(review.artifacts());
            ensureActiveSection(review.artifacts());
            addSectionTabs(sections);
            addArtifactControls(sectionArtifacts(review.artifacts(), activeSection));
            if (!completed) {
                Button accept = Button.builder(
                                Component.translatable("bugreport.screen.review.accept"),
                                ignored -> prepareSelection())
                        .bounds(width / 2 - 120, height - 56, 240, 20).build();
                accept.active = !preparing && decisionsComplete();
                addRenderableWidget(accept);
            }
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

    private void addSectionTabs(List<ReviewSection> sections) {
        if (sections.isEmpty()) {
            return;
        }
        int gap = 4;
        int availableWidth = Math.min(PANEL_MAX_WIDTH, width - 32);
        int tabWidth = Math.min(210, (availableWidth - gap * (sections.size() - 1)) / sections.size());
        int firstLeft = width / 2 - (tabWidth * sections.size() + gap * (sections.size() - 1)) / 2;
        for (int index = 0; index < sections.size(); index++) {
            ReviewSection section = sections.get(index);
            int count = sectionArtifacts(review.artifacts(), section).size();
            Button tab = Button.builder(
                            Component.translatable(section.translationKey, count),
                            ignored -> selectSection(section))
                    .bounds(firstLeft + index * (tabWidth + gap), 60, tabWidth, 20)
                    .build();
            tab.active = section != activeSection && !preparing && !completed;
            addRenderableWidget(tab);
        }
    }

    private void addArtifactControls(List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        ReviewPanel panel = reviewPanel();
        int visibleRows = visibleRows(panel);
        int first = clampedScrollOffset(artifacts.size(), visibleRows);
        int last = Math.min(first + visibleRows, artifacts.size());
        int controlsWidth = panel.width - PANEL_SIDE_PADDING * 2 - SCROLLBAR_WIDTH - 8;
        int halfWidth = (controlsWidth - 10) / 2;
        for (int index = first; index < last; index++) {
            WorkspaceReviewCoordinator.ArtifactReview artifact = artifacts.get(index);
            boolean failed = artifact.status() == WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED;
            int rowTop = panel.contentTop + (index - first) * ARTIFACT_ROW_HEIGHT;
            Button include = Button.builder(
                            Component.translatable(
                                    included.contains(artifact.artifactName())
                                            ? "bugreport.screen.review.included"
                                            : "bugreport.screen.review.excluded"),
                            ignored -> toggleIncluded(artifact))
                    .bounds(panel.left + PANEL_SIDE_PADDING, rowTop + 14, halfWidth, 20)
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
                        .bounds(panel.left + PANEL_SIDE_PADDING + halfWidth + 10,
                                rowTop + 14, halfWidth, 20)
                        .build();
                confirm.active = included.contains(artifact.artifactName())
                        && !preparing && !completed;
                addRenderableWidget(confirm);
            }
            addOpenControls(artifact, rowTop + 38, panel.left + PANEL_SIDE_PADDING,
                    controlsWidth, halfWidth);
        }
    }

    private void addOpenControls(
            WorkspaceReviewCoordinator.ArtifactReview artifact,
            int top,
            int left,
            int controlsWidth,
            int halfWidth) {
        if (artifact.status() == WorkspaceReviewCoordinator.ArtifactReviewStatus.SANITIZED) {
            Button original = Button.builder(
                            Component.translatable("bugreport.screen.review.open_original"),
                            ignored -> openArtifact(
                                    artifact,
                                    WorkspaceReviewCoordinator.ReviewArtifactVersion.ORIGINAL))
                    .bounds(left, top, halfWidth, 20)
                    .build();
            original.active = canOpenArtifact();
            addRenderableWidget(original);
            Button sanitized = Button.builder(
                            Component.translatable("bugreport.screen.review.open_sanitized"),
                            ignored -> openArtifact(
                                    artifact,
                                    WorkspaceReviewCoordinator.ReviewArtifactVersion.SANITIZED))
                    .bounds(left + halfWidth + 10, top, halfWidth, 20)
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
                .bounds(left, top, controlsWidth, 20)
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
        saveDecisionDraft();
        rebuildReviewWidgets();
    }

    private void toggleReviewed(WorkspaceReviewCoordinator.ArtifactReview artifact) {
        if (!explicitlyReviewed.add(artifact.artifactName())) {
            explicitlyReviewed.remove(artifact.artifactName());
        }
        saveDecisionDraft();
        rebuildReviewWidgets();
    }

    private void saveDecisionDraft() {
        if (review != null) {
            commands.saveReviewDecisionDraft(
                    review,
                    new BugReportCommandService.ReviewDecision(included, explicitlyReviewed));
        }
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

    private void selectSection(ReviewSection section) {
        activeSection = section;
        draggingScrollbar = false;
        rebuildReviewWidgets();
    }

    private void openExport() {
        commands.beginLocalExport(sessionId.toString()).ifPresent(request ->
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
            boolean discarded = commands.discard(sessionId.toString()).stream()
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
        visible = false;
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (review != null) {
            renderReviewPanelBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 42,
                completed ? 0x60FF60 : 0xFFCC66);
        if (review != null) {
            renderReviewPanel(graphics);
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

    private void renderReviewPanel(GuiGraphics graphics) {
        List<WorkspaceReviewCoordinator.ArtifactReview> artifacts =
                sectionArtifacts(review.artifacts(), activeSection);
        ReviewPanel panel = reviewPanel();
        graphics.drawString(
                font,
                Component.translatable("bugreport.screen.review.panel_hint"),
                panel.left + PANEL_SIDE_PADDING,
                panel.top + 7,
                0xB0B0B0);

        int visibleRows = visibleRows(panel);
        int first = clampedScrollOffset(artifacts.size(), visibleRows);
        int last = Math.min(first + visibleRows, artifacts.size());
        for (int index = first; index < last; index++) {
            var artifact = artifacts.get(index);
            Component row = Component.translatable(
                    "bugreport.screen.review.artifact",
                    Component.translatable(artifact.labelKey().value()),
                    artifact.artifactName(),
                    artifact.contentType().name(),
                    artifact.privacy().name(),
                    artifact.byteCount(),
                    artifact.status().name(),
                    artifact.findingCount());
            int rowTop = panel.contentTop + (index - first) * ARTIFACT_ROW_HEIGHT;
            graphics.enableScissor(
                    panel.left + 1,
                    rowTop,
                    panel.right() - SCROLLBAR_WIDTH - 9,
                    rowTop + 12);
            graphics.drawString(
                    font,
                    row,
                    panel.left + PANEL_SIDE_PADDING,
                    rowTop,
                    0xE0E0E0);
            graphics.disableScissor();
        }
        renderScrollbar(graphics, panel, artifacts.size(), visibleRows, first);
    }

    private void renderReviewPanelBackground(GuiGraphics graphics) {
        ReviewPanel panel = reviewPanel();
        graphics.fill(panel.left, panel.top, panel.right(), panel.bottom, 0x66000000);
        graphics.renderOutline(panel.left, panel.top, panel.width, panel.height(), 0xFF808080);
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            ReviewPanel panel,
            int totalRows,
            int visibleRows,
            int offset) {
        if (totalRows <= visibleRows) {
            return;
        }
        int trackLeft = panel.right() - SCROLLBAR_WIDTH - 4;
        int trackTop = panel.contentTop;
        int trackHeight = panel.bottom - panel.contentTop - 6;
        int thumbHeight = Math.max(20, trackHeight * visibleRows / totalRows);
        int travel = trackHeight - thumbHeight;
        int maximumOffset = totalRows - visibleRows;
        int thumbTop = trackTop + travel * offset / maximumOffset;
        graphics.fill(trackLeft, trackTop, trackLeft + SCROLLBAR_WIDTH, trackTop + trackHeight,
                0xFF303030);
        graphics.fill(trackLeft, thumbTop, trackLeft + SCROLLBAR_WIDTH, thumbTop + thumbHeight,
                0xFFA0A0A0);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (review != null && reviewPanel().contains(mouseX, mouseY) && scrollBy(verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && review != null && scrollbarContains(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean scrollBy(double verticalAmount) {
        if (verticalAmount == 0) {
            return false;
        }
        List<WorkspaceReviewCoordinator.ArtifactReview> artifacts = activeSectionArtifacts();
        int visibleRows = visibleRows(reviewPanel());
        int current = clampedScrollOffset(artifacts.size(), visibleRows);
        int maximum = Math.max(0, artifacts.size() - visibleRows);
        int next = Math.max(0, Math.min(maximum, current + (verticalAmount < 0 ? 1 : -1)));
        if (next == current) {
            return false;
        }
        scrollOffsets.put(activeSection, next);
        rebuildReviewWidgets();
        return true;
    }

    private boolean scrollbarContains(double mouseX, double mouseY) {
        ReviewPanel panel = reviewPanel();
        int visibleRows = visibleRows(panel);
        if (activeSectionArtifacts().size() <= visibleRows) {
            return false;
        }
        int left = panel.right() - SCROLLBAR_WIDTH - 6;
        return mouseX >= left && mouseX < panel.right()
                && mouseY >= panel.contentTop && mouseY < panel.bottom;
    }

    private void updateScrollFromMouse(double mouseY) {
        List<WorkspaceReviewCoordinator.ArtifactReview> artifacts = activeSectionArtifacts();
        ReviewPanel panel = reviewPanel();
        int visibleRows = visibleRows(panel);
        int maximumOffset = Math.max(0, artifacts.size() - visibleRows);
        if (maximumOffset == 0) {
            return;
        }
        int trackHeight = panel.bottom - panel.contentTop - 6;
        int thumbHeight = Math.max(20, trackHeight * visibleRows / artifacts.size());
        int travel = trackHeight - thumbHeight;
        double position = Math.max(0, Math.min(travel, mouseY - panel.contentTop - thumbHeight / 2.0));
        int next = (int) Math.round(position * maximumOffset / travel);
        if (next != scrollOffsets.getOrDefault(activeSection, 0)) {
            scrollOffsets.put(activeSection, next);
            rebuildReviewWidgets();
            draggingScrollbar = true;
        }
    }

    private void ensureActiveSection(List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        List<ReviewSection> available = availableSections(artifacts);
        if (activeSection == null || !available.contains(activeSection)) {
            activeSection = available.isEmpty() ? null : available.getFirst();
        }
    }

    private List<ReviewSection> availableSections(
            List<WorkspaceReviewCoordinator.ArtifactReview> artifacts) {
        List<ReviewSection> available = new ArrayList<>();
        for (ReviewSection section : ReviewSection.values()) {
            if (!sectionArtifacts(artifacts, section).isEmpty()) {
                available.add(section);
            }
        }
        return List.copyOf(available);
    }

    private List<WorkspaceReviewCoordinator.ArtifactReview> sectionArtifacts(
            List<WorkspaceReviewCoordinator.ArtifactReview> artifacts, ReviewSection section) {
        if (section == null) {
            return List.of();
        }
        return artifacts.stream()
                .filter(artifact -> section.accepts(artifact.contentType()))
                .toList();
    }

    private List<WorkspaceReviewCoordinator.ArtifactReview> activeSectionArtifacts() {
        return sectionArtifacts(review.artifacts(), activeSection);
    }

    private int clampedScrollOffset(int artifactCount, int visibleRows) {
        if (activeSection == null) {
            return 0;
        }
        int maximum = Math.max(0, artifactCount - visibleRows);
        int current = scrollOffsets.getOrDefault(activeSection, 0);
        int clamped = Math.max(0, Math.min(maximum, current));
        scrollOffsets.put(activeSection, clamped);
        return clamped;
    }

    private int visibleRows(ReviewPanel panel) {
        return Math.max(1, (panel.bottom - panel.contentTop - 6) / ARTIFACT_ROW_HEIGHT);
    }

    private ReviewPanel reviewPanel() {
        int panelWidth = Math.max(280, Math.min(PANEL_MAX_WIDTH, width - 32));
        int left = width / 2 - panelWidth / 2;
        int top = 86;
        int bottom = Math.max(top + ARTIFACT_ROW_HEIGHT + 30, height - 124);
        return new ReviewPanel(left, top, panelWidth, bottom, top + 24);
    }

    private enum ReviewSection {
        TEXT("bugreport.screen.review.section.text", DiagnosticContentType.TEXT),
        JSON("bugreport.screen.review.section.json", DiagnosticContentType.JSON),
        BINARY("bugreport.screen.review.section.binary", DiagnosticContentType.BINARY);

        private final String translationKey;
        private final DiagnosticContentType contentType;

        ReviewSection(String translationKey, DiagnosticContentType contentType) {
            this.translationKey = translationKey;
            this.contentType = contentType;
        }

        private boolean accepts(DiagnosticContentType candidate) {
            return contentType == candidate;
        }
    }

    private record ReviewPanel(int left, int top, int width, int bottom, int contentTop) {
        private int right() {
            return left + width;
        }

        private int height() {
            return bottom - top;
        }

        private boolean contains(double x, double y) {
            return x >= left && x < right() && y >= top && y < bottom;
        }
    }
}
