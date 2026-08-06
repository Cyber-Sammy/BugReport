package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Objects;

/** Fail-closed privacy assessment for opaque content that cannot enter the text pipeline. */
public record BinarySanitizationAssessment(
        String artifactName,
        PrivacyClassification classification,
        SanitizationAction action,
        boolean excludedPendingReview) {
    public BinarySanitizationAssessment {
        artifactName = SanitizationContract.requireArtifactName(artifactName);
        classification = Objects.requireNonNull(classification, "classification");
        action = Objects.requireNonNull(action, "action");
        if (!classification.isAtLeast(PrivacyClassification.SENSITIVE)
                || action != SanitizationAction.UNRESOLVED_WARNING
                || !excludedPendingReview) {
            throw new IllegalArgumentException(
                    "Binary content must remain sensitive and excluded pending review");
        }
    }
}
