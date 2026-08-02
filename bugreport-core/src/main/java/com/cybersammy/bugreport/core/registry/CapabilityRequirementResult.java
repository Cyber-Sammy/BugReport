package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Objects;
import java.util.Optional;

/** Immutable result of negotiating one provider capability requirement. */
public record CapabilityRequirementResult(
        CapabilityRequirement requirement,
        CapabilityRequirementStatus status,
        Optional<CapabilityVersion> availableVersion) {
    /** Validates a negotiation result. */
    public CapabilityRequirementResult {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(availableVersion, "availableVersion");
        validateStatus(requirement, status, availableVersion.orElse(null));
    }

    static CapabilityRequirementResult evaluate(
            CapabilityRequirement requirement,
            CapabilityVersion availableVersion) {
        if (availableVersion == null) {
            return new CapabilityRequirementResult(
                    requirement,
                    CapabilityRequirementStatus.MISSING,
                    Optional.empty());
        }
        CapabilityVersion minimum = requirement.minimumVersion();
        if (availableVersion.major() != minimum.major()) {
            return new CapabilityRequirementResult(
                    requirement,
                    CapabilityRequirementStatus.INCOMPATIBLE_MAJOR,
                    Optional.of(availableVersion));
        }
        if (availableVersion.minor() < minimum.minor()) {
            return new CapabilityRequirementResult(
                    requirement,
                    CapabilityRequirementStatus.INSUFFICIENT_MINOR,
                    Optional.of(availableVersion));
        }
        return new CapabilityRequirementResult(
                requirement,
                CapabilityRequirementStatus.SATISFIED,
                Optional.of(availableVersion));
    }

    /** Reports whether the requirement is satisfied. */
    public boolean satisfied() {
        return status == CapabilityRequirementStatus.SATISFIED;
    }

    private static void validateStatus(
            CapabilityRequirement requirement,
            CapabilityRequirementStatus status,
            CapabilityVersion availableVersion) {
        CapabilityVersion minimum = requirement.minimumVersion();
        switch (status) {
            case MISSING -> {
                if (availableVersion != null) {
                    throw new IllegalArgumentException(
                            "A missing capability cannot have an available version");
                }
            }
            case INCOMPATIBLE_MAJOR -> {
                requireAvailable(availableVersion);
                if (availableVersion.major() == minimum.major()) {
                    throw new IllegalArgumentException(
                            "An incompatible capability must have a different major version");
                }
            }
            case INSUFFICIENT_MINOR -> {
                requireAvailable(availableVersion);
                if (availableVersion.major() != minimum.major()
                        || availableVersion.minor() >= minimum.minor()) {
                    throw new IllegalArgumentException(
                            "An insufficient capability must have the required major and an older minor");
                }
            }
            case SATISFIED -> {
                requireAvailable(availableVersion);
                if (availableVersion.major() != minimum.major()
                        || availableVersion.minor() < minimum.minor()) {
                    throw new IllegalArgumentException(
                            "A satisfied capability must have a compatible available version");
                }
            }
        }
    }

    private static void requireAvailable(CapabilityVersion availableVersion) {
        if (availableVersion == null) {
            throw new IllegalArgumentException("Capability status requires an available version");
        }
    }
}
