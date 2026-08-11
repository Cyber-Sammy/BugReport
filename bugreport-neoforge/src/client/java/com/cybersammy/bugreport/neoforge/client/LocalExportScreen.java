package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.core.transport.ReportTransportResult;
import com.cybersammy.bugreport.core.transport.TransportProgressSnapshot;
import com.cybersammy.bugreport.core.transport.TransportRunControl;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.nio.file.Path;
import net.minecraft.Util;
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
    private final Path gameDirectory;
    private volatile boolean visible;
    private BugReportCommandService.LocalExportRequest export;
    private TransportRunControl control;
    private EditBox fileName;
    private Component status = Component.translatable("bugreport.screen.export.preparing");
    private boolean started;
    private boolean running;
    private boolean finished;
    private boolean successful;
    private boolean retrying;
    private boolean openingFolder;
    private String archiveFileName;

    LocalExportScreen(BugReportCommandService commands,
            BugReportCommandService.LocalExportPreparationRequest preparation, Path gameDirectory) {
        super(Component.translatable("bugreport.screen.export.title"));
        this.commands = commands;
        this.preparation = preparation;
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
    }

    LocalExportScreen(
            BugReportCommandService commands,
            BugReportCommandService.LocalExportRequest export,
            Path gameDirectory) {
        super(Component.translatable("bugreport.screen.export.title"));
        this.commands = commands;
        preparation = null;
        this.export = export;
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        status = Component.translatable("bugreport.screen.export.ready");
        started = true;
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
        if (fileName != null) {
            archiveFileName = fileName.getValue();
        }
        clearWidgets();
        if (export != null && !finished) {
            if (archiveFileName == null) {
                archiveFileName = "report-" + export.sessionId() + ".bugreport.zip";
            }
            fileName = new EditBox(font, width / 2 - 140, 102, 280, 20,
                    Component.translatable("bugreport.screen.export.filename"));
            fileName.setMaxLength(140);
            fileName.setValue(archiveFileName);
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
        if (finished && !successful) {
            Button retry = Button.builder(Component.translatable("bugreport.screen.export.retry"),
                            ignored -> retryExport())
                    .bounds(width / 2 - 100, 132, 200, 20).build();
            retry.active = !retrying;
            addRenderableWidget(retry);
        }
        Button openFolder = Button.builder(Component.translatable("bugreport.screen.export.open_folder"),
                        ignored -> openExportFolder())
                .bounds(width / 2 - 100, 184, 200, 20).build();
        openFolder.active = !openingFolder;
        addRenderableWidget(openFolder);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 58, height - 32, 116, 20).build());
    }

    private void startExport() {
        if (running || export == null) return;
        String requestedName = fileName.getValue();
        archiveFileName = requestedName;
        running = true;
        control = new TransportRunControl();
        status = Component.translatable("bugreport.screen.export.writing");
        rebuildExportWidgets();
        Thread.ofVirtual().name("bugreport-local-zip-export").start(() -> {
            var result = commands.executeLocalExport(export, gameDirectory, requestedName, control);
            Minecraft.getInstance().execute(() -> complete(result.orElse(null)));
        });
    }

    private void openExportFolder() {
        if (openingFolder) return;
        openingFolder = true;
        status = Component.translatable("bugreport.screen.export.opening_folder");
        rebuildExportWidgets();
        Thread.ofVirtual().name("bugreport-open-export-folder").start(() -> {
            var directory = commands.localExportDirectory(gameDirectory);
            boolean opened = false;
            if (directory.isPresent()) {
                try {
                    Util.getPlatform().openPath(directory.orElseThrow());
                    opened = true;
                } catch (RuntimeException ignored) {
                    // The localized UI status reports platform-launch failures without exposing paths.
                }
            }
            boolean folderOpened = opened;
            Minecraft.getInstance().execute(() -> {
                if (!visible) return;
                openingFolder = false;
                if (!folderOpened) {
                    status = Component.translatable("bugreport.screen.export.open_folder_failed");
                } else {
                    status = Component.translatable("bugreport.screen.export.folder_opened");
                }
                rebuildExportWidgets();
            });
        });
    }

    private void complete(ReportTransportResult result) {
        if (!visible) return;
        running = false;
        finished = true;
        successful = result != null && result.status() == ReportTransportResult.Status.SUCCESS;
        status = Component.translatable(successful
                ? "bugreport.screen.export.completed" : "bugreport.screen.export.failed");
        rebuildExportWidgets();
    }

    private void retryExport() {
        if (retrying || export == null || successful) return;
        retrying = true;
        status = Component.translatable("bugreport.screen.export.retrying");
        rebuildExportWidgets();
        String sessionId = export.sessionId().toString();
        Thread.ofVirtual().name("bugreport-local-export-retry").start(() -> {
            var preparation = commands.retryLocalExport(sessionId)
                    ? commands.beginLocalExport(sessionId)
                    : java.util.Optional.<BugReportCommandService.LocalExportPreparationRequest>empty();
            Minecraft.getInstance().execute(() -> {
                if (!visible) return;
                preparation.ifPresentOrElse(
                        request -> minecraft.setScreen(
                                new LocalExportScreen(commands, request, gameDirectory)),
                        this::retryFailed);
            });
        });
    }

    private void retryFailed() {
        retrying = false;
        status = Component.translatable("bugreport.screen.export.retry_failed");
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
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 50, successful ? 0x60FF60 : 0xFFCC66);
        if (export != null) {
            var summary = export.summary();
            graphics.drawCenteredString(font, Component.translatable("bugreport.screen.export.summary",
                    summary.providerId(), summary.categoryId(), summary.entryCount(), summary.totalBytes()),
                    width / 2, 78, 0xE0E0E0);
            graphics.drawCenteredString(font, Component.translatable("bugreport.screen.export.destination"),
                    width / 2, 210, 0xA0A0A0);
        }
    }
}
