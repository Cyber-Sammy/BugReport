package com.cybersammy.bugreport.core.error;

/** A typed Core failure that can be logged without inspecting its message or cause. */
public interface DomainFailure {
    /** Returns the stable, machine-readable error code. */
    DomainErrorCode errorCode();

    /** Returns only allow-listed, log-safe identifiers. */
    DomainErrorContext errorContext();
}
