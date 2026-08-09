package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.BuiltInSourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import com.cybersammy.bugreport.core.source.FileSourcePlan;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.SourceSelectionPlan;
import com.cybersammy.bugreport.core.source.UnavailableSourcePlan;
import com.cybersammy.bugreport.core.source.UserSelectionSourcePlan;
import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.core.transport.BugReportCommandService;
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
    private final BugReportCommandService commands;
    private final BugReportCommandService.CollectionPlanRequest request;
    private final CategoryFormScreen formScreen;
    private volatile boolean visible;
    private boolean planningStarted;
    private CategorySourcePlan plan;
    private Set<com.cybersammy.bugreport.api.identifier.DiagnosticSourceId> includedSourceIds = Set.of();
    private boolean planning = true;
    private boolean selectionAccepted;
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
        Button accept = Button.builder(Component.translatable("bugreport.screen.plan.accept"),
                        ignored -> acceptSelection())
                .bounds(width / 2 - 58, height - 56, 116, 20).build();
        accept.active = !selectionAccepted;
        addRenderableWidget(accept);
    }

    private void startPlanning(Path gameDirectory) {
        Thread.ofVirtual().name("bugreport-source-plan").start(() -> {
            try {
                CategorySourcePlan planned = new CategorySourcePlanner(
                        BugReportMod.providerRegistry(),
                        ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                        SupportedSide.PHYSICAL_CLIENT).plan(request.providerId(), request.categoryId());
                Minecraft.getInstance().execute(() -> presentPlan(planned));
            } catch (RuntimeException exception) {
                commands.returnToForm(request.sessionId().toString());
                Minecraft.getInstance().execute(this::presentPlanningFailure);
            }
        });
    }

    private void presentPlan(CategorySourcePlan planned) {
        if (!visible) {
            return;
        }
        plan = planned;
        includedSourceIds = new LinkedHashSet<>(ReviewedCollectionPlan.defaults(planned).includedSourceIds());
        planning = false;
        status = Component.translatable("bugreport.screen.plan.ready", planned.files().size(), knownBytes(planned));
        rebuildPlanWidgets();
    }

    private void presentPlanningFailure() {
        if (!visible) {
            return;
        }
        planning = false;
        status = Component.translatable("bugreport.screen.plan.failed");
    }

    private void addSourceControls() {
        List<com.cybersammy.bugreport.core.source.CoordinatedSourcePlan> sources = plan.sources();
        int pageSize = 5;
        int pageCount = Math.max(1, (sources.size() + pageSize - 1) / pageSize);
        sourcePage = Math.min(sourcePage, pageCount - 1);
        int first = sourcePage * pageSize;
        int last = Math.min(first + pageSize, sources.size());
        for (int index = first; index < last; index++) {
            com.cybersammy.bugreport.core.source.CoordinatedSourcePlan source = sources.get(index);
            var sourceId = source.provenance().sourceId();
            boolean selectable = !(source.selection() instanceof UnavailableSourcePlan);
            boolean included = includedSourceIds.contains(sourceId);
            Button toggle = Button.builder(
                            Component.translatable(
                                    included
                                            ? "bugreport.screen.plan.exclude"
                                            : "bugreport.screen.plan.include",
                                    Component.translatable(source.selection().source().labelKey().value())),
                            ignored -> toggleSource(sourceId))
                    .bounds(width / 2 - 140, 62 + (index - first) * 32, 280, 20).build();
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

    private void toggleSource(com.cybersammy.bugreport.api.identifier.DiagnosticSourceId sourceId) {
        LinkedHashSet<com.cybersammy.bugreport.api.identifier.DiagnosticSourceId> updated =
                new LinkedHashSet<>(includedSourceIds);
        if (!updated.add(sourceId)) {
            updated.remove(sourceId);
        }
        includedSourceIds = updated;
        rebuildPlanWidgets();
    }

    private void changePage(int delta) {
        sourcePage += delta;
        rebuildPlanWidgets();
    }

    private void acceptSelection() {
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(plan, includedSourceIds);
        if (!commands.acceptCollectionPlan(request, reviewed)) {
            status = Component.translatable("bugreport.screen.plan.failed");
            return;
        }
        selectionAccepted = true;
        commands.beginCollection(request.sessionId().toString()).ifPresentOrElse(
                execution -> minecraft.setScreen(new CollectionProgressScreen(commands, execution)),
                () -> {
                    status = Component.translatable("bugreport.screen.plan.failed");
                    rebuildPlanWidgets();
                });
    }

    private void returnToForm() {
        visible = false;
        commands.returnToForm(request.sessionId().toString());
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 44,
                plan == null ? 0xFFCC66 : 0x60FF60);
        if (plan != null) {
            renderPlan(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlan(GuiGraphics graphics) {
        List<com.cybersammy.bugreport.core.source.CoordinatedSourcePlan> sources = plan.sources();
        int y = 84;
        int pageSize = 5;
        int first = sourcePage * pageSize;
        int last = Math.min(first + pageSize, sources.size());
        for (int index = first; index < last; index++) {
            SourceSelectionPlan selection = sources.get(index).selection();
            Component row = Component.translatable(
                    "bugreport.screen.plan.source",
                    Component.translatable(selection.source().labelKey().value()),
                    selectionStatus(selection),
                    selection.estimate().selectedFileCount(),
                    selection.estimate().knownBytes());
            graphics.drawString(font, row, width / 2 - 140, y, 0xE0E0E0);
            y += 32;
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
