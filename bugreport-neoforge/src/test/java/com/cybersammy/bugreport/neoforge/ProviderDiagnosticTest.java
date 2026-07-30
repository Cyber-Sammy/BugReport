package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProviderDiagnosticTest {
    @Test
    void rendersNullAndEmptyInvalidIdsExplicitly() {
        assertEquals(
                "id|<null>|mod_a|Provider",
                invalidProviderId(null).logToken());
        assertEquals(
                "id|<empty>|mod_a|Provider",
                invalidProviderId("").logToken());
        assertEquals(
                "id|\\u0020|mod_a|Provider",
                invalidProviderId(" ").logToken());
    }

    @Test
    void retainsRawInvalidIdWhileEscapingItsLogRepresentation() {
        String invalidId = "Mod_A|line\né";
        ProviderDiagnostic diagnostic = invalidProviderId(invalidId);

        assertEquals(invalidId, diagnostic.providerId());
        assertEquals(
                "id|Mod_A\\|line\\u000a\\u00e9|mod_a|Provider",
                diagnostic.logToken());
    }

    @Test
    void boundsInvalidIdLogRepresentation() {
        String invalidId = "a".repeat(300);
        ProviderDiagnostic diagnostic = invalidProviderId(invalidId);

        assertEquals(invalidId, diagnostic.providerId());
        assertTrue(diagnostic.logToken().contains("...(length=300)"));
        assertTrue(diagnostic.logToken().length() < invalidId.length());
    }

    private static ProviderDiagnostic invalidProviderId(String providerId) {
        return ProviderDiagnostic.forInvalidProviderId(
                "mod_a",
                "Provider",
                providerId);
    }
}
