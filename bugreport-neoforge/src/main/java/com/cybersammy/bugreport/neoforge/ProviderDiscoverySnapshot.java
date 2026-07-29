package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.util.List;
import java.util.Objects;

record ProviderDiscoverySnapshot(
        List<ProviderRegistration> providers,
        List<String> diagnostics) {
    ProviderDiscoverySnapshot {
        providers = List.copyOf(providers);
        diagnostics = List.copyOf(diagnostics);
    }

    List<String> providerIds() {
        return providers.stream()
                .map(ProviderRegistration::providerId)
                .toList();
    }

    record ProviderRegistration(
            String ownerModId,
            String providerId,
            BugReportProvider provider) {
        ProviderRegistration {
            Objects.requireNonNull(ownerModId);
            Objects.requireNonNull(providerId);
            Objects.requireNonNull(provider);
        }
    }
}
