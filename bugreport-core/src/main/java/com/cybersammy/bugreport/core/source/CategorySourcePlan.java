package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable deterministic coordination result for all sources of one category. */
public final class CategorySourcePlan {
    private final ProviderId providerId;
    private final ProviderVersion providerVersion;
    private final CategoryId categoryId;
    private final List<CoordinatedSourcePlan> sources;
    private final List<PlannedSourceFile> files;
    private final List<SourcePlanConflict> conflicts;

    CategorySourcePlan(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            List<CoordinatedSourcePlan> sources,
            List<PlannedSourceFile> files,
            List<SourcePlanConflict> conflicts) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        this.files = List.copyOf(Objects.requireNonNull(files, "files"));
        this.conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
        validateCanonicalOrder();
        this.sources.forEach(source -> requireMatching(source.provenance()));
        this.files.forEach(file -> file.provenances().forEach(this::requireMatching));
        this.conflicts.forEach(conflict -> conflict.provenances().forEach(this::requireMatching));
    }

    /** Returns the trusted provider identity. */
    public ProviderId providerId() {
        return providerId;
    }

    /** Returns the exact provider specification version. */
    public ProviderVersion providerVersion() {
        return providerVersion;
    }

    /** Returns the planned provider-local category identity. */
    public CategoryId categoryId() {
        return categoryId;
    }

    /** Returns every declared source outcome in canonical source-ID order. */
    public List<CoordinatedSourcePlan> sources() {
        return sources;
    }

    /** Returns unique conflict-free files in canonical root/path order. */
    public List<PlannedSourceFile> files() {
        return files;
    }

    /** Returns incompatible duplicate groups excluded from the file set. */
    public List<SourcePlanConflict> conflicts() {
        return conflicts;
    }

    private void validateCanonicalOrder() {
        requireOrdered(
                sources,
                Comparator.comparing(source -> source.provenance().sourceId()),
                "sources");
        Comparator<PlannedSourceFile> fileOrder = Comparator
                .comparing((PlannedSourceFile file) -> file.file().root())
                .thenComparing(file -> file.file().relativePath());
        requireOrdered(files, fileOrder, "files");
        Comparator<SourcePlanConflict> conflictOrder = Comparator
                .comparing(SourcePlanConflict::root)
                .thenComparing(SourcePlanConflict::relativePath);
        requireOrdered(conflicts, conflictOrder, "conflicts");
    }

    private void requireMatching(SourceProvenance provenance) {
        if (!providerId.equals(provenance.providerId())
                || !providerVersion.equals(provenance.providerVersion())
                || !categoryId.equals(provenance.categoryId())) {
            throw new IllegalArgumentException("Source plan provenance identities must match");
        }
    }

    private static <T> void requireOrdered(
            List<T> values, Comparator<? super T> comparator, String name) {
        for (int index = 1; index < values.size(); index++) {
            if (comparator.compare(values.get(index - 1), values.get(index)) >= 0) {
                throw new IllegalArgumentException(name + " must be strictly canonically ordered");
            }
        }
    }
}
