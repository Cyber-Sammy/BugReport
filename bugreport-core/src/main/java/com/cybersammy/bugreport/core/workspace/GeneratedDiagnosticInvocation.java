package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Objects;

/** Trusted immutable identities and declaration for one generated callback invocation. */
record GeneratedDiagnosticInvocation(
        ProviderSpecification provider,
        CategorySpecification category,
        DiagnosticGeneratorSpecification generator) {
    GeneratedDiagnosticInvocation {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(generator, "generator");
        if (!provider.categories().containsKey(category.id())
                || !provider.generators().containsKey(generator.id())
                || !category.generatorIds().contains(generator.id())) {
            throw new IllegalArgumentException("Generated invocation declarations do not match");
        }
    }
}
