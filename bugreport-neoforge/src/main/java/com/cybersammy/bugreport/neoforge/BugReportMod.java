package com.cybersammy.bugreport.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(BugReportMod.MOD_ID)
public final class BugReportMod {
    public static final String MOD_ID = "bugreport";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BugReportMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ProviderDiscoverySnapshot snapshot = NeoForgeProviderDiscovery.discover();
        LOGGER.info(
                "Bug Report provider discovery completed: providers={}, diagnostics={}",
                snapshot.providerIds(),
                snapshot.diagnostics());
    }
}
