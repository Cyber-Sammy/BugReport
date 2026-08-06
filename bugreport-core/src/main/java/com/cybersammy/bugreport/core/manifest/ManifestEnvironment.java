package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import java.util.Objects;

/** Bounded non-secret platform identity required to reproduce a report. */
public record ManifestEnvironment(
        String gameVersion,
        String loaderId,
        String loaderVersion,
        SupportedSide physicalSide) {
    public ManifestEnvironment {
        gameVersion = ManifestContract.requireVersion(gameVersion, "gameVersion");
        loaderId = ManifestContract.requireToken(loaderId, "loaderId");
        loaderVersion = ManifestContract.requireVersion(loaderVersion, "loaderVersion");
        Objects.requireNonNull(physicalSide, "physicalSide");
    }
}
