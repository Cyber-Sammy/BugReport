package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import java.util.Objects;
import java.util.Optional;

/** Immutable point-in-time view of one report session. */
public record ReportSessionSnapshot(
        ReportSessionId id,
        ProviderSpecification providerSpecification,
        ProviderSupport providerSupport,
        Optional<CategorySpecification> selectedCategory,
        ReportSessionState state,
        long revision,
        SessionAuditTrail auditTrail) {
    /** Validates a report session snapshot. */
    public ReportSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerSpecification, "providerSpecification");
        Objects.requireNonNull(providerSupport, "providerSupport");
        Objects.requireNonNull(selectedCategory, "selectedCategory");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(auditTrail, "auditTrail");
        if (providerSupport.state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "A report session snapshot cannot reference a disabled provider");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Report session revision must be non-negative");
        }
        selectedCategory.ifPresent(
                category -> {
                    CategorySpecification declared =
                            providerSpecification.categories().get(category.id());
                    if (declared != category) {
                        throw new IllegalArgumentException(
                                "Selected category must be the exact declaration from the session provider");
                    }
                });
        if (requiresSelectedCategory(state) && selectedCategory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Report session state " + state + " requires a selected category");
        }
        validateAuditTrail(
                id,
                providerSpecification,
                selectedCategory,
                state,
                revision,
                auditTrail);
    }

    private static boolean requiresSelectedCategory(ReportSessionState state) {
        return state != ReportSessionState.CREATED && state != ReportSessionState.CANCELLED;
    }

    private static void validateAuditTrail(
            ReportSessionId id,
            ProviderSpecification providerSpecification,
            Optional<CategorySpecification> selectedCategory,
            ReportSessionState state,
            long revision,
            SessionAuditTrail auditTrail) {
        for (SessionAuditEvent event : auditTrail.events()) {
            if (!id.equals(event.sessionId())) {
                throw new IllegalArgumentException(
                        "Session snapshot and audit trail identities must match");
            }
        }
        SessionAuditEvent latest = auditTrail.events().getLast();
        if (latest.revision() != revision) {
            throw new IllegalArgumentException(
                    "Session snapshot revision must match the latest audit event");
        }
        switch (latest) {
            case SessionAuditEvent.Created created -> {
                if (state != ReportSessionState.CREATED
                        || !providerSpecification.id().equals(created.providerId())) {
                    throw new IllegalArgumentException(
                            "Creation audit event does not match the session snapshot");
                }
            }
            case SessionAuditEvent.CategorySelected selected ->
                    requireCategoryAuditState(selected.categoryId(), selectedCategory, state);
            case SessionAuditEvent.CategoryChanged changed ->
                    requireCategoryAuditState(changed.categoryId(), selectedCategory, state);
            case SessionAuditEvent.FormDraftUpdated ignored -> {
                if (state != ReportSessionState.FORM_IN_PROGRESS
                        || selectedCategory.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Form-draft audit event requires an editable selected category");
                }
            }
            case SessionAuditEvent.StateTransitioned transition -> {
                if (state != transition.state()) {
                    throw new IllegalArgumentException(
                            "Transition audit event does not match the session state");
                }
            }
            case SessionAuditEvent.Cancelled ignored -> {
                if (state != ReportSessionState.CANCELLED) {
                    throw new IllegalArgumentException(
                            "Cancellation audit event requires a cancelled snapshot");
                }
            }
            case SessionAuditEvent.Recovered recovered -> {
                if (state != recovered.state()) {
                    throw new IllegalArgumentException(
                            "Recovery audit event does not match the session state");
                }
            }
        }
    }

    private static void requireCategoryAuditState(
            CategoryId categoryId,
            Optional<CategorySpecification> selectedCategory,
            ReportSessionState state) {
        if (state != ReportSessionState.FORM_IN_PROGRESS
                || selectedCategory.map(CategorySpecification::id)
                        .filter(categoryId::equals)
                        .isEmpty()) {
            throw new IllegalArgumentException(
                    "Category audit event does not match the session snapshot");
        }
    }
}
