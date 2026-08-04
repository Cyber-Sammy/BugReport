package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import java.util.Objects;
import java.util.Optional;

/** Draft rebound to exact immutable declarations from the current trusted registry. */
public final class ResolvedReportDraft {
    private final ReportDraft draft;
    private final ProviderSpecification providerSpecification;
    private final ProviderSupport providerSupport;
    private final Optional<CategorySpecification> category;

    ResolvedReportDraft(
            ReportDraft draft,
            ProviderSpecification providerSpecification,
            ProviderSupport providerSupport,
            Optional<CategorySpecification> category) {
        this.draft = Objects.requireNonNull(draft, "draft");
        this.providerSpecification =
                Objects.requireNonNull(providerSpecification, "providerSpecification");
        this.providerSupport = Objects.requireNonNull(providerSupport, "providerSupport");
        this.category = Objects.requireNonNull(category, "category");
        if (!draft.providerId().equals(providerSpecification.id())
                || !draft.providerVersion().equals(providerSpecification.version())) {
            throw new IllegalArgumentException("Resolved provider identity must match the draft");
        }
        if (!draft.categoryId().equals(category.map(CategorySpecification::id))) {
            throw new IllegalArgumentException("Resolved category identity must match the draft");
        }
        category.ifPresent(
                value -> {
                    if (providerSpecification.categories().get(value.id()) != value) {
                        throw new IllegalArgumentException(
                                "Resolved category must come from the resolved provider specification");
                    }
                });
    }

    /** Returns the parsed persistence-safe draft. */
    public ReportDraft draft() {
        return draft;
    }

    /** Returns the exact current trusted provider specification. */
    public ProviderSpecification providerSpecification() {
        return providerSpecification;
    }

    /** Returns the exact current negotiated provider support. */
    public ProviderSupport providerSupport() {
        return providerSupport;
    }

    /** Returns the exact current trusted category declaration when selected. */
    public Optional<CategorySpecification> category() {
        return category;
    }
}
