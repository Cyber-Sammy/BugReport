package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import java.util.Objects;

/** Provider instance paired with trusted provenance supplied by a loader adapter. */
public record DiscoveredProvider(
        NamespaceId ownerNamespace,
        String implementationClass,
        BugReportProvider provider) {
    /** Validates a discovered provider candidate. */
    public DiscoveredProvider {
        Objects.requireNonNull(ownerNamespace, "ownerNamespace");
        if (Objects.requireNonNull(implementationClass, "implementationClass").isBlank()) {
            throw new IllegalArgumentException("implementationClass must not be blank");
        }
        Objects.requireNonNull(provider, "provider");
    }
}
