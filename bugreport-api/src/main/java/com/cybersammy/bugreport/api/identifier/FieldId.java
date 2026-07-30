package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical field identity within one provider category.
 *
 * @param value exact canonical identifier
 */
public record FieldId(String value) implements Comparable<FieldId> {
    /** Validates and creates a field identifier. */
    public FieldId {
        value = IdentifierRules.requireLocalName(IdentifierKind.FIELD, value);
    }

    /**
     * Creates a field identifier.
     *
     * @param value canonical local name
     * @return validated field ID
     */
    public static FieldId of(String value) {
        return new FieldId(value);
    }

    @Override
    public int compareTo(FieldId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
