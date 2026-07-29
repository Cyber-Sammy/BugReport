package com.cybersammy.bugreport.neoforge;

import java.util.Objects;

record ProviderDiagnostic(
        ProviderDiagnosticCode code,
        String ownerModId,
        String className,
        String providerId) {
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

    String logToken() {
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

    @Override
    public String toString() {
        return logToken();
    }
}
