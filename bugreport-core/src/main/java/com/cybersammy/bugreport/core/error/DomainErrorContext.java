package com.cybersammy.bugreport.core.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded, allow-listed context for structured logs.
 *
 * <p>It deliberately accepts identifiers only. Paths, text payloads, exception messages, and raw
 * provider data must not be added here.
 */
public final class DomainErrorContext implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final DomainErrorContext EMPTY =
            new DomainErrorContext(new EnumMap<>(DomainErrorContextKey.class));

    private final EnumMap<DomainErrorContextKey, String> values;

    private DomainErrorContext(Map<DomainErrorContextKey, String> values) {
        this.values = new EnumMap<>(DomainErrorContextKey.class);
        this.values.putAll(values);
    }

    /** Returns an empty context. */
    public static DomainErrorContext empty() {
        return EMPTY;
    }

    /** Starts a context builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the immutable structured fields. */
    public Map<DomainErrorContextKey, String> values() {
        return Map.copyOf(values);
    }

    /** Renders stable fields for a structured logger without retaining arbitrary error messages. */
    public String logToken() {
        return values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().ordinal()))
                .map(entry -> entry.getKey().logName() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }

    /** Builder for an allow-listed, identifier-only context. */
    public static final class Builder {
        private final EnumMap<DomainErrorContextKey, String> values =
                new EnumMap<>(DomainErrorContextKey.class);

        private Builder() {}

        /** Adds one canonical identifier. Each context key may appear only once. */
        public Builder put(DomainErrorContextKey key, String value) {
            key = Objects.requireNonNull(key, "key");
            value = Objects.requireNonNull(value, "value");
            if (!VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException("Domain error context value is not log-safe");
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Domain error context key is already present: " + key);
            }
            return this;
        }

        /** Builds an immutable context. */
        public DomainErrorContext build() {
            return values.isEmpty() ? EMPTY : new DomainErrorContext(values);
        }
    }
}
