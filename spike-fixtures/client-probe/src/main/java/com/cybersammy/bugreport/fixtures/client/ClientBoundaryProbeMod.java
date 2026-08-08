package com.cybersammy.bugreport.fixtures.client;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

/** Test-only mod that proves Bug Report's client bootstrap is available on a physical client. */
@Mod(value = "bugreport_client_probe", dist = Dist.CLIENT)
public final class ClientBoundaryProbeMod {
    private static final String MOD_ID = "bugreport_client_probe";

    public ClientBoundaryProbeMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("bugreport")) {
            throw new IllegalStateException("Bug Report did not load in the client boundary smoke run");
        }
        boolean accepted = NeoForgeGameThreadDispatchers.shared()
                .dispatcher(SupportedSide.PHYSICAL_CLIENT)
                .dispatch(this::writeMarkerAndStopClient);
        if (!accepted) {
            throw new IllegalStateException("Bug Report client dispatcher was unavailable");
        }
    }

    private void writeMarkerAndStopClient() {
        Path marker = FMLPaths.GAMEDIR.get().resolve("bugreport-client-smoke.marker");
        try {
            Files.writeString(marker, "phase=CLIENT_READY\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write the client boundary smoke marker", exception);
        }
        Thread.ofVirtual().name("bugreport-client-smoke-stop").start(() -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
        });
    }
}
