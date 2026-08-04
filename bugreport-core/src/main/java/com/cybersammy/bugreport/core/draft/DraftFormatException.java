package com.cybersammy.bugreport.core.draft;

import java.io.Serial;

/** Typed rejection of a malformed, unsupported, or unsafe persisted draft. */
public final class DraftFormatException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    DraftFormatException(String message) {
        super(message);
    }

    DraftFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
