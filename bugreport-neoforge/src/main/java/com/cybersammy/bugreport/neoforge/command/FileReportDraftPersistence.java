package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.core.draft.DraftLoadBatch;
import com.cybersammy.bugreport.core.draft.FileDraftStore;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;

/** Single-writer product adapter for one already trusted report-draft directory. */
public final class FileReportDraftPersistence
        implements BugReportCommandService.ReportDraftPersistence {
    private final FileDraftStore store;

    public FileReportDraftPersistence(FileDraftStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void save(ReportDraft draft) {
        store.save(draft);
    }

    @Override
    public DraftLoadBatch loadAll() {
        return store.loadAll();
    }

    @Override
    public boolean delete(ReportSessionId sessionId) {
        return store.delete(sessionId);
    }
}
