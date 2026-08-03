package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import java.util.Objects;

/** Immutable point-in-time view of one report session. */
public record ReportSessionSnapshot(
        ReportSessionId id,
        ProviderSpecification providerSpecification,
        ProviderSupport providerSupport,
        ReportSessionState state,
        long revision) {
    /** Validates a report session snapshot. */
    public ReportSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerSpecification, "providerSpecification");
        Objects.requireNonNull(providerSupport, "providerSupport");
        Objects.requireNonNull(state, "state");
        if (providerSupport.state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "A report session snapshot cannot reference a disabled provider");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Report session revision must be non-negative");
        }
    }
}
