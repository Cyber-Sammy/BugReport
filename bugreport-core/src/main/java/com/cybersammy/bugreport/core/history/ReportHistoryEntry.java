package com.cybersammy.bugreport.core.history;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.packaging.ReportZipArchive;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Minimal, path-free history summary; it is not a replacement for a draft or report package. */
public record ReportHistoryEntry(
        ReportSessionId sessionId,
        ProviderId providerId,
        ProviderVersion providerVersion,
        Optional<CategoryId> categoryId,
        ReportHistoryStatus status,
        long revision,
        Instant updatedAt,
        Optional<ReportArchiveSummary> archive) {
    public ReportHistoryEntry {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(status, "status");
        if (revision < 0) {
            throw new IllegalArgumentException("History revision must be non-negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(archive, "archive");
        if ((status == ReportHistoryStatus.COMPLETED) != archive.isPresent()) {
            throw new IllegalArgumentException("Only completed history entries retain archive identity");
        }
    }

    /** Creates the recoverable summary for one persisted draft. */
    public static ReportHistoryEntry draft(ReportDraft draft, Instant updatedAt) {
        ReportDraft value = Objects.requireNonNull(draft, "draft");
        return new ReportHistoryEntry(
                value.sessionId(), value.providerId(), value.providerVersion(), value.categoryId(),
                ReportHistoryStatus.DRAFT, value.revision(), updatedAt, Optional.empty());
    }

    /** Creates a completed summary only after ZIP validation has produced an archive identity. */
    public static ReportHistoryEntry completed(
            ReportHistoryEntry previous, long revision, Instant updatedAt, ReportZipArchive archive) {
        ReportHistoryEntry value = Objects.requireNonNull(previous, "previous");
        return new ReportHistoryEntry(
                value.sessionId(), value.providerId(), value.providerVersion(), value.categoryId(),
                ReportHistoryStatus.COMPLETED, revision, updatedAt,
                Optional.of(ReportArchiveSummary.from(archive)));
    }

    /** Creates a path- and error-free failure summary for a known report session. */
    public static ReportHistoryEntry failed(
            ReportHistoryEntry previous, long revision, Instant updatedAt) {
        ReportHistoryEntry value = Objects.requireNonNull(previous, "previous");
        return new ReportHistoryEntry(
                value.sessionId(), value.providerId(), value.providerVersion(), value.categoryId(),
                ReportHistoryStatus.FAILED, revision, updatedAt, Optional.empty());
    }
}
