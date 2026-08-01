package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministically ordered immutable result of one provider registration pass. */
public final class ProviderRegistrySnapshot {
    private final List<RegisteredProvider> providers;
    private final List<ProviderRegistryDiagnostic> diagnostics;

    ProviderRegistrySnapshot(
            List<RegisteredProvider> providers,
            List<ProviderRegistryDiagnostic> diagnostics) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Returns an empty registry snapshot. */
    public static ProviderRegistrySnapshot empty() {
        return new ProviderRegistrySnapshot(List.of(), List.of());
    }

    /** Returns accepted providers in canonical ID order. */
    public List<RegisteredProvider> providers() {
        return providers;
    }

    /** Returns diagnostics in stable field order. */
    public List<ProviderRegistryDiagnostic> diagnostics() {
        return diagnostics;
    }

    /** Returns accepted provider IDs in canonical order. */
    public List<ProviderId> providerIds() {
        return providers.stream().map(RegisteredProvider::id).toList();
    }

    /** Finds an accepted provider by its canonical ID. */
    public Optional<RegisteredProvider> find(ProviderId id) {
        Objects.requireNonNull(id, "id");
        return providers.stream().filter(provider -> provider.id().equals(id)).findFirst();
    }
}
