package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class DuplicateNamedProviderA implements BugReportProvider {
    @Override
    public String providerId() {
        return ProviderBMod.MOD_ID + ":duplicate";
    }
}
