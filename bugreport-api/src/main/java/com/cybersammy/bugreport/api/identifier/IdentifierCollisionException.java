package com.cybersammy.bugreport.api.identifier;

import java.util.Objects;

/** Indicates that multiple declarations used the same canonical ID in one scope. */
public final class IdentifierCollisionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Semantic scope containing the collision. */
    private final IdentifierKind kind;

    /** Exact validated identifier shared by multiple declarations. */
    private final String canonicalValue;

    /**
     * Creates a collision error.
     *
     * @param kind semantic identifier scope
     * @param canonicalValue validated colliding identifier
     */
    public IdentifierCollisionException(IdentifierKind kind, String canonicalValue) {
        super(
                "Duplicate "
                        + Objects.requireNonNull(kind, "kind")
                        + " identifier: "
                        + Objects.requireNonNull(canonicalValue, "canonicalValue"));
        this.kind = kind;
        this.canonicalValue = canonicalValue;
    }

    /**
     * Returns the colliding semantic scope.
     *
     * @return identifier kind
     */
    public IdentifierKind kind() {
        return kind;
    }

    /**
     * Returns the exact canonical colliding value.
     *
     * @return canonical identifier
     */
    public String canonicalValue() {
        return canonicalValue;
    }
}
