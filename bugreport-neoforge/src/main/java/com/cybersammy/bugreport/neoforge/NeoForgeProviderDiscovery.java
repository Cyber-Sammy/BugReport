package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

final class NeoForgeProviderDiscovery {
    private static final String PROVIDER_PROPERTY = "bugreportProviders";

    private final ProviderCandidateEvaluator candidateEvaluator;
    private final ProviderCandidateInstantiator candidateInstantiator;

    private NeoForgeProviderDiscovery(
            ProviderCandidateEvaluator candidateEvaluator,
            ProviderCandidateInstantiator candidateInstantiator) {
        this.candidateEvaluator = candidateEvaluator;
        this.candidateInstantiator = candidateInstantiator;
    }

    static ProviderDiscoverySnapshot discover() {
        return new NeoForgeProviderDiscovery(
                new ProviderCandidateEvaluator(
                                BugReportProvider.class.getClassLoader()),
                        new ProviderCandidateInstantiator())
                .discoverProviders();
    }

    private ProviderDiscoverySnapshot discoverProviders() {
        List<ProviderCandidate> candidates = new ArrayList<>();
        List<ProviderDiagnostic> diagnostics = new ArrayList<>();

        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(
                        modInfo ->
                                discoverModCandidates(
                                        modInfo,
                                        candidates,
                                        diagnostics));

        List<DiscoveredProvider> discoveredProviders = new ArrayList<>();
        candidates.stream()
                .sorted(
                        Comparator.comparing(ProviderCandidate::ownerModId)
                                .thenComparing(ProviderCandidate::className))
                .map(candidateInstantiator::instantiate)
                .forEach(
                        evaluation -> {
                            if (evaluation.provider() != null) {
                                discoveredProviders.add(evaluation.provider());
                            } else {
                                diagnostics.add(evaluation.diagnostic());
                            }
                        });

        diagnostics.sort(Comparator.comparing(ProviderDiagnostic::logToken));
        ProviderRegistrySnapshot registry =
                ProviderRegistry.createSnapshot(discoveredProviders);
        return new ProviderDiscoverySnapshot(registry, diagnostics);
    }

    private void discoverModCandidates(
            IModInfo modInfo,
            List<ProviderCandidate> candidates,
            List<ProviderDiagnostic> diagnostics) {
        Object property = modInfo.getModProperties().get(PROVIDER_PROPERTY);
        if (property == null) {
            return;
        }
        if (!(property instanceof List<?> declarations)) {
            diagnostics.add(
                    ProviderDiagnostic.forClass(
                            ProviderDiagnosticCode.INVALID_PROPERTY,
                            modInfo.getModId(),
                            PROVIDER_PROPERTY));
            return;
        }

        List<String> classNames = new ArrayList<>();
        for (Object declaration : declarations) {
            if (declaration instanceof String className && !className.isBlank()) {
                classNames.add(className);
            } else {
                diagnostics.add(
                        ProviderDiagnostic.forClass(
                                ProviderDiagnosticCode.INVALID_PROPERTY,
                                modInfo.getModId(),
                                PROVIDER_PROPERTY));
            }
        }

        classNames.stream()
                .sorted()
                .map(className -> candidateEvaluator.evaluate(modInfo, className))
                .forEach(
                        evaluation -> {
                            if (evaluation.candidate() != null) {
                                candidates.add(evaluation.candidate());
                            } else {
                                diagnostics.add(evaluation.diagnostic());
                            }
                        });
    }
}
