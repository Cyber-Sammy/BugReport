package com.cybersammy.bugreport.core.sanitization;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable product-owned identifier for one text sanitization stage. */
public record SanitizationStageId(String value) implements Comparable<SanitizationStageId> {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public SanitizationStageId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Sanitization stage ID is not canonical");
        }
    }

    @Override
    public int compareTo(SanitizationStageId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
