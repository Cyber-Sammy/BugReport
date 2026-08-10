package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import java.util.List;
import java.util.Objects;

/** Opaque in-process identity of exact reviewed file and generator selections. */
public final class CategoryCollectionFingerprint {
    private final CollectionPlanFingerprint files;
    private final List<DiagnosticGeneratorId> generators;

    private CategoryCollectionFingerprint(
            CollectionPlanFingerprint files, List<DiagnosticGeneratorId> generators) {
        this.files = Objects.requireNonNull(files, "files");
        this.generators = List.copyOf(Objects.requireNonNull(generators, "generators"));
    }

    /** Computes an identity without retaining absolute filesystem paths or provider callbacks. */
    public static CategoryCollectionFingerprint from(ReviewedCollectionPlan plan) {
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
        return new CategoryCollectionFingerprint(
                CollectionPlanFingerprint.from(reviewed.selectedFilePlan()),
                List.copyOf(reviewed.includedGeneratorIds()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CategoryCollectionFingerprint fingerprint
                && files.equals(fingerprint.files)
                && generators.equals(fingerprint.generators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(files, generators);
    }
}
