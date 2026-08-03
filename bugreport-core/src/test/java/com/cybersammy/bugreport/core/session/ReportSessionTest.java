package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportSessionTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

    @Test
    void completesThePrimaryLifecycleWithImmutableMonotonicSnapshots() {
        ProviderSpecification specification = specification();
        ReportSession session = new ReportSession(SESSION_ID, specification);
        ReportSessionSnapshot initial = session.snapshot();
        List<ReportSessionState> lifecycle =
                List.of(
                        ReportSessionState.FORM_IN_PROGRESS,
                        ReportSessionState.COLLECTION_PLANNED,
                        ReportSessionState.COLLECTING,
                        ReportSessionState.SANITIZING,
                        ReportSessionState.REVIEW_REQUIRED,
                        ReportSessionState.READY,
                        ReportSessionState.DELIVERING,
                        ReportSessionState.COMPLETED);

        ReportSessionSnapshot current = initial;
        for (ReportSessionState state : lifecycle) {
            current = session.transitionTo(state);
        }

        assertEquals(ReportSessionState.CREATED, initial.state());
        assertEquals(0, initial.revision());
        assertEquals(ReportSessionState.COMPLETED, current.state());
        assertEquals(lifecycle.size(), current.revision());
        assertEquals(SESSION_ID, current.id());
        assertSame(specification, current.providerSpecification());
    }

    @Test
    void recoversFromEachFailureAndFromPartialCollection() {
        ReportSession session = new ReportSession(SESSION_ID, specification());

        transition(
                session,
                ReportSessionState.FORM_IN_PROGRESS,
                ReportSessionState.FAILED_VALIDATION,
                ReportSessionState.FORM_IN_PROGRESS,
                ReportSessionState.COLLECTION_PLANNED,
                ReportSessionState.COLLECTING,
                ReportSessionState.PARTIALLY_COLLECTED,
                ReportSessionState.COLLECTION_PLANNED,
                ReportSessionState.COLLECTING,
                ReportSessionState.FAILED_COLLECTION,
                ReportSessionState.COLLECTION_PLANNED,
                ReportSessionState.COLLECTING,
                ReportSessionState.SANITIZING,
                ReportSessionState.FAILED_SANITIZATION,
                ReportSessionState.SANITIZING,
                ReportSessionState.REVIEW_REQUIRED,
                ReportSessionState.READY,
                ReportSessionState.DELIVERING,
                ReportSessionState.FAILED_DELIVERY,
                ReportSessionState.DELIVERING,
                ReportSessionState.COMPLETED);

        assertEquals(ReportSessionState.COMPLETED, session.snapshot().state());
        assertEquals(20, session.snapshot().revision());
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingSnapshot() {
        ReportSession session = new ReportSession(SESSION_ID, specification());
        ReportSessionSnapshot before = session.snapshot();

        InvalidReportSessionTransitionException exception =
                assertThrows(
                        InvalidReportSessionTransitionException.class,
                        () -> session.transitionTo(ReportSessionState.READY));

        assertEquals(SESSION_ID, exception.sessionId());
        assertEquals(ReportSessionState.CREATED, exception.currentState());
        assertEquals(ReportSessionState.READY, exception.requestedState());
        assertEquals(
                "Invalid report session transition for "
                        + SESSION_ID
                        + ": CREATED -> READY",
                exception.getMessage());
        assertEquals(before, session.snapshot());
        assertThrows(NullPointerException.class, () -> session.transitionTo(null));
        assertEquals(before, session.snapshot());
    }

    @Test
    void cancellationIsTerminal() {
        ReportSession session = new ReportSession(SESSION_ID, specification());

        ReportSessionSnapshot cancelled = session.transitionTo(ReportSessionState.CANCELLED);

        assertEquals(ReportSessionState.CANCELLED, cancelled.state());
        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.transitionTo(ReportSessionState.FORM_IN_PROGRESS));
        assertEquals(cancelled, session.snapshot());
    }

    private static void transition(ReportSession session, ReportSessionState... states) {
        for (ReportSessionState state : states) {
            session.transitionTo(state);
        }
    }

    private static ProviderSpecification specification() {
        return ProviderSpecification.builder(
                        ProviderId.parse("example_mod"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example_mod.bugreport.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(
                        CategorySpecification.builder(
                                        CategoryId.of("general"),
                                        LocalizationKey.of(
                                                "example_mod.bugreport.category.general"))
                                .build())
                .build();
    }
}
