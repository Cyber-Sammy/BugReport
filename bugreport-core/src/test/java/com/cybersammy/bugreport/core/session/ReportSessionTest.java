package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportSessionTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

    @Test
    void completesThePrimaryLifecycleWithImmutableMonotonicSnapshots() {
        ProviderSpecification specification = SessionProviderFixture.specification("example_mod");
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(specification);
        RegisteredProvider registered =
                registry.find(ProviderId.parse("example_mod")).orElseThrow();
        ReportSession session =
                new ReportSessionFactory(registry)
                        .create(SESSION_ID, registered.id());
        ReportSessionSnapshot initial = session.snapshot();
        List<ReportSessionState> lifecycle =
                List.of(
                        ReportSessionState.COLLECTION_PLANNED,
                        ReportSessionState.COLLECTING,
                        ReportSessionState.SANITIZING,
                        ReportSessionState.REVIEW_REQUIRED,
                        ReportSessionState.READY,
                        ReportSessionState.DELIVERING,
                        ReportSessionState.COMPLETED);

        ReportSessionSnapshot current = session.selectCategory(CategoryId.of("general"));
        for (ReportSessionState state : lifecycle) {
            current = session.transitionTo(state);
        }

        assertEquals(ReportSessionState.CREATED, initial.state());
        assertEquals(0, initial.revision());
        assertEquals(ReportSessionState.COMPLETED, current.state());
        assertEquals(lifecycle.size() + 1, current.revision());
        assertEquals(SESSION_ID, current.id());
        assertSame(specification, current.providerSpecification());
        assertSame(registered.support(), current.providerSupport());
    }

    @Test
    void recoversFromEachFailureAndFromPartialCollection() {
        ReportSession session = session();
        session.selectCategory(CategoryId.of("general"));

        transition(
                session,
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
        ReportSession session = session();
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
        ReportSession session = session();

        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.transitionTo(ReportSessionState.CANCELLED));
        ReportSessionSnapshot cancelled = session.cancel(CancellationReason.USER_REQUESTED);

        assertEquals(ReportSessionState.CANCELLED, cancelled.state());
        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.transitionTo(ReportSessionState.FORM_IN_PROGRESS));
        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.cancel(CancellationReason.OPERATION_ABORTED));
        assertEquals(cancelled, session.snapshot());
    }

    @Test
    void directFormTransitionCannotBypassCategorySelection() {
        ReportSession session = session();
        ReportSessionSnapshot before = session.snapshot();

        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.transitionTo(ReportSessionState.FORM_IN_PROGRESS));

        assertEquals(before, session.snapshot());
    }

    private static void transition(ReportSession session, ReportSessionState... states) {
        for (ReportSessionState state : states) {
            session.transitionTo(state);
        }
    }

    private static ReportSession session() {
        ProviderRegistrySnapshot registry =
                SessionProviderFixture.registry(
                        SessionProviderFixture.specification("example_mod"));
        return new ReportSessionFactory(registry)
                .create(SESSION_ID, ProviderId.parse("example_mod"));
    }
}
