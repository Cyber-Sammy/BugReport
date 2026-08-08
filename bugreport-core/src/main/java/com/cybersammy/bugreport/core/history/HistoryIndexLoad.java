package com.cybersammy.bugreport.core.history;

import java.util.Objects;

/** Corruption-tolerant history load result; corrupt persisted bytes never block an empty index. */
public record HistoryIndexLoad(ReportHistoryIndex index, boolean recoveredFromCorruption) {
    public HistoryIndexLoad {
        Objects.requireNonNull(index, "index");
        if (!recoveredFromCorruption && index.entries().isEmpty()) {
            // An absent file is distinguishable only at the storage boundary; empty is valid here.
        }
    }
}
