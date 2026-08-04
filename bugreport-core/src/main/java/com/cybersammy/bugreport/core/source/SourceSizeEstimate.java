package com.cybersammy.bugreport.core.source;

/**
 * Immutable planning-time byte estimate for one diagnostic source.
 *
 * @param selectedFileCount files already selected by the plan
 * @param knownBytes bytes observed for selected files or otherwise known now
 * @param complete whether the estimate accounts for every eventual source byte
 */
public record SourceSizeEstimate(int selectedFileCount, long knownBytes, boolean complete) {
    /** Validates an estimate without treating planning metadata as collection authority. */
    public SourceSizeEstimate {
        if (selectedFileCount < 0) {
            throw new IllegalArgumentException("selectedFileCount must be non-negative");
        }
        if (knownBytes < 0) {
            throw new IllegalArgumentException("knownBytes must be non-negative");
        }
    }

    /** Creates an estimate that accounts for every currently planned byte. */
    public static SourceSizeEstimate exact(int selectedFileCount, long knownBytes) {
        return new SourceSizeEstimate(selectedFileCount, knownBytes, true);
    }

    /** Creates a lower bound for a source whose bytes are not selected yet. */
    public static SourceSizeEstimate lowerBound(int selectedFileCount, long knownBytes) {
        return new SourceSizeEstimate(selectedFileCount, knownBytes, false);
    }
}
