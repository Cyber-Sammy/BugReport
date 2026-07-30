package com.cybersammy.bugreport.api.extension;

import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.IdentifierCollisionException;
import com.cybersammy.bugreport.api.identifier.IdentifierKind;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable bounded container for optional namespaced extension data.
 *
 * <p>Unknown values are safely ignorable. Required behavior must be declared
 * through capability negotiation rather than inferred from this container.
 */
public final class ExtensionMetadata {
    /** Maximum number of namespaced top-level entries. */
    public static final int MAX_TOP_LEVEL_ENTRIES = 64;
    /** Maximum number of values in one array or object. */
    public static final int MAX_CONTAINER_ENTRIES = 64;
    /** Maximum nested array/object depth. */
    public static final int MAX_DEPTH = 8;
    /** Maximum total values across the container. */
    public static final int MAX_VALUE_COUNT = 256;
    /** Maximum UTF-8 bytes in one string or nested object key. */
    public static final int MAX_STRING_BYTES = 8_192;
    /** Maximum decimal precision. */
    public static final int MAX_NUMBER_PRECISION = 128;
    /** Maximum absolute decimal scale. */
    public static final int MAX_ABSOLUTE_NUMBER_SCALE = 128;

    private static final ExtensionMetadata EMPTY =
            new ExtensionMetadata(Collections.emptyMap());

    private final Map<ExtensionMetadataKey, ExtensionValue> values;

    private ExtensionMetadata(Map<ExtensionMetadataKey, ExtensionValue> values) {
        this.values = values;
    }

    /**
     * Returns an empty extension container.
     *
     * @return empty metadata
     */
    public static ExtensionMetadata empty() {
        return EMPTY;
    }

    /**
     * Creates a builder that rejects duplicate canonical keys.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns immutable entries in canonical key order.
     *
     * @return extension entries
     */
    public Map<ExtensionMetadataKey, ExtensionValue> values() {
        return values;
    }

    /**
     * Reports whether the container has no entries.
     *
     * @return {@code true} for empty metadata
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExtensionMetadata metadata && values.equals(metadata.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** Builder for a bounded extension metadata container. */
    public static final class Builder {
        private final TreeMap<ExtensionMetadataKey, ExtensionValue> values = new TreeMap<>();

        private Builder() {}

        /**
         * Adds one optional extension entry.
         *
         * @param key canonical namespaced key
         * @param value JSON-compatible value
         * @return this builder
         * @throws IdentifierCollisionException when the key is already present
         */
        public Builder put(ExtensionMetadataKey key, ExtensionValue value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (values.containsKey(key)) {
                throw new IdentifierCollisionException(
                        IdentifierKind.EXTENSION_METADATA_KEY, key.value());
            }
            if (values.size() >= MAX_TOP_LEVEL_ENTRIES) {
                throw new IllegalArgumentException(
                        "Extension metadata exceeds "
                                + MAX_TOP_LEVEL_ENTRIES
                                + " top-level entries");
            }
            values.put(key, value);
            return this;
        }

        /**
         * Validates all resource bounds and creates immutable metadata.
         *
         * @return immutable metadata
         */
        public ExtensionMetadata build() {
            validateValues(values);
            return values.isEmpty()
                    ? EMPTY
                    : new ExtensionMetadata(
                            Collections.unmodifiableMap(new TreeMap<>(values)));
        }
    }

    private static void validateValues(Map<ExtensionMetadataKey, ExtensionValue> values) {
        ArrayDeque<ValueAtDepth> pending = new ArrayDeque<>();
        values.values().forEach(value -> pending.addLast(new ValueAtDepth(value, 1)));
        int valueCount = 0;

        while (!pending.isEmpty()) {
            ValueAtDepth current = pending.removeFirst();
            valueCount++;
            if (valueCount > MAX_VALUE_COUNT) {
                throw new IllegalArgumentException(
                        "Extension metadata exceeds " + MAX_VALUE_COUNT + " total values");
            }
            validateValue(current, pending);
        }
    }

    private static void validateValue(
            ValueAtDepth current, ArrayDeque<ValueAtDepth> pending) {
        ExtensionValue value = current.value();
        if (value instanceof ExtensionValue.ArrayValue arrayValue) {
            requireContainerDepth(current.depth());
            arrayValue.values()
                    .forEach(child -> pending.addLast(new ValueAtDepth(child, current.depth() + 1)));
        } else if (value instanceof ExtensionValue.ObjectValue objectValue) {
            requireContainerDepth(current.depth());
            objectValue.values()
                    .values()
                    .forEach(child -> pending.addLast(new ValueAtDepth(child, current.depth() + 1)));
        }
    }

    private static void requireContainerDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Extension metadata exceeds maximum depth " + MAX_DEPTH);
        }
    }

    private record ValueAtDepth(ExtensionValue value, int depth) {}
}
