package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable descriptive support destination.
 *
 * <p>This value cannot execute delivery or authorize transmission. The runtime
 * must negotiate the transport and obtain explicit user confirmation.
 */
public final class SupportDestinationSpecification {
    private final DestinationId id;
    private final SupportDestinationType type;
    private final TransportId transportId;
    private final SupportDestinationTarget target;
    private final LocalizationKey labelKey;
    private final LocalizationKey descriptionKey;
    private final ExtensionMetadata extensions;

    private SupportDestinationSpecification(Builder builder) {
        id = builder.id;
        type = builder.type;
        transportId = builder.transportId;
        target = builder.target;
        labelKey = builder.labelKey;
        descriptionKey = builder.descriptionKey;
        extensions = builder.extensions;
    }

    /**
     * Creates a destination builder.
     *
     * @param id globally namespaced destination ID
     * @param type descriptive destination category
     * @param transportId required internal transport capability
     * @param target bounded data-only target
     * @return new builder
     */
    public static Builder builder(
            DestinationId id,
            SupportDestinationType type,
            TransportId transportId,
            SupportDestinationTarget target) {
        return new Builder(id, type, transportId, target);
    }

    /**
     * Returns destination ID.
     *
     * @return destination ID
     */
    public DestinationId id() {
        return id;
    }

    /**
     * Returns descriptive destination category.
     *
     * @return descriptive destination category
     */
    public SupportDestinationType type() {
        return type;
    }

    /**
     * Returns required transport ID.
     *
     * @return required transport ID
     */
    public TransportId transportId() {
        return transportId;
    }

    /**
     * Returns bounded data-only target.
     *
     * @return bounded data-only target
     */
    public SupportDestinationTarget target() {
        return target;
    }

    /**
     * Returns localized destination label.
     *
     * @return localized destination label
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }

    /**
     * Returns optional localized destination description.
     *
     * @return optional localized destination description
     */
    public Optional<LocalizationKey> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Returns bounded optional extension metadata.
     *
     * @return bounded optional extension metadata
     */
    public ExtensionMetadata extensions() {
        return extensions;
    }

    /** Builder for an immutable destination declaration. */
    public static final class Builder {
        private final DestinationId id;
        private final SupportDestinationType type;
        private final TransportId transportId;
        private final SupportDestinationTarget target;
        private LocalizationKey labelKey;
        private LocalizationKey descriptionKey;
        private ExtensionMetadata extensions = ExtensionMetadata.empty();

        private Builder(
                DestinationId id,
                SupportDestinationType type,
                TransportId transportId,
                SupportDestinationTarget target) {
            this.id = Objects.requireNonNull(id, "id");
            this.type = Objects.requireNonNull(type, "type");
            this.transportId = Objects.requireNonNull(transportId, "transportId");
            this.target = Objects.requireNonNull(target, "target");
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
         * Returns validated immutable destination specification.
         *
         * @return validated immutable destination specification
         */
        public SupportDestinationSpecification build() {
            Objects.requireNonNull(labelKey, "labelKey");
            boolean validTarget = switch (type) {
                case LOCAL_ARCHIVE ->
                        target instanceof SupportDestinationTarget.LocalArchiveTarget;
                case EXTERNAL_SUPPORT_URL, ISSUE_TRACKER ->
                        target instanceof SupportDestinationTarget.WebTarget;
                case EMAIL -> target instanceof SupportDestinationTarget.EmailTarget;
            };
            if (!validTarget) {
                throw new IllegalArgumentException(
                        "Support destination target does not match destination type");
            }
            return new SupportDestinationSpecification(this);
        }
    }
}
