package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.Objects;

/** Plans file selectors and generated diagnostics against one trusted registry snapshot. */
public final class CategoryCollectionPlanner {
    private final ProviderRegistrySnapshot registry;
    private final ApprovedSourceRoots roots;
    private final SupportedSide side;

    public CategoryCollectionPlanner(
            ProviderRegistrySnapshot registry, ApprovedSourceRoots roots, SupportedSide side) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.roots = Objects.requireNonNull(roots, "roots");
        this.side = Objects.requireNonNull(side, "side");
    }

    /** Produces one deterministic category-level collection plan. */
    public CategoryCollectionPlan plan(ProviderId providerId, CategoryId categoryId) {
        CategorySourcePlan sourcePlan = new CategorySourcePlanner(registry, roots, side)
                .plan(providerId, categoryId);
        var provider = registry.find(providerId).orElseThrow();
        var category = provider.specification().categories().get(categoryId);
        var generators = category.generatorIds().stream()
                .map(id -> provider.specification().generators().get(id))
                .toList();
        return new CategoryCollectionPlan(sourcePlan, side, generators);
    }
}
