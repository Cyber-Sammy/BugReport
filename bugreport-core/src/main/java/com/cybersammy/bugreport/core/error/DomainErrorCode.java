package com.cybersammy.bugreport.core.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable, log-safe identifier for a failure at a Core boundary.
 *
 * <p>The code is intentionally separate from an exception message: messages may change for users,
 * while log processing and UI handling can rely on this identifier.
 */
public record DomainErrorCode(String value) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");

    public DomainErrorCode {
        value = Objects.requireNonNull(value, "value");
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Domain error code must be a dotted lowercase token");
        }
    }

    /** Creates a namespaced code from an existing boundary-specific enum. */
    public static DomainErrorCode from(String boundary, Enum<?> reason) {
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(reason, "reason");
        return new DomainErrorCode(
                boundary + "." + reason.name().toLowerCase(Locale.ROOT));
    }
}
