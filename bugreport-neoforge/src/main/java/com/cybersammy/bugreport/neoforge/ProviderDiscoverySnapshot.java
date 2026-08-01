package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.List;
import java.util.Objects;

record ProviderDiscoverySnapshot(
        ProviderRegistrySnapshot registry,
        List<ProviderDiagnostic> discoveryDiagnostics) {
    ProviderDiscoverySnapshot {
        Objects.requireNonNull(registry);
        discoveryDiagnostics = List.copyOf(discoveryDiagnostics);
    }

    static ProviderDiscoverySnapshot empty() {
        return new ProviderDiscoverySnapshot(
                ProviderRegistrySnapshot.empty(),
                List.of());
    }

    List<String> providerIds() {
        return registry.providerIds().stream()
                .map(Object::toString)
                .toList();
    }
}
