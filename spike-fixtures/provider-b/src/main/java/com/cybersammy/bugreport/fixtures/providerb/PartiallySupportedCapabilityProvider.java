package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Optional;

public final class PartiallySupportedCapabilityProvider implements BugReportProvider {
    private static final String PROVIDER_ID = ProviderBMod.MOD_ID + ":partial";
    private static final ProviderSpecification SPECIFICATION =
            CapabilityFixtureSpecifications.create(PROVIDER_ID, false);

    public PartiallySupportedCapabilityProvider() {}

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String providerVersion() {
        return "1.0.0";
    }

    @Override
    public Optional<ProviderSpecification> specification() {
        return Optional.of(SPECIFICATION);
    }
}
