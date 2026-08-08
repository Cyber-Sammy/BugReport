package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainFailureException;
import java.util.Objects;

/** Typed failure at the filesystem draft persistence boundary. */
public final class DraftStoreException extends DomainFailureException {
    private static final long serialVersionUID = 1L;

    private final DraftStoreCode code;

    DraftStoreException(DraftStoreCode code, String message) {
        super(DomainErrorCode.from("draft", code), DomainErrorContext.empty(), message);
        this.code = Objects.requireNonNull(code, "code");
    }

    DraftStoreException(DraftStoreCode code, String message, Throwable cause) {
        super(DomainErrorCode.from("draft", code), DomainErrorContext.empty(), message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns the stable failure reason. */
    public DraftStoreCode code() {
        return code;
    }
}
