package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Objects;

/**
 * Path-safe privacy finding that deliberately excludes the matched text.
 *
 * <p>Columns are one-based UTF-16 boundaries in the original input line, before earlier stages
 * changed its length.
 */
public record SanitizationFinding(
        String artifactName,
        SanitizationStageId stageId,
        long line,
        int startColumn,
        int endColumn,
        PrivacyClassification classification,
        SanitizationAction action) {
    public SanitizationFinding {
        artifactName = SanitizationContract.requireArtifactName(artifactName);
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(action, "action");
        if (line < 1 || startColumn < 1 || endColumn <= startColumn) {
            throw new IllegalArgumentException("Sanitization finding location is invalid");
        }
    }
}
