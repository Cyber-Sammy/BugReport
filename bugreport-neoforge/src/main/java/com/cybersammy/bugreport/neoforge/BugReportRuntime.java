package com.cybersammy.bugreport.neoforge;

import java.util.Objects;
import java.util.function.Supplier;

final class BugReportRuntime {
    private final Supplier<ProviderDiscoverySnapshot> providerDiscovery;
    private ProviderDiscoverySnapshot providerSnapshot =
            ProviderDiscoverySnapshot.empty();
    private boolean providersInitialized;

    BugReportRuntime() {
        this(NeoForgeProviderDiscovery::discover);
    }

    BugReportRuntime(
            Supplier<ProviderDiscoverySnapshot> providerDiscovery) {
        this.providerDiscovery = Objects.requireNonNull(providerDiscovery);
    }

    synchronized void initializeProviders() {
        if (providersInitialized) {
            throw new IllegalStateException("Bug Report providers are already initialized");
        }
        providersInitialized = true;
        providerSnapshot = Objects.requireNonNull(providerDiscovery.get());
    }

    synchronized ProviderDiscoverySnapshot providers() {
        return providerSnapshot;
    }
}
