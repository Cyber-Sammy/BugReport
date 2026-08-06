package com.cybersammy.bugreport.core.sanitization;

import java.util.Objects;

/** Secret-free failure metadata for one isolated sanitization stage invocation. */
public record SanitizationStageFailure(
        SanitizationStageId stageId, long line, SanitizationStageFailureCode code) {
    public SanitizationStageFailure {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(code, "code");
        if (line < 1) {
            throw new IllegalArgumentException("Sanitization failure line must be positive");
        }
    }
}
