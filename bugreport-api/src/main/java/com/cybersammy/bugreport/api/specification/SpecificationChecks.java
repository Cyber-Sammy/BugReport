package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.identifier.IdentifierCollisionException;
import com.cybersammy.bugreport.api.identifier.IdentifierKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class SpecificationChecks {
    private static final Pattern EMAIL_LOCAL_PART =
            Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+");

    private SpecificationChecks() {}

    static String requireSingleLine(String value, String name, int maxLength) {
        if (value == null
                || value.isBlank()
                || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    name + " must be non-blank, single-line text of at most " + maxLength + " characters");
        }
        return value;
    }

    static <E extends Enum<E>> Set<E> copyNonEmptyEnumSet(
            Set<E> values, Class<E> enumType, String name) {
        Objects.requireNonNull(values, name);
        EnumSet<E> copy = EnumSet.noneOf(enumType);
        copy.addAll(values);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Collections.unmodifiableSet(copy);
    }

    static <K, V> void putUnique(
            Map<K, V> values,
            K key,
            V value,
            IdentifierKind kind,
            String canonicalValue,
            int maximum) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (values.containsKey(key)) {
            throw new IdentifierCollisionException(kind, canonicalValue);
        }
        if (values.size() >= maximum) {
            throw new IllegalArgumentException(
                    kind + " declarations exceed maximum " + maximum);
        }
        values.put(key, value);
    }

    static String requireHttpsUrl(String value) {
        if (value == null || value.length() > 2_048 || !value.startsWith("https://")) {
            throw invalidHttpsUrl();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7E || character == '\\' || character == '#') {
                throw invalidHttpsUrl();
            }
        }

        int authorityStart = "https://".length();
        int pathStart = firstIndexOf(value, authorityStart, '/', '?');
        String authority = value.substring(
                authorityStart, pathStart < 0 ? value.length() : pathStart);
        if (authority.isEmpty() || authority.indexOf('@') >= 0) {
            throw invalidHttpsUrl();
        }

        String host = authority;
        int portSeparator = authority.lastIndexOf(':');
        if (portSeparator >= 0) {
            if (portSeparator != authority.indexOf(':')) {
                throw invalidHttpsUrl();
            }
            host = authority.substring(0, portSeparator);
            String port = authority.substring(portSeparator + 1);
            if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) {
                throw invalidHttpsUrl();
            }
            try {
                int parsedPort = Integer.parseInt(port);
                if (parsedPort < 1 || parsedPort > 65_535) {
                    throw invalidHttpsUrl();
                }
            } catch (NumberFormatException exception) {
                throw invalidHttpsUrl();
            }
        }
        requirePublicDnsName(host, "HTTPS URL");
        return value;
    }

    static String requireEmailAddress(String value) {
        if (value == null || value.length() > 254 || value.chars().anyMatch(Character::isISOControl)) {
            throw invalidEmailAddress();
        }
        int separator = value.indexOf('@');
        if (separator <= 0 || separator != value.lastIndexOf('@')) {
            throw invalidEmailAddress();
        }
        String localPart = value.substring(0, separator);
        if (localPart.length() > 64
                || localPart.startsWith(".")
                || localPart.endsWith(".")
                || localPart.contains("..")
                || !EMAIL_LOCAL_PART.matcher(localPart).matches()) {
            throw invalidEmailAddress();
        }
        try {
            requirePublicDnsName(value.substring(separator + 1), "email address");
        } catch (IllegalArgumentException exception) {
            throw invalidEmailAddress();
        }
        return value;
    }

    private static int firstIndexOf(String value, int start, char first, char second) {
        int firstIndex = value.indexOf(first, start);
        int secondIndex = value.indexOf(second, start);
        if (firstIndex < 0) {
            return secondIndex;
        }
        if (secondIndex < 0) {
            return firstIndex;
        }
        return Math.min(firstIndex, secondIndex);
    }

    private static void requireDnsName(String value, String context) {
        if (value.isEmpty() || value.length() > 253 || value.startsWith(".") || value.endsWith(".")) {
            throw new IllegalArgumentException(context + " must contain a valid ASCII DNS name");
        }
        for (String label : value.split("\\.", -1)) {
            if (label.isEmpty()
                    || label.length() > 63
                    || !isAsciiLetterOrDigit(label.charAt(0))
                    || !isAsciiLetterOrDigit(label.charAt(label.length() - 1))) {
                throw new IllegalArgumentException(context + " must contain a valid ASCII DNS name");
            }
            for (int index = 1; index < label.length() - 1; index++) {
                char character = label.charAt(index);
                if (!isAsciiLetterOrDigit(character) && character != '-') {
                    throw new IllegalArgumentException(context + " must contain a valid ASCII DNS name");
                }
            }
        }
    }

    private static void requirePublicDnsName(String value, String context) {
        requireDnsName(value, context);
        int finalSeparator = value.lastIndexOf('.');
        if (finalSeparator <= 0
                || value.substring(finalSeparator + 1).chars().noneMatch(
                        character -> (character >= 'a' && character <= 'z')
                                || (character >= 'A' && character <= 'Z'))) {
            throw new IllegalArgumentException(
                    context + " must contain a public ASCII DNS name");
        }
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9';
    }

    private static IllegalArgumentException invalidHttpsUrl() {
        return new IllegalArgumentException(
                "HTTPS URL must be bounded ASCII without credentials, fragments, or invalid authority");
    }

    private static IllegalArgumentException invalidEmailAddress() {
        return new IllegalArgumentException("Email address must be a canonical bounded ASCII address");
    }
}
