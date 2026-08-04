package com.cybersammy.bugreport.core.draft;

import java.io.Serial;
import java.util.Objects;

/** Typed rejection produced while binding a parsed draft to trusted runtime state. */
public final class DraftResolutionException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final DraftResolutionCode code;

    DraftResolutionException(DraftResolutionCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns the stable rejection reason. */
    public DraftResolutionCode code() {
        return code;
    }
}
