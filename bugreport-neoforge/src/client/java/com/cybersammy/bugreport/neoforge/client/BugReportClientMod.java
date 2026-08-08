package com.cybersammy.bugreport.neoforge.client;

import com.cybersammy.bugreport.neoforge.BugReportMod;
import com.cybersammy.bugreport.neoforge.NeoForgeGameThreadDispatchers;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandTree;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Physical-client entrypoint that installs the render-thread handoff adapter. */
@Mod(value = BugReportMod.MOD_ID, dist = Dist.CLIENT)
public final class BugReportClientMod {
    private final BugReportCommandService commands =
            new BugReportCommandService(BugReportMod::providerRegistry);

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
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BugReportCommandTree.register(event.getDispatcher(), commands,
                () -> Minecraft.getInstance().setScreen(new ProviderCategoryScreen(commands)));
    }
}
