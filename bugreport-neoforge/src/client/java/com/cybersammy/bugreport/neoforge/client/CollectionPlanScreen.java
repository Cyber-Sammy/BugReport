package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.BuiltInSourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategoryCollectionPlan;
import com.cybersammy.bugreport.core.source.CategoryCollectionPlanner;
import com.cybersammy.bugreport.core.source.FileSourcePlan;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.SourceSelectionPlan;
import com.cybersammy.bugreport.core.source.UnavailableSourcePlan;
import com.cybersammy.bugreport.core.source.UserSelectionSourcePlan;
import com.cybersammy.bugreport.neoforge.command.ScreenshotSelectionScreen;
import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Displays a bounded source plan after a trusted form submission is accepted. */
final class CollectionPlanScreen extends Screen {
    private static final int MAX_CHOICES_PER_PAGE = 5;
    private static final int CHOICE_ROW_HEIGHT = 40;

    private final BugReportCommandService commands;
    private final BugReportCommandService.CollectionPlanRequest request;
    private final CategoryFormScreen formScreen;
    private volatile boolean visible;
    private boolean planningStarted;
    private CategorySourcePlan plan;
    private CategoryCollectionPlan collectionPlan;
    private Set<com.cybersammy.bugreport.api.identifier.DiagnosticSourceId> includedSourceIds = Set.of();
    private Set<com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId> includedGeneratorIds = Set.of();
    private boolean planning = true;
    private boolean planningAuthorityReleased;
    private boolean selectionAccepted;
    private boolean emptySelectionConfirmationPending;
    private int sourcePage;
    private Component status = Component.translatable("bugreport.screen.plan.planning");

    CollectionPlanScreen(
            BugReportCommandService commands,
            BugReportCommandService.CollectionPlanRequest request,
            CategoryFormScreen formScreen) {
        super(Component.translatable("bugreport.screen.plan.title"));
        this.commands = commands;
        this.request = request;
        this.formScreen = formScreen;
    }

    @Override
    protected void init() {
        visible = true;
        rebuildPlanWidgets();
        if (!planningStarted) {
            planningStarted = true;
            startPlanning(Minecraft.getInstance().gameDirectory.toPath());
        }
    }

    private void rebuildPlanWidgets() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> returnToForm())
                .bounds(width / 2 - 120, height - 32, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancel())
                .bounds(width / 2 + 4, height - 32, 116, 20).build());
        if (plan == null) {
            return;
        }
        addSourceControls();
        Component acceptLabel = emptySelectionConfirmationPending
                ? Component.translatable("bugreport.screen.plan.continue_without")
                : Component.translatable(
                        "bugreport.screen.plan.accept", selectedChoiceCount());
        Button accept = Button.builder(acceptLabel,
                        ignored -> acceptSelection())
                .bounds(width / 2 - 90, height - 56, 180, 20).build();
        accept.active = !selectionAccepted;
        addRenderableWidget(accept);
    }

    private void startPlanning(Path gameDirectory) {
        Thread.ofVirtual().name("bugreport-source-plan").start(() -> {
            try {
                CategoryCollectionPlan planned = new CategoryCollectionPlanner(
                        BugReportMod.providerRegistry(),
                        ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                        SupportedSide.PHYSICAL_CLIENT).plan(request.providerId(), request.categoryId());
                Minecraft.getInstance().execute(() -> presentPlan(planned));
            } catch (RuntimeException exception) {
                boolean rolledBack = commands.returnToForm(request);
                Minecraft.getInstance().execute(() -> presentPlanningFailure(rolledBack));
            }
        });
    }

    private void presentPlan(CategoryCollectionPlan planned) {
        if (!visible) {
            return;
        }
        collectionPlan = planned;
        plan = planned.sources();
        ReviewedCollectionPlan defaults = ReviewedCollectionPlan.defaults(planned);
        includedSourceIds = new LinkedHashSet<>(defaults.includedSourceIds());
        includedGeneratorIds = new LinkedHashSet<>(defaults.includedGeneratorIds());
        planning = false;
        status = Component.translatable(
                "bugreport.screen.plan.ready",
                availableChoiceCount(planned),
                knownBytes(planned.sources()));
        rebuildPlanWidgets();
    }

    private void presentPlanningFailure(boolean rolledBack) {
        if (!visible) {
            return;
        }
        planningAuthorityReleased = rolledBack;
        planning = false;
        status = Component.translatable("bugreport.screen.plan.failed");
    }

    private void addSourceControls() {
        List<com.cybersammy.bugreport.core.source.CoordinatedSourcePlan> sources = plan.sources();
        var generators = collectionPlan.generators();
        int pageSize = choicesPerPage();
        int choiceCount = sources.size() + generators.size();
        int pageCount = Math.max(1, (choiceCount + pageSize - 1) / pageSize);
        sourcePage = Math.min(sourcePage, pageCount - 1);
        int first = sourcePage * pageSize;
        int last = Math.min(first + pageSize, choiceCount);
        for (int index = first; index < last; index++) {
            if (index >= sources.size()) {
                var generator = generators.get(index - sources.size());
                var generatorId = generator.id();
                boolean included = includedGeneratorIds.contains(generatorId);
                Button toggle = Button.builder(
                                Component.translatable(
                                        included
                                                ? "bugreport.screen.plan.selected"
                                                : "bugreport.screen.plan.not_selected"),
                                ignored -> toggleGenerator(generatorId))
                        .bounds(
                                width / 2 - 140,
                                76 + (index - first) * CHOICE_ROW_HEIGHT,
                                280,
                                20)
                        .build();
                toggle.active = collectionPlan.isAvailable(generator) && !selectionAccepted;
                addRenderableWidget(toggle);
                continue;
            }
            com.cybersammy.bugreport.core.source.CoordinatedSourcePlan source = sources.get(index);
            var sourceId = source.provenance().sourceId();
            boolean selectable = !(source.selection() instanceof UnavailableSourcePlan);
            boolean included = includedSourceIds.contains(sourceId);
            Button toggle = Button.builder(
                            Component.translatable(
                                    included
                                            ? "bugreport.screen.plan.selected"
                                            : "bugreport.screen.plan.not_selected"),
                            ignored -> toggleSource(sourceId))
                    .bounds(
                            width / 2 - 140,
                            76 + (index - first) * CHOICE_ROW_HEIGHT,
                            280,
                            20)
                    .build();
            toggle.active = selectable && !selectionAccepted;
            addRenderableWidget(toggle);
        }
        if (pageCount > 1) {
            Button previous = Button.builder(Component.translatable("bugreport.screen.form.previous"),
                            ignored -> changePage(-1))
                    .bounds(width / 2 - 120, height - 82, 56, 20).build();
            previous.active = sourcePage > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.translatable("bugreport.screen.form.next"),
                            ignored -> changePage(1))
                    .bounds(width / 2 + 64, height - 82, 56, 20).build();
            next.active = sourcePage + 1 < pageCount;
            addRenderableWidget(next);
        }
    }

    private int choicesPerPage() {
        int rowsAboveNavigation = Math.floorDiv(height - 190, CHOICE_ROW_HEIGHT) + 1;
        return Math.max(1, Math.min(MAX_CHOICES_PER_PAGE, rowsAboveNavigation));
    }

    private void toggleSource(com.cybersammy.bugreport.api.identifier.DiagnosticSourceId sourceId) {
        LinkedHashSet<com.cybersammy.bugreport.api.identifier.DiagnosticSourceId> updated =
                new LinkedHashSet<>(includedSourceIds);
        if (!updated.add(sourceId)) {
            updated.remove(sourceId);
        }
        includedSourceIds = updated;
        selectionChanged();
        rebuildPlanWidgets();
    }

    private void toggleGenerator(
            com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId generatorId) {
        LinkedHashSet<com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId> updated =
                new LinkedHashSet<>(includedGeneratorIds);
        if (!updated.add(generatorId)) {
            updated.remove(generatorId);
        }
        includedGeneratorIds = updated;
        selectionChanged();
        rebuildPlanWidgets();
    }

    private void changePage(int delta) {
        sourcePage += delta;
        rebuildPlanWidgets();
    }

    private void acceptSelection() {
        if (selectedChoiceCount() == 0 && !emptySelectionConfirmationPending) {
            emptySelectionConfirmationPending = true;
            status = Component.translatable("bugreport.screen.plan.empty_warning");
            rebuildPlanWidgets();
            return;
        }
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                collectionPlan, includedSourceIds, includedGeneratorIds);
        if (reviewed.includedSources().stream()
                .anyMatch(source -> source.selection() instanceof UserSelectionSourcePlan)) {
            if (!commands.acceptCollectionPlan(request, reviewed)) {
                status = Component.translatable("bugreport.screen.plan.failed");
                return;
            }
            minecraft.setScreen(new ScreenshotSelectionScreen(
                    commands,
                    this,
                    request.sessionId(),
                    reviewed,
                    execution -> {
                        selectionAccepted = true;
                        minecraft.setScreen(new CollectionProgressScreen(commands, execution));
                    }));
            return;
        }
        completeSelection(reviewed);
    }

    private void selectionChanged() {
        emptySelectionConfirmationPending = false;
        status = Component.translatable(
                "bugreport.screen.plan.selection", selectedChoiceCount());
    }

    private int selectedChoiceCount() {
        return Math.addExact(includedSourceIds.size(), includedGeneratorIds.size());
    }

    private static int availableChoiceCount(CategoryCollectionPlan planned) {
        int sources = (int) planned.sources().sources().stream()
                .filter(source -> !(source.selection() instanceof UnavailableSourcePlan))
                .count();
        int generators = (int) planned.generators().stream()
                .filter(planned::isAvailable)
                .count();
        return Math.addExact(sources, generators);
    }

    private void completeSelection(ReviewedCollectionPlan reviewed) {
        if (!commands.acceptCollectionPlan(request, reviewed)) {
            status = Component.translatable("bugreport.screen.plan.failed");
            minecraft.setScreen(this);
            return;
        }
        selectionAccepted = true;
        commands.beginCollection(request.sessionId().toString()).ifPresentOrElse(
                execution -> minecraft.setScreen(new CollectionProgressScreen(commands, execution)),
                () -> {
                    status = Component.translatable("bugreport.screen.plan.failed");
                    minecraft.setScreen(this);
                    rebuildPlanWidgets();
                });
    }


    private void returnToForm() {
        if (!planningAuthorityReleased && !commands.returnToForm(request)) {
            status = Component.translatable("bugreport.screen.plan.failed");
            rebuildPlanWidgets();
            return;
        }
        visible = false;
        formScreen.requireFreshDraftPersistence();
        minecraft.setScreen(formScreen);
    }

    private void cancel() {
        visible = false;
        commands.discard(request.sessionId().toString());
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        returnToForm();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 44,
                plan == null ? 0xFFCC66 : 0x60FF60);
        if (plan != null) {
            renderPlan(graphics);
        }
    }

    private void renderPlan(GuiGraphics graphics) {
        List<com.cybersammy.bugreport.core.source.CoordinatedSourcePlan> sources = plan.sources();
        var generators = collectionPlan.generators();
        int y = 62;
        int pageSize = choicesPerPage();
        int first = sourcePage * pageSize;
        int last = Math.min(first + pageSize, sources.size() + generators.size());
        for (int index = first; index < last; index++) {
            if (index >= sources.size()) {
                var generator = generators.get(index - sources.size());
                Component row = Component.translatable(
                        "bugreport.screen.plan.generator",
                        Component.translatable(generator.labelKey().value()),
                        collectionPlan.isAvailable(generator)
                                ? Component.translatable("bugreport.screen.plan.status.generated_later")
                                : Component.translatable("bugreport.screen.plan.status.unavailable", "SIDE"));
                graphics.drawString(font, row, width / 2 - 140, y, 0xE0E0E0);
                y += CHOICE_ROW_HEIGHT;
                continue;
            }
            SourceSelectionPlan selection = sources.get(index).selection();
            Component row = Component.translatable(
                    "bugreport.screen.plan.source",
                    Component.translatable(selection.source().labelKey().value()),
                    selectionStatus(selection),
                    selection.estimate().selectedFileCount(),
                    selection.estimate().knownBytes());
            graphics.drawString(font, row, width / 2 - 140, y, 0xE0E0E0);
            y += CHOICE_ROW_HEIGHT;
        }
        if (!plan.conflicts().isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("bugreport.screen.plan.conflicts", plan.conflicts().size()),
                    width / 2 - 140, y + 8, 0xFF6060);
        }
    }

    private static Component selectionStatus(SourceSelectionPlan selection) {
        if (selection instanceof FileSourcePlan) {
            return Component.translatable("bugreport.screen.plan.status.ready");
        }
        if (selection instanceof BuiltInSourcePlan) {
            return Component.translatable("bugreport.screen.plan.status.generated_later");
        }
        if (selection instanceof UserSelectionSourcePlan) {
            return Component.translatable("bugreport.screen.plan.status.user_selection");
        }
        UnavailableSourcePlan unavailable = (UnavailableSourcePlan) selection;
        return Component.translatable("bugreport.screen.plan.status.unavailable", unavailable.code().name());
    }

    private static long knownBytes(CategorySourcePlan plan) {
        long total = 0;
        for (var file : plan.files()) {
            try {
                total = Math.addExact(total, file.file().observedSize());
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }
}
