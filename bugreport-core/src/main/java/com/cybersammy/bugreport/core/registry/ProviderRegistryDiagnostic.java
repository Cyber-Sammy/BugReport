package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.diagnostic.DiagnosticLogValue;
import java.util.Comparator;
import java.util.Objects;

/** Immutable structured diagnostic for one rejected provider candidate. */
public record ProviderRegistryDiagnostic(
        ProviderRegistryDiagnosticCode code,
        NamespaceId ownerNamespace,
        String implementationClass,
        ProviderId providerId,
        String bridgeValue,
        String specificationValue) {
    static final Comparator<ProviderRegistryDiagnostic> CANONICAL_ORDER =
            Comparator.comparing(
                            (ProviderRegistryDiagnostic diagnostic) -> diagnostic.code().name())
                    .thenComparing(diagnostic -> diagnostic.ownerNamespace().value())
                    .thenComparing(ProviderRegistryDiagnostic::implementationClass)
                    .thenComparing(
                            diagnostic -> valueOrEmpty(diagnostic.providerId()))
                    .thenComparing(
                            diagnostic -> valueOrEmpty(diagnostic.bridgeValue()))
                    .thenComparing(
                            diagnostic -> valueOrEmpty(diagnostic.specificationValue()));

    /** Validates a registry diagnostic. */
    public ProviderRegistryDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(ownerNamespace, "ownerNamespace");
        Objects.requireNonNull(implementationClass, "implementationClass");
    }

    static ProviderRegistryDiagnostic forCandidate(
            ProviderRegistryDiagnosticCode code,
            DiscoveredProvider candidate) {
        return new ProviderRegistryDiagnostic(
                code,
                candidate.ownerNamespace(),
                candidate.implementationClass(),
                null,
                null,
                null);
    }

    static ProviderRegistryDiagnostic invalidId(
            DiscoveredProvider candidate,
            String returnedId) {
        return new ProviderRegistryDiagnostic(
                ProviderRegistryDiagnosticCode.INVALID_PROVIDER_ID,
                candidate.ownerNamespace(),
                candidate.implementationClass(),
                null,
                returnedId,
                null);
    }

    static ProviderRegistryDiagnostic forProvider(
            ProviderRegistryDiagnosticCode code,
            DiscoveredProvider candidate,
            ProviderId providerId) {
        return new ProviderRegistryDiagnostic(
                code,
                candidate.ownerNamespace(),
                candidate.implementationClass(),
                providerId,
                null,
                null);
    }

    static ProviderRegistryDiagnostic mismatch(
            ProviderRegistryDiagnosticCode code,
            DiscoveredProvider candidate,
            ProviderId providerId,
            String bridgeValue,
            String specificationValue) {
        return new ProviderRegistryDiagnostic(
                code,
                candidate.ownerNamespace(),
                candidate.implementationClass(),
                providerId,
                bridgeValue,
                specificationValue);
    }

    /** Returns a bounded, injection-safe token for structured logging. */
    public String logToken() {
        return code.logToken()
                + "|owner="
                + DiagnosticLogValue.render(ownerNamespace.value())
                + "|class="
                + DiagnosticLogValue.render(implementationClass)
                + "|provider="
                + DiagnosticLogValue.render(providerId == null ? null : providerId.value())
                + "|bridge="
                + DiagnosticLogValue.render(bridgeValue)
                + "|specification="
                + DiagnosticLogValue.render(specificationValue);
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String toString() {
        return logToken();
    }
}
