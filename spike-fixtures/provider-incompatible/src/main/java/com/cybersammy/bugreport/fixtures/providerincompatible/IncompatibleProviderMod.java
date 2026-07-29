package com.cybersammy.bugreport.fixtures.providerincompatible;

import com.cybersammy.bugreport.api.BugReportProvider;
import net.neoforged.fml.common.Mod;

@Mod(IncompatibleProviderMod.MOD_ID)
public final class IncompatibleProviderMod implements BugReportProvider {
    public static final String MOD_ID = "bugreport_provider_incompatible";

    @Override
    public String providerId() {
        return MOD_ID;
    }
}
