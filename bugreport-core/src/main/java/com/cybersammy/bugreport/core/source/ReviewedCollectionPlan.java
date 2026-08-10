package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable user-reviewed inclusion decision for one exact category source plan.
 *
 * <p>The decision is deliberately separate from {@link CategorySourcePlan}: planning records
 * trusted observations, while this type records only whether each currently available declared
 * source is included before collection starts.
 */
public final class ReviewedCollectionPlan {
    private final CategorySourcePlan plan;
    private final CategoryCollectionPlan collectionPlan;
    private final Set<DiagnosticSourceId> includedSourceIds;
    private final Set<DiagnosticGeneratorId> includedGeneratorIds;
    private final List<CoordinatedSourcePlan> includedSources;
    private final List<DiagnosticGeneratorSpecification> includedGenerators;

    private ReviewedCollectionPlan(
            CategoryCollectionPlan collectionPlan,
            Set<DiagnosticSourceId> requestedIncludedSourceIds,
            Set<DiagnosticGeneratorId> requestedIncludedGeneratorIds) {
        this.collectionPlan = Objects.requireNonNull(collectionPlan, "collectionPlan");
        this.plan = collectionPlan.sources();
        Set<DiagnosticSourceId> requested = Set.copyOf(
                Objects.requireNonNull(requestedIncludedSourceIds, "requestedIncludedSourceIds"));
        LinkedHashSet<DiagnosticSourceId> known = new LinkedHashSet<>();
        LinkedHashSet<DiagnosticSourceId> available = new LinkedHashSet<>();
        LinkedHashSet<DiagnosticSourceId> selectedIds = new LinkedHashSet<>();
        List<CoordinatedSourcePlan> selected = new ArrayList<>();
        for (CoordinatedSourcePlan source : plan.sources()) {
            DiagnosticSourceId sourceId = source.provenance().sourceId();
            known.add(sourceId);
            if (!(source.selection() instanceof UnavailableSourcePlan)) {
                available.add(sourceId);
            }
        }
        if (!known.containsAll(requested)) {
            throw new IllegalArgumentException("Collection selection contains an undeclared source ID");
        }
        if (!available.containsAll(requested)) {
            throw new IllegalArgumentException("Unavailable sources cannot be included");
        }
        for (CoordinatedSourcePlan source : plan.sources()) {
            if (requested.contains(source.provenance().sourceId())) {
                selectedIds.add(source.provenance().sourceId());
                selected.add(source);
            }
        }
        includedSourceIds = Collections.unmodifiableSet(selectedIds);
        includedSources = List.copyOf(selected);

        Set<DiagnosticGeneratorId> requestedGenerators = Set.copyOf(
                Objects.requireNonNull(requestedIncludedGeneratorIds, "requestedIncludedGeneratorIds"));
        LinkedHashSet<DiagnosticGeneratorId> knownGenerators = collectionPlan.generators().stream()
                .map(DiagnosticGeneratorSpecification::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!knownGenerators.containsAll(requestedGenerators)) {
            throw new IllegalArgumentException("Collection selection contains an undeclared generator ID");
        }
        if (collectionPlan.generators().stream()
                .filter(generator -> requestedGenerators.contains(generator.id()))
                .anyMatch(generator -> !collectionPlan.isAvailable(generator))) {
            throw new IllegalArgumentException("Unavailable generators cannot be included");
        }
        LinkedHashSet<DiagnosticGeneratorId> selectedGeneratorIds = new LinkedHashSet<>();
        List<DiagnosticGeneratorSpecification> selectedGenerators = new ArrayList<>();
        for (DiagnosticGeneratorSpecification generator : collectionPlan.generators()) {
            if (requestedGenerators.contains(generator.id())) {
                selectedGeneratorIds.add(generator.id());
                selectedGenerators.add(generator);
            }
        }
        includedGeneratorIds = Collections.unmodifiableSet(selectedGeneratorIds);
        includedGenerators = List.copyOf(selectedGenerators);
    }

    /** Creates a reviewed selection with exactly the requested available source IDs. */
    public static ReviewedCollectionPlan of(
            CategorySourcePlan plan, Set<DiagnosticSourceId> includedSourceIds) {
        return new ReviewedCollectionPlan(
                new CategoryCollectionPlan(
                        plan,
                        com.cybersammy.bugreport.api.classification.SupportedSide.PHYSICAL_CLIENT,
                        List.of()),
                includedSourceIds,
                Set.of());
    }

    /** Creates a reviewed selection for exact file sources and generated diagnostics. */
    public static ReviewedCollectionPlan of(
            CategoryCollectionPlan plan,
            Set<DiagnosticSourceId> includedSourceIds,
            Set<DiagnosticGeneratorId> includedGeneratorIds) {
        return new ReviewedCollectionPlan(plan, includedSourceIds, includedGeneratorIds);
    }

    /** Creates the initial selection requested by source declarations. */
    public static ReviewedCollectionPlan defaults(CategorySourcePlan plan) {
        Objects.requireNonNull(plan, "plan");
        LinkedHashSet<DiagnosticSourceId> defaults = new LinkedHashSet<>();
        for (CoordinatedSourcePlan source : plan.sources()) {
            if (!(source.selection() instanceof UnavailableSourcePlan)
                    && source.provenance().inclusionDefault() == InclusionDefault.INCLUDED) {
                defaults.add(source.provenance().sourceId());
            }
        }
        return new ReviewedCollectionPlan(
                new CategoryCollectionPlan(
                        plan,
                        com.cybersammy.bugreport.api.classification.SupportedSide.PHYSICAL_CLIENT,
                        List.of()),
                defaults,
                Set.of());
    }

    /** Creates the initial selection requested by source and generator declarations. */
    public static ReviewedCollectionPlan defaults(CategoryCollectionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Set<DiagnosticSourceId> sources = defaults(plan.sources()).includedSourceIds();
        LinkedHashSet<DiagnosticGeneratorId> generators = plan.generators().stream()
                .filter(plan::isAvailable)
                .filter(generator -> generator.inclusionDefault() == InclusionDefault.INCLUDED)
                .map(DiagnosticGeneratorSpecification::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new ReviewedCollectionPlan(plan, sources, generators);
    }

    /** Returns the immutable trusted planning result this decision reviews. */
    public CategorySourcePlan plan() {
        return plan;
    }

    /** Returns the complete trusted source and generator plan. */
    public CategoryCollectionPlan collectionPlan() {
        return collectionPlan;
    }

    /** Returns selected source IDs in canonical source-plan order. */
    public Set<DiagnosticSourceId> includedSourceIds() {
        return includedSourceIds;
    }

    /** Returns selected source outcomes in canonical source-plan order. */
    public List<CoordinatedSourcePlan> includedSources() {
        return includedSources;
    }

    /** Returns selected generator IDs in canonical plan order. */
    public Set<DiagnosticGeneratorId> includedGeneratorIds() {
        return includedGeneratorIds;
    }

    /** Returns selected generator declarations in canonical plan order. */
    public List<DiagnosticGeneratorSpecification> includedGenerators() {
        return includedGenerators;
    }

    /**
     * Returns the exact conflict-free file plan authorized by this selection.
     *
     * <p>When several declarations selected one file, only provenance for included sources is
     * retained. An excluded source therefore cannot be collected through a duplicate declaration.
     */
    public CategorySourcePlan selectedFilePlan() {
        List<PlannedSourceFile> files = plan.files().stream()
                .map(this::selectedFile)
                .filter(Objects::nonNull)
                .toList();
        List<SourcePlanConflict> conflicts = plan.conflicts().stream()
                .map(this::selectedConflict)
                .filter(Objects::nonNull)
                .toList();
        return new CategorySourcePlan(
                plan.providerId(), plan.providerVersion(), plan.categoryId(), includedSources, files, conflicts);
    }

    private PlannedSourceFile selectedFile(PlannedSourceFile file) {
        List<SourceProvenance> provenances = file.provenances().stream()
                .filter(provenance -> includedSourceIds.contains(provenance.sourceId()))
                .toList();
        return provenances.isEmpty() ? null : new PlannedSourceFile(
                file.file(), provenances, file.maximumBytes());
    }

    private SourcePlanConflict selectedConflict(SourcePlanConflict conflict) {
        List<SourceProvenance> provenances = conflict.provenances().stream()
                .filter(provenance -> includedSourceIds.contains(provenance.sourceId()))
                .toList();
        return provenances.size() < 2 ? null : new SourcePlanConflict(
                conflict.code(), conflict.root(), conflict.relativePath(), provenances);
    }
}
