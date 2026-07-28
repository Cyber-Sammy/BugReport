package com.cybersammy.bugreport.fixtures.providerincompatible;

import com.cybersammy.bugreport.api.ApiRuntime;
import com.cybersammy.bugreport.api.BugReportProvider;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(IncompatibleProviderMod.MOD_ID)
public final class IncompatibleProviderMod implements BugReportProvider {
    public static final String MOD_ID = "bugreport_provider_incompatible";
    private static final Logger LOGGER = LogUtils.getLogger();

    public IncompatibleProviderMod() {
        LOGGER.info(
                "BUGREPORT_API_SPIKE phase=LOADED role={} api={}",
                MOD_ID,
                ApiRuntime.version());
    }

    @Override
    public String providerId() {
        return MOD_ID;
    }
}
