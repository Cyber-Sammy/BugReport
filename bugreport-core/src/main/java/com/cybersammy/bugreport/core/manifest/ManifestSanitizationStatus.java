package com.cybersammy.bugreport.core.manifest;

/** Portable disposition of one included artifact after privacy review. */
public enum ManifestSanitizationStatus {
    NOT_REQUIRED,
    SANITIZED,
    REVIEWED_WITH_WARNINGS
}
