package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic typed result for conflict-free files in one category source plan. */
public final class FileCollectionResult {
    public enum Status {
        COMPLETE,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public enum SourceStatus {
        COLLECTED,
        FAILED,
        CANCELLED
    }

    /** Privacy-safe terminal result for one unique planned file. */
    public static final class SourceOutcome {
        private final int ordinal;
        private final List<SourceProvenance> provenances;
        private final SourceStatus status;
        private final CollectedSourceFile collectedFile;
        private final SourceCopyCode failureCode;

        private SourceOutcome(
                int ordinal,
                List<SourceProvenance> provenances,
                SourceStatus status,
                CollectedSourceFile collectedFile,
                SourceCopyCode failureCode) {
            if (ordinal <= 0) {
                throw new IllegalArgumentException("Source outcome ordinal must be positive");
            }
            this.ordinal = ordinal;
            this.provenances = List.copyOf(Objects.requireNonNull(provenances, "provenances"));
            if (this.provenances.isEmpty() || this.provenances.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Source outcome requires provenance");
            }
            this.status = Objects.requireNonNull(status, "status");
            this.collectedFile = collectedFile;
            this.failureCode = failureCode;
            if ((status == SourceStatus.COLLECTED) != (collectedFile != null)
                    || (status == SourceStatus.COLLECTED) != (failureCode == null)
                    || (status == SourceStatus.CANCELLED)
                            != (failureCode == SourceCopyCode.CANCELLED)) {
                throw new IllegalArgumentException("Source outcome payload does not match status");
            }
        }

        static SourceOutcome collected(int ordinal, CollectedSourceFile file) {
            return new SourceOutcome(
                    ordinal,
                    file.provenances(),
                    SourceStatus.COLLECTED,
                    file,
                    null);
        }

        static SourceOutcome failed(
                int ordinal,
                List<SourceProvenance> provenances,
                SourceCopyCode code) {
            SourceCopyCode failure = Objects.requireNonNull(code, "code");
            if (failure == SourceCopyCode.CANCELLED) {
                throw new IllegalArgumentException("Cancellation is not a collection failure");
            }
            return new SourceOutcome(
                    ordinal, provenances, SourceStatus.FAILED, null, failure);
        }

        static SourceOutcome cancelled(
                int ordinal, List<SourceProvenance> provenances) {
            return new SourceOutcome(
                    ordinal,
                    provenances,
                    SourceStatus.CANCELLED,
                    null,
                    SourceCopyCode.CANCELLED);
        }

        public int ordinal() {
            return ordinal;
        }

        public List<SourceProvenance> provenances() {
            return provenances;
        }

        public SourceStatus status() {
            return status;
        }

        public Optional<CollectedSourceFile> collectedFile() {
            return Optional.ofNullable(collectedFile);
        }

        public Optional<SourceCopyCode> failureCode() {
            return Optional.ofNullable(failureCode);
        }
    }

    private final ProviderId providerId;
    private final ProviderVersion providerVersion;
    private final CategoryId categoryId;
    private final Status status;
    private final List<SourceOutcome> outcomes;
    private final CollectionProgressSnapshot progress;

    FileCollectionResult(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            Status status,
            List<SourceOutcome> outcomes,
            CollectionProgressSnapshot progress) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.status = Objects.requireNonNull(status, "status");
        this.outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        this.progress = Objects.requireNonNull(progress, "progress");
        validate();
    }

    public ProviderId providerId() {
        return providerId;
    }

    public ProviderVersion providerVersion() {
        return providerVersion;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public Status status() {
        return status;
    }

    public List<SourceOutcome> outcomes() {
        return outcomes;
    }

    public CollectionProgressSnapshot progress() {
        return progress;
    }

    private void validate() {
        int collected = 0;
        int failed = 0;
        int cancelled = 0;
        for (int index = 0; index < outcomes.size(); index++) {
            SourceOutcome outcome = outcomes.get(index);
            if (outcome.ordinal() != index + 1) {
                throw new IllegalArgumentException("Source outcomes must use contiguous ordinals");
            }
            switch (outcome.status()) {
                case COLLECTED -> collected++;
                case FAILED -> failed++;
                case CANCELLED -> cancelled++;
            }
        }
        if (outcomes.size() != progress.totalFiles()
                || progress.completedFiles() != outcomes.size()
                || collected != progress.successfulFiles()
                || failed != progress.failedFiles()
                || cancelled != progress.cancelledFiles()
                || progress.state() != progressState(status)) {
            throw new IllegalArgumentException("Collection result and progress must agree");
        }
    }

    private static CollectionProgressSnapshot.State progressState(Status status) {
        return switch (status) {
            case COMPLETE -> CollectionProgressSnapshot.State.COMPLETE;
            case PARTIAL -> CollectionProgressSnapshot.State.PARTIAL;
            case FAILED -> CollectionProgressSnapshot.State.FAILED;
            case CANCELLED -> CollectionProgressSnapshot.State.CANCELLED;
        };
    }
}
