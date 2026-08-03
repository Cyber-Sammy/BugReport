package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Builds the loader-neutral production provider registry. */
public final class ProviderRegistry {
    private static final Comparator<DiscoveredProvider> CANDIDATE_ORDER =
            Comparator.comparing(
                            (DiscoveredProvider candidate) ->
                                    candidate.ownerNamespace().value())
                    .thenComparing(DiscoveredProvider::implementationClass);

    private ProviderRegistry() {}

    /**
     * Validates discovered providers and returns an immutable deterministic
     * snapshot. Failure of one candidate does not stop evaluation of another.
     *
     * @param candidates provider instances with trusted loader provenance
     * @return accepted providers and rejection diagnostics
     */
    public static ProviderRegistrySnapshot createSnapshot(
            List<DiscoveredProvider> candidates) {
        return createSnapshot(candidates, Map.of());
    }

    /**
     * Validates providers and negotiates them against product-owned runtime
     * capabilities.
     *
     * @param candidates provider instances with trusted loader provenance
     * @param runtimeCapabilities product-owned capability versions
     * @return accepted providers, support states, and rejection diagnostics
     */
    public static ProviderRegistrySnapshot createSnapshot(
            List<DiscoveredProvider> candidates,
            Map<CapabilityId, CapabilityVersion> runtimeCapabilities) {
        Objects.requireNonNull(candidates, "candidates");
        Map<ProviderId, List<IdentifiedProvider>> candidatesById = new TreeMap<>();
        List<ProviderRegistryDiagnostic> diagnostics = new ArrayList<>();

        candidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "candidate"))
                .sorted(CANDIDATE_ORDER)
                .forEach(
                        candidate ->
                                identifyCandidate(candidate, candidatesById, diagnostics));

        List<ValidatedProvider> accepted = new ArrayList<>();
        candidatesById.forEach(
                (providerId, identifiedProviders) ->
                        resolveProviderId(
                                providerId,
                                identifiedProviders,
                                accepted,
                                diagnostics));
        diagnostics.sort(ProviderRegistryDiagnostic.CANONICAL_ORDER);
        return new ProviderRegistrySnapshot(
                CapabilityNegotiator.negotiate(accepted, runtimeCapabilities),
                diagnostics);
    }

    private static void identifyCandidate(
            DiscoveredProvider candidate,
            Map<ProviderId, List<IdentifiedProvider>> candidatesById,
            List<ProviderRegistryDiagnostic> diagnostics) {
        String returnedId;
        try {
            returnedId = candidate.provider().providerId();
        } catch (RuntimeException | LinkageError exception) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forCandidate(
                            ProviderRegistryDiagnosticCode.PROVIDER_ID_FAILED,
                            candidate));
            return;
        }

        ProviderId providerId;
        try {
            providerId = ProviderId.parse(returnedId);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(ProviderRegistryDiagnostic.invalidId(candidate, returnedId));
            return;
        }
        if (!providerId.isOwnedBy(candidate.ownerNamespace())) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forProvider(
                            ProviderRegistryDiagnosticCode.PROVIDER_ID_OWNERSHIP_MISMATCH,
                            candidate,
                            providerId));
            return;
        }

        candidatesById
                .computeIfAbsent(providerId, ignored -> new ArrayList<>())
                .add(new IdentifiedProvider(candidate, providerId));
    }

    private static void validateSpecification(
            IdentifiedProvider identifiedProvider,
            List<ValidatedProvider> accepted,
            List<ProviderRegistryDiagnostic> diagnostics) {
        DiscoveredProvider candidate = identifiedProvider.candidate();
        ProviderId providerId = identifiedProvider.id();

        Optional<ProviderSpecification> declaredSpecification;
        try {
            declaredSpecification = candidate.provider().specification();
        } catch (RuntimeException | LinkageError exception) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forProvider(
                            ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_FAILED,
                            candidate,
                            providerId));
            return;
        }
        if (declaredSpecification == null) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forProvider(
                            ProviderRegistryDiagnosticCode.NULL_PROVIDER_SPECIFICATION,
                            candidate,
                            providerId));
            return;
        }
        if (declaredSpecification.isEmpty()) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forProvider(
                            ProviderRegistryDiagnosticCode.MISSING_PROVIDER_SPECIFICATION,
                            candidate,
                            providerId));
            return;
        }

        ProviderSpecification specification = declaredSpecification.orElseThrow();
        if (!providerId.equals(specification.id())) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.mismatch(
                            ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_ID_MISMATCH,
                            candidate,
                            providerId,
                            providerId.value(),
                            specification.id().value()));
            return;
        }

        String returnedVersion;
        try {
            returnedVersion = candidate.provider().providerVersion();
        } catch (RuntimeException | LinkageError exception) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.forProvider(
                            ProviderRegistryDiagnosticCode.PROVIDER_VERSION_FAILED,
                            candidate,
                            providerId));
            return;
        }
        if (!specification.version().value().equals(returnedVersion)) {
            diagnostics.add(
                    ProviderRegistryDiagnostic.mismatch(
                            ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_VERSION_MISMATCH,
                            candidate,
                            providerId,
                            returnedVersion,
                            specification.version().value()));
            return;
        }

        accepted.add(
                new ValidatedProvider(
                        candidate.ownerNamespace(),
                        candidate.implementationClass(),
                        providerId,
                        candidate.provider(),
                        specification));
    }

    private static void resolveProviderId(
            ProviderId providerId,
            List<IdentifiedProvider> identifiedProviders,
            List<ValidatedProvider> accepted,
            List<ProviderRegistryDiagnostic> diagnostics) {
        if (identifiedProviders.size() == 1) {
            validateSpecification(identifiedProviders.getFirst(), accepted, diagnostics);
            return;
        }

        identifiedProviders.stream()
                .sorted(
                        Comparator.comparing(
                                        (IdentifiedProvider identified) ->
                                                identified.candidate()
                                                        .ownerNamespace()
                                                        .value())
                                .thenComparing(
                                        identified ->
                                                identified.candidate().implementationClass()))
                .map(
                        identified ->
                                new ProviderRegistryDiagnostic(
                                        ProviderRegistryDiagnosticCode.DUPLICATE_PROVIDER_ID,
                                        identified.candidate().ownerNamespace(),
                                        identified.candidate().implementationClass(),
                                        providerId,
                                        null,
                                        null))
                .forEach(diagnostics::add);
    }

    private record IdentifiedProvider(
            DiscoveredProvider candidate,
            ProviderId id) {}
}
