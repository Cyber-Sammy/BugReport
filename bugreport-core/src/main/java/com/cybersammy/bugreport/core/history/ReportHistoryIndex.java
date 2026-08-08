package com.cybersammy.bugreport.core.history;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bounded deterministic index of recoverable drafts and terminal report summaries. */
public record ReportHistoryIndex(List<ReportHistoryEntry> entries) {
    public static final int MAX_ENTRIES = 1_024;

    private static final Comparator<ReportHistoryEntry> ORDER = Comparator
            .comparing(ReportHistoryEntry::updatedAt).reversed()
            .thenComparing(entry -> entry.sessionId().toString());

    public ReportHistoryIndex {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > MAX_ENTRIES || entries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("History index exceeds product bounds");
        }
        HashSet<ReportSessionId> ids = new HashSet<>();
        for (ReportHistoryEntry entry : entries) {
            if (!ids.add(entry.sessionId())) {
                throw new IllegalArgumentException("History index contains duplicate session IDs");
            }
        }
        ArrayList<ReportHistoryEntry> canonical = new ArrayList<>(entries);
        canonical.sort(ORDER);
        entries = List.copyOf(canonical);
    }

    public static ReportHistoryIndex empty() {
        return new ReportHistoryIndex(List.of());
    }

    /** Replaces one entry only with a strictly newer revision of the same session identity. */
    public ReportHistoryIndex upsert(ReportHistoryEntry incoming) {
        ReportHistoryEntry value = Objects.requireNonNull(incoming, "incoming");
        ArrayList<ReportHistoryEntry> next = new ArrayList<>(entries);
        for (int index = 0; index < next.size(); index++) {
            ReportHistoryEntry current = next.get(index);
            if (current.sessionId().equals(value.sessionId())) {
                if (value.revision() <= current.revision()) {
                    throw new IllegalArgumentException("History entry revision must advance");
                }
                next.set(index, value);
                return new ReportHistoryIndex(next);
            }
        }
        if (next.size() == MAX_ENTRIES) {
            throw new IllegalStateException("History index is full");
        }
        next.add(value);
        return new ReportHistoryIndex(next);
    }
}
