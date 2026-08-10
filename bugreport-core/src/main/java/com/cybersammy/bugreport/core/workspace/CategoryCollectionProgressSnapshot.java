package com.cybersammy.bugreport.core.workspace;

import java.util.Objects;

/** Privacy-safe polling snapshot for combined file and generated diagnostic collection. */
public record CategoryCollectionProgressSnapshot(
        Phase phase,
        CollectionProgressSnapshot fileProgress,
        int totalGenerators,
        int completedGenerators) {
    public enum Phase {
        IDLE,
        FILES,
        GENERATED_DIAGNOSTICS,
        COMPLETE,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public CategoryCollectionProgressSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(fileProgress, "fileProgress");
        if (totalGenerators < 0
                || completedGenerators < 0
                || completedGenerators > totalGenerators) {
            throw new IllegalArgumentException("Generated diagnostic progress is inconsistent");
        }
    }
}
