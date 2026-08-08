package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.core.error.DomainFailureException;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import java.util.concurrent.atomic.AtomicReference;

@Mod(BugReportMod.MOD_ID)
public final class BugReportMod {
    public static final String MOD_ID = "bugreport";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StructuredDomainFailureLogger FAILURE_LOGGER =
            new StructuredDomainFailureLogger(LOGGER);
    private static final AtomicReference<ProviderRegistrySnapshot> PROVIDER_REGISTRY =
            new AtomicReference<>(ProviderRegistrySnapshot.empty());
    private final BugReportRuntime runtime;

    public BugReportMod(IEventBus modEventBus) {
        runtime = new BugReportRuntime();
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(NeoForgeGameThreadDispatchers.shared()::onServerStarted);
        NeoForge.EVENT_BUS.addListener(NeoForgeGameThreadDispatchers.shared()::onServerStopping);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        try {
            runtime.initializeProviders();
            ProviderDiscoverySnapshot snapshot = runtime.providers();
            PROVIDER_REGISTRY.set(snapshot.registry());
            LOGGER.info(
                    "Bug Report provider discovery completed: providers={}, supportStates={}, "
                            + "discoveryDiagnostics={}, registryDiagnostics={}, providerCount={}, "
                            + "discoveryDiagnosticCount={}, registryDiagnosticCount={}",
                    snapshot.providerIds(),
                    snapshot.providerStates(),
                    snapshot.discoveryDiagnostics(),
                    snapshot.registry().diagnostics(),
                    snapshot.providerIds().size(),
                    snapshot.discoveryDiagnostics().size(),
                    snapshot.registry().diagnostics().size());
        } catch (DomainFailureException failure) {
            FAILURE_LOGGER.warn(failure);
            throw failure;
        }
    }

    BugReportRuntime runtime() {
        return runtime;
    }

    /** Returns the latest successfully discovered immutable provider registry. */
    public static ProviderRegistrySnapshot providerRegistry() {
        return PROVIDER_REGISTRY.get();
    }
}
