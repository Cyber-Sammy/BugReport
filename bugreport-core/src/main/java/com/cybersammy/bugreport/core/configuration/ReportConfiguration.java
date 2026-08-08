package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.core.sanitization.SanitizationProfile;
import java.util.Objects;

/** Immutable user configuration decoded only after bounded schema validation. */
public record ReportConfiguration(
        ReportSizeLimits sizeLimits,
        SanitizationProfile privacyProfile,
        WorkspaceLocation workspaceLocation,
        CleanupPolicy cleanupPolicy) {
    public ReportConfiguration {
        Objects.requireNonNull(sizeLimits, "sizeLimits");
        Objects.requireNonNull(privacyProfile, "privacyProfile");
        Objects.requireNonNull(workspaceLocation, "workspaceLocation");
        Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
    }

    /** Returns the complete configuration used when no persisted file exists. */
    public static ReportConfiguration productDefaults() {
        return new ReportConfiguration(
                ReportSizeLimits.productDefaults(),
                SanitizationProfile.STANDARD,
                WorkspaceLocation.productDefault(),
                CleanupPolicy.productDefault());
    }
}
