package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.core.manifest.ManifestSanitizationStatus;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import com.cybersammy.bugreport.core.sanitization.SanitizationResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Internal issuing boundary used only after trusted sanitization and explicit review. */
final class PreparedWorkspaceSnapshotFactory {
    private PreparedWorkspaceSnapshotFactory() {}

    /**
     * Issues post-sanitization authority for exact final bytes in a reviewed snapshot.
     *
     * <p>The package-private caller must be the workspace sanitization coordinator that produced
     * those final bytes and results. Keeping this boundary internal prevents an arbitrary manifest
     * caller from manufacturing a sanitization claim.
     */
    static PreparedWorkspaceSnapshot issue(
            ReviewedWorkspaceSnapshot snapshot,
            Map<String, SanitizationResult> textResults,
            Set<String> explicitlyReviewedArtifacts) {
        ReviewedWorkspaceSnapshot reviewed = Objects.requireNonNull(snapshot, "snapshot");
        Map<String, SanitizationResult> results = Map.copyOf(
                Objects.requireNonNull(textResults, "textResults"));
        Set<String> accepted = Set.copyOf(
                Objects.requireNonNull(explicitlyReviewedArtifacts, "explicitlyReviewedArtifacts"));
        if (results.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)
                || accepted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Prepared workspace evidence contains null");
        }

        List<PreparedWorkspaceArtifact> prepared = new ArrayList<>();
        Set<String> consumedResults = new HashSet<>();
        Set<String> consumedReviews = new HashSet<>();
        for (ReviewedWorkspaceArtifact artifact : reviewed.artifacts()) {
            if (artifact.contentType() == DiagnosticContentType.BINARY) {
                if (!accepted.contains(artifact.artifactName())) {
                    throw new IllegalArgumentException(
                            "Binary artifact requires explicit review evidence");
                }
                consumedReviews.add(artifact.artifactName());
                prepared.add(new PreparedWorkspaceArtifact(
                        artifact,
                        PrivacyClassification.mostRestrictive(
                                PrivacyClassification.SENSITIVE, artifact.privacy()),
                        ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS,
                        List.of()));
                continue;
            }

            SanitizationResult result = results.get(artifact.artifactName());
            if (result == null || !result.artifactName().equals(artifact.artifactName())) {
                throw new IllegalArgumentException(
                        "Text artifact requires trusted sanitization evidence");
            }
            consumedResults.add(artifact.artifactName());
            boolean unresolved = result.hasUnresolvedWarnings();
            if (unresolved && !accepted.contains(artifact.artifactName())) {
                throw new IllegalArgumentException(
                        "Unresolved sanitization findings require explicit review evidence");
            }
            if (unresolved) {
                consumedReviews.add(artifact.artifactName());
            }
            PrivacyClassification effective = effectivePrivacy(artifact, result.findings());
            prepared.add(new PreparedWorkspaceArtifact(
                    artifact,
                    effective,
                    unresolved
                            ? ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS
                            : ManifestSanitizationStatus.SANITIZED,
                    result.findings()));
        }
        if (consumedResults.size() != results.size() || consumedReviews.size() != accepted.size()) {
            throw new IllegalArgumentException(
                    "Prepared workspace evidence contains an unknown artifact");
        }
        return new PreparedWorkspaceSnapshot(reviewed, prepared);
    }

    private static PrivacyClassification effectivePrivacy(
            ReviewedWorkspaceArtifact artifact, List<SanitizationFinding> findings) {
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
