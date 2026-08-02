package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reject-all collision in the global capability offer scope. */
public record CapabilityOfferCollision(
        CapabilityId capabilityId,
        List<CapabilityOfferProvenance> providerOffers,
        Optional<CapabilityVersion> runtimeVersion) {
    /** Canonicalizes and defensively copies collision provenance. */
    public CapabilityOfferCollision {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        providerOffers = Objects.requireNonNull(providerOffers, "providerOffers").stream()
                .map(offer -> Objects.requireNonNull(offer, "provider offer"))
                .sorted(Comparator.comparing(CapabilityOfferProvenance::providerId))
                .toList();
        if (providerOffers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A capability collision must include at least one provider");
        }
        long uniqueProviders = providerOffers.stream()
                .map(CapabilityOfferProvenance::providerId)
                .distinct()
                .count();
        if (uniqueProviders != providerOffers.size()) {
            throw new IllegalArgumentException(
                    "A capability collision cannot repeat a provider ID");
        }
        if (runtimeVersion.isEmpty() && uniqueProviders < 2) {
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
