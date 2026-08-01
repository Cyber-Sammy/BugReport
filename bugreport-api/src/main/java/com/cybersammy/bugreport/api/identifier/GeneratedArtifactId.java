package com.cybersammy.bugreport.api.identifier;

/**
 * Canonical artifact identity within one generated diagnostic.
 *
 * @param value exact canonical identifier
 */
public record GeneratedArtifactId(String value) implements Comparable<GeneratedArtifactId> {
    /** Validates and creates a generated-artifact identifier. */
    public GeneratedArtifactId {
        value = IdentifierRules.requireLocalName(IdentifierKind.GENERATED_ARTIFACT, value);
    }

    /**
     * Creates a generated-artifact identifier.
     *
     * @param value canonical local value
     * @return validated identifier
     */
    public static GeneratedArtifactId of(String value) {
        return new GeneratedArtifactId(value);
    }

    @Override
    public int compareTo(GeneratedArtifactId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
