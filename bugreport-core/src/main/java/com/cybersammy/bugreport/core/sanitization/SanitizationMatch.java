package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Objects;
import java.util.Optional;

/**
 * One transient stage-local match containing no copy of the matched input text.
 *
 * <p>Automatic replacements must be fixed safe labels, never matched or derived secret text.
 */
public final class SanitizationMatch {
    private static final int MAXIMUM_REPLACEMENT_CHARACTERS = 256;

    private final int start;
    private final int end;
    private final PrivacyClassification classification;
    private final String replacement;

    private SanitizationMatch(
            int start,
            int end,
            PrivacyClassification classification,
            String replacement) {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Sanitization match range must be non-empty");
        }
        this.start = start;
        this.end = end;
        this.classification = Objects.requireNonNull(classification, "classification");
        if (replacement != null
                && (replacement.length() > MAXIMUM_REPLACEMENT_CHARACTERS
                        || replacement.indexOf('\n') >= 0
                        || replacement.indexOf('\r') >= 0
                        || replacement.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Sanitization replacement is not bounded text");
        }
        this.replacement = replacement;
    }

    public static SanitizationMatch redact(
            int start,
            int end,
            PrivacyClassification classification,
            String replacement) {
        return new SanitizationMatch(
                start,
                end,
                classification,
                Objects.requireNonNull(replacement, "replacement"));
    }

    public static SanitizationMatch warn(
            int start, int end, PrivacyClassification classification) {
        return new SanitizationMatch(start, end, classification, null);
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public PrivacyClassification classification() {
        return classification;
    }

    public Optional<String> replacement() {
        return Optional.ofNullable(replacement);
    }

    public SanitizationAction action() {
        return replacement == null
                ? SanitizationAction.UNRESOLVED_WARNING
                : SanitizationAction.AUTOMATIC_REDACTION;
    }
}
