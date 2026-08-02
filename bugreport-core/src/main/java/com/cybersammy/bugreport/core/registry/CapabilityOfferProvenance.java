package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Objects;

/** Provider and version provenance for one rejected capability offer. */
public record CapabilityOfferProvenance(
        ProviderId providerId,
        CapabilityVersion version) {
    /** Validates offer provenance. */
    public CapabilityOfferProvenance {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(version, "version");
    }
}
