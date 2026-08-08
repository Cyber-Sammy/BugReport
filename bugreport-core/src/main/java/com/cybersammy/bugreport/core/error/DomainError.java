package com.cybersammy.bugreport.core.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, safe-to-log description of a domain failure. */
public record DomainError(
        DomainErrorCode code, String safeMessage, DomainErrorContext context) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Pattern SAFE_MESSAGE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9 .,;:()'_-]{0,159}");

    public DomainError {
        code = Objects.requireNonNull(code, "code");
        safeMessage = Objects.requireNonNull(safeMessage, "safeMessage");
        context = Objects.requireNonNull(context, "context");
        if (!SAFE_MESSAGE.matcher(safeMessage).matches()) {
            throw new IllegalArgumentException("Domain error safe message is not log-safe");
        }
    }

    /** Creates the generic safe summary used when an exception message may contain external data. */
    public static DomainError fromCode(DomainErrorCode code, DomainErrorContext context) {
        return new DomainError(code, "Bug Report operation failed", context);
    }
}
