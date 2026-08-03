package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Objects;

/** Mutable state-machine boundary for one report based on an immutable provider specification. */
public final class ReportSession {
    private final ReportSessionId id;
    private final ProviderSpecification providerSpecification;

    private ReportSessionState state = ReportSessionState.CREATED;
    private long revision;

    /** Creates a new session in {@link ReportSessionState#CREATED}. */
    public ReportSession(
            ReportSessionId id,
            ProviderSpecification providerSpecification) {
        this.id = Objects.requireNonNull(id, "id");
        this.providerSpecification =
                Objects.requireNonNull(providerSpecification, "providerSpecification");
    }

    /** Returns an immutable, internally consistent point-in-time snapshot. */
    public synchronized ReportSessionSnapshot snapshot() {
        return new ReportSessionSnapshot(id, providerSpecification, state, revision);
    }

    /** Applies one valid direct transition and returns the resulting snapshot. */
    public synchronized ReportSessionSnapshot transitionTo(ReportSessionState requestedState) {
        Objects.requireNonNull(requestedState, "requestedState");
        if (!state.canTransitionTo(requestedState)) {
            throw new InvalidReportSessionTransitionException(id, state, requestedState);
        }
        state = requestedState;
        revision++;
        return new ReportSessionSnapshot(id, providerSpecification, state, revision);
    }
}
