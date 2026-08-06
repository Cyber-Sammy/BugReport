package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact portable metadata for one reviewed package content entry. */
public record ManifestEntry(
        String archivePath,
        long uncompressedBytes,
        Sha256Checksum checksum,
        DiagnosticContentType contentType,
        Optional<String> mediaType,
        PrivacyClassification effectivePrivacy,
        ReportQualityRole qualityRole,
        ManifestCollectionStatus collectionStatus,
        ManifestSanitizationStatus sanitizationStatus,
        List<ManifestEntryProvenance> provenances,
        List<SanitizationFinding> sanitizationFindings,
        ExtensionMetadata extensions)
        implements Comparable<ManifestEntry> {
    public static final int MAX_PROVENANCES = 64;
    public static final int MAX_FINDINGS = 10_000;

    public ManifestEntry {
        archivePath = ManifestContract.requireArchivePath(archivePath);
        if (uncompressedBytes < 0) {
            throw new IllegalArgumentException("Manifest entry size must be non-negative");
        }
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(mediaType, "mediaType");
        mediaType = mediaType.map(ManifestContract::requireMediaType);
        Objects.requireNonNull(effectivePrivacy, "effectivePrivacy");
        if (effectivePrivacy == PrivacyClassification.PROHIBITED) {
            throw new IllegalArgumentException("Prohibited content cannot enter a manifest");
        }
        Objects.requireNonNull(qualityRole, "qualityRole");
        Objects.requireNonNull(collectionStatus, "collectionStatus");
        Objects.requireNonNull(sanitizationStatus, "sanitizationStatus");
        provenances = canonicalProvenances(provenances);
        sanitizationFindings = canonicalFindings(archivePath, sanitizationFindings);
        Objects.requireNonNull(extensions, "extensions");
        validatePrivacy(effectivePrivacy, provenances);
        validateCollection(collectionStatus, provenances);
        validateSanitization(contentType, sanitizationStatus, sanitizationFindings);
    }

    @Override
    public int compareTo(ManifestEntry other) {
        return archivePath.compareTo(other.archivePath);
    }

    private static List<ManifestEntryProvenance> canonicalProvenances(
            List<ManifestEntryProvenance> values) {
        List<ManifestEntryProvenance> copy = List.copyOf(
                Objects.requireNonNull(values, "provenances"));
        if (copy.isEmpty()
                || copy.size() > MAX_PROVENANCES
                || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest entry provenance is invalid");
        }
        List<ManifestEntryProvenance> ordered = copy.stream().sorted().toList();
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).compareTo(ordered.get(index)) == 0) {
                throw new IllegalArgumentException(
                        "Manifest declaration provenance identities must be unique");
            }
        }
        return ordered;
    }

    private static List<SanitizationFinding> canonicalFindings(
            String archivePath, List<SanitizationFinding> values) {
        List<SanitizationFinding> copy = List.copyOf(
                Objects.requireNonNull(values, "sanitizationFindings"));
        if (copy.size() > MAX_FINDINGS || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest sanitization findings are invalid");
        }
        String artifactName = archivePath.substring("content/".length());
        if (copy.stream().anyMatch(finding -> !artifactName.equals(finding.artifactName()))) {
            throw new IllegalArgumentException(
                    "Manifest sanitization findings must belong to the entry artifact");
        }
        return copy.stream()
                .sorted(Comparator.comparingLong(SanitizationFinding::line)
                        .thenComparingInt(SanitizationFinding::startColumn)
                        .thenComparing(finding -> finding.stageId().value())
                        .thenComparingInt(SanitizationFinding::endColumn)
                        .thenComparing(finding -> finding.classification().name())
                        .thenComparing(finding -> finding.action().name()))
                .toList();
    }

    private static void validatePrivacy(
            PrivacyClassification effective,
            List<ManifestEntryProvenance> provenances) {
        if (provenances.stream()
                .map(ManifestEntryProvenance::declaredPrivacy)
                .anyMatch(declared -> !effective.isAtLeast(declared))) {
            throw new IllegalArgumentException(
                    "Manifest entry privacy cannot weaken declaration provenance");
        }
    }

    private static void validateCollection(
            ManifestCollectionStatus status,
            List<ManifestEntryProvenance> provenances) {
        ManifestDeclarationKind requiredKind = status == ManifestCollectionStatus.SOURCE_COLLECTED
                ? ManifestDeclarationKind.SOURCE
                : ManifestDeclarationKind.GENERATOR;
        if (provenances.stream()
                .anyMatch(provenance -> provenance.declarationKind() != requiredKind)) {
            throw new IllegalArgumentException(
                    "Manifest collection status must match every declaration provenance");
        }
        if (requiredKind == ManifestDeclarationKind.GENERATOR && provenances.size() != 1) {
            throw new IllegalArgumentException(
                    "A generated manifest entry requires exactly one generator provenance");
        }
    }

    private static void validateSanitization(
            DiagnosticContentType contentType,
            ManifestSanitizationStatus status,
            List<SanitizationFinding> findings) {
        boolean unresolved = findings.stream()
                .anyMatch(finding -> finding.action() == SanitizationAction.UNRESOLVED_WARNING);
        if (status == ManifestSanitizationStatus.NOT_REQUIRED && !findings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsanitized manifest entries cannot contain sanitization findings");
        }
        if (status == ManifestSanitizationStatus.SANITIZED && unresolved) {
            throw new IllegalArgumentException(
                    "Sanitized manifest entries cannot contain unresolved warnings");
        }
        if (status == ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS
                && !unresolved
                && contentType != DiagnosticContentType.BINARY) {
            throw new IllegalArgumentException(
                    "Warning-reviewed entries require an unresolved warning");
        }
        if (contentType == DiagnosticContentType.BINARY
                && status != ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS) {
            throw new IllegalArgumentException(
                    "Binary manifest entries require explicit warning review");
        }
    }
}
