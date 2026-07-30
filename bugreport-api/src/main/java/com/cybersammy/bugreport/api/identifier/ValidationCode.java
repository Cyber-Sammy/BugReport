package com.cybersammy.bugreport.api.identifier;

/**
 * Globally namespaced machine-readable validation issue code.
 *
 * @param value exact canonical identifier
 */
public record ValidationCode(String value) implements Comparable<ValidationCode> {
    /** Validates and creates a validation code. */
    public ValidationCode {
        value = IdentifierRules.requireGlobal(IdentifierKind.VALIDATION_CODE, value);
    }

    /**
     * Creates a validation issue code.
     *
     * @param value canonical namespaced value
     * @return validated code
     */
    public static ValidationCode of(String value) {
        return new ValidationCode(value);
    }

    @Override
    public int compareTo(ValidationCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
