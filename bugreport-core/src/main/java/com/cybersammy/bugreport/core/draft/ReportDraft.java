package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.util.Objects;
import java.util.Optional;

/** Immutable persistence-safe report draft without loader or provider objects. */
public record ReportDraft(
        ReportSessionId sessionId,
        long revision,
        ProviderId providerId,
        ProviderVersion providerVersion,
        Optional<CategoryId> categoryId,
        ReportSessionState recordedState,
        FormSubmission formSubmission) {
    /** Validates a bounded draft model. */
    public ReportDraft {
        Objects.requireNonNull(sessionId, "sessionId");
        if (revision < 0) {
            throw new IllegalArgumentException("Draft revision must be non-negative");
        }
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(recordedState, "recordedState");
        Objects.requireNonNull(formSubmission, "formSubmission");
        if (requiresCategory(recordedState) && categoryId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Draft state " + recordedState + " requires a category");
        }
        if (categoryId.isEmpty() && !formSubmission.values().isEmpty()) {
            throw new IllegalArgumentException("A draft without a category cannot contain fields");
        }
    }

    /**
     * Creates a draft from one internally consistent session snapshot and current form values.
     *
     * @param session trusted session snapshot
     * @param formSubmission bounded form values
     * @return persistence-safe draft
     */
    public static ReportDraft from(
            ReportSessionSnapshot session, FormSubmission formSubmission) {
        ReportSessionSnapshot snapshot = Objects.requireNonNull(session, "session");
        FormSubmission submission = Objects.requireNonNull(formSubmission, "formSubmission");
        snapshot.selectedCategory().ifPresent(
                category -> {
                    for (FieldId fieldId : submission.values().keySet()) {
                        if (!category.fields().containsKey(fieldId)) {
                            throw new IllegalArgumentException(
                                    "Draft field is not declared by the selected category: "
                                            + fieldId);
                        }
                    }
                });
        return new ReportDraft(
                snapshot.id(),
                snapshot.revision(),
                snapshot.providerSpecification().id(),
                snapshot.providerSpecification().version(),
                snapshot.selectedCategory().map(category -> category.id()),
                snapshot.state(),
                submission);
    }

    private static boolean requiresCategory(ReportSessionState state) {
        return state != ReportSessionState.CREATED && state != ReportSessionState.CANCELLED;
    }
}
