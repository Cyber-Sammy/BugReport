package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint;
import java.util.Objects;

/** Coordinator-issued terminal authority for one exact reviewed category collection plan. */
public final class CategoryCollectionResult {
    public enum Status {
        COMPLETE,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    private final Status status;
    private final FileCollectionResult files;
    private final CategoryGeneratedDiagnosticResult generated;
    private final CategoryCollectionFingerprint fingerprint;

    CategoryCollectionResult(
            Status status,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated,
            CategoryCollectionFingerprint fingerprint) {
        this.status = Objects.requireNonNull(status, "status");
        this.files = Objects.requireNonNull(files, "files");
        this.generated = Objects.requireNonNull(generated, "generated");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        validateIdentity();
        validateStatus();
    }

    public Status status() {
        return status;
    }

    public FileCollectionResult files() {
        return files;
    }

    public CategoryGeneratedDiagnosticResult generated() {
        return generated;
    }

    public CategoryCollectionFingerprint fingerprint() {
        return fingerprint;
    }

    /** Returns bytes retained by successful file and generator outcomes. */
    public long retainedBytes() {
        long fileBytes = files.outcomes().stream()
                .flatMap(outcome -> outcome.collectedFile().stream())
                .mapToLong(CollectedSourceFile::byteCount)
                .reduce(0L, Math::addExact);
        return Math.addExact(fileBytes, generated.retainedBytes());
    }

    private void validateIdentity() {
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

    private void validateStatus() {
        long fileSuccesses = files.outcomes().stream()
                .filter(outcome -> outcome.status() == FileCollectionResult.SourceStatus.COLLECTED)
                .count();
        long generatedSuccesses = generated.outcomes().stream()
                .filter(outcome -> outcome.status() == GeneratedDiagnosticOutcomeStatus.COLLECTED)
                .count();
        long cancellations = files.outcomes().stream()
                        .filter(outcome -> outcome.status()
                                == FileCollectionResult.SourceStatus.CANCELLED)
                        .count()
                + generated.outcomes().stream()
                        .filter(outcome -> outcome.status()
                                == GeneratedDiagnosticOutcomeStatus.CANCELLED)
                        .count();
        long successes = fileSuccesses + generatedSuccesses;
        long failures = files.outcomes().size()
                        - fileSuccesses
                        - files.outcomes().stream()
                                .filter(outcome -> outcome.status()
                                        == FileCollectionResult.SourceStatus.CANCELLED)
                                .count()
                + generated.outcomes().size()
                        - generatedSuccesses
                        - generated.outcomes().stream()
                                .filter(outcome -> outcome.status()
                                        == GeneratedDiagnosticOutcomeStatus.CANCELLED)
                                .count();
        boolean valid = switch (status) {
            case COMPLETE -> failures == 0
                    && cancellations == 0
                    && files.status() != FileCollectionResult.Status.CANCELLED;
            case PARTIAL -> successes > 0 && failures > 0 && cancellations == 0;
            case FAILED -> successes == 0 && failures > 0 && cancellations == 0;
            case CANCELLED -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Category collection status does not match its child outcomes");
        }
    }
}
