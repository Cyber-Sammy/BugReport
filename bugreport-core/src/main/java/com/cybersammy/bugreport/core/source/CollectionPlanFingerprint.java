package com.cybersammy.bugreport.core.source;

import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;

/** Opaque immutable identity of the exact canonical file plan handed to collection. */
public final class CollectionPlanFingerprint {
    private final List<Entry> entries;

    private CollectionPlanFingerprint(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Computes the identity from canonical selected files without retaining local absolute paths. */
    public static CollectionPlanFingerprint from(CategorySourcePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new CollectionPlanFingerprint(plan.files().stream().map(file -> new Entry(
                file.file().root(),
                file.file().relativePath(),
                file.file().observedSize(),
                file.file().observedLastModified(),
                file.file().observedFileKey().orElse(null),
                file.maximumBytes(),
                file.provenances())).toList());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CollectionPlanFingerprint fingerprint
                && entries.equals(fingerprint.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    private record Entry(
            com.cybersammy.bugreport.api.specification.LogicalRoot root,
            com.cybersammy.bugreport.api.specification.RelativePath relativePath,
            long observedSize,
            FileTime observedLastModified,
            Object observedFileKey,
            long maximumBytes,
            List<SourceProvenance> provenances) {
        private Entry {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(observedLastModified, "observedLastModified");
            if (observedSize < 0 || maximumBytes <= 0) {
                throw new IllegalArgumentException("Collection plan entry bounds are invalid");
            }
            provenances = List.copyOf(Objects.requireNonNull(provenances, "provenances"));
        }
    }
}
