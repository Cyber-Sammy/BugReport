package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Physical-client entrypoint that installs the render-thread handoff adapter. */
@Mod(value = BugReportMod.MOD_ID, dist = Dist.CLIENT)
public final class BugReportClientMod {
    public BugReportClientMod(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        NeoForgeGameThreadDispatchers.shared().installClient(command -> {
            try {
                Minecraft.getInstance().execute(Objects.requireNonNull(command, "command"));
                return true;
            } catch (RejectedExecutionException failure) {
                return false;
            }
        });
    }
}
