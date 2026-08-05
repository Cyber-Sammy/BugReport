package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/** Privacy-safe typed failure for one generated diagnostic invocation. */
public final class GeneratedDiagnosticException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final GeneratedDiagnosticCode code;
    private final String sessionId;
    private final String providerId;
    private final String categoryId;
    private final String generatorId;
    private final String artifactId;

    GeneratedDiagnosticException(
            GeneratedDiagnosticCode code,
            ReportSessionId sessionId,
            ProviderId providerId,
            CategoryId categoryId,
            DiagnosticGeneratorId generatorId,
            GeneratedArtifactId artifactId,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId").toString();
        this.providerId = Objects.requireNonNull(providerId, "providerId").value();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId").value();
        this.generatorId = Objects.requireNonNull(generatorId, "generatorId").value();
        this.artifactId = artifactId == null ? null : artifactId.value();
    }

    public GeneratedDiagnosticCode code() {
        return code;
    }

    public ReportSessionId sessionId() {
        return ReportSessionId.parse(sessionId);
    }

    public ProviderId providerId() {
        return ProviderId.parse(providerId);
    }

    public CategoryId categoryId() {
        return CategoryId.of(categoryId);
    }

    public DiagnosticGeneratorId generatorId() {
        return DiagnosticGeneratorId.of(generatorId);
    }

    public Optional<GeneratedArtifactId> artifactId() {
        return Optional.ofNullable(artifactId).map(GeneratedArtifactId::of);
    }
}
