package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainErrorContextKey;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Objects;

/** Typed failure from bounded streaming of one planned source file. */
public final class SourceCopyException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final SourceCopyCode code;
    private final String sessionId;
    private final LogicalRoot root;
    private final String relativePath;

    SourceCopyException(
            SourceCopyCode code,
            ReportSessionId sessionId,
            LogicalRoot root,
            RelativePath relativePath,
            String message) {
        super(
                DomainErrorCode.from("source_copy", code),
                context(sessionId, root),
                message);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath").value();
    }

    SourceCopyException(
            SourceCopyCode code,
            ReportSessionId sessionId,
            LogicalRoot root,
            RelativePath relativePath,
            String message,
            Throwable cause) {
        super(
                DomainErrorCode.from("source_copy", code),
                context(sessionId, root),
                message,
                cause);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath").value();
    }

    public SourceCopyCode code() {
        return code;
    }

    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    public LogicalRoot root() {
        return root;
    }

    public RelativePath relativePath() {
        return RelativePath.of(relativePath);
    }

    private static DomainErrorContext context(ReportSessionId sessionId, LogicalRoot root) {
        return DomainErrorContext.builder()
                .put(DomainErrorContextKey.SESSION_ID, sessionId.toString())
                .put(DomainErrorContextKey.LOGICAL_ROOT, root.name())
                .build();
    }
}
