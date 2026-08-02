package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class CapabilityNegotiator {
    private CapabilityNegotiator() {}

    static List<RegisteredProvider> negotiate(
            List<ValidatedProvider> providers,
            Map<CapabilityId, CapabilityVersion> runtimeCapabilities) {
        Map<CapabilityId, CapabilityVersion> validatedRuntime =
                validateRuntimeCapabilities(runtimeCapabilities);
        CollisionIndex collisions = findOfferCollisions(providers, validatedRuntime);
        Set<ProviderId> activeProviderIds = new LinkedHashSet<>();
        providers.stream()
                .filter(provider -> !collisions.byProvider().containsKey(provider.id()))
                .map(ValidatedProvider::id)
                .forEach(activeProviderIds::add);

        while (true) {
            Map<CapabilityId, CapabilityVersion> available =
                    buildAvailableCapabilities(
                            providers,
                            validatedRuntime,
                            collisions.capabilityIds(),
                            activeProviderIds);
            Map<ProviderId, ProviderSupport> supportByProvider =
                    evaluateProviders(providers, available, collisions.byProvider());
            Set<ProviderId> nextActiveProviderIds = new LinkedHashSet<>();
            activeProviderIds.stream()
                    .filter(
                            providerId ->
                                    supportByProvider.get(providerId).state()
                                            != ProviderSupportState.DISABLED)
                    .forEach(nextActiveProviderIds::add);

            if (nextActiveProviderIds.equals(activeProviderIds)) {
                return providers.stream()
                        .map(
                                provider ->
                                        register(
                                                provider,
                                                supportByProvider.get(provider.id())))
                        .toList();
            }
            activeProviderIds = nextActiveProviderIds;
        }
    }

    private static Map<CapabilityId, CapabilityVersion> validateRuntimeCapabilities(
            Map<CapabilityId, CapabilityVersion> runtimeCapabilities) {
        Objects.requireNonNull(runtimeCapabilities, "runtimeCapabilities");
        Map<CapabilityId, CapabilityVersion> validated = new TreeMap<>();
        runtimeCapabilities.forEach(
                (id, version) ->
                        validated.put(
                                Objects.requireNonNull(id, "runtime capability ID"),
                                Objects.requireNonNull(
                                        version,
                                        "runtime capability version")));
        return Map.copyOf(validated);
    }

    private static CollisionIndex findOfferCollisions(
            List<ValidatedProvider> providers,
            Map<CapabilityId, CapabilityVersion> runtimeCapabilities) {
        Map<CapabilityId, List<ValidatedProvider>> providersByCapability = new TreeMap<>();
        providers.forEach(
                provider ->
                        provider.specification().capabilityOffers().values().forEach(
                                offer ->
                                        providersByCapability
                                                .computeIfAbsent(
                                                        offer.id(),
                                                        ignored -> new ArrayList<>())
                                                .add(provider)));

        Set<CapabilityId> collisionIds = new HashSet<>();
        Map<ProviderId, List<CapabilityOfferCollision>> collisionsByProvider = new HashMap<>();
        providersByCapability.forEach(
                (capabilityId, offeringProviders) -> {
                    CapabilityVersion runtimeVersion = runtimeCapabilities.get(capabilityId);
                    if (offeringProviders.size() == 1 && runtimeVersion == null) {
                        return;
                    }

                    List<CapabilityOfferProvenance> providerOffers =
                            offeringProviders.stream()
                                    .map(
                                            provider ->
                                                    new CapabilityOfferProvenance(
                                                            provider.id(),
                                                            provider.specification()
                                                                    .capabilityOffers()
                                                                    .get(capabilityId)
                                                                    .version()))
                                    .sorted(
                                            java.util.Comparator.comparing(
                                                    CapabilityOfferProvenance::providerId))
                                    .toList();
                    CapabilityOfferCollision collision =
                            new CapabilityOfferCollision(
                                    capabilityId,
                                    providerOffers,
                                    Optional.ofNullable(runtimeVersion));
                    collisionIds.add(capabilityId);
                    providerOffers.forEach(
                            offer ->
                                    collisionsByProvider
                                            .computeIfAbsent(
                                                    offer.providerId(),
                                                    ignored -> new ArrayList<>())
                                            .add(collision));
                });
        collisionsByProvider.replaceAll((ignored, collisions) -> List.copyOf(collisions));
        return new CollisionIndex(Set.copyOf(collisionIds), Map.copyOf(collisionsByProvider));
    }

    private static Map<CapabilityId, CapabilityVersion> buildAvailableCapabilities(
            List<ValidatedProvider> providers,
            Map<CapabilityId, CapabilityVersion> runtimeCapabilities,
            Set<CapabilityId> collisionIds,
            Set<ProviderId> activeProviderIds) {
        Map<CapabilityId, CapabilityVersion> available = new TreeMap<>();
        runtimeCapabilities.forEach(
                (id, version) -> {
                    if (!collisionIds.contains(id)) {
                        available.put(id, version);
                    }
                });
        providers.stream()
                .filter(provider -> activeProviderIds.contains(provider.id()))
                .flatMap(
                        provider ->
                                provider.specification().capabilityOffers().values().stream())
                .filter(offer -> !collisionIds.contains(offer.id()))
                .forEach(offer -> available.put(offer.id(), offer.version()));
        return Map.copyOf(available);
    }

    private static Map<ProviderId, ProviderSupport> evaluateProviders(
            List<ValidatedProvider> providers,
            Map<CapabilityId, CapabilityVersion> available,
            Map<ProviderId, List<CapabilityOfferCollision>> collisionsByProvider) {
        Map<ProviderId, ProviderSupport> supportByProvider = new TreeMap<>();
        providers.forEach(
                provider -> {
                    List<CapabilityRequirementResult> requirements =
                            provider.specification().capabilityRequirements().values().stream()
                                    .map(
                                            requirement ->
                                                    negotiate(requirement, available))
                                    .toList();
                    supportByProvider.put(
                            provider.id(),
                            ProviderSupport.evaluate(
                                    requirements,
                                    collisionsByProvider.getOrDefault(
                                            provider.id(),
                                            List.of())));
                });
        return Map.copyOf(supportByProvider);
    }

    private static CapabilityRequirementResult negotiate(
            CapabilityRequirement requirement,
            Map<CapabilityId, CapabilityVersion> available) {
        return CapabilityRequirementResult.evaluate(
                requirement,
                available.get(requirement.id()));
    }

    private static RegisteredProvider register(
            ValidatedProvider provider,
            ProviderSupport support) {
        return new RegisteredProvider(
                provider.ownerNamespace(),
                provider.implementationClass(),
                provider.id(),
                provider.provider(),
                provider.specification(),
                support);
    }

    private record CollisionIndex(
            Set<CapabilityId> capabilityIds,
            Map<ProviderId, List<CapabilityOfferCollision>> byProvider) {}
}
