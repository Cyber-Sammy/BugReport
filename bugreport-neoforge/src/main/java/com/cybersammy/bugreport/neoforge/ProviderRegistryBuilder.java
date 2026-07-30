package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.neoforge.ProviderDiscoverySnapshot.ProviderRegistration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProviderRegistryBuilder {
    ProviderDiscoverySnapshot build(
            List<ProviderCandidate> candidates,
            List<ProviderDiagnostic> initialDiagnostics) {
        List<ProviderDiagnostic> diagnostics =
                new ArrayList<>(initialDiagnostics);
        Map<String, List<ProviderRegistration>> registrationsById =
                new HashMap<>();

        candidates.stream()
                .sorted(
                        Comparator.comparing(ProviderCandidate::ownerModId)
                                .thenComparing(ProviderCandidate::className))
                .forEach(
                        candidate ->
                                instantiateCandidate(
                                        candidate,
                                        registrationsById,
                                        diagnostics));

        List<ProviderRegistration> accepted = new ArrayList<>();
        registrationsById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                resolveProviderId(
                                        entry.getKey(),
                                        entry.getValue(),
                                        accepted,
                                        diagnostics));

        diagnostics.sort(Comparator.comparing(ProviderDiagnostic::logToken));
        return new ProviderDiscoverySnapshot(accepted, diagnostics);
    }

    private static void instantiateCandidate(
            ProviderCandidate candidate,
            Map<String, List<ProviderRegistration>> registrationsById,
            List<ProviderDiagnostic> diagnostics) {
        BugReportProvider provider;
        try {
            provider = candidate.constructor().newInstance();
        } catch (ReflectiveOperationException
                | RuntimeException
                | LinkageError exception) {
            diagnostics.add(
                    ProviderDiagnostic.forClass(
                            ProviderDiagnosticCode.INSTANTIATION_FAILED,
                            candidate.ownerModId(),
                            candidate.className()));
            return;
        }

        String providerId;
        try {
            providerId = provider.providerId();
        } catch (RuntimeException | LinkageError exception) {
            diagnostics.add(
                    ProviderDiagnostic.forClass(
                            ProviderDiagnosticCode.PROVIDER_ID_FAILED,
                            candidate.ownerModId(),
                            candidate.className()));
            return;
        }
        if (!ProviderIdPolicy.isValidForOwner(
                providerId,
                candidate.ownerModId())) {
            diagnostics.add(invalidProviderId(candidate, providerId));
            return;
        }

        registrationsById
                .computeIfAbsent(providerId, ignored -> new ArrayList<>())
                .add(
                        new ProviderRegistration(
                                candidate.ownerModId(),
                                providerId,
                                provider));
    }

    private static void resolveProviderId(
            String providerId,
            List<ProviderRegistration> registrations,
            List<ProviderRegistration> accepted,
            List<ProviderDiagnostic> diagnostics) {
        if (registrations.size() == 1) {
            accepted.add(registrations.getFirst());
            return;
        }

        registrations.stream()
                .sorted(
                        Comparator.comparing(ProviderRegistration::ownerModId)
                                .thenComparing(
                                        registration ->
                                                registration
                                                        .provider()
                                                        .getClass()
                                                        .getName()))
                .map(
                        registration ->
                                ProviderDiagnostic.forProvider(
                                        ProviderDiagnosticCode.DUPLICATE_PROVIDER_ID,
                                        registration.ownerModId(),
                                        registration.provider().getClass().getName(),
                                        providerId))
                .forEach(diagnostics::add);
    }

    private static ProviderDiagnostic invalidProviderId(
            ProviderCandidate candidate,
            String providerId) {
        return ProviderDiagnostic.forInvalidProviderId(
                candidate.ownerModId(),
                candidate.className(),
                providerId);
    }
}
