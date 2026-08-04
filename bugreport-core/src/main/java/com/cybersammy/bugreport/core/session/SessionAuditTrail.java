package com.cybersammy.bugreport.core.session;

import java.util.List;
import java.util.Objects;

/** Immutable bounded suffix of one report session's audit history. */
public record SessionAuditTrail(List<SessionAuditEvent> events, long discardedEvents) {
    /** Maximum recent events retained in a session snapshot. */
    public static final int MAX_RETAINED_EVENTS = 256;

    /** Defensively copies and validates ordered audit history. */
    public SessionAuditTrail {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Session audit trail cannot be empty");
        }
        if (events.size() > MAX_RETAINED_EVENTS) {
            throw new IllegalArgumentException(
                    "Session audit trail exceeds " + MAX_RETAINED_EVENTS + " events");
        }
        if (discardedEvents < 0) {
            throw new IllegalArgumentException("Discarded audit-event count must be non-negative");
        }
        ReportSessionId sessionId = null;
        long expectedSequence = discardedEvents;
        long previousRevision = -1;
        for (SessionAuditEvent event : events) {
            SessionAuditEvent validated = Objects.requireNonNull(event, "event");
            if (sessionId == null) {
                sessionId = validated.sessionId();
            } else if (!sessionId.equals(validated.sessionId())) {
                throw new IllegalArgumentException(
                        "Session audit trail cannot mix session identities");
            }
            if (validated.sequence() != expectedSequence
                    || validated.revision() <= previousRevision) {
                throw new IllegalArgumentException(
                        "Session audit events must have contiguous sequence and increasing revision");
            }
            expectedSequence = Math.addExact(expectedSequence, 1);
            previousRevision = validated.revision();
        }
        events = List.copyOf(events);
    }
}
