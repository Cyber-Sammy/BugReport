package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.util.List;

record ProviderCandidate(
        String owner,
        Class<? extends BugReportProvider> type,
        ProviderFactory factory) {
    String className() {
        return type.getName();
    }
}

@FunctionalInterface
interface ProviderFactory {
    BugReportProvider create() throws ReflectiveOperationException;
}

record ProviderCandidates(List<ProviderCandidate> candidates, List<String> failures) {
    ProviderCandidates {
        candidates = List.copyOf(candidates);
        failures = List.copyOf(failures);
    }
}

record ProviderDiscoveryResult(List<String> providers, List<String> failures) {
    ProviderDiscoveryResult {
        providers = List.copyOf(providers);
        failures = List.copyOf(failures);
    }
}
