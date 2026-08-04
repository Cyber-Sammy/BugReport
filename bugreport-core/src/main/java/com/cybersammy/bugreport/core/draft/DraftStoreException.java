package com.cybersammy.bugreport.core.draft;

import java.util.Objects;

/** Typed failure at the filesystem draft persistence boundary. */
public final class DraftStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final DraftStoreCode code;

    DraftStoreException(DraftStoreCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    DraftStoreException(DraftStoreCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns the stable failure reason. */
    public DraftStoreCode code() {
        return code;
    }
}
