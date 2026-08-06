package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.workspace.GameThreadDispatcher;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Side-safe NeoForge adapters for the currently available physical game threads. */
public final class NeoForgeGameThreadDispatchers {
    private static final NeoForgeGameThreadDispatchers SHARED =
            new NeoForgeGameThreadDispatchers();

    private final AtomicReference<GameThreadDispatcher> client = new AtomicReference<>();
    private final AtomicReference<MinecraftServer> server = new AtomicReference<>();

    NeoForgeGameThreadDispatchers() {}

    /** Returns the process-wide lifecycle-backed dispatcher registry. */
    public static NeoForgeGameThreadDispatchers shared() {
        return SHARED;
    }

    /** Installs the physical-client adapter from the isolated client entrypoint. */
    public void installClient(GameThreadDispatcher dispatcher) {
        if (!client.compareAndSet(null, Objects.requireNonNull(dispatcher, "dispatcher"))) {
            throw new IllegalStateException("Client game-thread dispatcher is already installed");
        }
    }

    /** Returns a stable dispatcher that resolves current lifecycle availability per request. */
    public GameThreadDispatcher dispatcher(SupportedSide side) {
        Objects.requireNonNull(side, "side");
        return side == SupportedSide.PHYSICAL_CLIENT
                ? command -> dispatchClient(command)
                : command -> dispatchServer(command);
    }

    void onServerStarted(ServerStartedEvent event) {
        MinecraftServer started = Objects.requireNonNull(event, "event").getServer();
        if (!server.compareAndSet(null, started)) {
            throw new IllegalStateException("Server game-thread dispatcher is already installed");
        }
    }

    void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer stopped = Objects.requireNonNull(event, "event").getServer();
        server.compareAndSet(stopped, null);
    }

    private boolean dispatchClient(Runnable command) {
        GameThreadDispatcher dispatcher = client.get();
        return dispatcher != null && dispatcher.dispatch(Objects.requireNonNull(command, "command"));
    }

    private boolean dispatchServer(Runnable command) {
        MinecraftServer current = server.get();
        if (current == null) {
            return false;
        }
        try {
            current.execute(Objects.requireNonNull(command, "command"));
            return true;
        } catch (RejectedExecutionException failure) {
            return false;
        }
    }
}
