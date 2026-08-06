package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.version.ApiVersion;
import java.util.Objects;

/** Exact Bug Report implementation and API versions that produced a manifest. */
public record ManifestProducer(String modVersion, ApiVersion apiVersion) {
    public ManifestProducer {
        modVersion = ManifestContract.requireVersion(modVersion, "producer modVersion");
        Objects.requireNonNull(apiVersion, "apiVersion");
    }
}
