package com.cybersammy.bugreport.core.session;

import java.io.Serial;
import java.util.Objects;

/** Typed rejection of an invalid report session state transition. */
public final class InvalidReportSessionTransitionException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final ReportSessionState currentState;
    private final ReportSessionState requestedState;

    InvalidReportSessionTransitionException(
            ReportSessionId sessionId,
            ReportSessionState currentState,
            ReportSessionState requestedState) {
        super(
                "Invalid report session transition for "
                        + sessionId
                        + ": "
                        + currentState
                        + " -> "
                        + requestedState);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.currentState = Objects.requireNonNull(currentState, "currentState");
        this.requestedState = Objects.requireNonNull(requestedState, "requestedState");
    }

    /** Returns the rejected session identity. */
    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    /** Returns the state that rejected the transition. */
    public ReportSessionState currentState() {
        return currentState;
    }

    /** Returns the requested state. */
    public ReportSessionState requestedState() {
        return requestedState;
    }
}
