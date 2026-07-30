package com.cybersammy.bugreport.api.identifier;

/**
 * Globally namespaced capability identity.
 *
 * @param value exact canonical identifier
 */
public record CapabilityId(String value) implements Comparable<CapabilityId> {
    /** Validates and creates a capability identifier. */
    public CapabilityId {
        value = IdentifierRules.requireGlobal(IdentifierKind.CAPABILITY, value);
    }

    /**
     * Creates a capability identifier.
     *
     * @param value canonical namespaced value
     * @return validated capability ID
     */
    public static CapabilityId of(String value) {
        return new CapabilityId(value);
    }

    /**
     * Returns the owning namespace.
     *
     * @return capability namespace
     */
    public NamespaceId namespace() {
        return NamespaceId.of(IdentifierRules.namespaceOfGlobal(value));
    }

    @Override
    public int compareTo(CapabilityId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
