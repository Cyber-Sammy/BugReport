package com.cybersammy.bugreport.api.classification;

import java.util.Objects;

/** Product privacy floor declared for a field, source, or generated value. */
public enum PrivacyClassification {
    /** Bounded technical data with no known personal or secret content. */
    LOW(0),
    /** Data linkable to a user, device, installation, or server relationship. */
    PERSONAL(1),
    /** Private or opaque content with material disclosure risk. */
    SENSITIVE(2),
    /** Secret or policy-forbidden content that must never be packaged. */
    PROHIBITED(3);

    private final int restrictiveness;

    PrivacyClassification(int restrictiveness) {
        this.restrictiveness = restrictiveness;
    }

    /**
     * Returns the more restrictive of two classifications.
     *
     * @param first first classification
     * @param second second classification
     * @return the effective privacy floor
     */
    public static PrivacyClassification mostRestrictive(
            PrivacyClassification first, PrivacyClassification second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return first.restrictiveness >= second.restrictiveness ? first : second;
    }

    /**
     * Reports whether this value is at least as restrictive as another.
     *
     * @param other comparison floor
     * @return {@code true} when this classification cannot weaken {@code other}
     */
    public boolean isAtLeast(PrivacyClassification other) {
        return restrictiveness >= Objects.requireNonNull(other, "other").restrictiveness;
    }
}
