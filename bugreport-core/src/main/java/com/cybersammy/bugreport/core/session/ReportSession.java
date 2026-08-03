package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Objects;

/** Mutable state-machine boundary created from one accepted registry provider. */
public final class ReportSession {
    private final ReportSessionId id;
    private final ProviderSpecification providerSpecification;
    private final ProviderSupport providerSupport;

    private ReportSessionState state = ReportSessionState.CREATED;
    private long revision;

    ReportSession(
            ReportSessionId id,
            RegisteredProvider provider) {
        this.id = Objects.requireNonNull(id, "id");
        RegisteredProvider acceptedProvider = Objects.requireNonNull(provider, "provider");
        if (acceptedProvider.support().state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "Cannot create a report session for a disabled provider: "
                            + acceptedProvider.id());
        }
        providerSpecification = acceptedProvider.specification();
        providerSupport = acceptedProvider.support();
    }

    /** Returns an immutable, internally consistent point-in-time snapshot. */
    public synchronized ReportSessionSnapshot snapshot() {
        return new ReportSessionSnapshot(
                id,
                providerSpecification,
                providerSupport,
                state,
                revision);
    }

    /** Applies one valid direct transition and returns the resulting snapshot. */
    public synchronized ReportSessionSnapshot transitionTo(ReportSessionState requestedState) {
        Objects.requireNonNull(requestedState, "requestedState");
        if (!state.canTransitionTo(requestedState)) {
            throw new InvalidReportSessionTransitionException(id, state, requestedState);
        }
        state = requestedState;
        revision++;
        return new ReportSessionSnapshot(
                id,
                providerSpecification,
                providerSupport,
                state,
                revision);
    }
}
