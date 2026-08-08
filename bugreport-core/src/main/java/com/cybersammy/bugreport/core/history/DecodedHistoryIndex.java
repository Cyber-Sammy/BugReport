package com.cybersammy.bugreport.core.history;

import java.util.Objects;

/** Decoded history plus isolated-entry recovery information. */
public record DecodedHistoryIndex(ReportHistoryIndex index, int skippedEntries) {
    public DecodedHistoryIndex {
        Objects.requireNonNull(index, "index");
        if (skippedEntries < 0) throw new IllegalArgumentException("Skipped history-entry count must be non-negative");
    }
}
