package com.cybersammy.bugreport.fixtures.runtime;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    private static final List<String> OBSERVED_MODS =
            List.of("bugreport", "bugreport_provider_a", "bugreport_provider_b");

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
        Path marker = FMLPaths.GAMEDIR.get().resolve("bugreport-spike.marker");
        List<String> markerLines =
                List.of(
                        "phase=STARTED",
                        "roles=" + String.join(",", loadedMods),
                        "api=" + apiVersion,
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

    private static String apiSource() {
        if (BugReportProvider.class.getProtectionDomain().getCodeSource() == null) {
            return "unknown";
        }
        URL location = BugReportProvider.class.getProtectionDomain().getCodeSource().getLocation();
        return location == null ? "unknown" : location.toExternalForm();
    }
}
