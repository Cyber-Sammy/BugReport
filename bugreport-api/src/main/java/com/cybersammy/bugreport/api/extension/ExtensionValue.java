package com.cybersammy.bugreport.api.extension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable JSON-compatible value stored under an extension metadata key.
 *
 * <p>Extension values are data only. They cannot nominate classes, callbacks,
 * scripts, services, or another executable extension mechanism.
 */
public sealed interface ExtensionValue
        permits ExtensionValue.ArrayValue,
                ExtensionValue.BooleanValue,
                ExtensionValue.NullValue,
                ExtensionValue.NumberValue,
                ExtensionValue.ObjectValue,
                ExtensionValue.StringValue {
    /**
     * Creates a string extension value.
     *
     * @param value string content
     * @return immutable value
     */
    static ExtensionValue of(String value) {
        return new StringValue(value);
    }

    /**
     * Creates a numeric extension value.
     *
     * @param value finite decimal content
     * @return immutable value
     */
    static ExtensionValue of(BigDecimal value) {
        return new NumberValue(value);
    }

    /**
     * Creates a boolean extension value.
     *
     * @param value boolean content
     * @return immutable value
     */
    static ExtensionValue of(boolean value) {
        return new BooleanValue(value);
    }

    /**
     * Creates an ordered array extension value.
     *
     * @param values array content
     * @return immutable value
     */
    static ExtensionValue array(List<ExtensionValue> values) {
        return new ArrayValue(values);
    }

    /**
     * Creates an object extension value with deterministically ordered keys.
     *
     * @param values object content
     * @return immutable value
     */
    static ExtensionValue object(Map<String, ExtensionValue> values) {
        return new ObjectValue(values);
    }

    /**
     * Returns the JSON null value.
     *
     * @return null extension value
     */
    static ExtensionValue nullValue() {
        return NullValue.INSTANCE;
    }

    /**
     * Immutable string value.
     *
     * @param value string content
     */
    record StringValue(String value) implements ExtensionValue {
        /** Validates and creates a string value. */
        public StringValue {
            Objects.requireNonNull(value, "value");
            requireBoundedString(value, "Extension string");
        }
    }

    /**
     * Immutable decimal number.
     *
     * @param value decimal content
     */
    record NumberValue(BigDecimal value) implements ExtensionValue {
        /** Validates and creates a numeric value. */
        public NumberValue {
            Objects.requireNonNull(value, "value");
            requireBoundedNumber(value);
        }
    }

    /**
     * Immutable boolean value.
     *
     * @param value boolean content
     */
    record BooleanValue(boolean value) implements ExtensionValue {}

    /**
     * Immutable ordered array.
     *
     * @param values array content
     */
    record ArrayValue(List<ExtensionValue> values) implements ExtensionValue {
        /** Defensively copies and creates an array value. */
        public ArrayValue {
            Objects.requireNonNull(values, "values");
            requireContainerSize(values.size(), "array");
            values = List.copyOf(values);
        }
    }

    /**
     * Immutable object with canonical key ordering.
     *
     * @param values object content
     */
    record ObjectValue(Map<String, ExtensionValue> values) implements ExtensionValue {
        /** Defensively copies and creates an object value. */
        public ObjectValue {
            values = copyObject(values);
        }

        private static Map<String, ExtensionValue> copyObject(
                Map<String, ExtensionValue> values) {
            Objects.requireNonNull(values, "values");
            requireContainerSize(values.size(), "object");
            TreeMap<String, ExtensionValue> copy = new TreeMap<>();
            values.forEach(
                    (key, value) -> {
                        requireBoundedString(
                                Objects.requireNonNull(key, "object key"),
                                "Extension object key");
                        copy.put(
                                key,
                                Objects.requireNonNull(value, "object value"));
                    });
            return Collections.unmodifiableMap(copy);
        }
    }

    /** JSON null singleton. */
    enum NullValue implements ExtensionValue {
        /** Shared immutable null value. */
        INSTANCE
    }

    private static void requireContainerSize(int size, String kind) {
        if (size > ExtensionMetadata.MAX_CONTAINER_ENTRIES) {
            throw new IllegalArgumentException(
                    "Extension "
                            + kind
                            + " exceeds "
                            + ExtensionMetadata.MAX_CONTAINER_ENTRIES
                            + " entries");
        }
    }

    private static void requireBoundedString(String value, String description) {
        int encodedLength = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x7F) {
                encodedLength++;
            } else if (character <= 0x7FF) {
                encodedLength += 2;
            } else if (Character.isHighSurrogate(character)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                encodedLength += 4;
                index++;
            } else if (Character.isSurrogate(character)) {
                encodedLength++;
            } else {
                encodedLength += 3;
            }
            if (encodedLength > ExtensionMetadata.MAX_STRING_BYTES) {
                throw new IllegalArgumentException(
                        description
                                + " exceeds "
                                + ExtensionMetadata.MAX_STRING_BYTES
                                + " UTF-8 bytes");
            }
        }
    }

    private static void requireBoundedNumber(BigDecimal value) {
        if (value.precision() > ExtensionMetadata.MAX_NUMBER_PRECISION
                || Math.abs((long) value.scale())
                        > ExtensionMetadata.MAX_ABSOLUTE_NUMBER_SCALE) {
            throw new IllegalArgumentException("Extension number exceeds decimal bounds");
        }
    }
}
