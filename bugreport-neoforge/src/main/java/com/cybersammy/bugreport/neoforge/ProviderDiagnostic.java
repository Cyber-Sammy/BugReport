package com.cybersammy.bugreport.neoforge;

import java.util.Objects;

record ProviderDiagnostic(
        ProviderDiagnosticCode code,
        String ownerModId,
        String className,
        String providerId) {
    private static final int MAX_LOGGED_PROVIDER_ID_LENGTH = 256;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    ProviderDiagnostic {
        Objects.requireNonNull(code);
        Objects.requireNonNull(ownerModId);
        Objects.requireNonNull(className);
    }

    static ProviderDiagnostic forClass(
            ProviderDiagnosticCode code,
            String ownerModId,
            String className) {
        return new ProviderDiagnostic(code, ownerModId, className, null);
    }

    static ProviderDiagnostic forProvider(
            ProviderDiagnosticCode code,
            String ownerModId,
            String className,
            String providerId) {
        return new ProviderDiagnostic(
                code,
                ownerModId,
                className,
                Objects.requireNonNull(providerId));
    }

    static ProviderDiagnostic forInvalidProviderId(
            String ownerModId,
            String className,
            String providerId) {
        return new ProviderDiagnostic(
                ProviderDiagnosticCode.INVALID_PROVIDER_ID,
                ownerModId,
                className,
                providerId);
    }

    String logToken() {
        if (code == ProviderDiagnosticCode.INVALID_PROVIDER_ID) {
            return code.logToken()
                    + "|"
                    + renderInvalidProviderId(providerId)
                    + "|"
                    + ownerModId
                    + "|"
                    + className;
        }
        if (providerId == null) {
            return code.logToken() + "|" + ownerModId + "|" + className;
        }
        return code.logToken()
                + "|"
                + providerId
                + "|"
                + ownerModId
                + "|"
                + className;
    }

    private static String renderInvalidProviderId(String value) {
        if (value == null) {
            return "<null>";
        }
        if (value.isEmpty()) {
            return "<empty>";
        }

        int renderedLength =
                Math.min(value.length(), MAX_LOGGED_PROVIDER_ID_LENGTH);
        StringBuilder rendered = new StringBuilder(renderedLength);
        for (int index = 0; index < renderedLength; index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '|') {
                rendered.append('\\').append(character);
            } else if (character >= 0x21 && character <= 0x7e) {
                rendered.append(character);
            } else {
                appendUnicodeEscape(rendered, character);
            }
        }
        if (renderedLength < value.length()) {
            rendered.append("...(length=").append(value.length()).append(')');
        }
        return rendered.toString();
    }

    private static void appendUnicodeEscape(
            StringBuilder target,
            char character) {
        target.append("\\u");
        target.append(HEX_DIGITS[(character >>> 12) & 0xf]);
        target.append(HEX_DIGITS[(character >>> 8) & 0xf]);
        target.append(HEX_DIGITS[(character >>> 4) & 0xf]);
        target.append(HEX_DIGITS[character & 0xf]);
    }

    @Override
    public String toString() {
        return logToken();
    }
}
