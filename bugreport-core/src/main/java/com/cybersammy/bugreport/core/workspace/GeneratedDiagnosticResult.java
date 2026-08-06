package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Successful deterministic result of one bounded generated diagnostic invocation. */
public record GeneratedDiagnosticResult(
        ProviderId providerId,
        ProviderVersion providerVersion,
        CategoryId categoryId,
        DiagnosticGeneratorId generatorId,
        List<CollectedGeneratedArtifact> artifacts,
        long byteCount) {
    public GeneratedDiagnosticResult {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(generatorId, "generatorId");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (artifacts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Generated artifacts must not contain null");
        }
        for (int index = 1; index < artifacts.size(); index++) {
            if (Comparator.comparing(CollectedGeneratedArtifact::artifactId)
                            .compare(artifacts.get(index - 1), artifacts.get(index))
                    >= 0) {
                throw new IllegalArgumentException(
                        "Generated artifacts must be strictly ordered by artifact ID");
            }
        }
        long actualBytes = artifacts.stream()
                .mapToLong(CollectedGeneratedArtifact::byteCount)
                .reduce(0L, Math::addExact);
        if (byteCount < 0 || byteCount != actualBytes) {
            throw new IllegalArgumentException("Generated artifact byte total is inconsistent");
        }
        artifacts.forEach(artifact -> {
            if (!providerId.equals(artifact.providerId())
                    || !providerVersion.equals(artifact.providerVersion())
                    || !categoryId.equals(artifact.categoryId())
                    || !generatorId.equals(artifact.generatorId())) {
                throw new IllegalArgumentException("Generated artifact provenance does not match");
            }
        });
    }
}
