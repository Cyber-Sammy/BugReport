package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.DestinationId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DiagnosticSpecificationTest {
    @Test
    void createsLogicalRootSourceWithoutExposingFilesystemPaths() {
        DiagnosticSourceSpecification source = DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of("debug_log"),
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of("debug/debug.log"))
                .labelKey(key("debug_log"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .build();

        assertEquals(LogicalRoot.GAME_LOGS, source.root().orElseThrow());
        assertEquals("debug/debug.log", source.path().orElseThrow().value());
    }

    @Test
    void preventsSensitiveDiagnosticsFromRequestingDefaultInclusion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiagnosticSourceSpecification.latestLog(DiagnosticSourceId.of("latest_log"))
                        .labelKey(key("latest_log"))
                        .privacy(PrivacyClassification.PERSONAL)
                        .contentType(DiagnosticContentType.TEXT)
                        .inclusionDefault(InclusionDefault.INCLUDED)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .build());
    }

    @Test
    void gameThreadGeneratorRequiresExplicitTimeout() {
        DiagnosticGeneratorSpecification.Builder builder = generatorBuilder()
                .executionContext(GeneratorExecutionContext.GAME_THREAD_SNAPSHOT);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void acceptsBoundedGameThreadGenerator() {
        DiagnosticGeneratorSpecification generator = generatorBuilder()
                .executionContext(GeneratorExecutionContext.GAME_THREAD_SNAPSHOT)
                .constraints(CollectionConstraints.builder()
                        .maxGeneratedArtifacts(1)
                        .maxTotalBytes(4096)
                        .callbackTimeout(Duration.ofMillis(100))
                        .build())
                .build();

        assertEquals(Duration.ofMillis(100), generator.constraints().callbackTimeout().orElseThrow());
    }

    @Test
    void rejectsDestinationTargetThatDoesNotMatchDeclaredType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SupportDestinationSpecification.builder(
                                DestinationId.of("example:email"),
                                SupportDestinationType.EMAIL,
                                TransportId.of("bugreport:email"),
                                SupportDestinationTarget.web(HttpsUrl.of("https://example.com")))
                        .labelKey(key("email"))
                        .build());
    }

    private static DiagnosticGeneratorSpecification.Builder generatorBuilder() {
        return DiagnosticGeneratorSpecification.builder(
                        DiagnosticGeneratorId.of("snapshot"), (request, sink) -> {})
                .labelKey(key("snapshot"))
                .privacy(PrivacyClassification.LOW)
                .contentType(DiagnosticContentType.JSON)
                .supportSide(SupportedSide.PHYSICAL_CLIENT);
    }

    private static LocalizationKey key(String suffix) {
        return LocalizationKey.of("example." + suffix);
    }
}
