package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

final class NeoForgeProviderDiscovery {
    private static final String PROVIDER_PROPERTY = "bugreportProviders";

    private final ProviderCandidateEvaluator candidateEvaluator;
    private final ProviderRegistryBuilder registryBuilder;

    private NeoForgeProviderDiscovery(
            ProviderCandidateEvaluator candidateEvaluator,
            ProviderRegistryBuilder registryBuilder) {
        this.candidateEvaluator = candidateEvaluator;
        this.registryBuilder = registryBuilder;
    }

    static ProviderDiscoverySnapshot discover() {
        return new NeoForgeProviderDiscovery(
                        new ProviderCandidateEvaluator(
                                BugReportProvider.class.getClassLoader()),
                        new ProviderRegistryBuilder())
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

        return registryBuilder.build(candidates, diagnostics);
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
