package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.core.transport.ReportTransportResult;
import com.cybersammy.bugreport.core.transport.TransportProgressSnapshot;
import com.cybersammy.bugreport.core.transport.TransportRunControl;
import com.cybersammy.bugreport.core.transport.BugReportCommandService;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Presents the exact local archive summary, then executes an explicit user-confirmed export. */
final class LocalExportScreen extends Screen {
    private final BugReportCommandService commands;
    private final BugReportCommandService.LocalExportPreparationRequest preparation;
    private final Path exportDirectory;
    private volatile boolean visible;
    private BugReportCommandService.LocalExportRequest export;
    private TransportRunControl control;
    private EditBox fileName;
    private Component status = Component.translatable("bugreport.screen.export.preparing");
    private boolean started;
    private boolean running;
    private boolean finished;
    private boolean successful;

    LocalExportScreen(BugReportCommandService commands,
            BugReportCommandService.LocalExportPreparationRequest preparation, Path gameDirectory) {
        super(Component.translatable("bugreport.screen.export.title"));
        this.commands = commands;
        this.preparation = preparation;
        exportDirectory = gameDirectory.toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        visible = true;
        rebuildExportWidgets();
        if (!started) {
            started = true;
            Thread.ofVirtual().name("bugreport-package-summary").start(() -> {
                var prepared = commands.prepareLocalExport(preparation);
                Minecraft.getInstance().execute(() -> prepared.ifPresentOrElse(this::present, this::failed));
            });
        }
    }

    private void present(BugReportCommandService.LocalExportRequest request) {
        if (!visible) return;
        export = request;
        status = Component.translatable("bugreport.screen.export.ready");
        rebuildExportWidgets();
    }

    private void failed() {
        if (!visible) return;
        status = Component.translatable("bugreport.screen.export.failed");
        rebuildExportWidgets();
    }

    private void rebuildExportWidgets() {
        clearWidgets();
        if (export != null && !finished) {
            fileName = new EditBox(font, width / 2 - 140, 102, 280, 20,
                    Component.translatable("bugreport.screen.export.filename"));
            fileName.setMaxLength(140);
            fileName.setValue("report-" + export.sessionId() + ".bugreport.zip");
            fileName.setEditable(!running);
            addRenderableWidget(fileName);
            Button confirm = Button.builder(Component.translatable("bugreport.screen.export.confirm"),
                            ignored -> startExport())
                    .bounds(width / 2 - 100, 132, 200, 20).build();
            confirm.active = !running;
            addRenderableWidget(confirm);
            if (running) {
                addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                                ignored -> control.requestCancellation())
                        .bounds(width / 2 - 58, 158, 116, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 58, height - 32, 116, 20).build());
    }

    private void startExport() {
        if (running || export == null) return;
        running = true;
        control = new TransportRunControl();
        status = Component.translatable("bugreport.screen.export.writing");
        rebuildExportWidgets();
        String requestedName = fileName.getValue();
        Thread.ofVirtual().name("bugreport-local-zip-export").start(() -> {
            var result = commands.executeLocalExport(export, exportDirectory, requestedName, control);
            Minecraft.getInstance().execute(() -> complete(result.orElse(null)));
        });
    }

    private void complete(ReportTransportResult result) {
        if (!visible) return;
        running = false;
        finished = result != null;
        successful = finished && result.status() == ReportTransportResult.Status.SUCCESS;
        status = Component.translatable(successful
                ? "bugreport.screen.export.completed" : "bugreport.screen.export.failed");
        rebuildExportWidgets();
    }

    @Override
    public void tick() {
        if (running && control != null) {
            TransportProgressSnapshot progress = control.progress();
            status = Component.translatable("bugreport.screen.export.progress",
                    progress.completedEntries(), progress.totalEntries(),
                    progress.processedBytes(), progress.totalBytes());
        }
    }

    @Override
    public void onClose() {
        visible = false;
        if (running && control != null) control.requestCancellation();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 50, successful ? 0x60FF60 : 0xFFCC66);
        if (export != null) {
            var summary = export.summary();
            graphics.drawCenteredString(font, Component.translatable("bugreport.screen.export.summary",
                    summary.providerId(), summary.categoryId(), summary.entryCount(), summary.totalBytes()),
                    width / 2, 78, 0xE0E0E0);
            graphics.drawCenteredString(font, Component.translatable("bugreport.screen.export.destination"),
                    width / 2, 182, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
