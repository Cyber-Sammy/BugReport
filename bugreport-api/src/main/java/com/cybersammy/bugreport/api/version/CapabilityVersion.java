package com.cybersammy.bugreport.api.version;

/**
 * Independent {@code major.minor} version of a capability contract.
 *
 * @param major incompatible capability generation
 * @param minor backward-compatible capability revision
 */
public record CapabilityVersion(int major, int minor)
        implements Comparable<CapabilityVersion> {
    /** Validates and creates a capability version. */
    public CapabilityVersion {
        requireNonNegative(major, "major");
        requireNonNegative(minor, "minor");
    }

    /**
     * Parses canonical {@code major.minor} text.
     *
     * @param value exact version text
     * @return parsed capability version
     */
    public static CapabilityVersion parse(String value) {
        int[] components = MajorMinorVersion.parse(value, "Capability");
        return new CapabilityVersion(components[0], components[1]);
    }

    private static void requireNonNegative(int value, String component) {
        if (value < 0) {
            throw new IllegalArgumentException(component + " version must be non-negative");
        }
    }

    @Override
    public int compareTo(CapabilityVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
