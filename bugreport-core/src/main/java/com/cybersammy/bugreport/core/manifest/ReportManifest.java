package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable canonical in-memory model for Bug Report manifest schema 1. */
public final class ReportManifest {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_CAPABILITIES = 128;
    public static final int MAX_ERRORS = 256;
    public static final int MAX_SANITIZATION_FINDINGS = 10_000;
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;

    private final ReportSessionId reportId;
    private final Instant createdAt;
    private final ManifestProducer producer;
    private final Optional<ManifestTarget> target;
    private final ManifestEnvironment environment;
    private final boolean incomplete;
    private final FormSubmission reviewedFields;
    private final List<ManifestCapability> requiredCapabilities;
    private final List<ManifestCapability> optionalCapabilities;
    private final List<ManifestEntry> entries;
    private final List<ManifestError> errors;
    private final ExtensionMetadata extensions;

    private ReportManifest(Builder builder) {
        this.reportId = Objects.requireNonNull(builder.reportId, "reportId");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
        this.producer = Objects.requireNonNull(builder.producer, "producer");
        this.target = Objects.requireNonNull(builder.target, "target");
        this.environment = Objects.requireNonNull(builder.environment, "environment");
        this.incomplete = builder.incomplete;
        this.reviewedFields = Objects.requireNonNull(builder.reviewedFields, "reviewedFields");
        this.requiredCapabilities = canonicalCapabilities(
                builder.requiredCapabilities, "required capabilities");
        this.optionalCapabilities = canonicalCapabilities(
                builder.optionalCapabilities, "optional capabilities");
        requireDisjointCapabilities(requiredCapabilities, optionalCapabilities);
        this.entries = canonicalEntries(builder.entries);
        this.errors = canonicalErrors(builder.errors);
        this.extensions = Objects.requireNonNull(builder.extensions, "extensions");
        validateTarget(target, entries, errors);
        if (!incomplete && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A manifest containing errors must be marked incomplete");
        }
    }

    public static Builder builder(
            ReportSessionId reportId,
            Instant createdAt,
            ManifestProducer producer,
            ManifestEnvironment environment) {
        return new Builder(reportId, createdAt, producer, environment);
    }

    public ReportSessionId reportId() {
        return reportId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ManifestProducer producer() {
        return producer;
    }

    public Optional<ManifestTarget> target() {
        return target;
    }

    public ManifestEnvironment environment() {
        return environment;
    }

    public boolean incomplete() {
        return incomplete;
    }

    /** Values already accepted by the trusted review boundary. */
    public FormSubmission reviewedFields() {
        return reviewedFields;
    }

    public List<ManifestCapability> requiredCapabilities() {
        return requiredCapabilities;
    }

    public List<ManifestCapability> optionalCapabilities() {
        return optionalCapabilities;
    }

    public List<ManifestEntry> entries() {
        return entries;
    }

    public List<ManifestError> errors() {
        return errors;
    }

    public ExtensionMetadata extensions() {
        return extensions;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReportManifest manifest)) {
            return false;
        }
        return incomplete == manifest.incomplete
                && reportId.equals(manifest.reportId)
                && createdAt.equals(manifest.createdAt)
                && producer.equals(manifest.producer)
                && target.equals(manifest.target)
                && environment.equals(manifest.environment)
                && reviewedFields.equals(manifest.reviewedFields)
                && requiredCapabilities.equals(manifest.requiredCapabilities)
                && optionalCapabilities.equals(manifest.optionalCapabilities)
                && entries.equals(manifest.entries)
                && errors.equals(manifest.errors)
                && extensions.equals(manifest.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                reportId,
                createdAt,
                producer,
                target,
                environment,
                incomplete,
                reviewedFields,
                requiredCapabilities,
                optionalCapabilities,
                entries,
                errors,
                extensions);
    }

    private static List<ManifestCapability> canonicalCapabilities(
            List<ManifestCapability> values, String description) {
        List<ManifestCapability> copy = List.copyOf(Objects.requireNonNull(values, description));
        if (copy.size() > MAX_CAPABILITIES || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest " + description + " are invalid");
        }
        List<ManifestCapability> ordered = copy.stream().sorted().toList();
        Set<Object> identifiers = new HashSet<>();
        if (ordered.stream().anyMatch(capability -> !identifiers.add(capability.id()))) {
            throw new IllegalArgumentException("Manifest capability IDs must be unique");
        }
        return ordered;
    }

    private static void requireDisjointCapabilities(
            List<ManifestCapability> required, List<ManifestCapability> optional) {
        Set<Object> identifiers = new HashSet<>();
        required.forEach(capability -> identifiers.add(capability.id()));
        if (optional.stream().anyMatch(capability -> identifiers.contains(capability.id()))) {
            throw new IllegalArgumentException(
                    "A capability cannot be both required and optional");
        }
    }

    private static List<ManifestEntry> canonicalEntries(List<ManifestEntry> values) {
        List<ManifestEntry> copy = List.copyOf(Objects.requireNonNull(values, "entries"));
        if (copy.size() > MAX_ENTRIES || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest entries are invalid");
        }
        List<ManifestEntry> ordered = copy.stream().sorted().toList();
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).archivePath().equals(ordered.get(index).archivePath())) {
                throw new IllegalArgumentException("Manifest archive paths must be unique");
            }
        }
        long total = 0;
        int findingCount = 0;
        for (ManifestEntry entry : ordered) {
            total = Math.addExact(total, entry.uncompressedBytes());
            findingCount = Math.addExact(findingCount, entry.sanitizationFindings().size());
            if (total > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException(
                        "Manifest entry bytes exceed the product limit");
            }
            if (findingCount > MAX_SANITIZATION_FINDINGS) {
                throw new IllegalArgumentException(
                        "Manifest sanitization findings exceed the product limit");
            }
        }
        return ordered;
    }

    private static List<ManifestError> canonicalErrors(List<ManifestError> values) {
        List<ManifestError> copy = List.copyOf(Objects.requireNonNull(values, "errors"));
        if (copy.size() > MAX_ERRORS || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest errors are invalid");
        }
        List<ManifestError> ordered = copy.stream().sorted().toList();
        if (new HashSet<>(ordered).size() != ordered.size()) {
            throw new IllegalArgumentException("Manifest errors must be unique");
        }
        return ordered;
    }

    private static void validateTarget(
            Optional<ManifestTarget> target,
            List<ManifestEntry> entries,
            List<ManifestError> errors) {
        if (target.isEmpty()) {
            return;
        }
        ManifestTarget selected = target.orElseThrow();
        boolean mismatch = entries.stream()
                .flatMap(entry -> entry.provenances().stream())
                .anyMatch(provenance -> !selected.providerId().equals(provenance.providerId())
                        || !selected.providerVersion().equals(provenance.providerVersion())
                        || !selected.categoryId().equals(provenance.categoryId()));
        if (mismatch) {
            throw new IllegalArgumentException(
                    "Manifest entry provenance must match the selected target");
        }
        if (errors.stream()
                .map(ManifestError::providerId)
                .flatMap(Optional::stream)
                .anyMatch(providerId -> !selected.providerId().equals(providerId))) {
            throw new IllegalArgumentException(
                    "Manifest error provenance must match the selected target");
        }
    }

    /** Mutable assembly boundary whose build operation revalidates all aggregate invariants. */
    public static final class Builder {
        private final ReportSessionId reportId;
        private final Instant createdAt;
        private final ManifestProducer producer;
        private final ManifestEnvironment environment;
        private Optional<ManifestTarget> target = Optional.empty();
        private boolean incomplete;
        private FormSubmission reviewedFields = FormSubmission.empty();
        private List<ManifestCapability> requiredCapabilities = List.of();
        private List<ManifestCapability> optionalCapabilities = List.of();
        private List<ManifestEntry> entries = List.of();
        private List<ManifestError> errors = List.of();
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                ReportSessionId reportId,
                Instant createdAt,
                ManifestProducer producer,
                ManifestEnvironment environment) {
            this.reportId = Objects.requireNonNull(reportId, "reportId");
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
            this.producer = Objects.requireNonNull(producer, "producer");
            this.environment = Objects.requireNonNull(environment, "environment");
        }

        public Builder target(ManifestTarget value) {
            this.target = Optional.of(Objects.requireNonNull(value, "target"));
            return this;
        }

        public Builder incomplete(boolean value) {
            this.incomplete = value;
            return this;
        }

        public Builder reviewedFields(FormSubmission value) {
            this.reviewedFields = Objects.requireNonNull(value, "reviewedFields");
            return this;
        }

        public Builder capabilities(
                List<ManifestCapability> required, List<ManifestCapability> optional) {
            this.requiredCapabilities = List.copyOf(required);
            this.optionalCapabilities = List.copyOf(optional);
            return this;
        }

        public Builder entries(List<ManifestEntry> values) {
            this.entries = List.copyOf(values);
            return this;
        }

        public Builder errors(List<ManifestError> values) {
            this.errors = List.copyOf(values);
            return this;
        }

        public Builder extensions(ExtensionMetadata value) {
            this.extensions = Objects.requireNonNull(value, "extensions");
            return this;
        }

        public ReportManifest build() {
            return new ReportManifest(this);
        }
    }
}
