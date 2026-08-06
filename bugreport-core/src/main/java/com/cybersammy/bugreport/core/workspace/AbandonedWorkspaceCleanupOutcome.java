package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;

/** Path-free result for one abandoned session requested by trusted lifecycle code. */
public record AbandonedWorkspaceCleanupOutcome(
        ReportSessionId sessionId, AbandonedWorkspaceCleanupCode code) {
    public AbandonedWorkspaceCleanupOutcome {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(code, "code");
    }

    public boolean removed() {
        return code == AbandonedWorkspaceCleanupCode.REMOVED;
    }
}
