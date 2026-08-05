package com.cybersammy.bugreport.core.source;

import java.util.Objects;

/** Compares trusted source-file observations without exposing filesystem keys. */
public final class SourceFileObservations {
    private SourceFileObservations() {}

    /** Returns whether both values describe the same path, identity, and file snapshot. */
    public static boolean sameSnapshot(
            ResolvedSourceFile first, ResolvedSourceFile second) {
        ResolvedSourceFile left = Objects.requireNonNull(first, "first");
        ResolvedSourceFile right = Objects.requireNonNull(second, "second");
        return left.localPath().equals(right.localPath())
                && left.root() == right.root()
                && left.relativePath().equals(right.relativePath())
                && left.observedFileKey().equals(right.observedFileKey())
                && left.observedSize() == right.observedSize()
                && left.observedLastModified().equals(right.observedLastModified());
    }
}
