package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class DuplicateProvider implements BugReportProvider {
    @Override
    public String providerId() {
        return "bugreport_provider_a";
    }
}
