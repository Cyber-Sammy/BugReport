package com.cybersammy.bugreport.core.workspace;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical lowercase SHA-256 checksum. */
public record Sha256Checksum(String value) {
    private static final Pattern CANONICAL = Pattern.compile("[0-9a-f]{64}");

    /** Validates a canonical lowercase checksum. */
    public Sha256Checksum {
        Objects.requireNonNull(value, "value");
        if (!CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException("SHA-256 checksum must contain 64 lowercase hex digits");
        }
    }
}
