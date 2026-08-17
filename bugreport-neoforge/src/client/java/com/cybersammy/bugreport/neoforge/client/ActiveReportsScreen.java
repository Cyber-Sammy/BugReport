package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Readable first-party picker for non-terminal in-memory reports. */
final class ActiveReportsScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BugReportCommandService commands;
    private final Screen parent;
    private final Function<String, BugReportCommandService.SessionResumeStatus> sessionOpener;
    private List<BugReportCommandService.ActiveReportChoice> reports = List.of();
    private Component status;
    private int page;

    ActiveReportsScreen(
            BugReportCommandService commands,
            Screen parent,
            Function<String, BugReportCommandService.SessionResumeStatus> sessionOpener) {
        super(Component.translatable("bugreport.screen.active.title"));
        this.commands = Objects.requireNonNull(commands, "commands");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.sessionOpener = Objects.requireNonNull(sessionOpener, "sessionOpener");
    }

    @Override
    protected void init() {
        reports = commands.activeReportChoices();
        int pageCount = Math.max(1, (reports.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pageCount - 1);
        int first = page * PAGE_SIZE;
        int last = Math.min(first + PAGE_SIZE, reports.size());
        int left = width / 2 - 170;
        for (int index = first; index < last; index++) {
            BugReportCommandService.ActiveReportChoice report = reports.get(index);
            Component provider = Component.translatable(report.providerLabelKey().value());
            Component category = report.categoryLabelKey()
                    .<Component>map(key -> Component.translatable(key.value()))
                    .orElseGet(() -> Component.translatable("bugreport.screen.active.no_category"));
            String shortId = report.sessionId().toString().substring(0, 8);
            String time = TIME.format(report.createdAt().atZone(ZoneId.systemDefault()));
            addRenderableWidget(Button.builder(
                            Component.translatable(
                                    "bugreport.screen.active.entry",
                                    index + 1,
                                    provider,
                                    category,
                                    time,
                                    shortId),
                            ignored -> open(report))
                    .bounds(left, 48 + (index - first) * 24, 340, 20)
                    .build());
        }
        if (pageCount > 1) {
            Button previous = Button.builder(
                            Component.translatable("bugreport.screen.form.previous"),
                            ignored -> {
                                page--;
                                rebuildWidgets();
                            })
                    .bounds(width / 2 - 120, height - 56, 90, 20)
                    .build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(
                            Component.translatable("bugreport.screen.form.next"),
                            ignored -> {
                                page++;
                                rebuildWidgets();
                            })
                    .bounds(width / 2 + 30, height - 56, 90, 20)
                    .build();
            next.active = page + 1 < pageCount;
            addRenderableWidget(next);
        }
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        ignored -> minecraft.setScreen(parent))
                .bounds(width / 2 - 58, height - 32, 116, 20)
                .build());
    }

    private void open(BugReportCommandService.ActiveReportChoice report) {
        BugReportCommandService.SessionResumeStatus result =
                sessionOpener.apply(report.sessionId().toString());
        if (result != BugReportCommandService.SessionResumeStatus.READY) {
            status = Component.translatable(switch (result) {
                case BUSY -> "bugreport.command.open.busy";
                case UNAVAILABLE -> "bugreport.command.open.unavailable";
                case TERMINAL -> "bugreport.command.open.terminal";
                case UNKNOWN_SESSION -> "bugreport.command.error.unknown_session";
                case READY -> throw new IllegalStateException("Ready session did not open");
            });
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        if (reports.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("bugreport.screen.active.empty"),
                    width / 2,
                    52,
                    0xA0A0A0);
        }
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height - 80, 0xFFCC66);
        }
    }
}
