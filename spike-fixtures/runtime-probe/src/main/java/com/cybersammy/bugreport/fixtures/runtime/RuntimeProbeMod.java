package com.cybersammy.bugreport.fixtures.runtime;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(RuntimeProbeMod.MOD_ID)
public final class RuntimeProbeMod {
    public static final String MOD_ID = "bugreport_spike_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROVIDER_A = "bugreport_provider_a";
    private static final String PROVIDER_B = "bugreport_provider_b";
    private static final String EXAMPLE_PROVIDER = "bugreport_example";
    private static final List<String> OBSERVED_MODS =
            List.of("bugreport", EXAMPLE_PROVIDER, PROVIDER_A, PROVIDER_B);
    private static final List<String> OBSERVED_PROVIDERS =
            List.of(EXAMPLE_PROVIDER, PROVIDER_A, PROVIDER_B);
    private static final Map<String, String> PROVIDER_CLASSES =
            Map.of(
                    EXAMPLE_PROVIDER,
                    "com.cybersammy.bugreport.example.ExampleBugReportProvider",
                    PROVIDER_A,
                    "com.cybersammy.bugreport.fixtures.providera.ProviderAMod",
                    PROVIDER_B,
                    "com.cybersammy.bugreport.fixtures.providerb.ProviderBMod");

    public RuntimeProbeMod() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        String apiVersion = BugReportProvider.class.getPackage().getImplementationVersion();
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalStateException("Selected Bug Report API has no implementation version");
        }

        List<String> loadedMods =
                OBSERVED_MODS.stream().filter(ModList.get()::isLoaded).sorted().toList();
        List<String> providerVersions = observeProviderVersions(apiVersion);
        Path marker = FMLPaths.GAMEDIR.get().resolve("bugreport-spike.marker");
        List<String> markerLines =
                List.of(
                        "phase=STARTED",
                        "roles=" + String.join(",", loadedMods),
                        "api=" + apiVersion,
                        "providerVersions=" + String.join(",", providerVersions),
                        "source=" + apiSource(),
                        "loader=" + BugReportProvider.class.getClassLoader().getClass().getName());

        try {
            Files.write(marker, markerLines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write API spike marker", exception);
        }

        LOGGER.info(
                "Bug Report API packaging scenario passed runtime observation with API {}",
                apiVersion);
        event.getServer().halt(false);
    }

    private static List<String> observeProviderVersions(String apiVersion) {
        if ("0.1.0".equals(apiVersion)) {
            return List.of();
        }

        return OBSERVED_PROVIDERS.stream()
                .filter(ModList.get()::isLoaded)
                .map(
                        modId -> {
                            BugReportProvider provider = instantiateProvider(modId);
                            return provider.providerId() + ":" + provider.providerVersion();
                        })
                .sorted()
                .toList();
    }

    private static BugReportProvider instantiateProvider(String modId) {
        String className = PROVIDER_CLASSES.get(modId);
        try {
            // Fixture @Mod classes deliberately have side-effect-free constructors.
            // Re-instantiation is limited to exercising binary linkage against the selected API.
            Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
            if (instance instanceof BugReportProvider provider) {
                return provider;
            }
            throw new IllegalStateException(
                    "Mod " + modId + " does not implement BugReportProvider");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to instantiate loaded provider " + modId, exception);
        }
    }

    private static String apiSource() {
        if (BugReportProvider.class.getProtectionDomain().getCodeSource() == null) {
            return "unknown";
        }
        URL location = BugReportProvider.class.getProtectionDomain().getCodeSource().getLocation();
        return location == null ? "unknown" : location.toExternalForm();
    }
}
