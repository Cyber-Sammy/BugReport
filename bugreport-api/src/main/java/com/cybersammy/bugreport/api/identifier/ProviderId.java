package com.cybersammy.bugreport.api.identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical provider identity.
 *
 * <p>A default provider uses its bare declaring namespace. Additional
 * providers use {@code <namespace>:<local_name>}. Loader adapters remain
 * responsible for comparing the parsed namespace with trusted module
 * provenance.
 *
 * @param value exact canonical identifier
 */
public record ProviderId(String value) implements Comparable<ProviderId> {
    /** Validates and creates a provider identifier. */
    public ProviderId {
        value = IdentifierRules.requireProvider(value);
    }

    /**
     * Parses an exact canonical provider identifier.
     *
     * @param value canonical provider ID
     * @return validated provider ID
     */
    public static ProviderId parse(String value) {
        return new ProviderId(value);
    }

    /**
     * Creates the default provider ID for a namespace.
     *
     * @param namespace declaring namespace
     * @return default provider ID
     */
    public static ProviderId defaultProvider(NamespaceId namespace) {
        return new ProviderId(Objects.requireNonNull(namespace, "namespace").value());
    }

    /**
     * Creates an additional provider ID.
     *
     * @param namespace declaring namespace
     * @param localName provider-local name
     * @return namespaced provider ID
     */
    public static ProviderId namespaced(NamespaceId namespace, String localName) {
        Objects.requireNonNull(namespace, "namespace");
        String validated =
                IdentifierRules.requireLocalName(IdentifierKind.PROVIDER, localName);
        return new ProviderId(namespace.value() + ":" + validated);
    }

    /**
     * Returns the provider namespace.
     *
     * @return canonical namespace
     */
    public NamespaceId namespace() {
        int separator = value.indexOf(':');
        return NamespaceId.of(separator < 0 ? value : value.substring(0, separator));
    }

    /**
     * Returns the additional provider's local name.
     *
     * @return local name, or empty for the default provider
     */
    public Optional<String> localName() {
        int separator = value.indexOf(':');
        return separator < 0 ? Optional.empty() : Optional.of(value.substring(separator + 1));
    }

    /**
     * Reports whether this provider belongs to a trusted declaring namespace.
     *
     * @param owner trusted declaring namespace
     * @return {@code true} when the namespaces match exactly
     */
    public boolean isOwnedBy(NamespaceId owner) {
        return namespace().equals(Objects.requireNonNull(owner, "owner"));
    }

    @Override
    public int compareTo(ProviderId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
