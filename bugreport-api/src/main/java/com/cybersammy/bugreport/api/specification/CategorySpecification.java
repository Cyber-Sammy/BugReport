package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.IdentifierCollisionException;
import com.cybersammy.bugreport.api.identifier.IdentifierKind;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable report-category specification within one provider. */
public final class CategorySpecification {
    /** Maximum fields declared by one category. */
    public static final int MAX_FIELDS = 128;
    /** Maximum references of each diagnostic or destination kind. */
    public static final int MAX_REFERENCES_PER_KIND = 128;

    private final CategoryId id;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final Map<FieldId, FieldSpecification> fields;
    private final Set<DiagnosticSourceId> sourceIds;
    private final Set<DiagnosticGeneratorId> generatorIds;
    private final Set<DestinationId> destinationIds;
    private final ExtensionMetadata extensions;

    private CategorySpecification(Builder builder) {
        id = builder.id;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        fields = Collections.unmodifiableMap(new TreeMap<>(builder.fields));
        sourceIds = Collections.unmodifiableSet(new TreeSet<>(builder.sourceIds));
        generatorIds = Collections.unmodifiableSet(new TreeSet<>(builder.generatorIds));
        destinationIds = Collections.unmodifiableSet(new TreeSet<>(builder.destinationIds));
        extensions = builder.extensions;
    }

    /**
     * Creates a category builder.
     *
     * @param id category ID within its provider
     * @param labelKey localized category label
     * @return new builder
     */
    public static Builder builder(CategoryId id, LocalizationKey labelKey) {
        return new Builder(id, labelKey);
    }

    /**
     * Returns category ID.
     *
     * @return category ID
     */
    public CategoryId id() {
        return id;
    }

    /**
     * Returns localized category label.
     *
     * @return localized category label
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized category description.
     *
     * @return optional localized category description
     */
    public Optional<LocalizationKey> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Returns immutable fields in canonical ID order.
     *
     * @return immutable fields in canonical ID order
     */
    public Map<FieldId, FieldSpecification> fields() {
        return fields;
    }

    /**
     * Returns immutable referenced source IDs in canonical order.
     *
     * @return immutable referenced source IDs in canonical order
     */
    public Set<DiagnosticSourceId> sourceIds() {
        return sourceIds;
    }

    /**
     * Returns immutable referenced generator IDs in canonical order.
     *
     * @return immutable referenced generator IDs in canonical order
     */
    public Set<DiagnosticGeneratorId> generatorIds() {
        return generatorIds;
    }

    /**
     * Returns immutable referenced destination IDs in canonical order.
     *
     * @return immutable referenced destination IDs in canonical order
     */
    public Set<DestinationId> destinationIds() {
        return destinationIds;
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    /** Builder for an immutable category specification. */
    public static final class Builder {
        private final CategoryId id;
        private final LocalizationKey labelKey;
        private final TreeMap<FieldId, FieldSpecification> fields = new TreeMap<>();
        private final TreeSet<DiagnosticSourceId> sourceIds = new TreeSet<>();
        private final TreeSet<DiagnosticGeneratorId> generatorIds = new TreeSet<>();
        private final TreeSet<DestinationId> destinationIds = new TreeSet<>();
        private LocalizationKey descriptionKey;
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(CategoryId id, LocalizationKey labelKey) {
            this.id = Objects.requireNonNull(id, "id");
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
         * Accepts field declaration and returns this builder.
         *
         * @param field field declaration
         * @return this builder
         */
        public Builder addField(FieldSpecification field) {
            Objects.requireNonNull(field, "field");
            SpecificationChecks.putUnique(
                    fields,
                    field.id(),
                    field,
                    IdentifierKind.FIELD,
                    field.id().value(),
                    MAX_FIELDS);
            return this;
        }

        /**
         * Accepts provider-level source ID and returns this builder.
         *
         * @param id provider-level source ID
         * @return this builder
         */
        public Builder useSource(DiagnosticSourceId id) {
            DiagnosticSourceId validated = Objects.requireNonNull(id, "id");
            addReference(
                    sourceIds,
                    validated,
                    IdentifierKind.DIAGNOSTIC_SOURCE,
                    validated.value(),
                    "source");
            return this;
        }

        /**
         * Accepts provider-level generator ID and returns this builder.
         *
         * @param id provider-level generator ID
         * @return this builder
         */
        public Builder useGenerator(DiagnosticGeneratorId id) {
            DiagnosticGeneratorId validated = Objects.requireNonNull(id, "id");
            addReference(
                    generatorIds,
                    validated,
                    IdentifierKind.DIAGNOSTIC_GENERATOR,
                    validated.value(),
                    "generator");
            return this;
        }

        /**
         * Accepts provider-level destination ID and returns this builder.
         *
         * @param id provider-level destination ID
         * @return this builder
         */
        public Builder useDestination(DestinationId id) {
            DestinationId validated = Objects.requireNonNull(id, "id");
            addReference(
                    destinationIds,
                    validated,
                    IdentifierKind.DESTINATION,
                    validated.value(),
                    "destination");
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
         * Returns immutable category specification.
         *
         * @return immutable category specification
         */
        public CategorySpecification build() {
            return new CategorySpecification(this);
        }

        private static <T> void addReference(
                Set<T> values,
                T value,
                IdentifierKind identifierKind,
                String canonicalValue,
                String kind) {
            if (!values.add(value)) {
                throw new IdentifierCollisionException(identifierKind, canonicalValue);
            }
            if (values.size() > MAX_REFERENCES_PER_KIND) {
                values.remove(value);
                throw new IllegalArgumentException(
                        "Category " + kind + " references exceed maximum "
                                + MAX_REFERENCES_PER_KIND);
            }
        }
    }
}
