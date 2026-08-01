package com.cybersammy.bugreport.api.identifier;

/**
 * Globally namespaced support destination identity.
 *
 * @param value exact canonical identifier
 */
public record DestinationId(String value) implements Comparable<DestinationId> {
    /** Validates and creates a destination identifier. */
    public DestinationId {
        value = IdentifierRules.requireGlobal(IdentifierKind.DESTINATION, value);
    }

    /**
     * Creates a destination identifier.
     *
     * @param value canonical namespaced value
     * @return validated identifier
     */
    public static DestinationId of(String value) {
        return new DestinationId(value);
    }

    /**
     * Returns the owning namespace.
     *
     * @return destination namespace
     */
    public NamespaceId namespace() {
        return NamespaceId.of(IdentifierRules.namespaceOfGlobal(value));
    }

    @Override
    public int compareTo(DestinationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
