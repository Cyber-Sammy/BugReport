package com.cybersammy.bugreport.core.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable capability-negotiation state for one validated provider. */
public final class ProviderSupport {
    private final ProviderSupportState state;
    private final List<CapabilityRequirementResult> requirements;
    private final List<CapabilityOfferCollision> offerCollisions;

    private ProviderSupport(
            ProviderSupportState state,
            List<CapabilityRequirementResult> requirements,
            List<CapabilityOfferCollision> offerCollisions) {
        this.state = Objects.requireNonNull(state, "state");
        this.requirements = List.copyOf(requirements);
        this.offerCollisions = List.copyOf(offerCollisions);
    }

    static ProviderSupport evaluate(
            List<CapabilityRequirementResult> requirements,
            List<CapabilityOfferCollision> offerCollisions) {
        List<CapabilityRequirementResult> orderedRequirements = requirements.stream()
                .sorted(Comparator.comparing(result -> result.requirement().id()))
                .toList();
        List<CapabilityOfferCollision> orderedCollisions = offerCollisions.stream()
                .sorted(Comparator.comparing(CapabilityOfferCollision::capabilityId))
                .toList();

        boolean requiredFailure = orderedRequirements.stream()
                .anyMatch(
                        result ->
                                result.requirement().required()
                                        && !result.satisfied());
        boolean optionalFailure = orderedRequirements.stream()
                .anyMatch(
                        result ->
                                !result.requirement().required()
                                        && !result.satisfied());
        ProviderSupportState state;
        if (!orderedCollisions.isEmpty() || requiredFailure) {
            state = ProviderSupportState.DISABLED;
        } else if (optionalFailure) {
            state = ProviderSupportState.PARTIALLY_SUPPORTED;
        } else {
            state = ProviderSupportState.ENABLED;
        }
        return new ProviderSupport(state, orderedRequirements, orderedCollisions);
    }

    /** Returns provider usability after negotiation. */
    public ProviderSupportState state() {
        return state;
    }

    /** Returns requirement results in canonical capability ID order. */
    public List<CapabilityRequirementResult> requirements() {
        return requirements;
    }

    /** Returns offer collisions attributable to this provider. */
    public List<CapabilityOfferCollision> offerCollisions() {
        return offerCollisions;
    }
}
