package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;

record ProviderCandidate(
        String owner,
        Class<? extends BugReportProvider> type,
        ProviderFactory factory) {
    String className() {
        return type.getName();
    }
}
