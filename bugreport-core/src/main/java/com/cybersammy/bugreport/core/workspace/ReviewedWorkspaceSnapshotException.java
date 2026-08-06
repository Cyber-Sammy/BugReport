package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;
import java.util.Optional;

/** Typed privacy-safe failure to seal or verify a reviewed workspace snapshot. */
public final class ReviewedWorkspaceSnapshotException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ReviewedWorkspaceSnapshotCode code;
    private final String sessionId;
    private final String artifactName;

    ReviewedWorkspaceSnapshotException(
            ReviewedWorkspaceSnapshotCode code,
            ReportSessionId sessionId,
            String artifactName,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.artifactName = artifactName;
    }

    public ReviewedWorkspaceSnapshotCode code() {
        return code;
    }

    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    public Optional<String> artifactName() {
        return Optional.ofNullable(artifactName);
    }
}
