package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.nio.file.Path;
import java.util.Objects;

/** Trusted handle to one newly created product-owned report workspace. */
public final class ReportWorkspace {
    private final ReportSessionId sessionId;
    private final Path directory;

    ReportWorkspace(ReportSessionId sessionId, Path directory) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        Path normalized = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath()
                .normalize();
        if (normalized.getFileName() == null
                || !normalized.getFileName().toString().equals(sessionId.toString())) {
            throw new IllegalArgumentException(
                    "A report workspace directory must use its canonical session ID");
        }
        this.directory = normalized;
    }

    /** Returns the report session that exclusively owns this workspace. */
    public ReportSessionId sessionId() {
        return sessionId;
    }

    /** Returns the Core-owned directory; this path must never be exposed to providers. */
    public Path directory() {
        return directory;
    }
}
