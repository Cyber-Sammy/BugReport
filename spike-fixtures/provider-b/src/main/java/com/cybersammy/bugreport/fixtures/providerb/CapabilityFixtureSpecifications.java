package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;

final class CapabilityFixtureSpecifications {
    private static final CapabilityId UNAVAILABLE_CAPABILITY =
            CapabilityId.of("bugreport:unavailable_fixture");

    private CapabilityFixtureSpecifications() {}

    static ProviderSpecification create(String providerId, boolean required) {
        String localizationPrefix = providerId.replace(':', '.');
        return ProviderSpecification.builder(
                        ProviderId.parse(providerId),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of(localizationPrefix + ".provider"))
                .supportSide(SupportedSide.DEDICATED_SERVER)
                .requireCapability(
                        new CapabilityRequirement(
                                UNAVAILABLE_CAPABILITY,
                                new CapabilityVersion(1, 0),
                                required))
                .addCategory(
                        CategorySpecification.builder(
                                        CategoryId.of("general"),
                                        LocalizationKey.of(
                                                localizationPrefix + ".category.general"))
                                .build())
                .build();
    }
}
