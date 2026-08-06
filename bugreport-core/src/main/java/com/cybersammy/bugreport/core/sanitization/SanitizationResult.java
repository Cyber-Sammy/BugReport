package com.cybersammy.bugreport.core.sanitization;

import java.util.List;
import java.util.Objects;

/** Immutable metadata for one successful streaming sanitization pass. */
public record SanitizationResult(
        String artifactName,
        long inputCharacters,
        long outputCharacters,
        List<SanitizationFinding> findings) {
    public SanitizationResult {
        String canonicalArtifact = SanitizationContract.requireArtifactName(artifactName);
        artifactName = canonicalArtifact;
        if (inputCharacters < 0
                || inputCharacters > SanitizationPipeline.PRODUCT_MAX_INPUT_CHARACTERS
                || outputCharacters < 0
                || outputCharacters > SanitizationPipeline.PRODUCT_MAX_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Sanitization character counts must be within product limits");
        }
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (findings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Sanitization metadata must not contain null");
        }
        if (findings.size() > SanitizationPipeline.PRODUCT_MAX_FINDINGS) {
            throw new IllegalArgumentException(
                    "Sanitization metadata exceeded product limits");
        }
        if (findings.stream()
                .anyMatch(finding -> !canonicalArtifact.equals(finding.artifactName()))) {
            throw new IllegalArgumentException(
                    "Sanitization findings must belong to the result artifact");
        }
        requireNonDecreasingLines(
                findings.stream().mapToLong(SanitizationFinding::line).toArray());
    }

    public boolean hasUnresolvedWarnings() {
        return findings.stream()
                .anyMatch(finding -> finding.action() == SanitizationAction.UNRESOLVED_WARNING);
    }

    private static void requireNonDecreasingLines(long[] lines) {
        for (int index = 1; index < lines.length; index++) {
            if (lines[index - 1] > lines[index]) {
                throw new IllegalArgumentException(
                        "Sanitization metadata must use deterministic line order");
            }
        }
    }
}
