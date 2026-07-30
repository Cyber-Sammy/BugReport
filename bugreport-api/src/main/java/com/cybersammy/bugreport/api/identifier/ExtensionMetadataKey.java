package com.cybersammy.bugreport.api.identifier;

/**
 * Globally namespaced key in an extension metadata container.
 *
 * @param value exact canonical identifier
 */
public record ExtensionMetadataKey(String value)
        implements Comparable<ExtensionMetadataKey> {
    /** Validates and creates an extension metadata key. */
    public ExtensionMetadataKey {
        value = IdentifierRules.requireGlobal(IdentifierKind.EXTENSION_METADATA_KEY, value);
    }

    /**
     * Creates an extension metadata key.
     *
     * @param value canonical namespaced value
     * @return validated extension key
     */
    public static ExtensionMetadataKey of(String value) {
        return new ExtensionMetadataKey(value);
    }

    /**
     * Returns the owning namespace.
     *
     * @return extension namespace
     */
    public NamespaceId namespace() {
        return NamespaceId.of(IdentifierRules.namespaceOfGlobal(value));
    }

    @Override
    public int compareTo(ExtensionMetadataKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
