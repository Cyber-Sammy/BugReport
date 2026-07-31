package com.cybersammy.bugreport.api.version;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic version identifier for the Bug Report Java API artifact.
 *
 * <p>This value validates bounded Semantic Versioning syntax. Each numeric core
 * component is restricted to {@code 0..2147483647} so it can be exposed as an
 * {@code int}. Equality represents exact canonical version text, including
 * build metadata; it does not represent SemVer precedence or API compatibility.
 * Compatibility is established by the published API baseline and executable
 * linkage fixtures, not by parsing or comparing a version string alone.
 */
public final class ApiVersion {
    private static final int MAX_LENGTH = 128;
    private static final BigInteger MAX_CORE_COMPONENT =
            BigInteger.valueOf(Integer.MAX_VALUE);
    private static final Pattern PATTERN =
            Pattern.compile(
                    "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                            + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    private final String value;
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    private ApiVersion(
            String value,
            int major,
            int minor,
            int patch,
            String preRelease,
            String buildMetadata) {
        this.value = value;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.buildMetadata = buildMetadata;
    }

    /**
     * Parses a bounded Semantic Versioning identifier.
     *
     * @param value exact version string
     * @return parsed API version
     * @throws IllegalArgumentException when the value is not canonical SemVer
     */
    public static ApiVersion parse(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            throw invalidVersion();
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches() || hasLeadingZeroNumericPreRelease(matcher.group(4))) {
            throw invalidVersion();
        }

        return new ApiVersion(
                value,
                parseCoreComponent(matcher.group(1)),
                parseCoreComponent(matcher.group(2)),
                parseCoreComponent(matcher.group(3)),
                matcher.group(4),
                matcher.group(5));
    }

    private static int parseCoreComponent(String component) {
        BigInteger parsed = new BigInteger(component);
        if (parsed.compareTo(MAX_CORE_COMPONENT) > 0) {
            throw invalidVersion();
        }
        return parsed.intValueExact();
    }

    private static boolean hasLeadingZeroNumericPreRelease(String preRelease) {
        if (preRelease == null) {
            return false;
        }
        for (String identifier : preRelease.split("\\.")) {
            if (identifier.length() > 1
                    && identifier.charAt(0) == '0'
                    && identifier.chars().allMatch(Character::isDigit)) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException invalidVersion() {
        return new IllegalArgumentException(
                "API version must be canonical Semantic Versioning text of at most "
                        + MAX_LENGTH
                        + " characters with numeric core components in 0.."
                        + Integer.MAX_VALUE);
    }

    /**
     * Returns the major component.
     *
     * @return major version
     */
    public int major() {
        return major;
    }

    /**
     * Returns the minor component.
     *
     * @return minor version
     */
    public int minor() {
        return minor;
    }

    /**
     * Returns the patch component.
     *
     * @return patch version
     */
    public int patch() {
        return patch;
    }

    /**
     * Returns the optional prerelease identifier.
     *
     * @return prerelease text
     */
    public Optional<String> preRelease() {
        return Optional.ofNullable(preRelease);
    }

    /**
     * Returns the optional build metadata.
     *
     * @return build metadata
     */
    public Optional<String> buildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    /**
     * Returns the canonical version text.
     *
     * @return version text
     */
    public String value() {
        return value;
    }

    /**
     * Compares exact canonical text, including prerelease and build metadata.
     *
     * <p>This is identity equality, not SemVer precedence or API compatibility.
     *
     * @param other value to compare
     * @return {@code true} when the complete canonical text is identical
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ApiVersion version && value.equals(version.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
