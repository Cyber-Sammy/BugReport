package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Objects;

/**
 * Minimum compatible version of a required or optional capability.
 *
 * @param id globally namespaced capability ID
 * @param minimumVersion minimum compatible major/minor version
 * @param required whether absence invalidates the containing specification
 */
public record CapabilityRequirement(
        CapabilityId id, CapabilityVersion minimumVersion, boolean required)
        implements Comparable<CapabilityRequirement> {
    /** Validates and creates a capability requirement. */
    public CapabilityRequirement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(minimumVersion, "minimumVersion");
    }

    @Override
    public int compareTo(CapabilityRequirement other) {
        return id.compareTo(other.id);
    }
}
