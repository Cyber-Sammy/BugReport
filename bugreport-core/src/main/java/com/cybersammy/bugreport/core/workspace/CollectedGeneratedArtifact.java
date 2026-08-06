package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable path-free metadata for one provider-generated workspace artifact. */
public record CollectedGeneratedArtifact(
        String artifactName,
        GeneratedArtifactId artifactId,
        long byteCount,
        Sha256Checksum checksum,
        ProviderId providerId,
        ProviderVersion providerVersion,
        CategoryId categoryId,
        DiagnosticGeneratorId generatorId,
        DiagnosticContentType contentType,
        PrivacyClassification privacy,
        ReportQualityRole qualityRole,
        InclusionDefault inclusionDefault) {
    private static final Pattern ARTIFACT_NAME =
            Pattern.compile("generated-[0-9a-f]{64}\\.(?:txt|json)");

    public CollectedGeneratedArtifact {
        Objects.requireNonNull(artifactName, "artifactName");
        if (!ARTIFACT_NAME.matcher(artifactName).matches()) {
            throw new IllegalArgumentException("Generated artifact name is not canonical");
        }
        Objects.requireNonNull(artifactId, "artifactId");
        if (byteCount < 0) {
            throw new IllegalArgumentException("Generated artifact byte count must be non-negative");
        }
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(generatorId, "generatorId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(qualityRole, "qualityRole");
        Objects.requireNonNull(inclusionDefault, "inclusionDefault");
        if (contentType == DiagnosticContentType.BINARY) {
            throw new IllegalArgumentException("Generated artifact must be TEXT or JSON");
        }
        String requiredSuffix = contentType == DiagnosticContentType.TEXT ? ".txt" : ".json";
        if (!artifactName.endsWith(requiredSuffix)) {
            throw new IllegalArgumentException(
                    "Generated artifact suffix does not match its content type");
        }
    }
}
