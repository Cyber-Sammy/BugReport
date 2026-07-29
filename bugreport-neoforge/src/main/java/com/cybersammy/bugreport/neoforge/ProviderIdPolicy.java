package com.cybersammy.bugreport.neoforge;

import java.util.regex.Pattern;

final class ProviderIdPolicy {
    private static final int MAX_COMPONENT_LENGTH = 64;
    private static final Pattern NAMESPACE =
            Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern LOCAL_NAME =
            Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private ProviderIdPolicy() {}

    static boolean isValidForOwner(String providerId, String ownerModId) {
        if (!isNamespace(ownerModId) || providerId == null) {
            return false;
        }

        int separator = providerId.indexOf(':');
        if (separator < 0) {
            return providerId.equals(ownerModId);
        }
        if (separator != providerId.lastIndexOf(':')) {
            return false;
        }

        String namespace = providerId.substring(0, separator);
        String localName = providerId.substring(separator + 1);
        return namespace.equals(ownerModId) && isLocalName(localName);
    }

    private static boolean isNamespace(String value) {
        return value != null
                && value.length() <= MAX_COMPONENT_LENGTH
                && NAMESPACE.matcher(value).matches();
    }

    private static boolean isLocalName(String value) {
        return value.length() <= MAX_COMPONENT_LENGTH
                && LOCAL_NAME.matcher(value).matches();
    }
}
