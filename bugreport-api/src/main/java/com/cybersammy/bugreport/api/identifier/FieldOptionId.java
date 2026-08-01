package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical option identity within one form field.
 *
 * @param value exact canonical identifier
 */
public record FieldOptionId(String value) implements Comparable<FieldOptionId> {
    /** Validates and creates a field-option identifier. */
    public FieldOptionId {
        value = IdentifierRules.requireLocalName(IdentifierKind.FIELD_OPTION, value);
    }

    /**
     * Creates a field-option identifier.
     *
     * @param value canonical local value
     * @return validated identifier
     */
    public static FieldOptionId of(String value) {
        return new FieldOptionId(value);
    }

    @Override
    public int compareTo(FieldOptionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
