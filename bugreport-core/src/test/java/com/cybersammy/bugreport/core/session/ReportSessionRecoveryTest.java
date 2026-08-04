package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.form.FormSubmission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ReportSessionRecoveryTest {
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("00000000-0000-4000-8000-000000000041");
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final Instant RECOVERED_AT = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void resumesAdvancedDraftInFormEditingWithOneTruthfulRecoveryEvent() {
        FormSubmission form = FormSubmission.empty();
        ReportDraft draft = draft(7, ReportSessionState.COLLECTING, Optional.of(CATEGORY_ID), form);

        RecoveredReportSession recovered = factory().recover(draft);
        ReportSessionSnapshot snapshot = recovered.session().snapshot();

        assertEquals(ReportSessionState.FORM_IN_PROGRESS, snapshot.state());
        assertEquals(8, snapshot.revision());
        assertEquals(CATEGORY_ID, snapshot.selectedCategory().orElseThrow().id());
        assertSame(form, recovered.formSubmission());
        assertEquals(ReportSessionState.COLLECTING, recovered.recordedState());
        assertEquals(8, snapshot.auditTrail().discardedEvents());
        SessionAuditEvent.Recovered event =
                (SessionAuditEvent.Recovered) snapshot.auditTrail().events().getFirst();
        assertEquals(8, event.sequence());
        assertEquals(8, event.revision());
        assertEquals(RECOVERED_AT, event.occurredAt());
        assertEquals(ReportSessionState.COLLECTING, event.recordedState());
        assertEquals(ReportSessionState.FORM_IN_PROGRESS, event.state());
    }

    @Test
    void resumesUntouchedDraftInCreatedState() {
        RecoveredReportSession recovered =
                factory().recover(draft(0, ReportSessionState.CREATED, Optional.empty(), FormSubmission.empty()));

        ReportSessionSnapshot snapshot = recovered.session().snapshot();
        assertEquals(ReportSessionState.CREATED, snapshot.state());
        assertEquals(1, snapshot.revision());
        assertEquals(1, snapshot.auditTrail().discardedEvents());
    }

    @Test
    void rejectsTerminalDraftsAndExhaustedRevisionWithStableCodes() {
        assertRecoveryCode(
                ReportSessionRecoveryCode.TERMINAL_DRAFT,
                draft(3, ReportSessionState.COMPLETED, Optional.of(CATEGORY_ID), FormSubmission.empty()));
        assertRecoveryCode(
                ReportSessionRecoveryCode.TERMINAL_DRAFT,
                draft(2, ReportSessionState.CANCELLED, Optional.empty(), FormSubmission.empty()));
        assertRecoveryCode(
                ReportSessionRecoveryCode.REVISION_EXHAUSTED,
                draft(
                        Long.MAX_VALUE,
                        ReportSessionState.FORM_IN_PROGRESS,
                        Optional.of(CATEGORY_ID),
                        FormSubmission.empty()));
    }

    private static ReportSessionFactory factory() {
        return new ReportSessionFactory(
                SessionProviderFixture.registry(SessionProviderFixture.specification("example_mod")),
                Clock.fixed(RECOVERED_AT, ZoneOffset.UTC));
    }

    private static ReportDraft draft(
            long revision,
            ReportSessionState state,
            Optional<CategoryId> category,
            FormSubmission form) {
        return new ReportDraft(
                SESSION_ID,
                revision,
                PROVIDER_ID,
                PROVIDER_VERSION,
                category,
                state,
                form);
    }

    private static void assertRecoveryCode(ReportSessionRecoveryCode code, ReportDraft draft) {
        ReportSessionRecoveryException exception =
                assertThrows(ReportSessionRecoveryException.class, () -> factory().recover(draft));
        assertEquals(code, exception.code());
    }
}
