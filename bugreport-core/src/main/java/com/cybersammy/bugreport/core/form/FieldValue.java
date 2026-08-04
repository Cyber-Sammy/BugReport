package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable, persistence-neutral values accepted by the headless form engine. */
public sealed interface FieldValue {
    /** Maximum Unicode code points retained by one text value or text list. */
    int MAX_TEXT_CODE_POINTS = 65_536;
    /** Maximum entries retained by one ordered text list. */
    int MAX_TEXT_ITEMS = 128;
    /** Maximum selected options retained by one multi-selection value. */
    int MAX_SELECTION_ITEMS = 64;
    /** Maximum precision and absolute scale retained by one decimal value. */
    int MAX_DECIMAL_COMPONENT = 1_024;
    /** Maximum binary magnitude retained by one integer value. */
    int MAX_INTEGER_BITS = 4_096;

    /**
     * Free-form text for a text-like field.
     *
     * @param value bounded well-formed Unicode text
     */
    record Text(String value) implements FieldValue {
        /** Validates bounded text. */
        public Text {
            value = requireText(value, MAX_TEXT_CODE_POINTS, "Text value");
        }
    }

    /**
     * Ordered text items, primarily reproduction steps.
     *
     * @param values bounded immutable text items
     */
    record TextList(List<String> values) implements FieldValue {
        /** Defensively copies and validates bounded text items. */
        public TextList {
            Objects.requireNonNull(values, "values");
            if (values.size() > MAX_TEXT_ITEMS) {
                throw new IllegalArgumentException(
                        "Text list exceeds " + MAX_TEXT_ITEMS + " items");
            }
            int totalCodePoints = 0;
            for (String value : values) {
                String validated = requireText(value, MAX_TEXT_CODE_POINTS, "Text list item");
                totalCodePoints = Math.addExact(
                        totalCodePoints,
                        validated.codePointCount(0, validated.length()));
                if (totalCodePoints > MAX_TEXT_CODE_POINTS) {
                    throw new IllegalArgumentException(
                            "Text list exceeds " + MAX_TEXT_CODE_POINTS + " code points");
                }
            }
            values = List.copyOf(values);
        }
    }

    /**
     * Boolean checkbox state.
     *
     * @param checked submitted checkbox state
     */
    record Checkbox(boolean checked) implements FieldValue {}

    /**
     * One selected provider-declared option.
     *
     * @param optionId selected option identity
     */
    record Selection(FieldOptionId optionId) implements FieldValue {
        /** Validates the selected option identity. */
        public Selection {
            Objects.requireNonNull(optionId, "optionId");
        }
    }

    /**
     * Canonically ordered provider-declared option selection.
     *
     * @param optionIds selected option identities
     */
    record MultiSelection(Set<FieldOptionId> optionIds) implements FieldValue {
        /** Defensively copies and validates selected option identities. */
        public MultiSelection {
            Objects.requireNonNull(optionIds, "optionIds");
            if (optionIds.size() > MAX_SELECTION_ITEMS) {
                throw new IllegalArgumentException(
                        "Multi-selection exceeds " + MAX_SELECTION_ITEMS + " options");
            }
            TreeSet<FieldOptionId> ordered = new TreeSet<>();
            for (FieldOptionId optionId : optionIds) {
                ordered.add(Objects.requireNonNull(optionId, "optionId"));
            }
            optionIds = Collections.unmodifiableSet(ordered);
        }
    }

    /**
     * Arbitrary-precision whole number within the engine safety ceiling.
     *
     * @param value exact whole number
     */
    record IntegerNumber(BigInteger value) implements FieldValue {
        /** Validates the integer magnitude. */
        public IntegerNumber {
            Objects.requireNonNull(value, "value");
            if (value.bitLength() > MAX_INTEGER_BITS) {
                throw new IllegalArgumentException(
                        "Integer value exceeds " + MAX_INTEGER_BITS + " bits");
            }
        }
    }

    /**
     * Arbitrary-precision decimal within the engine safety ceiling.
     *
     * @param value exact decimal number
     */
    record DecimalNumber(BigDecimal value) implements FieldValue {
        /** Validates decimal precision and scale. */
        public DecimalNumber {
            Objects.requireNonNull(value, "value");
            if (value.precision() > MAX_DECIMAL_COMPONENT
                    || Math.abs((long) value.scale()) > MAX_DECIMAL_COMPONENT) {
                throw new IllegalArgumentException(
                        "Decimal precision and absolute scale must not exceed "
                                + MAX_DECIMAL_COMPONENT);
            }
        }
    }

    /**
     * Product-defined report severity.
     *
     * @param value severity choice
     */
    record Severity(ReportSeverity value) implements FieldValue {
        /** Validates the severity choice. */
        public Severity {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Product-defined physical-side or gameplay context.
     *
     * @param value side/context choice
     */
    record SideContext(ReportSideContext value) implements FieldValue {
        /** Validates the side/context choice. */
        public SideContext {
            Objects.requireNonNull(value, "value");
        }
    }

    private static String requireText(String value, int maximumCodePoints, String description) {
        Objects.requireNonNull(value, "value");
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(
                    description + " exceeds " + maximumCodePoints + " code points");
        }
        if (value.codePoints().anyMatch(FieldValue::isDisallowedControl)) {
            throw new IllegalArgumentException(description + " contains a disallowed control");
        }
        requireWellFormedUtf16(value, description);
        return value;
    }

    private static void requireWellFormedUtf16(String value, String description) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            description + " contains an unpaired Unicode surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(
                        description + " contains an unpaired Unicode surrogate");
            }
        }
    }

    private static boolean isDisallowedControl(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }
}
