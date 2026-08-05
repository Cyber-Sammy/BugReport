package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable path-safe metadata for one copied workspace source artifact. */
public final class CollectedSourceFile {
    private static final Pattern ARTIFACT_NAME = Pattern.compile("source-[0-9a-f]{64}\\.data");

    private final String artifactName;
    private final long byteCount;
    private final Sha256Checksum checksum;
    private final List<SourceProvenance> provenances;
    private final DiagnosticContentType contentType;
    private final PrivacyClassification privacy;
    private final ReportQualityRole qualityRole;
    private final InclusionDefault inclusionDefault;

    CollectedSourceFile(
            String artifactName,
            long byteCount,
            Sha256Checksum checksum,
            List<SourceProvenance> provenances,
            DiagnosticContentType contentType,
            PrivacyClassification privacy,
            ReportQualityRole qualityRole,
            InclusionDefault inclusionDefault) {
        this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
        if (!ARTIFACT_NAME.matcher(artifactName).matches()) {
            throw new IllegalArgumentException("Collected source artifact name is not canonical");
        }
        if (byteCount < 0) {
            throw new IllegalArgumentException("Collected source byte count must be non-negative");
        }
        this.byteCount = byteCount;
        this.checksum = Objects.requireNonNull(checksum, "checksum");
        this.provenances = List.copyOf(Objects.requireNonNull(provenances, "provenances"));
        if (this.provenances.isEmpty() || this.provenances.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Collected source requires provenance");
        }
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.privacy = Objects.requireNonNull(privacy, "privacy");
        this.qualityRole = Objects.requireNonNull(qualityRole, "qualityRole");
        this.inclusionDefault = Objects.requireNonNull(inclusionDefault, "inclusionDefault");
    }

    public String artifactName() {
        return artifactName;
    }

    public long byteCount() {
        return byteCount;
    }

    public Sha256Checksum checksum() {
        return checksum;
    }

    public List<SourceProvenance> provenances() {
        return provenances;
    }

    public DiagnosticContentType contentType() {
        return contentType;
    }

    public PrivacyClassification privacy() {
        return privacy;
    }

    public ReportQualityRole qualityRole() {
        return qualityRole;
    }

    public InclusionDefault inclusionDefault() {
        return inclusionDefault;
    }
}
