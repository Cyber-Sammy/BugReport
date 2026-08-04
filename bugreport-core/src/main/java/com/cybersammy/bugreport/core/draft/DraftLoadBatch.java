package com.cybersammy.bugreport.core.draft;

import java.util.List;
import java.util.Objects;

/** Bounded result with canonically ordered outcomes from one draft-directory scan. */
public record DraftLoadBatch(
        List<DraftLoadOutcome> outcomes,
        int temporaryFilesDeleted,
        boolean scanLimitReached) {
    public DraftLoadBatch {
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (outcomes.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("outcomes contains null");
        }
        if (temporaryFilesDeleted < 0) {
            throw new IllegalArgumentException("Temporary-file count must be non-negative");
        }
    }
}
