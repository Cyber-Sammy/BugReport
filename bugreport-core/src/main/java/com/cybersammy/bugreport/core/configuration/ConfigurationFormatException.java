package com.cybersammy.bugreport.core.configuration;

import java.io.Serial;

/** Typed rejection of malformed, unsupported, or unsafe persisted configuration. */
public final class ConfigurationFormatException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    ConfigurationFormatException(String message) {
        super(message);
    }

    ConfigurationFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
