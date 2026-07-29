package com.cybersammy.bugreport.fixtures.providerb;

import com.cybersammy.bugreport.api.BugReportProvider;
import net.neoforged.fml.common.Mod;

@Mod(ProviderBMod.MOD_ID)
public final class ProviderBMod implements BugReportProvider {
    public static final String MOD_ID = "bugreport_provider_b";

    @Override
    public String providerId() {
        return MOD_ID;
    }
}
