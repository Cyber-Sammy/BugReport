package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.core.history.ReportHistoryEntry;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact path-free list of persisted completed and failed delivery summaries. */
final class ReportHistoryScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private final BugReportCommandService commands;
    private final Screen parent;
    private final List<ReportHistoryEntry> entries;
    private int page;

    ReportHistoryScreen(BugReportCommandService commands, Screen parent) {
        super(Component.translatable("bugreport.screen.history.title"));
        this.commands = commands;
        this.parent = parent;
        entries = commands.reportHistory();
    }

    @Override
    protected void init() {
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pageCount - 1);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored ->
                        minecraft.setScreen(parent))
                .bounds(width / 2 - 58, height - 32, 116, 20).build());
        if (pageCount > 1) {
            Button previous = Button.builder(Component.translatable("bugreport.screen.form.previous"),
                            ignored -> { page--; rebuildWidgets(); })
                    .bounds(width / 2 - 120, height - 56, 56, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.translatable("bugreport.screen.form.next"),
                            ignored -> { page++; rebuildWidgets(); })
                    .bounds(width / 2 + 64, height - 56, 56, 20).build();
            next.active = page + 1 < pageCount;
            addRenderableWidget(next);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("bugreport.screen.history.empty"),
                    width / 2, 52, 0xA0A0A0);
        }
        int first = page * PAGE_SIZE;
        for (int index = first; index < Math.min(first + PAGE_SIZE, entries.size()); index++) {
            ReportHistoryEntry entry = entries.get(index);
            Component row = Component.translatable("bugreport.screen.history.entry",
                    entry.providerId(), entry.categoryId().map(Object::toString).orElse("-"),
                    entry.status().name(), entry.revision());
            graphics.drawString(font, row, width / 2 - 145, 48 + (index - first) * 22, 0xE0E0E0);
        }
    }
}
