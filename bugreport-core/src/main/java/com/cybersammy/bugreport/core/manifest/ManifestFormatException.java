package com.cybersammy.bugreport.core.manifest;

import java.util.Objects;

/** Typed safe rejection of an untrusted encoded report manifest. */
public final class ManifestFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ManifestFormatCode code;

    public ManifestFormatException(ManifestFormatCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ManifestFormatException(
            ManifestFormatCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ManifestFormatCode code() {
        return code;
    }
}
