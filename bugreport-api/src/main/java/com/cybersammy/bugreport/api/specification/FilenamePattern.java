package com.cybersammy.bugreport.api.specification;

/**
 * Bounded non-recursive filename pattern using {@code *} and {@code ?} wildcards.
 *
 * @param value exact portable pattern
 */
public record FilenamePattern(String value) implements Comparable<FilenamePattern> {
    private static final int MAX_LENGTH = 128;

    /** Validates and creates a filename pattern. */
    public FilenamePattern {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_LENGTH
                || value.equals(".")
                || value.equals("..")
                || value.contains("**")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0
                || value.endsWith(".")
                || value.endsWith(" ")
                || value.chars().allMatch(character -> character == '*' || character == '?')) {
            throw invalidPattern();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || "<>\"|".indexOf(character) >= 0) {
                throw invalidPattern();
            }
        }
    }

    /**
     * Creates a validated filename pattern.
     *
     * @param value portable non-recursive pattern
     * @return validated pattern
     */
    public static FilenamePattern of(String value) {
        return new FilenamePattern(value);
    }

    private static IllegalArgumentException invalidPattern() {
        return new IllegalArgumentException(
                "Filename pattern must be bounded, portable, and non-recursive");
    }

    @Override
    public int compareTo(FilenamePattern other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
