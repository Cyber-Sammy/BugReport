package com.cybersammy.bugreport.fixtures.discovery;

import java.util.List;

record ProviderCandidates(List<ProviderCandidate> candidates, List<String> failures) {
    ProviderCandidates {
        candidates = List.copyOf(candidates);
        failures = List.copyOf(failures);
    }
}
