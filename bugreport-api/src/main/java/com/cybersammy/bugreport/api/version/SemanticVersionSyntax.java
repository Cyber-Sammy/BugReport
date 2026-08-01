package com.cybersammy.bugreport.api.version;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared syntax validation for semantically distinct bounded version types. */
final class SemanticVersionSyntax {
    static final int MAX_LENGTH = 128;

    private static final BigInteger MAX_CORE_COMPONENT =
            BigInteger.valueOf(Integer.MAX_VALUE);
    private static final Pattern PATTERN =
            Pattern.compile(
                    "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                            + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    private SemanticVersionSyntax() {}

    static Components parse(String value, String domainName) {
        if (value == null || value.length() > MAX_LENGTH) {
            throw invalidVersion(domainName);
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches() || hasLeadingZeroNumericPreRelease(matcher.group(4))) {
            throw invalidVersion(domainName);
        }

        return new Components(
                value,
                parseCoreComponent(matcher.group(1), domainName),
                parseCoreComponent(matcher.group(2), domainName),
                parseCoreComponent(matcher.group(3), domainName),
                matcher.group(4),
                matcher.group(5));
    }

    private static int parseCoreComponent(String component, String domainName) {
        BigInteger parsed = new BigInteger(component);
        if (parsed.compareTo(MAX_CORE_COMPONENT) > 0) {
            throw invalidVersion(domainName);
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

    private static IllegalArgumentException invalidVersion(String domainName) {
        return new IllegalArgumentException(
                domainName
                        + " must be canonical Semantic Versioning text of at most "
                        + MAX_LENGTH
                        + " characters with numeric core components in 0.."
                        + Integer.MAX_VALUE);
    }

    record Components(
            String value,
            int major,
            int minor,
            int patch,
            String preRelease,
            String buildMetadata) {}
}
