package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SessionAuditTrailTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000021"));

    @Test
    void defensivelyCopiesEvents() {
        ArrayList<SessionAuditEvent> events = new ArrayList<>();
        events.add(created(SESSION_ID));

        SessionAuditTrail trail = new SessionAuditTrail(events, 0);
        events.clear();

        assertEquals(1, trail.events().size());
        assertThrows(UnsupportedOperationException.class, () -> trail.events().clear());
    }

    @Test
    void rejectsEmptyMixedOrNonContiguousHistory() {
        assertThrows(IllegalArgumentException.class, () -> new SessionAuditTrail(List.of(), 0));

        ReportSessionId other =
                new ReportSessionId(
                        UUID.fromString("00000000-0000-4000-8000-000000000022"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SessionAuditTrail(
                                List.of(
                                        created(SESSION_ID),
                                        new SessionAuditEvent.CategorySelected(
                                                other,
                                                1,
                                                1,
                                                Instant.EPOCH,
                                                CategoryId.of("general"))),
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionAuditTrail(List.of(created(SESSION_ID)), 1));
    }

    @Test
    void rejectsRevisionThatDiffersFromSequenceInFullAndTruncatedHistory() {
        SessionAuditEvent mismatchedFull =
                new SessionAuditEvent.CategorySelected(
                        SESSION_ID,
                        1,
                        2,
                        Instant.EPOCH,
                        CategoryId.of("general"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SessionAuditTrail(
                                List.of(created(SESSION_ID), mismatchedFull),
                                0));

        SessionAuditEvent mismatchedSuffix =
                new SessionAuditEvent.CategorySelected(
                        SESSION_ID,
                        46,
                        47,
                        Instant.EPOCH,
                        CategoryId.of("general"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionAuditTrail(List.of(mismatchedSuffix), 46));
    }

    private static SessionAuditEvent.Created created(ReportSessionId sessionId) {
        return new SessionAuditEvent.Created(
                sessionId,
                0,
                0,
                Instant.EPOCH,
                ProviderId.parse("example_mod"));
    }
}
