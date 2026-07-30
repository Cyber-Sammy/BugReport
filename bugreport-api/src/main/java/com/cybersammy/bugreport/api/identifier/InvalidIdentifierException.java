package com.cybersammy.bugreport.api.identifier;

import java.util.Objects;
import java.util.Optional;

/** Indicates that a supplied identifier is not canonical for its semantic scope. */
public final class InvalidIdentifierException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Semantic scope whose grammar was violated. */
    private final IdentifierKind kind;

    /** Exact rejected input, retained for explicit diagnostic handling only. */
    private final String rejectedValue;

    InvalidIdentifierException(IdentifierKind kind, String rejectedValue, String requirement) {
        super("Invalid " + Objects.requireNonNull(kind, "kind") + " identifier: " + requirement);
        this.kind = kind;
        this.rejectedValue = rejectedValue;
    }

    /**
     * Returns the semantic scope whose validation failed.
     *
     * @return the identifier kind
     */
    public IdentifierKind kind() {
        return kind;
    }

    /**
     * Returns the exact rejected value when one was supplied.
     *
     * <p>The value is intentionally omitted from the exception message so that
     * untrusted, oversized, or control-character-containing input is not
     * copied into ordinary logs.
     *
     * @return the rejected value, or empty when the input was {@code null}
     */
    public Optional<String> rejectedValue() {
        return Optional.ofNullable(rejectedValue);
    }
}
