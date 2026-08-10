package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import java.util.List;
import java.util.Objects;

/** Opaque in-process identity of exact reviewed file and generator selections. */
public final class CategoryCollectionFingerprint {
    private final CollectionPlanFingerprint files;
    private final List<DiagnosticGeneratorId> generators;
    private final ScreenshotCollectionRequest.ScreenshotCollectionFingerprint screenshots;

    private CategoryCollectionFingerprint(
            CollectionPlanFingerprint files,
            List<DiagnosticGeneratorId> generators,
            ScreenshotCollectionRequest.ScreenshotCollectionFingerprint screenshots) {
        this.files = Objects.requireNonNull(files, "files");
        this.generators = List.copyOf(Objects.requireNonNull(generators, "generators"));
        this.screenshots = Objects.requireNonNull(screenshots, "screenshots");
    }

    /** Computes an identity without retaining absolute filesystem paths or provider callbacks. */
    public static CategoryCollectionFingerprint from(ReviewedCollectionPlan plan) {
        return from(plan, ScreenshotCollectionRequest.from(plan, List.of()));
    }

    /** Computes identity for exact files, generators, and explicit screenshot selections. */
    public static CategoryCollectionFingerprint from(
            ReviewedCollectionPlan plan, ScreenshotCollectionRequest screenshots) {
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
        ScreenshotCollectionRequest selected = Objects.requireNonNull(screenshots, "screenshots");
        if (!reviewed.plan().providerId().equals(selected.providerId())
                || !reviewed.plan().providerVersion().equals(selected.providerVersion())
                || !reviewed.plan().categoryId().equals(selected.categoryId())) {
            throw new IllegalArgumentException("Screenshot request belongs to another collection plan");
        }
        return new CategoryCollectionFingerprint(
                CollectionPlanFingerprint.from(reviewed.selectedFilePlan()),
                List.copyOf(reviewed.includedGeneratorIds()),
                selected.fingerprint());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CategoryCollectionFingerprint fingerprint
                && files.equals(fingerprint.files)
                && generators.equals(fingerprint.generators)
                && screenshots.equals(fingerprint.screenshots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(files, generators, screenshots);
    }
}
