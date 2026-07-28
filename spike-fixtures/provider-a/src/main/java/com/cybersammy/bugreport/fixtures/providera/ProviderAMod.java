package com.cybersammy.bugreport.fixtures.providera;

import com.cybersammy.bugreport.api.ApiRuntime;
import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(ProviderAMod.MOD_ID)
public final class ProviderAMod implements BugReportProvider {
    public static final String MOD_ID = "bugreport_provider_a";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ProviderAMod() {
        writeProbe("LOADED");
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    @Override
    public String providerId() {
        return MOD_ID;
    }

    private void onServerStarted(ServerStartedEvent event) {
        writeProbe("STARTED");
        if (MOD_ID.equals(System.getProperty("bugreport.spike.exitOwner"))) {
            event.getServer().halt(false);
        }
    }

    private static void writeProbe(String phase) {
        String line =
                "phase="
                        + phase
                        + " role="
                        + MOD_ID
                        + " api="
                        + ApiRuntime.version()
                        + " source="
                        + apiSource()
                        + " loader="
                        + ApiRuntime.class.getClassLoader().getClass().getName();
        LOGGER.info("BUGREPORT_API_SPIKE {}", line);
        appendMarker(line);
    }

    private static void appendMarker(String line) {
        String marker = System.getProperty("bugreport.spike.marker");
        if (marker == null || marker.isBlank()) {
            return;
        }
        Path markerPath = Path.of(marker);
        try {
            Path parent = markerPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    markerPath,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write API spike marker", exception);
        }
    }

    private static String apiSource() {
        URL location = ApiRuntime.class.getProtectionDomain().getCodeSource().getLocation();
        return location == null ? "unknown" : location.toExternalForm();
    }
}
