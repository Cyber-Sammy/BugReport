package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Map;
import java.util.Objects;

/** Immutable product policy selecting redaction or explicit review for each detector. */
public final class SanitizationPolicy {
    private final SanitizationProfile profile;
    private final SanitizationArtifactPolicy artifactPolicy;
    private final Map<SanitizationStageId, SanitizationAction> customActions;

    private SanitizationPolicy(
            SanitizationProfile profile,
            SanitizationArtifactPolicy artifactPolicy,
            Map<SanitizationStageId, SanitizationAction> customActions) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.artifactPolicy = Objects.requireNonNull(artifactPolicy, "artifactPolicy");
        Map<SanitizationStageId, SanitizationAction> supplied =
                Objects.requireNonNull(customActions, "customActions");
        if (supplied.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Custom sanitization actions must not contain null");
        }
        this.customActions = Map.copyOf(supplied);
        if (profile != SanitizationProfile.CUSTOM_REVIEW && !this.customActions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Custom actions require the custom-review profile");
        }
    }

    public static SanitizationPolicy standard(SanitizationArtifactPolicy artifactPolicy) {
        return new SanitizationPolicy(
                SanitizationProfile.STANDARD, artifactPolicy, Map.of());
    }

    public static SanitizationPolicy strictPrivacy(
            SanitizationArtifactPolicy artifactPolicy) {
        return new SanitizationPolicy(
                SanitizationProfile.STRICT_PRIVACY, artifactPolicy, Map.of());
    }

    public static SanitizationPolicy customReview(
            SanitizationArtifactPolicy artifactPolicy,
            Map<SanitizationStageId, SanitizationAction> customActions) {
        return new SanitizationPolicy(
                SanitizationProfile.CUSTOM_REVIEW, artifactPolicy, customActions);
    }

    public SanitizationProfile profile() {
        return profile;
    }

    public SanitizationArtifactPolicy artifactPolicy() {
        return artifactPolicy;
    }

    public SanitizationAction actionFor(
            SanitizationStageId stageId, PrivacyClassification classification) {
        SanitizationStageId id = Objects.requireNonNull(stageId, "stageId");
        PrivacyClassification privacy =
                Objects.requireNonNull(classification, "classification");
        if (privacy == PrivacyClassification.PROHIBITED) {
            return SanitizationAction.AUTOMATIC_REDACTION;
        }
        return switch (profile) {
            case STRICT_PRIVACY -> SanitizationAction.AUTOMATIC_REDACTION;
            case CUSTOM_REVIEW -> customActions.getOrDefault(
                    id, SanitizationAction.UNRESOLVED_WARNING);
            case STANDARD -> standardAction(id);
        };
    }

    private SanitizationAction standardAction(SanitizationStageId stageId) {
        if (artifactPolicy == SanitizationArtifactPolicy.CONFIGURATION) {
            return SanitizationAction.AUTOMATIC_REDACTION;
        }
        if (stageId.equals(NetworkAddressSanitizationStage.ID)
                || stageId.equals(ServerAddressSanitizationStage.ID)) {
            return SanitizationAction.UNRESOLVED_WARNING;
        }
        return SanitizationAction.AUTOMATIC_REDACTION;
    }
}
