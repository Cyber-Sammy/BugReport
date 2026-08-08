package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.BuiltInSourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import com.cybersammy.bugreport.core.source.FileSourcePlan;
import com.cybersammy.bugreport.core.source.SourceSelectionPlan;
import com.cybersammy.bugreport.core.source.UnavailableSourcePlan;
import com.cybersammy.bugreport.core.source.UserSelectionSourcePlan;
import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.nio.file.Path;
import java.util.List;
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
    private boolean planning = true;
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
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> returnToForm())
                .bounds(width / 2 - 120, height - 32, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancel())
                .bounds(width / 2 + 4, height - 32, 116, 20).build());
        if (!planningStarted) {
            planningStarted = true;
            startPlanning(Minecraft.getInstance().gameDirectory.toPath());
        }
    }

    private void startPlanning(Path gameDirectory) {
        Thread.ofVirtual().name("bugreport-source-plan").start(() -> {
            try {
                CategorySourcePlan planned = new CategorySourcePlanner(
                        BugReportMod.providerRegistry(),
                        ApprovedSourceRoots.forGameDirectory(gameDirectory.toAbsolutePath()),
                        SupportedSide.PHYSICAL_CLIENT).plan(request.providerId(), request.categoryId());
                boolean accepted = commands.acceptCollectionPlan(
                        request.sessionId().toString(), planned);
                Minecraft.getInstance().execute(() -> presentPlan(planned, accepted));
            } catch (RuntimeException exception) {
                commands.returnToForm(request.sessionId().toString());
                Minecraft.getInstance().execute(this::presentPlanningFailure);
            }
        });
    }

    private void presentPlan(CategorySourcePlan planned, boolean accepted) {
        if (!visible) {
            return;
        }
        if (!accepted) {
            planning = false;
            status = Component.translatable("bugreport.screen.plan.failed");
            return;
        }
        plan = planned;
        planning = false;
        status = Component.translatable("bugreport.screen.plan.ready", planned.files().size(), knownBytes(planned));
    }

    private void presentPlanningFailure() {
        if (!visible) {
            return;
        }
        planning = false;
        status = Component.translatable("bugreport.screen.plan.failed");
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
        int y = 66;
        int shown = Math.min(sources.size(), 7);
        for (int index = 0; index < shown; index++) {
            SourceSelectionPlan selection = sources.get(index).selection();
            Component row = Component.translatable(
                    "bugreport.screen.plan.source",
                    Component.translatable(selection.source().labelKey().value()),
                    selectionStatus(selection),
                    selection.estimate().selectedFileCount(),
                    selection.estimate().knownBytes());
            graphics.drawString(font, row, width / 2 - 140, y, 0xE0E0E0);
            y += 14;
        }
        if (sources.size() > shown) {
            graphics.drawString(font,
                    Component.translatable("bugreport.screen.plan.more_sources", sources.size() - shown),
                    width / 2 - 140, y, 0xA0A0A0);
        }
        if (!plan.conflicts().isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("bugreport.screen.plan.conflicts", plan.conflicts().size()),
                    width / 2 - 140, y + 18, 0xFF6060);
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
