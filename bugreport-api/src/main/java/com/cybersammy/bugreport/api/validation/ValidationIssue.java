package com.cybersammy.bugreport.api.validation;

import com.cybersammy.bugreport.api.identifier.ValidationCode;
import java.util.Objects;

/**
 * One immutable machine-readable validation issue.
 *
 * @param severity issue severity
 * @param code stable namespaced issue code
 * @param path exact nested contract path
 * @param message bounded developer-facing explanation
 */
public record ValidationIssue(
        ValidationSeverity severity,
        ValidationCode code,
        ValidationPath path,
        String message) {
    private static final int MAX_MESSAGE_LENGTH = 1_024;

    /** Validates and creates an issue. */
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(path, "path");
        if (message == null
                || message.isBlank()
                || message.length() > MAX_MESSAGE_LENGTH
                || message.chars().anyMatch(ValidationIssue::isDisallowedControl)) {
            throw new IllegalArgumentException(
                    "Validation message must be non-blank, bounded, single-line text");
        }
    }

    private static boolean isDisallowedControl(int character) {
        return Character.isISOControl(character);
    }
}
