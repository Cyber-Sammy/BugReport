package com.cybersammy.bugreport.core.packaging;

import java.util.Objects;
import java.util.Optional;

/** Safe typed package-plan failure that never retains a local path or content value. */
public final class ReportPackagePlanException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ReportPackagePlanCode code;
    private final String artifactName;

    ReportPackagePlanException(
            ReportPackagePlanCode code, String artifactName, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.artifactName = artifactName;
    }

    public ReportPackagePlanCode code() {
        return code;
    }

    public Optional<String> artifactName() {
        return Optional.ofNullable(artifactName);
    }
}
