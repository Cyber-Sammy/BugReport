package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.Arrays;
import java.util.Optional;

final class SessionProviderFixture {
    private SessionProviderFixture() {}

    static ProviderSpecification.Builder specificationBuilder(String providerId) {
        return ProviderSpecification.builder(
                        ProviderId.parse(providerId),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of(providerId.replace(':', '.') + ".bugreport.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(
                        CategorySpecification.builder(
                                        CategoryId.of("general"),
                                        LocalizationKey.of(
                                                providerId.replace(':', '.')
                                                        + ".bugreport.category.general"))
                                .build());
    }

    static ProviderSpecification specification(String providerId) {
        return specificationBuilder(providerId).build();
    }

    static ProviderRegistrySnapshot registry(ProviderSpecification... specifications) {
        return ProviderRegistry.createSnapshot(
                Arrays.stream(specifications)
                        .map(SessionProviderFixture::candidate)
                        .toList());
    }

    private static DiscoveredProvider candidate(ProviderSpecification specification) {
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return specification.id().value();
            }

            @Override
            public String providerVersion() {
                return specification.version().value();
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return new DiscoveredProvider(
                specification.id().namespace(),
                "FixtureProvider_" + specification.id().value(),
                provider);
    }
}
