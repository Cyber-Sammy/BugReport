package com.cybersammy.bugreport.core.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ReportDraftTest {
    @Test
    void createdAndEarlyCancelledDraftsMayOmitCategory() {
        assertDoesNotThrow(
                () -> draft(ReportSessionState.CREATED, Optional.empty(), FormSubmission.empty()));
        assertDoesNotThrow(
                () -> draft(ReportSessionState.CANCELLED, Optional.empty(), FormSubmission.empty()));
        assertDoesNotThrow(
                () ->
                        draft(
                                ReportSessionState.CANCELLED,
                                Optional.of(CategoryId.of("general")),
                                FormSubmission.empty()));
    }

    @Test
    void rejectsMissingCategoryForActiveStateAndValuesWithoutCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        draft(
                                ReportSessionState.FORM_IN_PROGRESS,
                                Optional.empty(),
                                FormSubmission.empty()));
        FormSubmission values =
                FormSubmission.builder()
                        .put(FieldId.of("summary"), new FieldValue.Text("summary"))
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> draft(ReportSessionState.CREATED, Optional.empty(), values));
    }

    private static ReportDraft draft(
            ReportSessionState state,
            Optional<CategoryId> category,
            FormSubmission submission) {
        return new ReportDraft(
                ReportSessionId.parse("00000000-0000-4000-8000-000000000015"),
                0,
                ProviderId.parse("example_mod"),
                ProviderVersion.parse("1.0.0"),
                category,
                state,
                submission);
    }
}
