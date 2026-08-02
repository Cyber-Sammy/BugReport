package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Objects;

/** Provider accepted with a validated M1 specification and trusted provenance. */
public record RegisteredProvider(
        NamespaceId ownerNamespace,
        String implementationClass,
        ProviderId id,
        BugReportProvider provider,
        ProviderSpecification specification,
        ProviderSupport support) {
    /** Validates an accepted provider registration. */
    public RegisteredProvider {
        Objects.requireNonNull(ownerNamespace, "ownerNamespace");
        Objects.requireNonNull(implementationClass, "implementationClass");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(support, "support");
        if (!id.equals(specification.id())) {
            throw new IllegalArgumentException("Registration ID must match specification ID");
        }
    }
}
