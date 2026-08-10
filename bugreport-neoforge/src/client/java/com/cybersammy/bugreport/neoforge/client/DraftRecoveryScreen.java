package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Bounded first-party restart UI that restores form state but no later authority. */
final class DraftRecoveryScreen extends Screen {
    private final BugReportCommandService commands;
    private final ProviderCategoryScreen parent;
    private BugReportCommandService.DraftRecoveryOverview overview;
    private int page;
    private Component status;
    private boolean discardInProgress;

    DraftRecoveryScreen(BugReportCommandService commands, ProviderCategoryScreen parent) {
        super(Component.translatable("bugreport.screen.recovery.title"));
        this.commands = commands;
        this.parent = parent;
        overview = commands.draftRecovery();
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        List<BugReportCommandService.DraftRecoveryChoice> choices = overview.choices();
        if (!choices.isEmpty()) {
            BugReportCommandService.DraftRecoveryChoice choice = choices.get(page);
            Button resume = Button.builder(
                            Component.translatable("bugreport.screen.recovery.resume"),
                            ignored -> resume(choice))
                    .bounds(left, height - 80, 116, 20)
                    .build();
            resume.active = choice.resumable() && !discardInProgress;
            addRenderableWidget(resume);
            Button discard = Button.builder(
                            Component.translatable("bugreport.screen.recovery.discard"),
                            ignored -> discard(choice))
                    .bounds(left + 124, height - 80, 116, 20)
                    .build();
            discard.active = !discardInProgress;
            addRenderableWidget(discard);

            Button previous = Button.builder(
                            Component.translatable("bugreport.screen.form.previous"),
                            ignored -> changePage(-1))
                    .bounds(left, height - 54, 116, 20)
                    .build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(
                            Component.translatable("bugreport.screen.form.next"),
                            ignored -> changePage(1))
                    .bounds(left + 124, height - 54, 116, 20)
                    .build();
            next.active = page + 1 < choices.size();
            addRenderableWidget(next);
        }
        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.back"),
                                ignored -> minecraft.setScreen(parent))
                        .bounds(left, height - 28, 240, 20)
                        .build());
    }

    private void resume(BugReportCommandService.DraftRecoveryChoice choice) {
        commands.resumeDraft(choice.sessionId()).ifPresentOrElse(
                parent::resumeRecoveredDraft,
                () -> {
                    status = Component.translatable("bugreport.screen.recovery.failed");
                    rebuildWidgets();
                });
    }

    private void discard(BugReportCommandService.DraftRecoveryChoice choice) {
        if (discardInProgress) {
            return;
        }
        discardInProgress = true;
        status = null;
        rebuildWidgets();
        Thread.ofVirtual()
                .name("bugreport-draft-discard")
                .start(
                        () -> {
                            boolean deleted =
                                    commands.discardRecoveredDraft(choice.sessionId());
                            BugReportCommandService.DraftRecoveryOverview refreshed =
                                    commands.draftRecovery();
                            Minecraft.getInstance()
                                    .execute(() -> finishDiscard(deleted, refreshed));
                        });
    }

    private void finishDiscard(
            boolean deleted, BugReportCommandService.DraftRecoveryOverview refreshed) {
        discardInProgress = false;
        overview = refreshed;
        parent.updateRecoveryOverview(refreshed);
        page = Math.min(page, Math.max(0, overview.choices().size() - 1));
        status = Component.translatable(
                deleted
                        ? "bugreport.screen.recovery.discarded"
                        : "bugreport.screen.recovery.discard_failed");
        rebuildWidgets();
    }

    private void changePage(int delta) {
        page += delta;
        status = null;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        List<BugReportCommandService.DraftRecoveryChoice> choices = overview.choices();
        if (choices.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("bugreport.screen.recovery.empty"),
                    width / 2,
                    72,
                    0xA0A0A0);
        } else {
            BugReportCommandService.DraftRecoveryChoice choice = choices.get(page);
            Component provider = choice.providerLabel()
                    .<Component>map(label -> Component.translatable(label.value()))
                    .orElseGet(
                            () ->
                                    choice.providerId()
                                            .<Component>map(
                                                    id -> Component.literal(id.toString()))
                                            .orElseGet(
                                                    () ->
                                                            Component.translatable(
                                                                    "bugreport.screen.recovery.unknown")));
            Component category = choice.categoryLabel()
                    .<Component>map(label -> Component.translatable(label.value()))
                    .orElseGet(
                            () -> Component.translatable("bugreport.screen.recovery.unknown"));
            graphics.drawCenteredString(font, provider, width / 2, 58, 0xFFFFFF);
            graphics.drawCenteredString(font, category, width / 2, 76, 0xA0A0A0);
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "bugreport.screen.recovery.state",
                            choice.recordedState().map(Enum::name).orElse("UNKNOWN")),
                    width / 2,
                    94,
                    0xA0A0A0);
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "bugreport.screen.recovery.status."
                                    + choice.status().name().toLowerCase(java.util.Locale.ROOT)),
                    width / 2,
                    112,
                    choice.resumable() ? 0x60FF60 : 0xFF6060);
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "bugreport.screen.recovery.page", page + 1, choices.size()),
                    width / 2,
                    130,
                    0xA0A0A0);
        }
        if (overview.scanIncomplete()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("bugreport.screen.recovery.scan_incomplete"),
                    width / 2,
                    148,
                    0xFFB060);
        }
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, 166, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
