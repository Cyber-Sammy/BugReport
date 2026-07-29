package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

final class PackagePrivateProvider implements BugReportProvider {
    public PackagePrivateProvider() {}

    @Override
    public String providerId() {
        return "unreachable";
    }
}
