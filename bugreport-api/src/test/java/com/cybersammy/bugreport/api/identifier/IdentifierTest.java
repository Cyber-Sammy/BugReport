package com.cybersammy.bugreport.api.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

final class IdentifierTest {
    @Test
    void createsTypedIdentifiersWithoutChangingCanonicalText() {
        NamespaceId namespace = NamespaceId.of("example_mod");
        ProviderId provider = ProviderId.namespaced(namespace, "client_2");

        assertEquals("example_mod:client_2", provider.value());
        assertEquals(namespace, provider.namespace());
        assertEquals("client_2", provider.localName().orElseThrow());
        assertTrue(provider.isOwnedBy(namespace));
        assertEquals("general", CategoryId.of("general").value());
        assertEquals("steps", FieldId.of("steps").value());
        assertEquals("latest_log", DiagnosticSourceId.of("latest_log").value());
        assertEquals("environment", DiagnosticGeneratorId.of("environment").value());
        assertEquals("example_mod:json_v1", CapabilityId.of("example_mod:json_v1").value());
        assertEquals("bugreport:local_zip", TransportId.of("bugreport:local_zip").value());
        assertEquals(
                "example_mod:details",
                ExtensionMetadataKey.of("example_mod:details").value());
    }

    @Test
    void acceptsExactComponentLengthBounds() {
        NamespaceId namespace = NamespaceId.of("a".repeat(64));
        ProviderId provider = ProviderId.namespaced(namespace, "b".repeat(64));

        assertEquals(129, provider.value().length());
        assertEquals("a1", NamespaceId.of("a1").value());
        assertEquals("a", CategoryId.of("a").value());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "a",
                "Example_mod",
                "example-mod",
                "example.mod",
                "example_mod ",
                "éxample_mod"
            })
    void rejectsInvalidNamespacesWithoutNormalization(String value) {
        InvalidIdentifierException exception =
                assertThrows(
                        InvalidIdentifierException.class,
                        () -> NamespaceId.of(value));

        assertEquals(IdentifierKind.NAMESPACE, exception.kind());
        assertEquals(value, exception.rejectedValue().orElse(null));
    }

    @Test
    void invalidIdentifierMessageDoesNotEchoRejectedInput() {
        String rejectedValue = "bad\nFORGED_LOG_ENTRY";

        InvalidIdentifierException exception =
                assertThrows(
                        InvalidIdentifierException.class,
                        () -> NamespaceId.of(rejectedValue));

        assertFalse(exception.getMessage().contains(rejectedValue));
        assertEquals(Optional.of(rejectedValue), exception.rejectedValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "example_mod",
                "example_mod:",
                ":client",
                "example_mod:Client",
                "example_mod:client-name",
                "example_mod:client:extra"
            })
    void globallyNamespacedTypesRequireBothCanonicalComponents(String value) {
        assertThrows(InvalidIdentifierException.class, () -> CapabilityId.of(value));
    }

    @Test
    void semanticScopesRemainDistinct() {
        assertNotEquals(CategoryId.of("general"), FieldId.of("general"));
        assertNotEquals(
                DiagnosticSourceId.of("environment"),
                DiagnosticGeneratorId.of("environment"));
    }

    @Test
    void canonicalOrderingUsesExactAsciiOrder() {
        List<CategoryId> ids =
                List.of(CategoryId.of("zeta"), CategoryId.of("alpha"), CategoryId.of("alpha_2"));

        assertEquals(
                List.of(
                        CategoryId.of("alpha"),
                        CategoryId.of("alpha_2"),
                        CategoryId.of("zeta")),
                ids.stream().sorted().toList());
    }
}
