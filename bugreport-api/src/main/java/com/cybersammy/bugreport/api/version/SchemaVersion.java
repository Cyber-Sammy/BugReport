package com.cybersammy.bugreport.api.version;

/**
 * Independent {@code major.minor} version of a persisted Bug Report schema.
 *
 * @param major incompatible schema generation
 * @param minor backward-compatible schema revision
 */
public record SchemaVersion(int major, int minor) implements Comparable<SchemaVersion> {
    /** Validates and creates a schema version. */
    public SchemaVersion {
        requireNonNegative(major, "major");
        requireNonNegative(minor, "minor");
    }

    /**
     * Parses canonical {@code major.minor} text.
     *
     * @param value exact version text
     * @return parsed schema version
     */
    public static SchemaVersion parse(String value) {
        int[] components = MajorMinorVersion.parse(value, "Schema");
        return new SchemaVersion(components[0], components[1]);
    }

    private static void requireNonNegative(int value, String component) {
        if (value < 0) {
            throw new IllegalArgumentException(component + " version must be non-negative");
        }
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
