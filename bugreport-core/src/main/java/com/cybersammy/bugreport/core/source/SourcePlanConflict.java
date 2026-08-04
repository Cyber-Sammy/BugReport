package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Privacy-safe conflict for one canonical file selected by incompatible declarations. */
public final class SourcePlanConflict {
    private final SourcePlanConflictCode code;
    private final LogicalRoot root;
    private final RelativePath relativePath;
    private final List<SourceProvenance> provenances;

    SourcePlanConflict(
            SourcePlanConflictCode code,
            LogicalRoot root,
            RelativePath relativePath,
            List<SourceProvenance> provenances) {
        this.code = Objects.requireNonNull(code, "code");
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.provenances = canonicalProvenances(provenances);
        if (this.provenances.size() < 2) {
            throw new IllegalArgumentException("A source conflict requires at least two sources");
        }
    }

    /** Returns the stable incompatibility reason. */
    public SourcePlanConflictCode code() {
        return code;
    }

    /** Returns the approved logical root without exposing its local mapping. */
    public LogicalRoot root() {
        return root;
    }

    /** Returns one canonical relative path naming the conflicting file. */
    public RelativePath relativePath() {
        return relativePath;
    }

    /** Returns conflicting declarations in canonical source-ID order. */
    public List<SourceProvenance> provenances() {
        return provenances;
    }

    static List<SourceProvenance> canonicalProvenances(List<SourceProvenance> values) {
        List<SourceProvenance> ordered =
                Objects.requireNonNull(values, "provenances").stream()
                        .map(value -> Objects.requireNonNull(value, "provenance"))
                        .sorted(Comparator.comparing(SourceProvenance::sourceId))
                        .toList();
        long unique = ordered.stream().map(SourceProvenance::sourceId).distinct().count();
        if (unique != ordered.size()) {
            throw new IllegalArgumentException("Source provenance IDs must be unique");
        }
        return ordered;
    }
}
