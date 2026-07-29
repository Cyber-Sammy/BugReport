package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class ServiceProviderDiscovery {
    private static final int MAX_DESCRIPTOR_FAILURES = 16;

    ProviderCandidates discover() {
        List<ProviderCandidate> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Iterator<ServiceLoader.Provider<BugReportProvider>> providers =
                ServiceLoader.load(BugReportProvider.class).stream().iterator();
        int descriptorFailures = 0;

        while (descriptorFailures < MAX_DESCRIPTOR_FAILURES) {
            ServiceLoader.Provider<BugReportProvider> provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError error) {
                addOnce(failures, "descriptor:service");
                descriptorFailures++;
                continue;
            }

            try {
                Class<? extends BugReportProvider> type = provider.type();
                candidates.add(new ProviderCandidate("service", type, provider::get));
            } catch (ServiceConfigurationError error) {
                addOnce(failures, "descriptor:service");
                descriptorFailures++;
            }
        }
        if (descriptorFailures == MAX_DESCRIPTOR_FAILURES) {
            failures.add("descriptor-limit:service");
        }
        return new ProviderCandidates(candidates, failures);
    }

    private static void addOnce(List<String> failures, String failure) {
        if (!failures.contains(failure)) {
            failures.add(failure);
        }
    }
}
