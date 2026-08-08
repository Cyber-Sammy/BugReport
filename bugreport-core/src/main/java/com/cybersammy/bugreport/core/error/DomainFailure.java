package com.cybersammy.bugreport.core.error;

/** A typed Core failure that can be logged without inspecting its message or cause. */
public interface DomainFailure {
    /** Returns the complete immutable failure value. */
    DomainError error();

    /** Returns the stable, machine-readable error code. */
    default DomainErrorCode errorCode() {
        return error().code();
    }

    /** Returns only allow-listed, log-safe identifiers. */
    default DomainErrorContext errorContext() {
        return error().context();
    }
}
