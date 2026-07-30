package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical category identity within one provider.
 *
 * @param value exact canonical identifier
 */
public record CategoryId(String value) implements Comparable<CategoryId> {
    /** Validates and creates a category identifier. */
    public CategoryId {
        value = IdentifierRules.requireLocalName(IdentifierKind.CATEGORY, value);
    }

    /**
     * Creates a category identifier.
     *
     * @param value canonical local name
     * @return validated category ID
     */
    public static CategoryId of(String value) {
        return new CategoryId(value);
    }

    @Override
    public int compareTo(CategoryId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
