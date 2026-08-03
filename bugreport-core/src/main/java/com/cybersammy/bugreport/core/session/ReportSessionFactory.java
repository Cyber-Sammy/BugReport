package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Objects;

/** Creates report sessions only from providers accepted by one immutable registry snapshot. */
public final class ReportSessionFactory {
    private final ProviderRegistrySnapshot registry;

    /** Binds session creation to a trusted immutable registry result. */
    public ReportSessionFactory(ProviderRegistrySnapshot registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** Creates a session for an accepted enabled or partially supported provider. */
    public ReportSession create(ReportSessionId sessionId, ProviderId providerId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(providerId, "providerId");
        RegisteredProvider provider = registry.find(providerId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Cannot create a report session for an unregistered provider: "
                                                + providerId));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "Cannot create a report session for a disabled provider: " + providerId);
        }
        return new ReportSession(sessionId, provider);
    }
}
