package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class NoPublicConstructorProvider implements BugReportProvider {
    private NoPublicConstructorProvider() {}

    @Override
    public String providerId() {
        return "unreachable";
    }
}
