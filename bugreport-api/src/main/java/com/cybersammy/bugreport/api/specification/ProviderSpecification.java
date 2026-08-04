package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.IdentifierKind;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable aggregate returned by an M1 Bug Report provider. */
public final class ProviderSpecification {
    /** Maximum report categories in one provider. */
    public static final int MAX_CATEGORIES = 64;
    /** Maximum diagnostic sources in one provider. */
    public static final int MAX_SOURCES = 128;
    /** Maximum generated diagnostic declarations in one provider. */
    public static final int MAX_GENERATORS = 64;
    /** Maximum support destinations in one provider. */
    public static final int MAX_DESTINATIONS = 32;
    /** Maximum requirements or offers in each capability collection. */
    public static final int MAX_CAPABILITIES = 64;
    /** Maximum aggregate field declarations across all categories. */
    public static final int MAX_TOTAL_FIELDS = 512;

    private final ProviderId id;
    private final ProviderVersion version;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final LocalizationKey privacyNoticeKey;
    private final HttpsUrl documentationUrl;
    private final Set<SupportedSide> supportedSides;
    private final Map<CategoryId, CategorySpecification> categories;
    private final Map<DiagnosticSourceId, DiagnosticSourceSpecification> sources;
    private final Map<DiagnosticGeneratorId, DiagnosticGeneratorSpecification> generators;
    private final Map<DestinationId, SupportDestinationSpecification> destinations;
    private final Map<CapabilityId, CapabilityRequirement> capabilityRequirements;
    private final Map<CapabilityId, CapabilityOffer> capabilityOffers;
    private final ExtensionMetadata extensions;

    private ProviderSpecification(Builder builder) {
        id = builder.id;
        version = builder.version;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        privacyNoticeKey = builder.privacyNoticeKey;
        documentationUrl = builder.documentationUrl;
        supportedSides = SpecificationChecks.copyNonEmptyEnumSet(
                builder.supportedSides, SupportedSide.class, "supportedSides");
        categories = immutableMap(builder.categories);
        sources = immutableMap(builder.sources);
        generators = immutableMap(builder.generators);
        destinations = immutableMap(builder.destinations);
        capabilityRequirements = immutableMap(builder.capabilityRequirements);
        capabilityOffers = immutableMap(builder.capabilityOffers);
        extensions = builder.extensions;
    }

    /**
     * Creates a provider specification builder.
     *
     * @param id canonical provider ID
     * @param version provider specification version
     * @param labelKey localized provider label
     * @return new builder
     */
    public static Builder builder(
            ProviderId id, ProviderVersion version, LocalizationKey labelKey) {
        return new Builder(id, version, labelKey);
    }

    /**
     * Returns provider ID.
     *
     * @return provider ID
     */
    public ProviderId id() {
        return id;
    }

    /**
     * Returns exact provider-defined version text.
     *
     * @return exact provider-defined version text
     */
    public ProviderVersion version() {
        return version;
    }

    /**
     * Returns localized provider label.
     *
     * @return localized provider label
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized provider description.
     *
     * @return optional localized provider description
     */
    public Optional<LocalizationKey> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Returns optional localized privacy notice.
     *
     * @return optional localized privacy notice
     */
    public Optional<LocalizationKey> privacyNoticeKey() {
        return Optional.ofNullable(privacyNoticeKey);
    }

    /**
     * Returns optional HTTPS documentation address.
     *
     * @return optional HTTPS documentation address
     */
    public Optional<HttpsUrl> documentationUrl() {
        return Optional.ofNullable(documentationUrl);
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
     * Returns immutable categories in canonical ID order.
     *
     * @return immutable categories in canonical ID order
     */
    public Map<CategoryId, CategorySpecification> categories() {
        return categories;
    }

    /**
     * Returns immutable sources in canonical ID order.
     *
     * @return immutable sources in canonical ID order
     */
    public Map<DiagnosticSourceId, DiagnosticSourceSpecification> sources() {
        return sources;
    }

    /**
     * Returns immutable generators in canonical ID order.
     *
     * @return immutable generators in canonical ID order
     */
    public Map<DiagnosticGeneratorId, DiagnosticGeneratorSpecification> generators() {
        return generators;
    }

    /**
     * Returns immutable destinations in canonical ID order.
     *
     * @return immutable destinations in canonical ID order
     */
    public Map<DestinationId, SupportDestinationSpecification> destinations() {
        return destinations;
    }

    /**
     * Returns immutable capability requirements in canonical ID order.
     *
     * @return immutable capability requirements in canonical ID order
     */
    public Map<CapabilityId, CapabilityRequirement> capabilityRequirements() {
        return capabilityRequirements;
    }

    /**
     * Returns immutable capability offers in canonical ID order.
     *
     * @return immutable capability offers in canonical ID order
     */
    public Map<CapabilityId, CapabilityOffer> capabilityOffers() {
        return capabilityOffers;
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableMap(
            Map<K, V> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    /** Builder for a complete immutable provider specification. */
    public static final class Builder {
        private final ProviderId id;
        private final ProviderVersion version;
        private final LocalizationKey labelKey;
        private final EnumSet<SupportedSide> supportedSides =
                EnumSet.noneOf(SupportedSide.class);
        private final TreeMap<CategoryId, CategorySpecification> categories = new TreeMap<>();
        private final TreeMap<DiagnosticSourceId, DiagnosticSourceSpecification> sources =
                new TreeMap<>();
        private final TreeMap<DiagnosticGeneratorId, DiagnosticGeneratorSpecification> generators =
                new TreeMap<>();
        private final TreeMap<DestinationId, SupportDestinationSpecification> destinations =
                new TreeMap<>();
        private final TreeMap<CapabilityId, CapabilityRequirement> capabilityRequirements =
                new TreeMap<>();
        private final TreeMap<CapabilityId, CapabilityOffer> capabilityOffers = new TreeMap<>();
        private LocalizationKey descriptionKey;
        private LocalizationKey privacyNoticeKey;
        private HttpsUrl documentationUrl;
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                ProviderId id, ProviderVersion version, LocalizationKey labelKey) {
            this.id = Objects.requireNonNull(id, "id");
            this.version = Objects.requireNonNull(version, "version");
            this.labelKey = Objects.requireNonNull(labelKey, "labelKey");
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
         * Accepts localized privacy notice and returns this builder.
         *
         * @param value localized privacy notice
         * @return this builder
         */
        public Builder privacyNoticeKey(LocalizationKey value) {
            privacyNoticeKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Accepts HTTPS documentation address and returns this builder.
         *
         * @param value HTTPS documentation address
         * @return this builder
         */
        public Builder documentationUrl(HttpsUrl value) {
            documentationUrl = Objects.requireNonNull(value, "value");
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
         * Accepts category declaration and returns this builder.
         *
         * @param category category declaration
         * @return this builder
         */
        public Builder addCategory(CategorySpecification category) {
            Objects.requireNonNull(category, "category");
            SpecificationChecks.putUnique(
                    categories,
                    category.id(),
                    category,
                    IdentifierKind.CATEGORY,
                    category.id().value(),
                    MAX_CATEGORIES);
            return this;
        }

        /**
         * Accepts source declaration and returns this builder.
         *
         * @param source source declaration
         * @return this builder
         */
        public Builder addSource(DiagnosticSourceSpecification source) {
            Objects.requireNonNull(source, "source");
            SpecificationChecks.putUnique(
                    sources,
                    source.id(),
                    source,
                    IdentifierKind.DIAGNOSTIC_SOURCE,
                    source.id().value(),
                    MAX_SOURCES);
            return this;
        }

        /**
         * Accepts generated diagnostic declaration and returns this builder.
         *
         * @param generator generated diagnostic declaration
         * @return this builder
         */
        public Builder addGenerator(DiagnosticGeneratorSpecification generator) {
            Objects.requireNonNull(generator, "generator");
            SpecificationChecks.putUnique(
                    generators,
                    generator.id(),
                    generator,
                    IdentifierKind.DIAGNOSTIC_GENERATOR,
                    generator.id().value(),
                    MAX_GENERATORS);
            return this;
        }

        /**
         * Accepts support destination declaration and returns this builder.
         *
         * @param destination support destination declaration
         * @return this builder
         */
        public Builder addDestination(SupportDestinationSpecification destination) {
            Objects.requireNonNull(destination, "destination");
            SpecificationChecks.putUnique(
                    destinations,
                    destination.id(),
                    destination,
                    IdentifierKind.DESTINATION,
                    destination.id().value(),
                    MAX_DESTINATIONS);
            return this;
        }

        /**
         * Accepts capability requirement and returns this builder.
         *
         * @param requirement capability requirement
         * @return this builder
         */
        public Builder requireCapability(CapabilityRequirement requirement) {
            Objects.requireNonNull(requirement, "requirement");
            SpecificationChecks.putUnique(
                    capabilityRequirements,
                    requirement.id(),
                    requirement,
                    IdentifierKind.CAPABILITY,
                    requirement.id().value(),
                    MAX_CAPABILITIES);
            return this;
        }

        /**
         * Accepts provider-owned capability offer and returns this builder.
         *
         * @param offer provider-owned capability offer
         * @return this builder
         */
        public Builder offerCapability(CapabilityOffer offer) {
            Objects.requireNonNull(offer, "offer");
            SpecificationChecks.putUnique(
                    capabilityOffers,
                    offer.id(),
                    offer,
                    IdentifierKind.CAPABILITY,
                    offer.id().value(),
                    MAX_CAPABILITIES);
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
         * Returns validated immutable provider specification.
         *
         * @return validated immutable provider specification
         */
        public ProviderSpecification build() {
            if (supportedSides.isEmpty()) {
                throw new IllegalArgumentException("supportedSides must not be empty");
            }
            if (categories.isEmpty()) {
                throw new IllegalArgumentException("A provider must declare at least one category");
            }
            validateOwnedGlobalIds();
            validateChildSides();
            validateCategoryReferences();
            validateGeneratorCapabilities();
            validateTotalFieldCount();
            return new ProviderSpecification(this);
        }

        private void validateOwnedGlobalIds() {
            destinations.values().forEach(
                    destination -> {
                        if (!destination.id().namespace().equals(id.namespace())) {
                            throw new IllegalArgumentException(
                                    "Destination IDs must be owned by the provider namespace");
                        }
                    });
            capabilityOffers.values().forEach(
                    offer -> {
                        if (!offer.id().namespace().equals(id.namespace())) {
                            throw new IllegalArgumentException(
                                    "Capability offers must be owned by the provider namespace");
                        }
                    });
        }

        private void validateChildSides() {
            sources.values().forEach(
                    source -> requireSupportedSides(source.supportedSides(), "source"));
            generators.values().forEach(
                    generator -> requireSupportedSides(generator.supportedSides(), "generator"));
        }

        private void requireSupportedSides(Set<SupportedSide> childSides, String kind) {
            if (!supportedSides.containsAll(childSides)) {
                throw new IllegalArgumentException(
                        "Provider supported sides must contain every " + kind + " side");
            }
        }

        private void validateGeneratorCapabilities() {
            boolean hasWorldStateExport = generators.values().stream()
                    .anyMatch(
                            generator ->
                                    generator.kind()
                                            == DiagnosticGeneratorKind.WORLD_STATE_EXPORT);
            if (!hasWorldStateExport) {
                return;
            }
            CapabilityRequirement standard =
                    StandardCapabilities.boundedWorldStateExport();
            CapabilityRequirement requirement = capabilityRequirements.get(standard.id());
            if (requirement == null
                    || !requirement.required()
                    || requirement.minimumVersion().compareTo(standard.minimumVersion()) < 0) {
                throw new IllegalArgumentException(
                        "World-state exports require mandatory bounded world-state capability "
                                + "1.0 or newer");
            }
        }

        private void validateCategoryReferences() {
            categories.values().forEach(
                    category -> {
                        requireKnown(category.sourceIds(), sources, "source");
                        requireKnown(category.generatorIds(), generators, "generator");
                        requireKnown(category.destinationIds(), destinations, "destination");
                    });
        }

        private static <K> void requireKnown(Set<K> references, Map<K, ?> declarations, String kind) {
            for (K reference : references) {
                if (!declarations.containsKey(reference)) {
                    throw new IllegalArgumentException(
                            "Category references unknown " + kind + " ID: " + reference);
                }
            }
        }

        private void validateTotalFieldCount() {
            int total = categories.values().stream()
                    .mapToInt(category -> category.fields().size())
                    .sum();
            if (total > MAX_TOTAL_FIELDS) {
                throw new IllegalArgumentException(
                        "Provider fields exceed aggregate maximum " + MAX_TOTAL_FIELDS);
            }
        }
    }
}
