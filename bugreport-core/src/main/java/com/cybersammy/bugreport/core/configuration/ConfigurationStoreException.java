package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import java.util.Objects;

/** Typed failure at the local configuration persistence boundary. */
public final class ConfigurationStoreException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final ConfigurationStoreCode code;

    ConfigurationStoreException(ConfigurationStoreCode code, String message, Throwable cause) {
        super(DomainErrorCode.from("configuration", code), DomainErrorContext.empty(), message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    ConfigurationStoreException(ConfigurationStoreCode code, String message) {
        super(DomainErrorCode.from("configuration", code), DomainErrorContext.empty(), message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns a stable, path-safe failure category. */
    public ConfigurationStoreCode code() {
        return code;
    }
}
