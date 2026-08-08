package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import com.cybersammy.bugreport.core.error.DomainOperation;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Typed path-free failure requiring the caller to discard partial output. */
public final class SanitizationException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final SanitizationCode code;
    private final String artifactName;
    private final String stageId;
    private final long line;

    SanitizationException(
            SanitizationCode code, String artifactName, String message, Throwable cause) {
        this(code, artifactName, null, 0, message, cause);
    }

    SanitizationException(
            SanitizationCode code,
            String artifactName,
            SanitizationStageId stageId,
            long line,
            String message,
            Throwable cause) {
        super(
                DomainErrorCode.from("sanitization", code),
                DomainErrorContext.builder().operation(DomainOperation.SANITIZATION).build(),
                message,
                cause);
        this.code = Objects.requireNonNull(code, "code");
        this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
        this.stageId = stageId == null ? null : stageId.value();
        if ((stageId == null) != (line == 0) || line < 0) {
            throw new IllegalArgumentException(
                    "Sanitization stage failure metadata is inconsistent");
        }
        this.line = line;
    }

    public SanitizationCode code() {
        return code;
    }

    public String artifactName() {
        return artifactName;
    }

    public Optional<SanitizationStageId> stageId() {
        return Optional.ofNullable(stageId).map(SanitizationStageId::new);
    }

    public OptionalLong line() {
        return line == 0 ? OptionalLong.empty() : OptionalLong.of(line);
    }
}
