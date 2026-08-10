package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable plan for declared file sources and generated diagnostics of one category. */
public final class CategoryCollectionPlan {
    private final CategorySourcePlan sources;
    private final SupportedSide side;
    private final List<DiagnosticGeneratorSpecification> generators;

    CategoryCollectionPlan(
            CategorySourcePlan sources,
            SupportedSide side,
            List<DiagnosticGeneratorSpecification> generators) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.side = Objects.requireNonNull(side, "side");
        this.generators = List.copyOf(Objects.requireNonNull(generators, "generators"));
        if (this.generators.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Generator plans must not contain null");
        }
        for (int index = 1; index < this.generators.size(); index++) {
            if (Comparator.comparing(DiagnosticGeneratorSpecification::id)
                            .compare(this.generators.get(index - 1), this.generators.get(index))
                    >= 0) {
                throw new IllegalArgumentException(
                        "Generator plans must be strictly ordered by generator ID");
            }
        }
    }

    /** Returns the exact trusted file-source plan. */
    public CategorySourcePlan sources() {
        return sources;
    }

    /** Returns the physical side for which availability was planned. */
    public SupportedSide side() {
        return side;
    }

    /** Returns declared generators in canonical ID order. */
    public List<DiagnosticGeneratorSpecification> generators() {
        return generators;
    }

    /** Reports whether a generator can execute on the planned physical side. */
    public boolean isAvailable(DiagnosticGeneratorSpecification generator) {
        return Objects.requireNonNull(generator, "generator").supportedSides().contains(side);
    }
}
