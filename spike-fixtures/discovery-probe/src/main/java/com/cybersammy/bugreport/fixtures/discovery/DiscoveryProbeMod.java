package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(DiscoveryProbeMod.MOD_ID)
public final class DiscoveryProbeMod {
    public static final String MOD_ID = "bugreport_discovery_probe";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DiscoveryProbeMod() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        ClassLoader apiClassLoader = BugReportProvider.class.getClassLoader();
        ProviderCandidateEvaluator evaluator = new ProviderCandidateEvaluator();
        ProviderDiscoveryResult serviceResult =
                evaluator.evaluate(new ServiceProviderDiscovery().discover());
        ProviderCandidates metadataCandidates =
                new MetadataProviderDiscovery(apiClassLoader).discover();
        ProviderDiscoveryResult metadataResult = evaluator.evaluate(metadataCandidates);

        writeMarker(serviceResult, metadataResult, metadataCandidates);
        LOGGER.info(
                "Provider discovery comparison completed: services={}, metadata={}",
                serviceResult.providers(),
                metadataResult.providers());
        event.getServer().halt(false);
    }

    private static void writeMarker(
            ProviderDiscoveryResult serviceResult,
            ProviderDiscoveryResult metadataResult,
            ProviderCandidates metadataCandidates) {
        String apiVersion = BugReportProvider.class.getPackage().getImplementationVersion();
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalStateException("Selected Bug Report API has no implementation version");
        }

        List<String> metadataLoaders =
                metadataCandidates.candidates().stream()
                        .map(candidate -> candidate.type().getClassLoader().getClass().getName())
                        .distinct()
                        .sorted()
                        .toList();
        List<String> metadataModules =
                metadataCandidates.candidates().stream()
                        .map(candidate -> candidate.type().getModule().getName())
                        .distinct()
                        .sorted()
                        .toList();
        List<String> lines =
                List.of(
                        "phase=STARTED",
                        "decision=mod_metadata",
                        "serviceProviders=" + String.join(",", serviceResult.providers()),
                        "serviceFailures=" + String.join(",", serviceResult.failures()),
                        "metadataProviders=" + String.join(",", metadataResult.providers()),
                        "metadataFailures=" + String.join(",", metadataResult.failures()),
                        "api=" + apiVersion,
                        "apiLoader="
                                + BugReportProvider.class.getClassLoader().getClass().getName(),
                        "metadataLoaders=" + String.join(",", metadataLoaders),
                        "metadataLoadersShareApiIdentity="
                                + metadataCandidates.candidates().stream()
                                        .allMatch(
                                                candidate ->
                                                        candidate.type().getClassLoader()
                                                                == BugReportProvider.class
                                                                        .getClassLoader()),
                        "metadataModules=" + String.join(",", metadataModules));
        Path marker = FMLPaths.GAMEDIR.get().resolve("bugreport-discovery.marker");
        try {
            Files.write(marker, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write provider discovery marker", exception);
        }
    }
}
