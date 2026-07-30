package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical diagnostic source identity within one provider.
 *
 * @param value exact canonical identifier
 */
public record DiagnosticSourceId(String value) implements Comparable<DiagnosticSourceId> {
    /** Validates and creates a diagnostic source identifier. */
    public DiagnosticSourceId {
        value = IdentifierRules.requireLocalName(IdentifierKind.DIAGNOSTIC_SOURCE, value);
    }

    /**
     * Creates a diagnostic source identifier.
     *
     * @param value canonical local name
     * @return validated source ID
     */
    public static DiagnosticSourceId of(String value) {
        return new DiagnosticSourceId(value);
    }

    @Override
    public int compareTo(DiagnosticSourceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
