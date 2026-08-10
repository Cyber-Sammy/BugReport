package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.core.history.FileReportHistoryStore;
import com.cybersammy.bugreport.core.history.ReportHistoryEntry;
import com.cybersammy.bugreport.core.history.ReportHistoryIndex;
import java.util.List;
import java.util.Objects;

/** Product-side owner of one already trusted, existing directory for terminal report history. */
public final class FileReportHistoryRecorder implements BugReportCommandService.ReportHistoryRecorder {
    private final FileReportHistoryStore store;
    private ReportHistoryIndex index;

    public FileReportHistoryRecorder(FileReportHistoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
        index = store.load().index();
    }

    @Override
    public synchronized void record(ReportHistoryEntry entry) {
        ReportHistoryIndex next = index.upsert(Objects.requireNonNull(entry, "entry"));
        store.save(next);
        index = next;
    }

    @Override
    public synchronized List<ReportHistoryEntry> entries() {
        return index.entries();
    }
}
