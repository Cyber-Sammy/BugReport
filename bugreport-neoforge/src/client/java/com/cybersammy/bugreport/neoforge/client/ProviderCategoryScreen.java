package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal client flow for selecting a registered provider and declared category. */
final class ProviderCategoryScreen extends Screen {
    private final BugReportCommandService commands;
    private BugReportCommandService.ProviderChoice selectedProvider;
    private Component status;
    private CategoryFormScreen activeForm;
    private BugReportCommandService.DraftRecoveryOverview recoveryOverview;
    private boolean recoveryLoading;

    ProviderCategoryScreen(BugReportCommandService commands) {
        super(Component.translatable("bugreport.screen.select_provider.title"));
        this.commands = commands;
    }

    ProviderCategoryScreen(BugReportCommandService commands,
            BugReportCommandService.ProviderChoice selectedProvider) {
        this(commands);
        this.selectedProvider = selectedProvider;
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        int top = 48;
        if (selectedProvider == null) {
            List<BugReportCommandService.ProviderChoice> providers = commands.providerChoices();
            if (providers.isEmpty()) {
                status = Component.translatable("bugreport.screen.select_provider.empty");
            }
            for (BugReportCommandService.ProviderChoice provider : providers) {
                Component label = Component.translatable(provider.labelKey().value())
                        .append(Component.literal(" [" + provider.supportState().name() + "]"));
                Button button = Button.builder(label, ignored -> selectProvider(provider))
                        .bounds(left, top, 240, 20).build();
                button.active = provider.supportState() != ProviderSupportState.DISABLED;
                addRenderableWidget(button);
                top += 24;
            }
        } else {
            for (BugReportCommandService.CategoryChoice category :
                    commands.categoryChoices(selectedProvider.id())) {
                Button button = Button.builder(Component.translatable(category.labelKey().value()),
                        ignored -> create(selectedProvider, category))
                        .bounds(left, top, 240, 20).build();
                button.active = activeForm == null;
                addRenderableWidget(button);
                top += 24;
            }
            if (activeForm != null) {
                addRenderableWidget(Button.builder(
                        Component.translatable("bugreport.screen.form.resume"),
                        ignored -> minecraft.setScreen(activeForm))
                        .bounds(left, top + 4, 240, 20).build());
            }
            Button back = Button.builder(Component.translatable("gui.back"), ignored -> {
                selectedProvider = null;
                status = null;
                rebuildWidgets();
            }).bounds(left, height - 32, 116, 20).build();
            back.active = activeForm == null;
            addRenderableWidget(back);
        }
        if (selectedProvider == null) {
            loadRecoveryOverview();
            int recoveryCount = recoveryOverview == null
                    ? 0
                    : recoveryOverview.choices().size();
            if (recoveryCount > 0) {
                addRenderableWidget(Button.builder(
                                Component.translatable(
                                        "bugreport.screen.recovery.open", recoveryCount),
                                ignored -> minecraft.setScreen(new DraftRecoveryScreen(commands, this)))
                        .bounds(left, height - 56, 240, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("bugreport.screen.history.open"),
                            ignored -> minecraft.setScreen(new ReportHistoryScreen(commands, this)))
                    .bounds(left, height - 32, 116, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancel())
                .bounds(left + 124, height - 32, 116, 20).build());
    }

    private void selectProvider(BugReportCommandService.ProviderChoice provider) {
        selectedProvider = provider;
        status = null;
        rebuildWidgets();
    }

    private void create(BugReportCommandService.ProviderChoice provider,
            BugReportCommandService.CategoryChoice category) {
        BugReportCommandService.Message message = commands.create(
                provider.id().toString(), category.id().toString()).getFirst();
        status = Component.translatable(message.translationKey(), message.arguments());
        if ("bugreport.command.create.success".equals(message.translationKey())) {
            activeForm = new CategoryFormScreen(
                    commands, (String) message.arguments()[0], this);
            minecraft.setScreen(activeForm);
        }
    }

    private void cancel() {
        if (activeForm != null) {
            activeForm.discardSession(
                    discarded -> {
                        if (!discarded) {
                            status = Component.translatable(
                                    "bugreport.command.error.draft_discard_failed");
                            rebuildWidgets();
                            return;
                        }
                        activeForm = null;
                        onClose();
                    });
            return;
        }
        onClose();
    }

    void resumeRecoveredDraft(BugReportCommandService.DraftResume resumed) {
        recoveryOverview = commands.draftRecovery();
        selectedProvider = commands.providerChoice(resumed.providerId()).orElseThrow();
        activeForm = new CategoryFormScreen(
                commands,
                resumed.sessionId().toString(),
                this,
                resumed.formSubmission());
        status = Component.translatable(
                "bugreport.screen.recovery.resumed", resumed.recordedState().name());
        minecraft.setScreen(activeForm);
    }

    CategoryFormScreen attachLiveForm(
            ReportSessionId sessionId,
            BugReportCommandService.ProviderChoice provider,
            FormSubmission submission) {
        selectedProvider = provider;
        activeForm = new CategoryFormScreen(
                commands, sessionId.toString(), this, submission);
        return activeForm;
    }

    void updateRecoveryOverview(BugReportCommandService.DraftRecoveryOverview overview) {
        recoveryOverview = overview;
    }

    private void loadRecoveryOverview() {
        if (recoveryOverview != null || recoveryLoading) {
            return;
        }
        recoveryLoading = true;
        Thread.ofVirtual()
                .name("bugreport-draft-scan")
                .start(
                        () -> {
                            BugReportCommandService.DraftRecoveryOverview loaded =
                                    commands.draftRecovery();
                            Minecraft.getInstance()
                                    .execute(
                                            () -> {
                                                recoveryOverview = loaded;
                                                recoveryLoading = false;
                                                if (Minecraft.getInstance().screen == this) {
                                                    rebuildWidgets();
                                                }
                                            });
                        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, selectedProvider == null ? title
                : Component.translatable("bugreport.screen.select_category.title",
                        Component.translatable(selectedProvider.labelKey().value())), width / 2, 20, 0xFFFFFF);
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height - 56, 0xFFFFFF);
        }
    }
}
