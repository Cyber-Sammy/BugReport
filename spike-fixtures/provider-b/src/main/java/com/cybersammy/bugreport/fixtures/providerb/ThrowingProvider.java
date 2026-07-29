package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class ThrowingProvider implements BugReportProvider {
    public ThrowingProvider() {
        throw new IllegalStateException("Intentional discovery fixture failure");
    }

    @Override
    public String providerId() {
        return "unreachable";
    }
}
