package com.cybersammy.bugreport.core.registry;

/** Stable reasons why a discovered provider was rejected by the Core registry. */
public enum ProviderRegistryDiagnosticCode {
    PROVIDER_ID_FAILED("id_call"),
    INVALID_PROVIDER_ID("invalid_id"),
    PROVIDER_ID_OWNERSHIP_MISMATCH("id_ownership"),
    PROVIDER_SPECIFICATION_FAILED("specification_call"),
    MISSING_PROVIDER_SPECIFICATION("specification_missing"),
    PROVIDER_SPECIFICATION_ID_MISMATCH("specification_id_mismatch"),
    PROVIDER_VERSION_FAILED("version_call"),
    PROVIDER_SPECIFICATION_VERSION_MISMATCH("specification_version_mismatch"),
    DUPLICATE_PROVIDER_ID("duplicate");

    private final String logToken;

    ProviderRegistryDiagnosticCode(String logToken) {
        this.logToken = logToken;
    }

    String logToken() {
        return logToken;
    }
}
