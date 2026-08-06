package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import java.io.Serial;
import java.util.Objects;

/** Internal unchecked propagation of a bounded-sink policy failure through provider code. */
final class GeneratedSinkViolation extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final GeneratedDiagnosticCode code;
    private final String artifactId;

    GeneratedSinkViolation(
            GeneratedDiagnosticCode code,
            GeneratedArtifactId artifactId,
            String message) {
        this(code, artifactId, message, null);
    }

    GeneratedSinkViolation(
            GeneratedDiagnosticCode code,
            GeneratedArtifactId artifactId,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.artifactId = artifactId == null ? null : artifactId.value();
    }

    GeneratedDiagnosticCode code() {
        return code;
    }

    GeneratedArtifactId artifactId() {
        return artifactId == null ? null : GeneratedArtifactId.of(artifactId);
    }
}
