package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.Comparator;
import java.util.Objects;

/** Capability actually required or optionally used by portable manifest content. */
public record ManifestCapability(CapabilityId id, CapabilityVersion version)
        implements Comparable<ManifestCapability> {
    private static final Comparator<ManifestCapability> ORDER =
            Comparator.comparing(ManifestCapability::id)
                    .thenComparing(ManifestCapability::version);

    public ManifestCapability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }

    @Override
    public int compareTo(ManifestCapability other) {
        return ORDER.compare(this, other);
    }
}
