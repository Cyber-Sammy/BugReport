package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.validation.ValidationPath;
import java.util.Objects;

/** Stable reasons why a discovered provider was rejected by the Core registry. */
public enum ProviderRegistryDiagnosticCode {
    PROVIDER_ID_FAILED(
            "id_call",
            ValidationPath.root().property("providerId"),
            "Calling providerId() failed."),
    INVALID_PROVIDER_ID(
            "invalid_id",
            ValidationPath.root().property("providerId"),
            "Provider ID is not a valid canonical provider identifier."),
    PROVIDER_ID_OWNERSHIP_MISMATCH(
            "id_ownership",
            ValidationPath.root().property("providerId"),
            "Provider ID is outside the declaring module namespace."),
    PROVIDER_SPECIFICATION_FAILED(
            "specification_call",
            ValidationPath.root().property("specification"),
            "Calling specification() failed."),
    NULL_PROVIDER_SPECIFICATION(
            "specification_null",
            ValidationPath.root().property("specification"),
            "specification() returned null instead of Optional."),
    MISSING_PROVIDER_SPECIFICATION(
            "specification_missing",
            ValidationPath.root().property("specification"),
            "Provider does not declare an M1 specification."),
    PROVIDER_SPECIFICATION_ID_MISMATCH(
            "specification_id_mismatch",
            ValidationPath.root().property("specification").property("id"),
            "Specification ID does not match providerId()."),
    PROVIDER_VERSION_FAILED(
            "version_call",
            ValidationPath.root().property("providerVersion"),
            "Calling providerVersion() failed."),
    PROVIDER_SPECIFICATION_VERSION_MISMATCH(
            "specification_version_mismatch",
            ValidationPath.root().property("specification").property("version"),
            "Specification version does not match providerVersion()."),
    DUPLICATE_PROVIDER_ID(
            "duplicate",
            ValidationPath.root().property("providerId"),
            "Provider ID is declared by multiple provider candidates.");

    private final String logToken;
    private final ValidationPath validationPath;
    private final String message;

    ProviderRegistryDiagnosticCode(
            String logToken,
            ValidationPath validationPath,
            String message) {
        this.logToken = logToken;
        this.validationPath = Objects.requireNonNull(validationPath, "validationPath");
        this.message = Objects.requireNonNull(message, "message");
    }

    String logToken() {
        return logToken;
    }

    ValidationPath validationPath() {
        return validationPath;
    }

    String message() {
        return message;
    }
}
