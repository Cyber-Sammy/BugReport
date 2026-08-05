package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable declaration of one bounded generated diagnostic callback. */
public final class DiagnosticGeneratorSpecification {
    private final DiagnosticGeneratorId id;
    private final DiagnosticGeneratorKind kind;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final PrivacyClassification privacy;
    private final DiagnosticContentType contentType;
    private final ReportQualityRole qualityRole;
    private final InclusionDefault inclusionDefault;
    private final Set<SupportedSide> supportedSides;
    private final GeneratorExecutionContext executionContext;
    private final CollectionConstraints constraints;
    private final ExtensionMetadata extensions;
    private final GeneratedDiagnosticProducer producer;

    private DiagnosticGeneratorSpecification(Builder builder) {
        id = builder.id;
        kind = builder.kind;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        privacy = builder.privacy;
        contentType = builder.contentType;
        qualityRole = builder.qualityRole;
        inclusionDefault = builder.inclusionDefault;
        supportedSides = SpecificationChecks.copyNonEmptyEnumSet(
                builder.supportedSides, SupportedSide.class, "supportedSides");
        executionContext = builder.executionContext;
        constraints = builder.constraints;
        extensions = builder.extensions;
        producer = builder.producer;
    }

    /**
     * Creates a generated diagnostic builder.
     *
     * @param id generator ID within its provider
     * @param producer bounded callback
     * @return new builder
     */
    public static Builder builder(
            DiagnosticGeneratorId id, GeneratedDiagnosticProducer producer) {
        return new Builder(id, DiagnosticGeneratorKind.GENERAL, producer);
    }

    /**
     * Creates a bounded high-sensitivity world-state export builder.
     *
     * <p>The resulting provider specification must require {@link
     * StandardCapabilities#boundedWorldStateExport()}. The callback receives only the normal
     * bounded sink and no filesystem authority.
     *
     * @param id generator ID within its provider
     * @param producer bounded callback
     * @return new world-state export builder
     */
    public static Builder worldStateExport(
            DiagnosticGeneratorId id, GeneratedDiagnosticProducer producer) {
        return new Builder(id, DiagnosticGeneratorKind.WORLD_STATE_EXPORT, producer);
    }

    /**
     * Returns generator ID.
     *
     * @return generator ID
     */
    public DiagnosticGeneratorId id() {
        return id;
    }

    /**
     * Returns the product-recognized generator purpose.
     *
     * @return generator purpose
     */
    public DiagnosticGeneratorKind kind() {
        return kind;
    }

    /**
     * Returns localized label.
     *
     * @return localized label
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized description.
     *
     * @return optional localized description
     */
    public Optional<LocalizationKey> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Returns provider-declared privacy floor.
     *
     * @return provider-declared privacy floor
     */
    public PrivacyClassification privacy() {
        return privacy;
    }

    /**
     * Returns declared output representation.
     *
     * @return declared output representation
     */
    public DiagnosticContentType contentType() {
        return contentType;
    }

    /**
     * Returns report-quality role.
     *
     * @return report-quality role
     */
    public ReportQualityRole qualityRole() {
        return qualityRole;
    }

    /**
     * Returns provider-requested initial inclusion state.
     *
     * @return provider-requested initial inclusion state
     */
    public InclusionDefault inclusionDefault() {
        return inclusionDefault;
    }

    /**
     * Returns immutable supported physical sides.
     *
     * @return immutable supported physical sides
     */
    public Set<SupportedSide> supportedSides() {
        return supportedSides;
    }

    /**
     * Returns requested execution context.
     *
     * @return requested execution context
     */
    public GeneratorExecutionContext executionContext() {
        return executionContext;
    }

    /**
     * Returns provider-requested output and timeout constraints.
     *
     * @return provider-requested output and timeout constraints
     */
    public CollectionConstraints constraints() {
        return constraints;
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    /**
     * Returns generated diagnostic callback.
     *
     * @return generated diagnostic callback
     */
    public GeneratedDiagnosticProducer producer() {
        return producer;
    }

    /** Builder for a generated diagnostic specification. */
    public static final class Builder {
        private final DiagnosticGeneratorId id;
        private final DiagnosticGeneratorKind kind;
        private final GeneratedDiagnosticProducer producer;
        private final EnumSet<SupportedSide> supportedSides =
                EnumSet.noneOf(SupportedSide.class);
        private LocalizationKey labelKey;
        private LocalizationKey descriptionKey;
        private PrivacyClassification privacy;
        private DiagnosticContentType contentType;
        private ReportQualityRole qualityRole = ReportQualityRole.OPTIONAL;
        private InclusionDefault inclusionDefault = InclusionDefault.EXCLUDED;
        private GeneratorExecutionContext executionContext;
        private CollectionConstraints constraints = CollectionConstraints.defaults();
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                DiagnosticGeneratorId id,
                DiagnosticGeneratorKind kind,
                GeneratedDiagnosticProducer producer) {
            this.id = Objects.requireNonNull(id, "id");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.producer = Objects.requireNonNull(producer, "producer");
        }

        /**
         * Accepts localized label and returns this builder.
         *
         * @param value localized label
         * @return this builder
         */
        public Builder labelKey(LocalizationKey value) {
            labelKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts localized description and returns this builder.
         *
         * @param value localized description
         * @return this builder
         */
        public Builder descriptionKey(LocalizationKey value) {
            descriptionKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts declared privacy floor and returns this builder.
         *
         * @param value declared privacy floor
         * @return this builder
         */
        public Builder privacy(PrivacyClassification value) {
            privacy = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts declared output representation and returns this builder.
         *
         * @param value declared output representation
         * @return this builder
         */
        public Builder contentType(DiagnosticContentType value) {
            contentType = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts report-quality role and returns this builder.
         *
         * @param value report-quality role
         * @return this builder
         */
        public Builder qualityRole(ReportQualityRole value) {
            qualityRole = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts requested initial inclusion state and returns this builder.
         *
         * @param value requested initial inclusion state
         * @return this builder
         */
        public Builder inclusionDefault(InclusionDefault value) {
            inclusionDefault = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts supported physical side and returns this builder.
         *
         * @param value supported physical side
         * @return this builder
         */
        public Builder supportSide(SupportedSide value) {
            supportedSides.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Accepts requested execution context and returns this builder.
         *
         * @param value requested execution context
         * @return this builder
         */
        public Builder executionContext(GeneratorExecutionContext value) {
            executionContext = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts provider-requested output and timeout limits and returns this builder.
         *
         * @param value provider-requested output and timeout limits
         * @return this builder
         */
        public Builder constraints(CollectionConstraints value) {
            constraints = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts bounded extension metadata and returns this builder.
         *
         * @param value bounded extension metadata
         * @return this builder
         */
        public Builder extensions(ExtensionMetadata value) {
            extensions = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Returns validated immutable generator specification.
         *
         * @return validated immutable generator specification
         */
        public DiagnosticGeneratorSpecification build() {
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(privacy, "privacy");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(executionContext, "executionContext");
            if (supportedSides.isEmpty()) {
                throw new IllegalArgumentException("supportedSides must not be empty");
            }
            if (privacy == PrivacyClassification.PROHIBITED) {
                throw new IllegalArgumentException("A prohibited diagnostic cannot be generated");
            }
            if (contentType == DiagnosticContentType.BINARY) {
                throw new IllegalArgumentException(
                        "Generated diagnostics initially support only TEXT or JSON");
            }
            if (inclusionDefault == InclusionDefault.INCLUDED
                    && privacy != PrivacyClassification.LOW) {
                throw new IllegalArgumentException(
                        "Only LOW generated data may request default inclusion");
            }
            if (executionContext == GeneratorExecutionContext.GAME_THREAD_SNAPSHOT
                    && constraints.callbackTimeout().isEmpty()) {
                throw new IllegalArgumentException(
                        "Game-thread snapshot generators require an explicit timeout");
            }
            validateWorldStateExport();
            return new DiagnosticGeneratorSpecification(this);
        }

        private void validateWorldStateExport() {
            if (kind != DiagnosticGeneratorKind.WORLD_STATE_EXPORT) {
                return;
            }
            if (!privacy.isAtLeast(PrivacyClassification.SENSITIVE)) {
                throw new IllegalArgumentException(
                        "World-state exports must be classified SENSITIVE");
            }
            if (inclusionDefault != InclusionDefault.EXCLUDED) {
                throw new IllegalArgumentException(
                        "World-state exports must be excluded by default");
            }
            if (constraints.maxTraversalDepth().isPresent()
                    || constraints.maxMatchedFiles().isPresent()) {
                throw new IllegalArgumentException(
                        "World-state exports cannot request filesystem traversal constraints");
            }
            if (constraints.maxGeneratedArtifacts().isEmpty()
                    || constraints.maxBytesPerFile().isEmpty()
                    || constraints.maxTotalBytes().isEmpty()
                    || constraints.callbackTimeout().isEmpty()) {
                throw new IllegalArgumentException(
                        "World-state exports require explicit artifact, byte, and timeout limits");
            }
            if (constraints.maxGeneratedArtifacts().getAsInt()
                            > StandardCapabilities.WORLD_STATE_MAX_ARTIFACTS
                    || constraints.maxBytesPerFile().getAsLong()
                            > StandardCapabilities.WORLD_STATE_MAX_BYTES_PER_ARTIFACT
                    || constraints.maxTotalBytes().getAsLong()
                            > StandardCapabilities.WORLD_STATE_MAX_TOTAL_BYTES
                    || constraints.callbackTimeout().orElseThrow()
                            .compareTo(StandardCapabilities.WORLD_STATE_MAX_CALLBACK_TIMEOUT)
                            > 0) {
                throw new IllegalArgumentException(
                        "World-state export limits exceed the capability 1.0 ceilings");
            }
        }
    }
}
