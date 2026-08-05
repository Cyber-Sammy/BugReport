package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;

/** Typed failure raised while creating one product-owned report workspace. */
public final class WorkspaceCreationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final WorkspaceCreationCode code;
    private final String sessionId;

    WorkspaceCreationException(
            WorkspaceCreationCode code,
            ReportSessionId sessionId,
            String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
    }

    WorkspaceCreationException(
            WorkspaceCreationCode code,
            ReportSessionId sessionId,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
    }

    /** Returns the stable failure classification. */
    public WorkspaceCreationCode code() {
        return code;
    }

    /** Returns the affected report-session identity. */
    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }
}
