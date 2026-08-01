package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Objects;

/**
 * Capability version implemented by a provider specification.
 *
 * @param id globally namespaced capability ID
 * @param version implemented major/minor version
 */
public record CapabilityOffer(CapabilityId id, CapabilityVersion version)
        implements Comparable<CapabilityOffer> {
    /** Validates and creates a capability offer. */
    public CapabilityOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }

    @Override
    public int compareTo(CapabilityOffer other) {
        return id.compareTo(other.id);
    }
}
