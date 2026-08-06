package com.cybersammy.bugreport.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BugReportMod.MOD_ID)
public final class BugReportMod {
    public static final String MOD_ID = "bugreport";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BugReportRuntime runtime;

    public BugReportMod(IEventBus modEventBus) {
        runtime = new BugReportRuntime();
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(NeoForgeGameThreadDispatchers.shared()::onServerStarted);
        NeoForge.EVENT_BUS.addListener(NeoForgeGameThreadDispatchers.shared()::onServerStopping);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        runtime.initializeProviders();
        ProviderDiscoverySnapshot snapshot = runtime.providers();
        LOGGER.info(
                "Bug Report provider discovery completed: providers={}, supportStates={}, "
                        + "discoveryDiagnostics={}, registryDiagnostics={}",
                snapshot.providerIds(),
                snapshot.providerStates(),
                snapshot.discoveryDiagnostics(),
                snapshot.registry().diagnostics());
    }

    BugReportRuntime runtime() {
        return runtime;
    }
}
