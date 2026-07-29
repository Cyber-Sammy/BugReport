package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

final class MetadataProviderDiscovery {
    private static final String PROVIDER_PROPERTY = "bugreportProviders";
    private final ClassLoader providerClassLoader;

    MetadataProviderDiscovery(ClassLoader providerClassLoader) {
        this.providerClassLoader = Objects.requireNonNull(providerClassLoader);
    }

    ProviderCandidates discover() {
        List<ProviderCandidate> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(modInfo -> discoverModCandidates(modInfo, candidates, failures));
        return new ProviderCandidates(candidates, failures);
    }

    private void discoverModCandidates(
            IModInfo modInfo,
            List<ProviderCandidate> candidates,
            List<String> failures) {
        Object property = modInfo.getModProperties().get(PROVIDER_PROPERTY);
        if (property == null) {
            return;
        }
        if (!(property instanceof List<?> classNames)) {
            failures.add("property:" + modInfo.getModId());
            return;
        }
        for (Object value : classNames) {
            if (!(value instanceof String className) || className.isBlank()) {
                failures.add("property:" + modInfo.getModId());
                continue;
            }
            discoverCandidate(modInfo, className, candidates, failures);
        }
    }

    private void discoverCandidate(
            IModInfo modInfo,
            String className,
            List<ProviderCandidate> candidates,
            List<String> failures) {
        Class<?> type;
        try {
            type = Class.forName(className, false, providerClassLoader);
        } catch (ClassNotFoundException exception) {
            failures.add("missing:" + className);
            return;
        } catch (LinkageError | RuntimeException exception) {
            failures.add("load:" + className);
            return;
        }

        String expectedModule = modInfo.getOwningFile().moduleName();
        if (!Objects.equals(expectedModule, type.getModule().getName())) {
            failures.add("ownership:" + className);
            return;
        }
        if (!BugReportProvider.class.isAssignableFrom(type)) {
            failures.add("invalid:" + className);
            return;
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            failures.add("abstract:" + className);
            return;
        }

        Class<? extends BugReportProvider> providerType =
                type.asSubclass(BugReportProvider.class);
        Constructor<? extends BugReportProvider> constructor;
        try {
            constructor = providerType.getConstructor();
        } catch (NoSuchMethodException | SecurityException exception) {
            failures.add("constructor:" + className);
            return;
        }
        candidates.add(
                new ProviderCandidate(
                        modInfo.getModId(),
                        providerType,
                        constructor::newInstance));
    }
}
