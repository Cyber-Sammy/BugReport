package com.cybersammy.bugreport.core.registry;

/** Result of matching one capability requirement against the active catalog. */
public enum CapabilityRequirementStatus {
    SATISFIED,
    MISSING,
    INCOMPATIBLE_MAJOR,
    INSUFFICIENT_MINOR
}
