package com.cybersammy.bugreport.core.sanitization;

import java.util.Objects;

/** Typed path-free failure requiring the caller to discard partial output. */
public final class SanitizationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final SanitizationCode code;
    private final String artifactName;

    SanitizationException(
            SanitizationCode code, String artifactName, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
    }

    public SanitizationCode code() {
        return code;
    }

    public String artifactName() {
        return artifactName;
    }
}
