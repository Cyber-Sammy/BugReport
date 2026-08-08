package com.cybersammy.bugreport.core.error;

import java.util.Objects;

/** Base exception for a typed, safely loggable Core boundary failure. */
public abstract class DomainFailureException extends RuntimeException implements DomainFailure {
    private static final long serialVersionUID = 1L;

    private final DomainErrorCode errorCode;
    private final DomainErrorContext errorContext;

    protected DomainFailureException(
            DomainErrorCode errorCode, DomainErrorContext errorContext, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.errorContext = Objects.requireNonNull(errorContext, "errorContext");
    }

    protected DomainFailureException(
            DomainErrorCode errorCode, DomainErrorContext errorContext, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.errorContext = Objects.requireNonNull(errorContext, "errorContext");
    }

    @Override
    public final DomainErrorCode errorCode() {
        return errorCode;
    }

    @Override
    public final DomainErrorContext errorContext() {
        return errorContext;
    }
}
