package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import java.util.Objects;

final class ProviderCandidateInstantiator {
    Evaluation instantiate(ProviderCandidate candidate) {
        BugReportProvider provider;
        try {
            provider = candidate.constructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return Evaluation.rejected(
                    ProviderDiagnostic.forClass(
                            ProviderDiagnosticCode.INSTANTIATION_FAILED,
                            candidate.ownerModId(),
                            candidate.className()));
        }

        return Evaluation.accepted(
                new DiscoveredProvider(
                        NamespaceId.of(candidate.ownerModId()),
                        candidate.className(),
                        provider));
    }

    record Evaluation(
            DiscoveredProvider provider,
            ProviderDiagnostic diagnostic) {
        Evaluation {
            if ((provider == null) == (diagnostic == null)) {
                throw new IllegalArgumentException(
                        "Exactly one instantiation outcome is required");
            }
        }

        static Evaluation accepted(DiscoveredProvider provider) {
            return new Evaluation(Objects.requireNonNull(provider), null);
        }

        static Evaluation rejected(ProviderDiagnostic diagnostic) {
            return new Evaluation(null, Objects.requireNonNull(diagnostic));
        }
    }
}
