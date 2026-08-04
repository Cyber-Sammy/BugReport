package com.cybersammy.bugreport.core.session;

import java.io.Serial;
import java.util.Objects;

/** Typed rejection of a draft that cannot safely resume as a live report session. */
public final class ReportSessionRecoveryException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ReportSessionRecoveryCode code;

    ReportSessionRecoveryException(ReportSessionRecoveryCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Returns the stable recovery rejection reason. */
    public ReportSessionRecoveryCode code() {
        return code;
    }
}
