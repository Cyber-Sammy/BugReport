package com.cybersammy.bugreport.api.specification;

import java.util.Locale;
import java.util.Set;

/**
 * Portable forward-slash-separated path relative to one approved logical root.
 *
 * <p>This value performs lexical declaration validation only. Core and the
 * platform adapter still enforce containment, link, file-type, and race-safe
 * read policy at planning and collection time.
 *
 * @param value exact portable relative path
 */
public record RelativePath(String value) implements Comparable<RelativePath> {
    private static final int MAX_LENGTH = 512;
    private static final int MAX_SEGMENTS = 16;
    private static final int MAX_SEGMENT_LENGTH = 128;
    private static final Set<String> RESERVED_NAMES =
            Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
                    "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4",
                    "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    /** Validates and creates a relative path. */
    public RelativePath {
        if (value == null
                || value.isEmpty()
                || value.length() > MAX_LENGTH
                || value.startsWith("/")
                || value.endsWith("/")
                || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0) {
            throw invalidPath();
        }
        String[] segments = value.split("/", -1);
        if (segments.length > MAX_SEGMENTS) {
            throw invalidPath();
        }
        for (String segment : segments) {
            validateSegment(segment);
        }
    }

    /**
     * Creates a validated relative path.
     *
     * @param value portable relative path
     * @return validated path
     */
    public static RelativePath of(String value) {
        return new RelativePath(value);
    }

    private static void validateSegment(String segment) {
        if (segment.isEmpty()
                || segment.equals(".")
                || segment.equals("..")
                || segment.length() > MAX_SEGMENT_LENGTH
                || segment.endsWith(".")
                || segment.endsWith(" ")) {
            throw invalidPath();
        }
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (Character.isISOControl(character) || "<>\"|?*".indexOf(character) >= 0) {
                throw invalidPath();
            }
        }
        String baseName = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (RESERVED_NAMES.contains(baseName)) {
            throw invalidPath();
        }
    }

    private static IllegalArgumentException invalidPath() {
        return new IllegalArgumentException(
                "Relative path must be a bounded portable path below an approved root");
    }

    @Override
    public int compareTo(RelativePath other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
