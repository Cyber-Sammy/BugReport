package com.cybersammy.bugreport.fixtures.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;

final class ProviderCandidateEvaluator {
    ProviderDiscoveryResult evaluate(ProviderCandidates discovery) {
        List<String> failures = new ArrayList<>(discovery.failures());
        Map<String, List<String>> providersById = new HashMap<>();
        discovery.candidates().stream()
                .sorted(
                        Comparator.comparing(ProviderCandidate::owner)
                                .thenComparing(ProviderCandidate::className))
                .forEach(
                        candidate -> {
                            try {
                                // Fixture @Mod providers deliberately have side-effect-free
                                // constructors; this spike creates them again only to verify
                                // discovery and binary linkage.
                                String providerId = candidate.factory().create().providerId();
                                if (providerId == null || providerId.isBlank()) {
                                    failures.add("id:" + candidate.className());
                                    return;
                                }
                                providersById
                                        .computeIfAbsent(providerId, ignored -> new ArrayList<>())
                                        .add(candidate.className());
                            } catch (ReflectiveOperationException
                                    | ServiceConfigurationError
                                    | RuntimeException
                                    | LinkageError exception) {
                                failures.add("load:" + candidate.className());
                            }
                        });

        List<String> accepted = new ArrayList<>();
        providersById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            if (entry.getValue().size() == 1) {
                                accepted.add(entry.getKey());
                            } else {
                                failures.add("duplicate:" + entry.getKey());
                            }
                        });
        failures.sort(String::compareTo);
        return new ProviderDiscoveryResult(accepted, failures);
    }
}
