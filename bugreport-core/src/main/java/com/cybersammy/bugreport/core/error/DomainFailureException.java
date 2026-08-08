package com.cybersammy.bugreport.core.error;

import java.util.Objects;

/** Base exception for a typed, safely loggable Core boundary failure. */
public abstract class DomainFailureException extends RuntimeException implements DomainFailure {
    private static final long serialVersionUID = 1L;

    private final DomainError error;

    protected DomainFailureException(
            DomainErrorCode errorCode, DomainErrorContext errorContext, String message) {
        this(DomainError.fromCode(errorCode, errorContext), message);
    }

    protected DomainFailureException(
            DomainErrorCode errorCode, DomainErrorContext errorContext, String message, Throwable cause) {
        this(DomainError.fromCode(errorCode, errorContext), message, cause);
    }

    protected DomainFailureException(DomainError error, String message) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
    }

    protected DomainFailureException(DomainError error, String message, Throwable cause) {
        super(message, cause);
        this.error = Objects.requireNonNull(error, "error");
    }

    @Override
    public final DomainError error() {
        return error;
    }
}
