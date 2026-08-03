package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Objects;

/** Immutable point-in-time view of one report session. */
public record ReportSessionSnapshot(
        ReportSessionId id,
        ProviderSpecification providerSpecification,
        ReportSessionState state,
        long revision) {
    /** Validates a report session snapshot. */
    public ReportSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerSpecification, "providerSpecification");
        Objects.requireNonNull(state, "state");
        if (revision < 0) {
            throw new IllegalArgumentException("Report session revision must be non-negative");
        }
    }
}
