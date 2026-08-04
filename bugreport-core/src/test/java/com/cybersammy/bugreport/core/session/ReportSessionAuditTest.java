package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ReportSessionAuditTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000020"));
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final CategoryId GENERAL = CategoryId.of("general");
    private static final CategoryId CRASH = CategoryId.of("crash");
    private static final Instant AUDIT_TIME = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void recordsTypedPrivacyMinimizedEventsForSuccessfulMutations() {
        ReportSession session = session(Clock.fixed(AUDIT_TIME, ZoneOffset.UTC));

        session.selectCategory(GENERAL);
        session.selectCategory(CRASH);
        session.transitionTo(ReportSessionState.FAILED_VALIDATION);
        session.transitionTo(ReportSessionState.FORM_IN_PROGRESS);
        ReportSessionSnapshot cancelled = session.cancel(CancellationReason.USER_REQUESTED);

        var events = cancelled.auditTrail().events();
        SessionAuditEvent.Created created =
                assertInstanceOf(SessionAuditEvent.Created.class, events.get(0));
        assertEquals(PROVIDER_ID, created.providerId());
        assertEquals(AUDIT_TIME, created.occurredAt());

        SessionAuditEvent.CategorySelected selected =
                assertInstanceOf(SessionAuditEvent.CategorySelected.class, events.get(1));
        assertEquals(GENERAL, selected.categoryId());

        SessionAuditEvent.CategoryChanged changed =
                assertInstanceOf(SessionAuditEvent.CategoryChanged.class, events.get(2));
        assertEquals(GENERAL, changed.previousCategoryId());
        assertEquals(CRASH, changed.categoryId());

        SessionAuditEvent.StateTransitioned failed =
                assertInstanceOf(SessionAuditEvent.StateTransitioned.class, events.get(3));
        assertEquals(ReportSessionState.FORM_IN_PROGRESS, failed.previousState());
        assertEquals(ReportSessionState.FAILED_VALIDATION, failed.state());

        SessionAuditEvent.Cancelled cancellation =
                assertInstanceOf(SessionAuditEvent.Cancelled.class, events.getLast());
        assertEquals(ReportSessionState.FORM_IN_PROGRESS, cancellation.previousState());
        assertEquals(CancellationReason.USER_REQUESTED, cancellation.reason());
        assertEquals(cancelled.revision(), cancellation.revision());
        assertEquals(0, cancelled.auditTrail().discardedEvents());
    }

    @Test
    void rejectedAndIdempotentOperationsDoNotReadClockOrAppendEvents() {
        CountingClock clock = new CountingClock(AUDIT_TIME);
        ReportSession session = session(clock);
        assertEquals(1, clock.reads());

        session.selectCategory(GENERAL);
        ReportSessionSnapshot selected = session.snapshot();
        assertEquals(2, clock.reads());

        assertEquals(selected, session.selectCategory(GENERAL));
        assertThrows(
                UnknownReportCategoryException.class,
                () -> session.selectCategory(CategoryId.of("missing")));
        assertThrows(
                InvalidReportSessionTransitionException.class,
                () -> session.transitionTo(ReportSessionState.READY));
        assertThrows(NullPointerException.class, () -> session.cancel(null));

        assertEquals(2, clock.reads());
        assertEquals(selected, session.snapshot());
    }

    @Test
    void clockFailureLeavesSessionAndAuditTrailUnchanged() {
        Clock clock = new Clock() {
            private boolean created;

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                if (created) {
                    throw new IllegalStateException("clock failure");
                }
                created = true;
                return AUDIT_TIME;
            }
        };
        ReportSession session = session(clock);
        ReportSessionSnapshot before = session.snapshot();

        assertThrows(IllegalStateException.class, () -> session.selectCategory(GENERAL));

        assertEquals(before, session.snapshot());
    }

    @Test
    void retainsBoundedAuditSuffixWithExactDiscardCount() {
        ReportSession session = session(Clock.fixed(AUDIT_TIME, ZoneOffset.UTC));
        session.selectCategory(GENERAL);
        for (int index = 0; index < 300; index++) {
            session.transitionTo(
                    index % 2 == 0
                            ? ReportSessionState.FAILED_VALIDATION
                            : ReportSessionState.FORM_IN_PROGRESS);
        }

        ReportSessionSnapshot snapshot = session.snapshot();
        SessionAuditTrail trail = snapshot.auditTrail();

        assertEquals(SessionAuditTrail.MAX_RETAINED_EVENTS, trail.events().size());
        assertEquals(46, trail.discardedEvents());
        assertEquals(46, trail.events().getFirst().sequence());
        assertEquals(301, trail.events().getLast().sequence());
        assertEquals(snapshot.revision(), trail.events().getLast().revision());
    }

    private static ReportSession session(Clock clock) {
        ProviderSpecification specification =
                SessionProviderFixture.specificationBuilder(PROVIDER_ID.value())
                        .addCategory(
                                CategorySpecification.builder(
                                                CRASH,
                                                LocalizationKey.of(
                                                        "example_mod.category.crash"))
                                        .build())
                        .build();
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(specification);
        return new ReportSessionFactory(registry, clock).create(SESSION_ID, PROVIDER_ID);
    }

    private static final class CountingClock extends Clock {
        private final Instant instant;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            reads.incrementAndGet();
            return instant;
        }

        private int reads() {
            return reads.get();
        }
    }
}
