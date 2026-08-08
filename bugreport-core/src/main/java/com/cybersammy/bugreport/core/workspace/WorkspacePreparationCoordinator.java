package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.core.manifest.ManifestSanitizationStatus;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import com.cybersammy.bugreport.core.sanitization.SanitizationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Issues package authority only from exact coordinator-produced sanitization evidence. */
final class WorkspacePreparationCoordinator {
    private WorkspacePreparationCoordinator() {}

    static PreparedWorkspaceSnapshot prepare(
            ReviewedWorkspaceSnapshot snapshot,
            List<WorkspaceSanitizationCoordinator.SanitizedSource> textEvidence,
            Set<String> explicitlyReviewedArtifacts) {
        ReviewedWorkspaceSnapshot reviewed = Objects.requireNonNull(snapshot, "snapshot");
        Map<String, WorkspaceSanitizationCoordinator.SanitizedSource> evidence = indexEvidence(
                textEvidence);
        Set<String> accepted = Set.copyOf(
                Objects.requireNonNull(explicitlyReviewedArtifacts, "explicitlyReviewedArtifacts"));
        if (accepted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Prepared workspace review contains null");
        }

        List<PreparedWorkspaceArtifact> prepared = new ArrayList<>();
        Set<String> consumedEvidence = new HashSet<>();
        Set<String> consumedReviews = new HashSet<>();
        for (ReviewedWorkspaceArtifact artifact : reviewed.artifacts()) {
            if (artifact.contentType() == DiagnosticContentType.BINARY) {
                requireExplicitBinaryReview(artifact, accepted, consumedReviews);
                prepared.add(new PreparedWorkspaceArtifact(
                        artifact,
                        PrivacyClassification.mostRestrictive(
                                PrivacyClassification.SENSITIVE, artifact.privacy()),
                        ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS,
                        List.of()));
                continue;
            }

            WorkspaceSanitizationCoordinator.SanitizedSource sanitized = evidence.get(
                    artifact.artifactName());
            if (sanitized == null || !sanitized.matches(artifact)) {
                throw new IllegalArgumentException(
                        "Text artifact requires matching coordinator sanitization evidence");
            }
            consumedEvidence.add(artifact.artifactName());
            SanitizationResult result = sanitized.result();
            boolean unresolved = result.hasUnresolvedWarnings();
            if (unresolved && !accepted.contains(artifact.artifactName())) {
                throw new IllegalArgumentException(
                        "Unresolved sanitization findings require explicit review evidence");
            }
            if (unresolved) {
                consumedReviews.add(artifact.artifactName());
            }
            prepared.add(new PreparedWorkspaceArtifact(
                    artifact,
                    effectivePrivacy(artifact, result.findings()),
                    unresolved
                            ? ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS
                            : ManifestSanitizationStatus.SANITIZED,
                    result.findings()));
        }
        if (consumedEvidence.size() != evidence.size() || consumedReviews.size() != accepted.size()) {
            throw new IllegalArgumentException("Prepared workspace evidence contains an unknown artifact");
        }
        return new PreparedWorkspaceSnapshot(reviewed, prepared);
    }

    private static Map<String, WorkspaceSanitizationCoordinator.SanitizedSource> indexEvidence(
            List<WorkspaceSanitizationCoordinator.SanitizedSource> values) {
        List<WorkspaceSanitizationCoordinator.SanitizedSource> copy = List.copyOf(
                Objects.requireNonNull(values, "textEvidence"));
        Map<String, WorkspaceSanitizationCoordinator.SanitizedSource> indexed = new HashMap<>();
        for (WorkspaceSanitizationCoordinator.SanitizedSource value : copy) {
            Objects.requireNonNull(value, "sanitization evidence");
            if (indexed.put(value.source().artifactName(), value) != null) {
                throw new IllegalArgumentException("Sanitization evidence must be unique per artifact");
            }
        }
        return Map.copyOf(indexed);
    }

    private static void requireExplicitBinaryReview(
            ReviewedWorkspaceArtifact artifact,
            Set<String> accepted,
            Set<String> consumedReviews) {
        if (!accepted.contains(artifact.artifactName())) {
            throw new IllegalArgumentException("Binary artifact requires explicit review evidence");
        }
        consumedReviews.add(artifact.artifactName());
    }

    private static PrivacyClassification effectivePrivacy(
            ReviewedWorkspaceArtifact artifact,
            List<SanitizationFinding> findings) {
        PrivacyClassification effective = artifact.privacy();
        for (SanitizationFinding finding : findings) {
            if (finding.action() != SanitizationAction.UNRESOLVED_WARNING) {
                continue;
            }
            if (finding.classification() == PrivacyClassification.PROHIBITED) {
                throw new IllegalArgumentException(
                        "Prohibited sanitization findings cannot remain unresolved");
            }
            effective = PrivacyClassification.mostRestrictive(
                    effective, finding.classification());
        }
        return effective;
    }
}
