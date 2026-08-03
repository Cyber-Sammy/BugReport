package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.IdentifierKind;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable declarative form-field specification. */
public final class FieldSpecification {
    /** Maximum choices declared by one selection field. */
    public static final int MAX_OPTIONS = 64;

    private final FieldId id;
    private final FieldKind kind;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final PrivacyClassification privacy;
    private final boolean required;
    private final FieldConstraints constraints;
    private final Map<FieldOptionId, FieldOption> options;
    private final ExtensionMetadata extensions;

    private FieldSpecification(Builder builder) {
        id = builder.id;
        kind = builder.kind;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        privacy = builder.privacy;
        required = builder.required;
        constraints = builder.constraints;
        options = Collections.unmodifiableMap(new TreeMap<>(builder.options));
        extensions = builder.extensions;
    }

    /**
     * Creates a field builder with all identity and policy floors explicit.
     *
     * @param id field ID within its category
     * @param kind closed field behavior
     * @param labelKey localized label
     * @param privacy declared privacy floor
     * @return new builder
     */
    public static Builder builder(
            FieldId id,
            FieldKind kind,
            LocalizationKey labelKey,
            PrivacyClassification privacy) {
        return new Builder(id, kind, labelKey, privacy);
    }

    /**
     * Returns field ID.
     *
     * @return field ID
     */
    public FieldId id() {
        return id;
    }

    /**
     * Returns field behavior.
     *
     * @return field behavior
     */
    public FieldKind kind() {
        return kind;
    }

    /**
     * Returns localized label key.
     *
     * @return localized label key
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized description key.
     *
     * @return optional localized description key
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
     * Returns whether a value is required when this field is present in the selected category.
     * This does not require a provider or category to declare the field.
     *
     * @return whether a value is required when the declared field is active
     */
    public boolean required() {
        return required;
    }

    /**
     * Returns immutable declarative constraints.
     *
     * @return immutable declarative constraints
     */
    public FieldConstraints constraints() {
        return constraints;
    }

    /**
     * Returns immutable options in canonical ID order.
     *
     * @return immutable options in canonical ID order
     */
    public Map<FieldOptionId, FieldOption> options() {
        return options;
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    /** Builder for an immutable field specification. */
    public static final class Builder {
        private final FieldId id;
        private final FieldKind kind;
        private final LocalizationKey labelKey;
        private final PrivacyClassification privacy;
        private final TreeMap<FieldOptionId, FieldOption> options = new TreeMap<>();
        private LocalizationKey descriptionKey;
        private boolean required;
        private FieldConstraints constraints = FieldConstraints.none();
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                FieldId id,
                FieldKind kind,
                LocalizationKey labelKey,
                PrivacyClassification privacy) {
            this.id = Objects.requireNonNull(id, "id");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.labelKey = Objects.requireNonNull(labelKey, "labelKey");
            this.privacy = Objects.requireNonNull(privacy, "privacy");
        }

        /**
         * Sets the optional localized description.
         *
         * @param value localization key
         * @return this builder
         */
        public Builder descriptionKey(LocalizationKey value) {
            descriptionKey = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets whether completion requires a value.
         *
         * @param value required state
         * @return this builder
         */
        public Builder required(boolean value) {
            required = value;
            return this;
        }

        /**
         * Sets declarative constraints.
         *
         * @param value immutable constraints
         * @return this builder
         */
        public Builder constraints(FieldConstraints value) {
            constraints = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Adds one selection option.
         *
         * @param option immutable option
         * @return this builder
         */
        public Builder addOption(FieldOption option) {
            Objects.requireNonNull(option, "option");
            SpecificationChecks.putUnique(
                    options,
                    option.id(),
                    option,
                    IdentifierKind.FIELD_OPTION,
                    option.id().value(),
                    MAX_OPTIONS);
            return this;
        }

        /**
         * Sets bounded extension metadata.
         *
         * @param value extension metadata
         * @return this builder
         */
        public Builder extensions(ExtensionMetadata value) {
            extensions = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Validates kind-specific invariants and creates the specification.
         *
         * @return immutable field specification
         */
        public FieldSpecification build() {
            if (privacy == PrivacyClassification.PROHIBITED) {
                throw new IllegalArgumentException("A prohibited field cannot be requested");
            }
            validateFreeFormPrivacy();
            validateKindConstraints();
            return new FieldSpecification(this);
        }

        private void validateFreeFormPrivacy() {
            if (isFreeForm(kind) && !privacy.isAtLeast(PrivacyClassification.PERSONAL)) {
                throw new IllegalArgumentException(
                        "Free-form fields require at least PERSONAL privacy");
            }
        }

        private void validateKindConstraints() {
            boolean selection = kind == FieldKind.SINGLE_SELECT || kind == FieldKind.MULTI_SELECT;
            if (selection && options.size() < 2) {
                throw new IllegalArgumentException("Selection fields require at least two options");
            }
            if (!selection && !options.isEmpty()) {
                throw new IllegalArgumentException("Only selection fields may declare options");
            }

            switch (kind) {
                case SINGLE_LINE_TEXT,
                        MULTILINE_TEXT,
                        EXPECTED_BEHAVIOR,
                        ACTUAL_BEHAVIOR -> requireConstraintGroups(true, false, false);
                case REPRODUCTION_STEPS -> requireConstraintGroups(true, true, false);
                case MULTI_SELECT -> {
                    requireConstraintGroups(false, true, false);
                    validateSelectionCounts();
                }
                case INTEGER -> {
                    requireConstraintGroups(false, false, true);
                    validateIntegerBounds();
                }
                case DECIMAL -> requireConstraintGroups(false, false, true);
                case SINGLE_SELECT,
                        CHECKBOX,
                        SEVERITY,
                        SIDE_CONTEXT,
                        READ_ONLY_INFORMATION -> requireConstraintGroups(false, false, false);
            }
            if (kind == FieldKind.READ_ONLY_INFORMATION && required) {
                throw new IllegalArgumentException("Read-only information cannot be required input");
            }
        }

        private void requireConstraintGroups(
                boolean lengthsAllowed, boolean itemsAllowed, boolean numbersAllowed) {
            if ((!lengthsAllowed && constraints.hasLengthBounds())
                    || (!itemsAllowed && constraints.hasItemBounds())
                    || (!numbersAllowed && constraints.hasNumericBounds())) {
                throw new IllegalArgumentException(
                        "Field constraints are not applicable to " + kind);
            }
        }

        private void validateSelectionCounts() {
            if ((constraints.minimumItems().isPresent()
                            && constraints.minimumItems().getAsInt() > options.size())
                    || (constraints.maximumItems().isPresent()
                            && constraints.maximumItems().getAsInt() > options.size())) {
                throw new IllegalArgumentException(
                        "Selection item bounds cannot exceed the option count");
            }
        }

        private void validateIntegerBounds() {
            if (constraints.minimumNumber().map(Builder::hasFraction).orElse(false)
                    || constraints.maximumNumber().map(Builder::hasFraction).orElse(false)) {
                throw new IllegalArgumentException("Integer field bounds must be whole numbers");
            }
        }

        private static boolean hasFraction(BigDecimal value) {
            return value.stripTrailingZeros().scale() > 0;
        }

        private static boolean isFreeForm(FieldKind kind) {
            return switch (kind) {
                case SINGLE_LINE_TEXT,
                        MULTILINE_TEXT,
                        REPRODUCTION_STEPS,
                        EXPECTED_BEHAVIOR,
                        ACTUAL_BEHAVIOR -> true;
                default -> false;
            };
        }
    }
}
