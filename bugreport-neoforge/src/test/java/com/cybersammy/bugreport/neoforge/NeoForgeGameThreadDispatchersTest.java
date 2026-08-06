package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class NeoForgeGameThreadDispatchersTest {
    @Test
    void rejectsDispatchWhenPhysicalSideLifecycleIsUnavailable() {
        NeoForgeGameThreadDispatchers dispatchers = new NeoForgeGameThreadDispatchers();

        assertFalse(dispatchers.dispatcher(SupportedSide.PHYSICAL_CLIENT).dispatch(() -> {}));
        assertFalse(dispatchers.dispatcher(SupportedSide.DEDICATED_SERVER).dispatch(() -> {}));
    }

    @Test
    void delegatesClientDispatchAfterIsolatedAdapterInstallation() {
        NeoForgeGameThreadDispatchers dispatchers = new NeoForgeGameThreadDispatchers();
        AtomicBoolean invoked = new AtomicBoolean();
        dispatchers.installClient(command -> {
            command.run();
            return true;
        });

        assertTrue(dispatchers
                .dispatcher(SupportedSide.PHYSICAL_CLIENT)
                .dispatch(() -> invoked.set(true)));
        assertTrue(invoked.get());
        assertThrows(
                IllegalStateException.class,
                () -> dispatchers.installClient(command -> true));
    }
}
