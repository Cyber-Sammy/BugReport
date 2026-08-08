package com.cybersammy.bugreport.core.history;

import java.io.Serial;

/** Typed rejection of malformed or unsupported persisted report history. */
public final class HistoryFormatException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    HistoryFormatException(String message) { super(message); }
    HistoryFormatException(String message, Throwable cause) { super(message, cause); }
}
