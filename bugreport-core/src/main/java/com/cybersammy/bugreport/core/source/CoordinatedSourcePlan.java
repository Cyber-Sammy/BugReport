package com.cybersammy.bugreport.core.source;

import java.util.Objects;

/** One category source declaration paired with its isolated selector outcome. */
public final class CoordinatedSourcePlan {
    private final SourceProvenance provenance;
    private final SourceSelectionPlan selection;

    CoordinatedSourcePlan(SourceProvenance provenance, SourceSelectionPlan selection) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.selection = Objects.requireNonNull(selection, "selection");
        if (!provenance.sourceId().equals(selection.source().id())
                || provenance.kind() != selection.source().kind()
                || provenance.contentType() != selection.source().contentType()
                || provenance.privacy() != selection.source().privacy()
                || provenance.qualityRole() != selection.source().qualityRole()
                || provenance.inclusionDefault() != selection.source().inclusionDefault()) {
            throw new IllegalArgumentException(
                    "Source provenance must match its exact selector declaration");
        }
    }

    /** Returns privacy-safe declaration provenance. */
    public SourceProvenance provenance() {
        return provenance;
    }

    /** Returns the isolated selector result, including typed unavailability. */
    public SourceSelectionPlan selection() {
        return selection;
    }
}
