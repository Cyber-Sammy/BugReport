package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

@Mod(DiscoveryProbeMod.MOD_ID)
public final class DiscoveryProbeMod {
    public static final String MOD_ID = "bugreport_discovery_probe";
    private static final String PROVIDER_PROPERTY = "bugreportProviders";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DiscoveryProbeMod() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        DiscoveryResult serviceResult = discoverServices();
        DiscoveryResult metadataResult = discoverMetadata();
        writeMarker(serviceResult, metadataResult);
        LOGGER.info(
                "Provider discovery comparison completed: services={}, metadata={}",
                serviceResult.providers(),
                metadataResult.providers());
        event.getServer().halt(false);
    }

    private static DiscoveryResult discoverServices() {
        List<Candidate> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        try {
            ServiceLoader.load(BugReportProvider.class)
                    .stream()
                    .forEach(
                            provider -> {
                                try {
                                    Class<? extends BugReportProvider> type = provider.type();
                                    candidates.add(
                                            new Candidate(
                                                    "service",
                                                    type.getName(),
                                                    provider::get));
                                } catch (ServiceConfigurationError error) {
                                    failures.add("descriptor:service");
                                }
                            });
        } catch (ServiceConfigurationError error) {
            failures.add("descriptor:service");
        }
        return evaluate(candidates, failures);
    }

    private static DiscoveryResult discoverMetadata() {
        List<Candidate> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(
                        modInfo -> {
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
                                candidates.add(
                                        new Candidate(
                                                modInfo.getModId(),
                                                className,
                                                () ->
                                                        Class.forName(className)
                                                                .getDeclaredConstructor()
                                                                .newInstance()));
                            }
                        });
        return evaluate(candidates, failures);
    }

    private static DiscoveryResult evaluate(
            List<Candidate> candidates, List<String> initialFailures) {
        List<String> failures = new ArrayList<>(initialFailures);
        Map<String, List<String>> providersById = new HashMap<>();
        candidates.stream()
                .sorted(
                        Comparator.comparing(Candidate::owner)
                                .thenComparing(Candidate::className))
                .forEach(
                        candidate -> {
                            try {
                                // Fixture @Mod providers deliberately have side-effect-free
                                // constructors; this spike creates them again only to verify
                                // discovery and binary linkage.
                                Object instance = candidate.factory().create();
                                if (!(instance instanceof BugReportProvider provider)) {
                                    failures.add("invalid:" + candidate.className());
                                    return;
                                }
                                String providerId = provider.providerId();
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
        return new DiscoveryResult(List.copyOf(accepted), List.copyOf(failures));
    }

    private static void writeMarker(
            DiscoveryResult serviceResult, DiscoveryResult metadataResult) {
        String apiVersion = BugReportProvider.class.getPackage().getImplementationVersion();
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalStateException("Selected Bug Report API has no implementation version");
        }

        List<String> lines =
                List.of(
                        "phase=STARTED",
                        "selected=mod_metadata",
                        "serviceProviders=" + String.join(",", serviceResult.providers()),
                        "serviceFailures=" + String.join(",", serviceResult.failures()),
                        "metadataProviders=" + String.join(",", metadataResult.providers()),
                        "metadataFailures=" + String.join(",", metadataResult.failures()),
                        "api=" + apiVersion,
                        "loader=" + BugReportProvider.class.getClassLoader().getClass().getName());
        Path marker = FMLPaths.GAMEDIR.get().resolve("bugreport-discovery.marker");
        try {
            Files.write(marker, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write provider discovery marker", exception);
        }
    }

    @FunctionalInterface
    private interface ProviderFactory {
        Object create() throws ReflectiveOperationException;
    }

    private record Candidate(String owner, String className, ProviderFactory factory) {}

    private record DiscoveryResult(List<String> providers, List<String> failures) {}
}
