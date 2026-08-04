package com.cybersammy.bugreport.core.session;

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
        long revision) {
    /** Validates a report session snapshot. */
    public ReportSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerSpecification, "providerSpecification");
        Objects.requireNonNull(providerSupport, "providerSupport");
        Objects.requireNonNull(selectedCategory, "selectedCategory");
        Objects.requireNonNull(state, "state");
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
    }

    private static boolean requiresSelectedCategory(ReportSessionState state) {
        return state != ReportSessionState.CREATED && state != ReportSessionState.CANCELLED;
    }
}
