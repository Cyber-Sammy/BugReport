package com.cybersammy.bugreport.core.session;

import java.util.Objects;
import java.util.UUID;

/** Stable UUID identity assigned to one report session. */
public record ReportSessionId(UUID value) {
    /** Validates a report session identity. */
    public ReportSessionId {
        Objects.requireNonNull(value, "value");
    }

    /** Creates a new random session identity. */
    public static ReportSessionId random() {
        return new ReportSessionId(UUID.randomUUID());
    }

    /** Parses the canonical lowercase UUID representation. */
    public static ReportSessionId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Report session ID must be a canonical UUID");
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Report session ID must be a canonical UUID",
                    exception);
        }
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Report session ID must be a canonical UUID");
        }
        return new ReportSessionId(parsed);
    }

    /** Returns the canonical lowercase UUID representation. */
    @Override
    public String toString() {
        return value.toString();
    }
}
