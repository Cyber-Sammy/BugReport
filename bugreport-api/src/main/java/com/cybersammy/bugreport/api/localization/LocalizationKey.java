package com.cybersammy.bugreport.api.localization;

import java.util.regex.Pattern;

/**
 * Loader-neutral key for localized display text.
 *
 * <p>Keys contain lowercase ASCII dot-separated segments. Display text itself
 * remains in the integrating mod's localization resources and is never used
 * as identity.
 *
 * @param value canonical localization key
 */
public record LocalizationKey(String value) implements Comparable<LocalizationKey> {
    private static final int MAX_LENGTH = 255;
    private static final Pattern PATTERN =
            Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    /** Validates and creates a localization key. */
    public LocalizationKey {
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Localization key must contain lowercase ASCII dot-separated segments "
                            + "and be at most "
                            + MAX_LENGTH
                            + " characters");
        }
    }

    /**
     * Creates a localization key.
     *
     * @param value canonical key
     * @return validated key
     */
    public static LocalizationKey of(String value) {
        return new LocalizationKey(value);
    }

    @Override
    public int compareTo(LocalizationKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
