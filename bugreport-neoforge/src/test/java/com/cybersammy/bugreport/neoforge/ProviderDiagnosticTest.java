package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class ProviderDiagnosticTest {
    @Test
    void safelyRendersUntrustedDiscoveryMetadata() {
        ProviderDiagnostic diagnostic =
                ProviderDiagnostic.forClass(
                        ProviderDiagnosticCode.MISSING_CLASS,
                        "mod_a",
                        "Provider|line\né");

        assertEquals(
                "missing|owner=mod_a|class=Provider\\|line\\u000a\\u00e9",
                diagnostic.logToken());
    }
}
