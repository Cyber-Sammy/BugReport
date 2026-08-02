package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reject-all collision in the global capability offer scope. */
public record CapabilityOfferCollision(
        CapabilityId capabilityId,
        List<CapabilityOfferProvenance> providerOffers,
        Optional<CapabilityVersion> runtimeVersion) {
    /** Defensively copies collision provenance. */
    public CapabilityOfferCollision {
        Objects.requireNonNull(capabilityId, "capabilityId");
        providerOffers = List.copyOf(
                Objects.requireNonNull(providerOffers, "providerOffers"));
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        if (providerOffers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A capability collision must include at least one provider");
        }
        if (runtimeVersion.isEmpty() && providerOffers.size() < 2) {
            throw new IllegalArgumentException(
                    "A provider-only capability collision requires at least two providers");
        }
    }

    /** Returns colliding provider IDs in canonical order. */
    public List<ProviderId> providerIds() {
        return providerOffers.stream()
                .map(CapabilityOfferProvenance::providerId)
                .toList();
    }
}
