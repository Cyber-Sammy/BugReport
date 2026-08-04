package com.cybersammy.bugreport.core.session;

import java.time.Instant;
import java.util.Objects;

final class SessionAuditEventChecks {
    private SessionAuditEventChecks() {}

    static void requireMetadata(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (sequence < 0 || revision < 0) {
            throw new IllegalArgumentException(
                    "Audit sequence and revision must be non-negative");
        }
    }

    static void requireMutationMetadata(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt) {
        requireMetadata(sessionId, sequence, revision, occurredAt);
        if (sequence == 0 || revision == 0) {
            throw new IllegalArgumentException(
                    "Mutation audit sequence and revision must be positive");
        }
    }
}
