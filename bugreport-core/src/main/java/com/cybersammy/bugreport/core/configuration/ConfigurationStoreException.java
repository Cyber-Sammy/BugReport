package com.cybersammy.bugreport.core.configuration;

import java.util.Objects;

/** Typed failure at the local configuration persistence boundary. */
public final class ConfigurationStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ConfigurationStoreCode code;

    ConfigurationStoreException(ConfigurationStoreCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    ConfigurationStoreException(ConfigurationStoreCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns a stable, path-safe failure category. */
    public ConfigurationStoreCode code() {
        return code;
    }
}
