package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.core.manifest.ManifestSanitizationStatus;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import java.util.List;
import java.util.Objects;

/** Exact reviewed artifact plus trusted post-sanitization and review metadata. */
public final class PreparedWorkspaceArtifact {
    private final ReviewedWorkspaceArtifact artifact;
    private final PrivacyClassification effectivePrivacy;
    private final ManifestSanitizationStatus sanitizationStatus;
    private final List<SanitizationFinding> sanitizationFindings;

    PreparedWorkspaceArtifact(
            ReviewedWorkspaceArtifact artifact,
            PrivacyClassification effectivePrivacy,
            ManifestSanitizationStatus sanitizationStatus,
            List<SanitizationFinding> sanitizationFindings) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.effectivePrivacy = Objects.requireNonNull(effectivePrivacy, "effectivePrivacy");
        this.sanitizationStatus =
                Objects.requireNonNull(sanitizationStatus, "sanitizationStatus");
        this.sanitizationFindings = List.copyOf(
                Objects.requireNonNull(sanitizationFindings, "sanitizationFindings"));
        if (this.sanitizationFindings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Prepared findings must not contain null");
        }
        if (!effectivePrivacy.isAtLeast(artifact.privacy())
                || this.sanitizationFindings.stream()
                        .anyMatch(finding -> !artifact.artifactName().equals(
                                finding.artifactName()))) {
            throw new IllegalArgumentException(
                    "Prepared metadata must retain artifact identity and privacy floor");
        }
        boolean unresolved = this.sanitizationFindings.stream()
                .anyMatch(finding -> finding.action() == SanitizationAction.UNRESOLVED_WARNING);
        if (this.sanitizationFindings.stream()
                .filter(finding -> finding.action() == SanitizationAction.UNRESOLVED_WARNING)
                .anyMatch(finding -> finding.classification() == PrivacyClassification.PROHIBITED
                        || !effectivePrivacy.isAtLeast(finding.classification()))) {
            throw new IllegalArgumentException(
                    "Prepared metadata cannot weaken unresolved privacy findings");
        }
        if (artifact.contentType() == DiagnosticContentType.BINARY) {
            if (sanitizationStatus != ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS
                    || (effectivePrivacy != PrivacyClassification.SENSITIVE
                            && effectivePrivacy != PrivacyClassification.PROHIBITED)
                    || !this.sanitizationFindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "Prepared binary artifacts require explicit sensitive review");
            }
        } else if (sanitizationStatus
                != (unresolved
                        ? ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS
                        : ManifestSanitizationStatus.SANITIZED)) {
            throw new IllegalArgumentException(
                    "Prepared text status must match unresolved sanitization findings");
        }
    }

    public ReviewedWorkspaceArtifact artifact() {
        return artifact;
    }

    public PrivacyClassification effectivePrivacy() {
        return effectivePrivacy;
    }

    public ManifestSanitizationStatus sanitizationStatus() {
        return sanitizationStatus;
    }

    public List<SanitizationFinding> sanitizationFindings() {
        return sanitizationFindings;
    }
}
