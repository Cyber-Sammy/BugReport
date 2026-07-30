package com.cybersammy.bugreport.fixtures.discovery;

import java.util.List;

record ProviderDiscoveryResult(List<String> providers, List<String> failures) {
    ProviderDiscoveryResult {
        providers = List.copyOf(providers);
        failures = List.copyOf(failures);
    }
}
