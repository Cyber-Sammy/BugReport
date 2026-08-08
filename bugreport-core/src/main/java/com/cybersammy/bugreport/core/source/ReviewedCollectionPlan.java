package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
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
    private final Set<DiagnosticSourceId> includedSourceIds;
    private final List<CoordinatedSourcePlan> includedSources;

    private ReviewedCollectionPlan(
            CategorySourcePlan plan, Set<DiagnosticSourceId> requestedIncludedSourceIds) {
        this.plan = Objects.requireNonNull(plan, "plan");
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
    }

    /** Creates a reviewed selection with exactly the requested available source IDs. */
    public static ReviewedCollectionPlan of(
            CategorySourcePlan plan, Set<DiagnosticSourceId> includedSourceIds) {
        return new ReviewedCollectionPlan(plan, includedSourceIds);
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
        return new ReviewedCollectionPlan(plan, defaults);
    }

    /** Returns the immutable trusted planning result this decision reviews. */
    public CategorySourcePlan plan() {
        return plan;
    }

    /** Returns selected source IDs in canonical source-plan order. */
    public Set<DiagnosticSourceId> includedSourceIds() {
        return includedSourceIds;
    }

    /** Returns selected source outcomes in canonical source-plan order. */
    public List<CoordinatedSourcePlan> includedSources() {
        return includedSources;
    }
}
