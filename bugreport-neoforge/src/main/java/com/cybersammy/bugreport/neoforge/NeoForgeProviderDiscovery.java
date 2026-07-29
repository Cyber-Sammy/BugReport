package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.neoforge.ProviderDiscoverySnapshot.ProviderRegistration;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

final class NeoForgeProviderDiscovery {
    private static final String PROVIDER_PROPERTY = "bugreportProviders";

    private NeoForgeProviderDiscovery() {}

    static ProviderDiscoverySnapshot discover() {
        List<ProviderCandidate> candidates = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        ClassLoader apiClassLoader = BugReportProvider.class.getClassLoader();

        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(
                        modInfo ->
                                discoverModCandidates(
                                        modInfo,
                                        apiClassLoader,
                                        candidates,
                                        diagnostics));

        return instantiateCandidates(candidates, diagnostics);
    }

    private static void discoverModCandidates(
            IModInfo modInfo,
            ClassLoader apiClassLoader,
            List<ProviderCandidate> candidates,
            List<String> diagnostics) {
        Object property = modInfo.getModProperties().get(PROVIDER_PROPERTY);
        if (property == null) {
            return;
        }
        if (!(property instanceof List<?> declarations)) {
            diagnostics.add(diagnostic("property", modInfo.getModId(), PROVIDER_PROPERTY));
            return;
        }

        List<String> classNames = new ArrayList<>();
        for (Object declaration : declarations) {
            if (declaration instanceof String className && !className.isBlank()) {
                classNames.add(className);
            } else {
                diagnostics.add(
                        diagnostic("property", modInfo.getModId(), PROVIDER_PROPERTY));
            }
        }
        classNames.stream()
                .sorted()
                .forEach(
                        className ->
                                loadCandidate(
                                        modInfo,
                                        className,
                                        apiClassLoader,
                                        candidates,
                                        diagnostics));
    }

    private static void loadCandidate(
            IModInfo modInfo,
            String className,
            ClassLoader apiClassLoader,
            List<ProviderCandidate> candidates,
            List<String> diagnostics) {
        Class<?> type;
        try {
            type = Class.forName(className, false, apiClassLoader);
        } catch (ClassNotFoundException exception) {
            diagnostics.add(diagnostic("missing", modInfo.getModId(), className));
            return;
        } catch (LinkageError | RuntimeException exception) {
            diagnostics.add(diagnostic("load", modInfo.getModId(), className));
            return;
        }

        if (!Objects.equals(
                modInfo.getOwningFile().moduleName(),
                type.getModule().getName())) {
            diagnostics.add(diagnostic("ownership", modInfo.getModId(), className));
            return;
        }
        if (!BugReportProvider.class.isAssignableFrom(type)) {
            diagnostics.add(diagnostic("invalid", modInfo.getModId(), className));
            return;
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            diagnostics.add(diagnostic("access", modInfo.getModId(), className));
            return;
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            diagnostics.add(diagnostic("abstract", modInfo.getModId(), className));
            return;
        }

        Class<? extends BugReportProvider> providerType =
                type.asSubclass(BugReportProvider.class);
        Constructor<? extends BugReportProvider> constructor;
        try {
            constructor = providerType.getConstructor();
        } catch (NoSuchMethodException | SecurityException exception) {
            diagnostics.add(diagnostic("constructor", modInfo.getModId(), className));
            return;
        }
        candidates.add(
                new ProviderCandidate(
                        modInfo.getModId(),
                        className,
                        constructor));
    }

    private static ProviderDiscoverySnapshot instantiateCandidates(
            List<ProviderCandidate> candidates,
            List<String> initialDiagnostics) {
        List<String> diagnostics = new ArrayList<>(initialDiagnostics);
        Map<String, List<ProviderRegistration>> registrationsById = new HashMap<>();

        candidates.stream()
                .sorted(
                        Comparator.comparing(ProviderCandidate::ownerModId)
                                .thenComparing(ProviderCandidate::className))
                .forEach(
                        candidate -> {
                            BugReportProvider provider;
                            try {
                                provider = candidate.constructor().newInstance();
                            } catch (ReflectiveOperationException
                                    | RuntimeException
                                    | LinkageError exception) {
                                diagnostics.add(
                                        diagnostic(
                                                "load",
                                                candidate.ownerModId(),
                                                candidate.className()));
                                return;
                            }

                            String providerId;
                            try {
                                providerId = provider.providerId();
                            } catch (RuntimeException | LinkageError exception) {
                                diagnostics.add(
                                        diagnostic(
                                                "id",
                                                candidate.ownerModId(),
                                                candidate.className()));
                                return;
                            }
                            if (providerId == null || providerId.isBlank()) {
                                diagnostics.add(
                                        diagnostic(
                                                "id",
                                                candidate.ownerModId(),
                                                candidate.className()));
                                return;
                            }
                            registrationsById
                                    .computeIfAbsent(
                                            providerId,
                                            ignored -> new ArrayList<>())
                                    .add(
                                            new ProviderRegistration(
                                                    candidate.ownerModId(),
                                                    providerId,
                                                    provider));
                        });

        List<ProviderRegistration> accepted = new ArrayList<>();
        registrationsById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            if (entry.getValue().size() == 1) {
                                accepted.add(entry.getValue().getFirst());
                                return;
                            }
                            entry.getValue().stream()
                                    .sorted(
                                            Comparator.comparing(
                                                    ProviderRegistration::ownerModId))
                                    .forEach(
                                            registration ->
                                                    diagnostics.add(
                                                            duplicateDiagnostic(
                                                                    entry.getKey(),
                                                                    registration)));
                        });
        diagnostics.sort(String::compareTo);
        return new ProviderDiscoverySnapshot(accepted, diagnostics);
    }

    private static String diagnostic(String code, String ownerModId, String className) {
        return code + "|" + ownerModId + "|" + className;
    }

    private static String duplicateDiagnostic(
            String providerId,
            ProviderRegistration registration) {
        return "duplicate|"
                + providerId
                + "|"
                + registration.ownerModId()
                + "|"
                + registration.provider().getClass().getName();
    }

    private record ProviderCandidate(
            String ownerModId,
            String className,
            Constructor<? extends BugReportProvider> constructor) {}
}
