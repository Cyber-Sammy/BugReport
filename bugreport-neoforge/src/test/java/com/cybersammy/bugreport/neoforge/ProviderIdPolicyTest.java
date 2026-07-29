package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ProviderIdPolicyTest {
    @ParameterizedTest
    @CsvSource({
        "example_mod, example_mod",
        "example_mod, example_mod:client",
        "example_mod, example_mod:client_2",
        "a1, a1",
        "a1, a1:b"
    })
    void acceptsOwnedCanonicalIds(String ownerModId, String providerId) {
        assertTrue(ProviderIdPolicy.isValidForOwner(providerId, ownerModId));
    }

    @Test
    void acceptsMaximumLengthComponents() {
        String namespace = "a".repeat(64);
        String localName = "b".repeat(64);

        assertTrue(ProviderIdPolicy.isValidForOwner(namespace, namespace));
        assertTrue(
                ProviderIdPolicy.isValidForOwner(
                        namespace + ":" + localName,
                        namespace));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "example_mod ",
                "Example_mod",
                "éxample_mod",
                "example-mod",
                "example.mod",
                "example_mod:",
                "example_mod:1client",
                "example_mod:_client",
                "example_mod:Client",
                "example_mod:client-name",
                "example_mod:client.name",
                "example_mod:client/name",
                "example_mod:client:extra",
                "other_mod",
                "other_mod:client"
            })
    void rejectsMalformedOrForeignIds(String providerId) {
        assertFalse(
                ProviderIdPolicy.isValidForOwner(
                        providerId,
                        "example_mod"));
    }

    @Test
    void rejectsComponentsOutsideLengthBounds() {
        assertFalse(ProviderIdPolicy.isValidForOwner("a", "a"));

        String longNamespace = "a".repeat(65);
        assertFalse(
                ProviderIdPolicy.isValidForOwner(
                        longNamespace,
                        longNamespace));

        assertFalse(
                ProviderIdPolicy.isValidForOwner(
                        "example_mod:" + "a".repeat(65),
                        "example_mod"));
    }

    @Test
    void reservedNamespaceRequiresMatchingDeclaringMod() {
        assertTrue(ProviderIdPolicy.isValidForOwner("bugreport", "bugreport"));
        assertTrue(
                ProviderIdPolicy.isValidForOwner(
                        "bugreport:general",
                        "bugreport"));
        assertFalse(
                ProviderIdPolicy.isValidForOwner(
                        "bugreport:general",
                        "example_mod"));
    }
}
