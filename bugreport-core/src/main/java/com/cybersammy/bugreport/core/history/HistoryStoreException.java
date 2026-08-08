package com.cybersammy.bugreport.core.history;

import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import com.cybersammy.bugreport.core.error.DomainOperation;
import java.util.Objects;

/** Typed, path-free failure at the local history persistence boundary. */
public final class HistoryStoreException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final HistoryStoreCode code;

    HistoryStoreException(HistoryStoreCode code, DomainOperation operation, String message) {
        this(code, operation, message, null);
    }

    HistoryStoreException(
            HistoryStoreCode code, DomainOperation operation, String message, Throwable cause) {
        super(
                DomainErrorCode.from("history", code),
                DomainErrorContext.builder().operation(operation).build(),
                message,
                cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns the history-specific stable failure category. */
    public HistoryStoreCode code() {
        return code;
    }
}
