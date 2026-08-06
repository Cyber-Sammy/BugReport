package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Objects;

/** Provider/category identity selected for a portable report. */
public record ManifestTarget(
        ProviderId providerId, ProviderVersion providerVersion, CategoryId categoryId) {
    public ManifestTarget {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
    }
}
