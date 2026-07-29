package com.cybersammy.bugreport.fixtures.providera;

import com.cybersammy.bugreport.api.BugReportProvider;
import net.neoforged.fml.common.Mod;

@Mod(ProviderAMod.MOD_ID)
public final class ProviderAMod implements BugReportProvider {
    public static final String MOD_ID = "bugreport_provider_a";

    @Override
    public String providerId() {
        return MOD_ID;
    }
}
