package com.cybersammy.bugreport.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.core.diagnostic.DiagnosticLogValue;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ProviderRegistryDiagnosticTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("diagnosticContracts")
    void exposesExactPathAndStableMessage(
            ProviderRegistryDiagnosticCode code,
            String expectedPath,
            String expectedMessage) {
        ProviderRegistryDiagnostic diagnostic =
                new ProviderRegistryDiagnostic(
                        code,
                        NamespaceId.of("test_mod"),
                        "com.example.Provider",
                        null,
                        null,
                        null);

        assertEquals(expectedPath, diagnostic.path().toString());
        assertEquals(expectedMessage, diagnostic.message());
        assertTrue(
                diagnostic.logToken().contains(
                        "|path=" + DiagnosticLogValue.render(expectedPath)));
        assertTrue(
                diagnostic.logToken().endsWith(
                        "|message=" + DiagnosticLogValue.render(expectedMessage)));
    }

    private static Stream<Arguments> diagnosticContracts() {
        return Stream.of(
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_ID_FAILED,
                        "$.providerId",
                        "Calling providerId() failed."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.INVALID_PROVIDER_ID,
                        "$.providerId",
                        "Provider ID is not a valid canonical provider identifier."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_ID_OWNERSHIP_MISMATCH,
                        "$.providerId",
                        "Provider ID is outside the declaring module namespace."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_FAILED,
                        "$.specification",
                        "Calling specification() failed."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.NULL_PROVIDER_SPECIFICATION,
                        "$.specification",
                        "specification() returned null instead of Optional."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.MISSING_PROVIDER_SPECIFICATION,
                        "$.specification",
                        "Provider does not declare an M1 specification."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_ID_MISMATCH,
                        "$.specification.id",
                        "Specification ID does not match providerId()."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_VERSION_FAILED,
                        "$.providerVersion",
                        "Calling providerVersion() failed."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_VERSION_MISMATCH,
                        "$.specification.version",
                        "Specification version does not match providerVersion()."),
                Arguments.of(
                        ProviderRegistryDiagnosticCode.DUPLICATE_PROVIDER_ID,
                        "$.providerId",
                        "Provider ID is declared by multiple provider candidates."));
    }
}
