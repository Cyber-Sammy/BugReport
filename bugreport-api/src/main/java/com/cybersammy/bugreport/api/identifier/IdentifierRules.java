package com.cybersammy.bugreport.api.identifier;

import java.util.regex.Pattern;

final class IdentifierRules {
    private static final int MAX_COMPONENT_LENGTH = 64;
    private static final Pattern NAMESPACE =
            Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern LOCAL_NAME =
            Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private IdentifierRules() {}

    static String requireNamespace(IdentifierKind kind, String value) {
        if (value == null || !NAMESPACE.matcher(value).matches()) {
            throw invalid(
                    kind,
                    value,
                    "expected 2-64 lowercase ASCII letters, digits, or underscores, "
                            + "starting with a letter");
        }
        return value;
    }

    static String requireLocalName(IdentifierKind kind, String value) {
        if (value == null || !LOCAL_NAME.matcher(value).matches()) {
            throw invalid(
                    kind,
                    value,
                    "expected 1-64 lowercase ASCII letters, digits, or underscores, "
                            + "starting with a letter");
        }
        return value;
    }

    static String requireGlobal(IdentifierKind kind, String value) {
        if (value == null) {
            throw invalid(kind, null, "expected <namespace>:<local_name>");
        }

        int separator = value.indexOf(':');
        if (separator < 0 || separator != value.lastIndexOf(':')) {
            throw invalid(kind, value, "expected exactly one ':' separator");
        }

        requireNamespace(kind, value.substring(0, separator));
        requireLocalName(kind, value.substring(separator + 1));
        return value;
    }

    static String requireProvider(String value) {
        if (value == null) {
            throw invalid(IdentifierKind.PROVIDER, null, "expected a provider namespace");
        }

        int separator = value.indexOf(':');
        if (separator < 0) {
            return requireNamespace(IdentifierKind.PROVIDER, value);
        }
        return requireGlobal(IdentifierKind.PROVIDER, value);
    }

    static String namespaceOfGlobal(String value) {
        return value.substring(0, value.indexOf(':'));
    }

    static String localNameOfGlobal(String value) {
        return value.substring(value.indexOf(':') + 1);
    }

    private static InvalidIdentifierException invalid(
            IdentifierKind kind, String value, String requirement) {
        return new InvalidIdentifierException(kind, value, requirement);
    }
}
