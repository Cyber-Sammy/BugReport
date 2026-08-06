package com.cybersammy.bugreport.core.sanitization;

import java.util.Objects;

/** Shared construction rules for path-free sanitization metadata. */
final class SanitizationContract {
    private SanitizationContract() {}

    static String requireArtifactName(String artifactName) {
        String value = Objects.requireNonNull(artifactName, "artifactName");
        if (value.isBlank()
                || value.length() > 160
                || value.contains("/")
                || value.contains("\\")) {
            throw new IllegalArgumentException("Sanitization artifact name is not path-safe");
        }
        return value;
    }
}
