package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical generated diagnostic identity within one provider.
 *
 * @param value exact canonical identifier
 */
public record DiagnosticGeneratorId(String value) implements Comparable<DiagnosticGeneratorId> {
    /** Validates and creates a generated diagnostic identifier. */
    public DiagnosticGeneratorId {
        value = IdentifierRules.requireLocalName(IdentifierKind.DIAGNOSTIC_GENERATOR, value);
    }

    /**
     * Creates a generated diagnostic identifier.
     *
     * @param value canonical local name
     * @return validated generator ID
     */
    public static DiagnosticGeneratorId of(String value) {
        return new DiagnosticGeneratorId(value);
    }

    @Override
    public int compareTo(DiagnosticGeneratorId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
