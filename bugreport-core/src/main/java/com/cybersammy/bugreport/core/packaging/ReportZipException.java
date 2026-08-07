package com.cybersammy.bugreport.core.packaging;

import java.util.Objects;
import java.util.Optional;

/** Path-safe typed failure from the report ZIP boundary. */
public final class ReportZipException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ReportZipCode code;
    private final String archiveEntry;

    ReportZipException(
            ReportZipCode code, String archiveEntry, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.archiveEntry = archiveEntry;
    }

    public ReportZipCode code() {
        return code;
    }

    public Optional<String> archiveEntry() {
        return Optional.ofNullable(archiveEntry);
    }
}
