package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class DuplicateNamedProviderB implements BugReportProvider {
    @Override
    public String providerId() {
        return ProviderBMod.MOD_ID + ":duplicate";
    }
}
