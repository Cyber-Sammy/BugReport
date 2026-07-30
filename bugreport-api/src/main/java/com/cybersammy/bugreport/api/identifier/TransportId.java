package com.cybersammy.bugreport.api.identifier;

/**
 * Globally namespaced report transport identity.
 *
 * @param value exact canonical identifier
 */
public record TransportId(String value) implements Comparable<TransportId> {
    /** Validates and creates a transport identifier. */
    public TransportId {
        value = IdentifierRules.requireGlobal(IdentifierKind.TRANSPORT, value);
    }

    /**
     * Creates a transport identifier.
     *
     * @param value canonical namespaced value
     * @return validated transport ID
     */
    public static TransportId of(String value) {
        return new TransportId(value);
    }

    /**
     * Returns the owning namespace.
     *
     * @return transport namespace
     */
    public NamespaceId namespace() {
        return NamespaceId.of(IdentifierRules.namespaceOfGlobal(value));
    }

    @Override
    public int compareTo(TransportId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
