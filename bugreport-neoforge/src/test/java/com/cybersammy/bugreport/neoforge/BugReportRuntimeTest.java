package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BugReportRuntimeTest {
    @Test
    void retainsOneDiscoverySnapshotAndRejectsReinitialization() {
        ProviderDiscoverySnapshot expected =
                new ProviderDiscoverySnapshot(List.of(), List.of());
        AtomicInteger discoveryCalls = new AtomicInteger();
        BugReportRuntime runtime =
                new BugReportRuntime(
                        () -> {
                            discoveryCalls.incrementAndGet();
                            return expected;
                        });

        runtime.initializeProviders();

        assertSame(expected, runtime.providers());
        assertEquals(1, discoveryCalls.get());
        assertThrows(IllegalStateException.class, runtime::initializeProviders);
        assertSame(expected, runtime.providers());
        assertEquals(1, discoveryCalls.get());
    }

    @Test
    void failedDiscoveryDoesNotMarkRuntimeInitialized() {
        AtomicInteger discoveryAttempts = new AtomicInteger();
        BugReportRuntime runtime =
                new BugReportRuntime(
                        () -> {
                            if (discoveryAttempts.getAndIncrement() == 0) {
                                throw new IllegalStateException("discovery failed");
                            }
                            return ProviderDiscoverySnapshot.empty();
                        });

        assertThrows(IllegalStateException.class, runtime::initializeProviders);

        runtime.initializeProviders();

        assertEquals(2, discoveryAttempts.get());
        assertEquals(List.of(), runtime.providers().providers());
    }
}
