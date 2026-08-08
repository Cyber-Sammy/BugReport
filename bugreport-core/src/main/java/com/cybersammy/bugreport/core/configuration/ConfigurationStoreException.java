package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import com.cybersammy.bugreport.core.error.DomainOperation;
import java.util.Objects;

/** Typed failure at the local configuration persistence boundary. */
public final class ConfigurationStoreException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final ConfigurationStoreCode code;

    ConfigurationStoreException(
            ConfigurationStoreCode code, DomainOperation operation, String message, Throwable cause) {
        super(
                DomainErrorCode.from("configuration", code),
                DomainErrorContext.builder().operation(operation).build(),
                message,
                cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    ConfigurationStoreException(ConfigurationStoreCode code, DomainOperation operation, String message) {
        super(
                DomainErrorCode.from("configuration", code),
                DomainErrorContext.builder().operation(operation).build(),
                message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns a stable, path-safe failure category. */
    public ConfigurationStoreCode code() {
        return code;
    }
}
