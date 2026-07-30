package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical namespace owned by a mod or first-party platform adapter.
 *
 * @param value exact canonical identifier
 */
public record NamespaceId(String value) implements Comparable<NamespaceId> {
    /** Validates and creates a namespace identifier. */
    public NamespaceId {
        value = IdentifierRules.requireNamespace(IdentifierKind.NAMESPACE, value);
    }

    /**
     * Creates a namespace identifier from its exact canonical representation.
     *
     * @param value canonical namespace
     * @return validated namespace
     */
    public static NamespaceId of(String value) {
        return new NamespaceId(value);
    }

    @Override
    public int compareTo(NamespaceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
