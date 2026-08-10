package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint;
import java.util.Objects;

/** Terminal result of one exact reviewed file and generated diagnostic collection plan. */
public record CategoryCollectionResult(
        Status status,
        FileCollectionResult files,
        CategoryGeneratedDiagnosticResult generated,
        CategoryCollectionFingerprint fingerprint) {
    public enum Status {
        COMPLETE,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public CategoryCollectionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!files.providerId().equals(generated.providerId())
                || !files.categoryId().equals(generated.categoryId())) {
            throw new IllegalArgumentException("Category collection result identities do not match");
        }
        if (generated.outcomes().stream()
                .flatMap(outcome -> outcome.result().stream())
                .anyMatch(result -> !files.providerVersion().equals(result.providerVersion()))) {
            throw new IllegalArgumentException(
                    "Generated result provider version does not match file collection identity");
        }
    }

    /** Returns bytes retained by successful file and generator outcomes. */
    public long retainedBytes() {
        long fileBytes = files.outcomes().stream()
                .flatMap(outcome -> outcome.collectedFile().stream())
                .mapToLong(CollectedSourceFile::byteCount)
                .reduce(0L, Math::addExact);
        return Math.addExact(fileBytes, generated.retainedBytes());
    }
}
